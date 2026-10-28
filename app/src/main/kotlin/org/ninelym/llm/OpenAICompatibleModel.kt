package org.ninelym.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Endpoint of an OpenAI-compatible server: a locally served model (llama.cpp `llama-server`,
 * Ollama, LM Studio, vLLM...) or a remote API. [baseUrl] includes the API version prefix,
 * e.g. `http://127.0.0.1:8080/v1`.
 */
data class ModelEndpointConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String? = null,
    val timeoutSeconds: Long = 120
) {
    companion object {
        fun fromEnvironment(env: (String) -> String? = System::getenv): ModelEndpointConfig? {
            val baseUrl = env("TOGAI_LLM_BASE_URL")?.takeIf { it.isNotBlank() } ?: return null
            val model = env("TOGAI_LLM_MODEL")?.takeIf { it.isNotBlank() } ?: return null
            return ModelEndpointConfig(baseUrl.trimEnd('/'), model, env("TOGAI_LLM_API_KEY")?.takeIf { it.isNotBlank() })
        }
    }
}

class OpenAICompatibleModel(
    val config: ModelEndpointConfig,
    private val http: OkHttpClient = defaultClient(config.timeoutSeconds)
) : LanguageModel {

    override val modelId: String get() = config.model

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()
    private val base = config.baseUrl.trimEnd('/')

    override suspend fun chat(
        messages: List<ChatMessage>,
        options: GenerationOptions,
        onToken: ((String) -> Unit)?
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val streaming = onToken != null
            val body = buildJsonObject {
                put("model", config.model)
                put("messages", buildJsonArray {
                    messages.forEach { m -> add(buildJsonObject { put("role", m.role.wire); put("content", m.content) }) }
                })
                put("temperature", options.temperature)
                put("top_p", options.topP)
                put("max_tokens", options.maxTokens)
                if (options.stop.isNotEmpty()) put("stop", JsonArray(options.stop.map { JsonPrimitive(it) }))
                if (options.jsonObject) put("response_format", buildJsonObject { put("type", "json_object") })
                put("stream", streaming)
            }
            val request = authorized(Request.Builder().url("$base/chat/completions"))
                .post(body.toString().toRequestBody(jsonType))
                .build()

            execute(request).use { response ->
                if (!response.isSuccessful) {
                    throw ModelResponseException("Model server returned HTTP ${response.code}: ${response.body?.string()?.take(500)}")
                }
                val text = if (streaming) readStream(response, onToken!!) else readCompletion(response)
                if (text.isBlank()) throw ModelResponseException("Model returned an empty response")
                text
            }
        }
    }

    private fun readCompletion(response: Response): String {
        val root = json.parseToJsonElement(response.body!!.string()).jsonObject
        root["error"]?.let { throw ModelResponseException("Model error: $it") }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw ModelResponseException("Model response had no choices")
        return choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
    }

    /** Server-sent events: `data: {json}` lines, terminated by `data: [DONE]`. */
    private fun readStream(response: Response, onToken: (String) -> Unit): String {
        val out = StringBuilder()
        val source = response.body!!.source()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            if (data.isEmpty()) continue
            val event = json.parseToJsonElement(data).jsonObject
            event["error"]?.let { throw ModelResponseException("Model error: $it") }
            val delta = event["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            if (!delta.isNullOrEmpty()) {
                out.append(delta)
                onToken(delta)
            }
        }
        return out.toString()
    }

    /** `GET /models`: confirms the server is up and serving. */
    suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            execute(authorized(Request.Builder().url("$base/models")).get().build()).use { response ->
                if (!response.isSuccessful) throw ModelResponseException("Model server returned HTTP ${response.code}")
                val data = json.parseToJsonElement(response.body!!.string()).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
                data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            }
        }
    }

    suspend fun embed(texts: List<String>, model: String = config.model): Result<List<FloatArray>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("model", model)
                put("input", JsonArray(texts.map { JsonPrimitive(it) }))
            }
            execute(authorized(Request.Builder().url("$base/embeddings")).post(body.toString().toRequestBody(jsonType)).build()).use { response ->
                if (!response.isSuccessful) throw ModelResponseException("Embedding request failed: HTTP ${response.code}")
                json.parseToJsonElement(response.body!!.string()).jsonObject["data"]!!.jsonArray.map { item ->
                    item.jsonObject["embedding"]!!.jsonArray.map { it.jsonPrimitive.floatOrNull ?: 0f }.toFloatArray()
                }
            }
        }
    }

    /** Speech-to-text via `POST /audio/transcriptions` (Whisper-compatible servers). */
    suspend fun transcribe(audio: File, model: String = "whisper-1"): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", audio.name, audio.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            execute(authorized(Request.Builder().url("$base/audio/transcriptions")).post(form).build()).use { response ->
                if (!response.isSuccessful) throw ModelResponseException("Transcription failed: HTTP ${response.code}")
                json.parseToJsonElement(response.body!!.string()).jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: throw ModelResponseException("Transcription response had no text")
            }
        }
    }

    /** Text-to-speech via `POST /audio/speech`; returns the encoded audio bytes. */
    suspend fun speech(text: String, voice: String = "alloy", model: String = "tts-1"): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject { put("model", model); put("input", text); put("voice", voice) }
            execute(authorized(Request.Builder().url("$base/audio/speech")).post(body.toString().toRequestBody(jsonType)).build()).use { response ->
                if (!response.isSuccessful) throw ModelResponseException("Speech synthesis failed: HTTP ${response.code}")
                response.body!!.bytes()
            }
        }
    }

    /** Image generation via `POST /images/generations`; returns decoded image bytes. */
    suspend fun generateImage(prompt: String, size: String = "512x512", model: String = config.model): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("model", model); put("prompt", prompt); put("size", size); put("response_format", "b64_json")
                }
                execute(authorized(Request.Builder().url("$base/images/generations")).post(body.toString().toRequestBody(jsonType)).build()).use { response ->
                    if (!response.isSuccessful) throw ModelResponseException("Image generation failed: HTTP ${response.code}")
                    val first = json.parseToJsonElement(response.body!!.string()).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: throw ModelResponseException("Image response had no data")
                    val b64 = first["b64_json"]?.jsonPrimitive?.contentOrNull
                        ?: throw ModelResponseException("Image response had no b64_json")
                    Base64.getDecoder().decode(b64)
                }
            }
        }

    private fun authorized(builder: Request.Builder): Request.Builder =
        config.apiKey?.let { builder.header("Authorization", "Bearer $it") } ?: builder

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call: Call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        try {
            cont.resume(call.execute())
        } catch (e: Exception) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    }

    companion object {
        fun defaultClient(timeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds * 2, TimeUnit.SECONDS)
            .build()
    }
}

/** Process-wide model selection. There is no fallback model: unconfigured means failure. */
object LanguageModels {
    @Volatile
    private var configured: LanguageModel? = null

    fun configure(model: LanguageModel) {
        configured = model
    }

    fun configure(config: ModelEndpointConfig) {
        configured = OpenAICompatibleModel(config)
    }

    fun current(): Result<LanguageModel> {
        configured?.let { return Result.success(it) }
        val fromEnv = ModelEndpointConfig.fromEnvironment() ?: return Result.failure(ModelNotConfiguredException())
        return Result.success(OpenAICompatibleModel(fromEnv).also { configured = it })
    }

    /** The configured OpenAI-compatible model, for endpoints beyond chat (embeddings, audio, images). */
    fun currentEndpoint(): Result<OpenAICompatibleModel> = current().mapCatching {
        it as? OpenAICompatibleModel
            ?: throw IllegalStateException("The configured model (${it.modelId}) has no OpenAI-compatible endpoint for this operation")
    }

    /** Run [block] with the configured model, propagating "not configured" as a failure. */
    suspend fun <T> with(block: suspend (LanguageModel) -> Result<T>): Result<T> =
        current().fold(onSuccess = { block(it) }, onFailure = { Result.failure(it) })
}

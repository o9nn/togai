package org.ninelym.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

enum class Role(val wire: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }

data class ChatMessage(val role: Role, val content: String) {
    companion object {
        fun system(content: String) = ChatMessage(Role.SYSTEM, content)
        fun user(content: String) = ChatMessage(Role.USER, content)
        fun assistant(content: String) = ChatMessage(Role.ASSISTANT, content)
    }
}

data class GenerationOptions(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512,
    val stop: List<String> = emptyList(),
    /** Ask the server for a JSON object response (OpenAI `response_format`). */
    val jsonObject: Boolean = false
)

/**
 * A real language model. Every implementation must obtain its text from a model that is
 * actually being run (a locally served model or a remote API); there is deliberately no
 * stub/offline implementation.
 */
interface LanguageModel {
    val modelId: String

    /**
     * Generate the next assistant message. When [onToken] is given the response is streamed
     * from the model and each received chunk is passed to it as it arrives.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        options: GenerationOptions = GenerationOptions(),
        onToken: ((String) -> Unit)? = null
    ): Result<String>
}

class ModelNotConfiguredException :
    IllegalStateException(
        "No language model is configured. Set an OpenAI-compatible endpoint (for example a local " +
            "llama.cpp server or Ollama at http://127.0.0.1:11434/v1) via LanguageModels.configure(...) " +
            "or the TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL environment variables."
    )

class ModelResponseException(message: String) : RuntimeException(message)

suspend fun LanguageModel.ask(
    system: String,
    user: String,
    options: GenerationOptions = GenerationOptions(),
    onToken: ((String) -> Unit)? = null
): Result<String> = chat(listOf(ChatMessage.system(system), ChatMessage.user(user)), options, onToken)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Ask for a JSON answer and parse it. Fails if the model's output isn't valid JSON, rather
 * than substituting a default.
 */
suspend fun LanguageModel.askJson(
    system: String,
    user: String,
    options: GenerationOptions = GenerationOptions(temperature = 0.2f)
): Result<JsonElement> = ask(system, user, options.copy(jsonObject = true)).mapCatching { text ->
    val start = text.indexOfFirst { it == '{' || it == '[' }
    val end = text.indexOfLast { it == '}' || it == ']' }
    if (start < 0 || end < start) throw ModelResponseException("Model did not return JSON: ${text.take(200)}")
    lenientJson.parseToJsonElement(text.substring(start, end + 1))
}

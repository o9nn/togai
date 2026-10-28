/**
 * ServiceEndpoints.kt - Service Endpoint Adapters
 *
 * This file provides endpoint adapters that wrap existing Layla/framework services
 * and expose them through the Integration Hub. Each adapter translates between
 * the hub's unified interface and the specific service implementation.
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    Service Endpoint Layer                       │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  ┌────────────────────────────────────────────────────────┐    │
 * │  │               Endpoint Adapter Base                     │    │
 * │  └────────────────────────────────────────────────────────┘    │
 * │         ↓                    ↓                    ↓            │
 * │  ┌──────────┐         ┌──────────┐         ┌──────────┐       │
 * │  │Inference │         │Voice     │         │Image     │  ...  │
 * │  │Endpoint  │         │Endpoint  │         │Endpoint  │       │
 * │  └──────────┘         └──────────┘         └──────────┘       │
 * │       ↓                    ↓                    ↓              │
 * │  ┌──────────┐         ┌──────────┐         ┌──────────┐       │
 * │  │Inference │         │Voice     │         │SD        │       │
 * │  │Service   │         │Service   │         │Service   │       │
 * │  └──────────┘         └──────────┘         └──────────┘       │
 * └─────────────────────────────────────────────────────────────────┘
 */
package org.ninelym.aid.endpoint

import kotlinx.coroutines.*
import org.ninelym.aid.core.*
import org.ninelym.aid.hub.*
import org.ninelym.llm.ChatMessage
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import org.ninelym.llm.ask
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Base class for all service endpoint adapters
 */
abstract class BaseEndpointAdapter(
    val endpointName: String,
    val endpointDescription: String,
    val endpointCapabilities: Set<ServiceCapability>
) : EndpointHandler {

    protected var isInitialized = false
    private var lastHealthCheck = System.currentTimeMillis()
    private var errorCount = 0
    private var requestCount = 0

    override suspend fun initialize(): AIDResult<Unit> {
        return try {
            val result = doInitialize()
            isInitialized = result is AIDResult.Success
            result
        } catch (e: Exception) {
            AIDResult.Error(
                AIDErrorCode.DRIVER_INITIALIZATION_FAILED,
                "Failed to initialize ${endpointName}: ${e.message}",
                e
            )
        }
    }

    override suspend fun handle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        if (!isInitialized) {
            return AIDResult.Error(
                AIDErrorCode.ENDPOINT_NOT_AVAILABLE,
                "$endpointName endpoint not initialized"
            )
        }

        requestCount++
        return try {
            doHandle(request, tensor)
        } catch (e: Exception) {
            errorCount++
            AIDResult.Error(
                AIDErrorCode.INTEGRATION_HUB_ERROR,
                "Error handling request in $endpointName: ${e.message}",
                e
            )
        }
    }

    override fun getHealth(): EndpointHealth {
        val successRate = if (requestCount > 0) {
            (requestCount - errorCount).toFloat() / requestCount
        } else 1.0f

        return EndpointHealth(
            healthy = isInitialized && successRate > 0.5f,
            state = if (isInitialized) EndpointState.READY else EndpointState.OFFLINE,
            uptime = System.currentTimeMillis() - lastHealthCheck,
            metrics = mapOf(
                "requestCount" to requestCount,
                "errorCount" to errorCount,
                "successRate" to successRate
            )
        )
    }

    override suspend fun shutdown() {
        doShutdown()
        isInitialized = false
    }

    /**
     * Subclass implementation of initialization
     */
    protected abstract suspend fun doInitialize(): AIDResult<Unit>

    /**
     * Subclass implementation of request handling
     */
    protected abstract suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any>

    /**
     * Subclass implementation of shutdown
     */
    protected open suspend fun doShutdown() {}
}

// ============= Shared model helpers =============

private fun modelUnavailable(e: Throwable): AIDResult.Error =
    AIDResult.Error(AIDErrorCode.ENDPOINT_NOT_AVAILABLE, "Model unavailable: ${e.message}", e)

/** Turns a model call result into an endpoint result; failures are reported, never papered over. */
private inline fun <T> Result<T>.toEndpointResult(build: (T) -> Map<String, Any>): AIDResult<Any> =
    fold(onSuccess = { AIDResult.Success(build(it)) }, onFailure = { modelUnavailable(it) })

private fun tempOutput(prefix: String, suffix: String, bytes: ByteArray): File =
    File.createTempFile(prefix, suffix).apply { writeBytes(bytes) }

// ============= Inference Endpoint =============

/**
 * Endpoint adapter for AI inference operations, backed by the configured language model.
 */
class InferenceEndpoint(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) : BaseEndpointAdapter(
    endpointName = "inference",
    endpointDescription = "AI language model inference endpoint for text generation and processing",
    endpointCapabilities = setOf(
        ServiceCapability.INFERENCE,
        ServiceCapability.TEXT_INPUT,
        ServiceCapability.TEXT_OUTPUT,
        ServiceCapability.GENERATION
    )
) {
    override suspend fun doInitialize(): AIDResult<Unit> =
        modelProvider().fold(onSuccess = { AIDResult.Success(Unit) }, onFailure = { modelUnavailable(it) })

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "generate" -> handleGenerate(request, tensor)
            "complete" -> handleComplete(request, tensor)
            "embed" -> handleEmbed(request, tensor)
            "chat" -> handleChat(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown inference action: ${request.action}"
            )
        }
    }

    private fun options(request: EndpointRequest, tensor: AIDTensor): GenerationOptions {
        val maxTokens = (request.parameters["maxTokens"] as? Number)?.toInt() ?: 256
        val temperature = (request.parameters["temperature"] as? Number)?.toFloat() ?: 0.7f
        // Modulate parameters based on tensor
        return GenerationOptions(temperature = temperature * (0.5f + tensor.creativityFactor), maxTokens = maxTokens)
    }

    private suspend fun handleGenerate(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val prompt = request.parameters["prompt"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Prompt required")
        val system = request.parameters["system"] as? String ?: "You are a helpful assistant."
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        val opts = options(request, tensor)
        return model.ask(system, prompt, opts).toEndpointResult { text ->
            mapOf(
                "generated" to text,
                "model" to model.modelId,
                "temperature" to opts.temperature,
                "tensor" to tensor
            )
        }
    }

    private suspend fun handleComplete(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val text = request.parameters["text"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Text required")
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        return model.ask(
            "Continue the user's text. Output only the continuation, without repeating the text.",
            text, options(request, tensor)
        ).toEndpointResult { continuation ->
            mapOf("completion" to text + continuation, "continuation" to continuation, "tensor" to tensor)
        }
    }

    private suspend fun handleEmbed(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val text = request.parameters["text"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Text required")
        val endpoint = LanguageModels.currentEndpoint().getOrElse { return modelUnavailable(it) }
        return endpoint.embed(listOf(text)).toEndpointResult { vectors ->
            val embedding = vectors.first()
            mapOf("embedding" to embedding, "dimensions" to embedding.size, "text" to text)
        }
    }

    private suspend fun handleChat(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val messages = request.parameters["messages"] as? List<*>
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Messages required")
        val chat = messages.mapNotNull { m ->
            when (m) {
                is ChatMessage -> m
                is String -> ChatMessage.user(m)
                is Map<*, *> -> {
                    val content = m["content"] as? String ?: return@mapNotNull null
                    when ((m["role"] as? String)?.lowercase()) {
                        "system" -> ChatMessage.system(content)
                        "assistant" -> ChatMessage.assistant(content)
                        else -> ChatMessage.user(content)
                    }
                }
                else -> null
            }
        }
        if (chat.isEmpty()) return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No usable messages")
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        return model.chat(chat, options(request, tensor)).toEndpointResult { reply ->
            mapOf("response" to reply, "messageCount" to chat.size, "model" to model.modelId, "tensor" to tensor)
        }
    }
}

// ============= Voice Endpoint =============

/**
 * Endpoint adapter for voice: transcription and synthesis by the served speech models.
 * Microphone capture belongs to VoiceInputService; send its recordings to `transcribe`.
 */
class VoiceEndpoint(
    private val endpointProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() }
) : BaseEndpointAdapter(
    endpointName = "voice",
    endpointDescription = "Voice input and output endpoint for speech recognition and synthesis",
    endpointCapabilities = setOf(
        ServiceCapability.VOICE_INPUT,
        ServiceCapability.VOICE_OUTPUT,
        ServiceCapability.TRANSFORMATION
    )
) {
    override suspend fun doInitialize(): AIDResult<Unit> =
        endpointProvider().fold(onSuccess = { AIDResult.Success(Unit) }, onFailure = { modelUnavailable(it) })

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "transcribe" -> handleTranscribe(request, tensor)
            "synthesize" -> handleSynthesize(request, tensor)
            "startListening", "stopListening" -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Microphone capture is handled by VoiceInputService; pass recorded audio to 'transcribe'"
            )
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown voice action: ${request.action}"
            )
        }
    }

    private suspend fun handleTranscribe(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val audioFile = when (val audio = request.parameters["audio"] ?: request.parameters["audioPath"]) {
            is ByteArray -> tempOutput("aid-audio", ".wav", audio)
            is String -> File(audio)
            is File -> audio
            else -> return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Audio bytes or audioPath required")
        }
        val model = request.parameters["model"] as? String ?: "whisper-1"
        val endpoint = endpointProvider().getOrElse { return modelUnavailable(it) }
        return endpoint.transcribe(audioFile, model).toEndpointResult { text ->
            mapOf("transcription" to text, "model" to model)
        }
    }

    private suspend fun handleSynthesize(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val text = request.parameters["text"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Text required")
        val voice = request.parameters["voice"] as? String ?: "alloy"
        val model = request.parameters["model"] as? String ?: "tts-1"
        val endpoint = endpointProvider().getOrElse { return modelUnavailable(it) }
        return endpoint.speech(text, voice, model).toEndpointResult { audio ->
            mapOf("audioPath" to tempOutput("aid-speech", ".mp3", audio).absolutePath, "bytes" to audio.size, "voice" to voice)
        }
    }
}

// ============= Image Endpoint =============

/**
 * Endpoint adapter for image generation, editing and analysis by served image/vision models.
 */
class ImageEndpoint(
    private val endpointProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() }
) : BaseEndpointAdapter(
    endpointName = "image",
    endpointDescription = "Image generation and processing endpoint (Stable Diffusion)",
    endpointCapabilities = setOf(
        ServiceCapability.IMAGE_OUTPUT,
        ServiceCapability.GENERATION,
        ServiceCapability.TRANSFORMATION
    )
) {
    override suspend fun doInitialize(): AIDResult<Unit> =
        endpointProvider().fold(onSuccess = { AIDResult.Success(Unit) }, onFailure = { modelUnavailable(it) })

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "generate" -> handleGenerate(request, tensor)
            "edit" -> handleEdit(request, tensor)
            "upscale" -> handleUpscale(request, tensor)
            "analyze" -> handleAnalyze(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown image action: ${request.action}"
            )
        }
    }

    private suspend fun handleGenerate(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val prompt = request.parameters["prompt"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Prompt required")
        val width = (request.parameters["width"] as? Number)?.toInt() ?: 512
        val height = (request.parameters["height"] as? Number)?.toInt() ?: 512
        val model = request.parameters["model"] as? String
        val endpoint = endpointProvider().getOrElse { return modelUnavailable(it) }
        val result = if (model != null) endpoint.generateImage(prompt, "${width}x$height", model)
            else endpoint.generateImage(prompt, "${width}x$height")
        return result.toEndpointResult { bytes ->
            mapOf(
                "prompt" to prompt,
                "width" to width,
                "height" to height,
                "imagePath" to tempOutput("aid-image", ".png", bytes).absolutePath
            )
        }
    }

    private suspend fun handleEdit(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val imagePath = request.parameters["imagePath"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Image path required")
        val editPrompt = request.parameters["prompt"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Edit prompt required")
        val endpoint = endpointProvider().getOrElse { return modelUnavailable(it) }
        return endpoint.editImage(File(imagePath), editPrompt).toEndpointResult { bytes ->
            mapOf("originalPath" to imagePath, "editedPath" to tempOutput("aid-edit", ".png", bytes).absolutePath)
        }
    }

    private suspend fun handleUpscale(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val imagePath = request.parameters["imagePath"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Image path required")
        val scale = (request.parameters["scale"] as? Number)?.toInt() ?: 2
        val source = android.graphics.BitmapFactory.decodeFile(imagePath)
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Could not decode image: $imagePath")
        val scaled = android.graphics.Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, true)
        val out = File.createTempFile("aid-upscaled", ".png")
        out.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return AIDResult.Success(mapOf(
            "originalPath" to imagePath,
            "upscaledPath" to out.absolutePath,
            "scale" to scale,
            "method" to "bilinear"
        ))
    }

    private suspend fun handleAnalyze(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val imagePath = request.parameters["imagePath"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Image path required")
        val question = request.parameters["prompt"] as? String
            ?: "Describe this image and list the main objects in it."
        val file = File(imagePath)
        if (!file.isFile) return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Image not found: $imagePath")
        val mime = if (imagePath.endsWith(".jpg", true) || imagePath.endsWith(".jpeg", true)) "image/jpeg" else "image/png"
        val endpoint = endpointProvider().getOrElse { return modelUnavailable(it) }
        return endpoint.describeImage(file.readBytes(), question, mime).toEndpointResult { description ->
            mapOf("imagePath" to imagePath, "description" to description, "model" to endpoint.modelId)
        }
    }
}

// ============= Document Endpoint =============

/**
 * Endpoint adapter for document processing. Loaded text is kept in memory; summaries, answers
 * and extractions are written by the language model from that text.
 */
class DocumentEndpoint(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) : BaseEndpointAdapter(
    endpointName = "document",
    endpointDescription = "Document processing endpoint for PDF, text, and structured documents",
    endpointCapabilities = setOf(
        ServiceCapability.DOCUMENT_INPUT,
        ServiceCapability.TEXT_OUTPUT,
        ServiceCapability.ANALYSIS
    )
) {
    private data class LoadedDocument(val path: String, val text: String)

    private val documents = ConcurrentHashMap<String, LoadedDocument>()

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "load" -> handleLoad(request, tensor)
            "summarize" -> handleSummarize(request, tensor)
            "query" -> handleQuery(request, tensor)
            "extract" -> handleExtract(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown document action: ${request.action}"
            )
        }
    }

    private fun handleLoad(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val path = request.parameters["path"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Document path required")
        val file = File(path)
        if (!file.isFile) return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Document not found: $path")
        if (path.endsWith(".pdf", ignoreCase = true)) {
            return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "PDF text extraction isn't available here; use DocumentProcessingService")
        }
        val text = file.readText()
        val id = UUID.randomUUID().toString()
        documents[id] = LoadedDocument(path, text)
        return AIDResult.Success(mapOf(
            "path" to path,
            "documentId" to id,
            "characterCount" to text.length,
            "wordCount" to text.split(Regex("\\s+")).count { it.isNotEmpty() }
        ))
    }

    private fun document(request: EndpointRequest): LoadedDocument? =
        (request.parameters["documentId"] as? String)?.let { documents[it] }

    /** Keeps the prompt within a small model's context window. */
    private fun excerpt(text: String, limit: Int = 12_000) = if (text.length <= limit) text else text.take(limit) + "\n[...truncated]"

    private suspend fun handleSummarize(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val doc = document(request)
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Unknown or missing documentId")
        val maxLength = (request.parameters["maxLength"] as? Number)?.toInt() ?: 500
        // Adjust summary depth based on tensor
        val depth = if (tensor.depth > 0.7f) "detailed" else "brief"
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        return model.ask(
            "Summarize the document. Write a $depth summary of at most $maxLength characters.",
            excerpt(doc.text)
        ).toEndpointResult { summary ->
            mapOf("documentId" to request.parameters["documentId"]!!, "summary" to summary, "depth" to depth)
        }
    }

    private suspend fun handleQuery(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val doc = document(request)
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Unknown or missing documentId")
        val query = request.parameters["query"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Query required")
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        return model.ask(
            "Answer the question using only the document. If the document doesn't contain the answer, say so.",
            "Document:\n${excerpt(doc.text)}\n\nQuestion: $query"
        ).toEndpointResult { answer ->
            mapOf("documentId" to request.parameters["documentId"]!!, "query" to query, "answer" to answer)
        }
    }

    private suspend fun handleExtract(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val doc = document(request)
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Unknown or missing documentId")
        val extractType = request.parameters["type"] as? String ?: "text"
        if (extractType == "text") {
            return AIDResult.Success(mapOf("documentId" to request.parameters["documentId"]!!, "type" to extractType, "extracted" to doc.text))
        }
        val model = modelProvider().getOrElse { return modelUnavailable(it) }
        return model.ask(
            "Extract every $extractType from the document. Output one per line and nothing else.",
            excerpt(doc.text)
        ).toEndpointResult { extracted ->
            mapOf("documentId" to request.parameters["documentId"]!!, "type" to extractType, "extracted" to extracted)
        }
    }
}

// ============= Calendar Endpoint =============

/**
 * Endpoint adapter for calendar operations. Events are held by this endpoint; device calendar
 * sync is CalendarService's job.
 */
class CalendarEndpoint : BaseEndpointAdapter(
    endpointName = "calendar",
    endpointDescription = "Calendar integration endpoint for event management",
    endpointCapabilities = setOf(
        ServiceCapability.CALENDAR,
        ServiceCapability.MEMORY
    )
) {
    private val events = ConcurrentHashMap<String, MutableMap<String, Any>>()

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "createEvent" -> handleCreateEvent(request, tensor)
            "getEvents" -> handleGetEvents(request, tensor)
            "updateEvent" -> handleUpdateEvent(request, tensor)
            "deleteEvent" -> handleDeleteEvent(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown calendar action: ${request.action}"
            )
        }
    }

    private fun handleCreateEvent(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val title = request.parameters["title"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Event title required")
        val startTime = (request.parameters["startTime"] as? Number)?.toLong()
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Start time required")
        val id = UUID.randomUUID().toString()
        events[id] = (request.parameters + mapOf("eventId" to id, "title" to title, "startTime" to startTime)).toMutableMap()
        return AIDResult.Success(mapOf("eventId" to id, "title" to title, "startTime" to startTime))
    }

    private fun handleGetEvents(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val startDate = (request.parameters["startDate"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val endDate = (request.parameters["endDate"] as? Number)?.toLong() ?: (startDate + 7 * 24 * 60 * 60 * 1000)
        val inRange = events.values
            .filter { (it["startTime"] as Long) in startDate..endDate }
            .sortedBy { it["startTime"] as Long }
            .map { it.toMap() }
        return AIDResult.Success(mapOf("events" to inRange, "startDate" to startDate, "endDate" to endDate))
    }

    private fun handleUpdateEvent(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val eventId = request.parameters["eventId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Event ID required")
        val event = events[eventId] ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No event $eventId")
        event.putAll(request.parameters - "eventId")
        return AIDResult.Success(event.toMap())
    }

    private fun handleDeleteEvent(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val eventId = request.parameters["eventId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Event ID required")
        events.remove(eventId) ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No event $eventId")
        return AIDResult.Success(mapOf("deleted" to true, "eventId" to eventId))
    }
}

// ============= Automation Endpoint =============

/**
 * Endpoint adapter for task automation. A workflow is a list of steps, each
 * `{"endpoint": name, "action": action, "parameters": {...}}`, executed for real through
 * [dispatch] (the integration hub when registered via [EndpointFactory]).
 */
class AutomationEndpoint(
    var dispatch: (suspend (endpoint: String, action: String, parameters: Map<String, Any>) -> AIDResult<Any>)? = null
) : BaseEndpointAdapter(
    endpointName = "automation",
    endpointDescription = "Task automation endpoint for workflows and triggers",
    endpointCapabilities = setOf(
        ServiceCapability.AUTOMATION,
        ServiceCapability.TRANSFORMATION
    )
) {
    private data class Workflow(val id: String, val name: String, val steps: List<Map<*, *>>)

    private val workflows = ConcurrentHashMap<String, Workflow>()
    private val triggers = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doShutdown() {
        scope.cancel()
        triggers.clear()
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "createWorkflow" -> handleCreateWorkflow(request, tensor)
            "executeWorkflow" -> handleExecuteWorkflow(request, tensor)
            "createTrigger" -> handleCreateTrigger(request, tensor)
            "listWorkflows" -> handleListWorkflows(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown automation action: ${request.action}"
            )
        }
    }

    private fun handleCreateWorkflow(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val name = request.parameters["name"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Workflow name required")
        val steps = (request.parameters["steps"] as? List<*> ?: emptyList<Any>()).map {
            it as? Map<*, *> ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Each step must be a map")
        }
        steps.forEach {
            if (it["endpoint"] !is String || it["action"] !is String) {
                return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Each step needs 'endpoint' and 'action'")
            }
        }
        val id = UUID.randomUUID().toString()
        workflows[id] = Workflow(id, name, steps)
        return AIDResult.Success(mapOf("workflowId" to id, "name" to name, "stepCount" to steps.size))
    }

    private suspend fun execute(workflow: Workflow): AIDResult<Any> {
        val run = dispatch ?: return AIDResult.Error(AIDErrorCode.ENDPOINT_NOT_AVAILABLE, "Automation endpoint is not connected to a hub")
        val results = mutableListOf<Any>()
        for ((index, step) in workflow.steps.withIndex()) {
            @Suppress("UNCHECKED_CAST")
            val params = (step["parameters"] as? Map<String, Any>) ?: emptyMap()
            when (val result = run(step["endpoint"] as String, step["action"] as String, params)) {
                is AIDResult.Success -> results += result.data
                is AIDResult.Error -> return AIDResult.Error(result.code, "Step ${index + 1} failed: ${result.message}", result.cause)
                else -> return AIDResult.Error(AIDErrorCode.INTERNAL_ERROR, "Step ${index + 1} did not complete")
            }
        }
        return AIDResult.Success(mapOf("workflowId" to workflow.id, "stepResults" to results))
    }

    private suspend fun handleExecuteWorkflow(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val workflowId = request.parameters["workflowId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Workflow ID required")
        val workflow = workflows[workflowId] ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No workflow $workflowId")
        return execute(workflow)
    }

    private fun handleCreateTrigger(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val workflowId = request.parameters["workflowId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Workflow ID required")
        val workflow = workflows[workflowId] ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No workflow $workflowId")
        val triggerType = request.parameters["triggerType"] as? String ?: "interval"
        if (triggerType != "interval") {
            return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Unsupported trigger type '$triggerType' (supported: interval)")
        }
        val intervalMs = (request.parameters["intervalMs"] as? Number)?.toLong()
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "intervalMs required")
        val id = UUID.randomUUID().toString()
        triggers[id] = scope.launch {
            while (isActive) {
                delay(intervalMs)
                execute(workflow)
            }
        }
        return AIDResult.Success(mapOf("triggerId" to id, "workflowId" to workflowId, "triggerType" to triggerType))
    }

    private fun handleListWorkflows(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return AIDResult.Success(mapOf(
            "workflows" to workflows.values.map { mapOf("workflowId" to it.id, "name" to it.name, "stepCount" to it.steps.size) }
        ))
    }
}

// ============= Memory Endpoint =============

/**
 * Endpoint adapter for memory and context management: an in-process store with keyword search.
 */
class MemoryEndpoint : BaseEndpointAdapter(
    endpointName = "memory",
    endpointDescription = "Memory and context management endpoint",
    endpointCapabilities = setOf(
        ServiceCapability.MEMORY,
        ServiceCapability.PERSISTENCE,
        ServiceCapability.CACHING
    )
) {
    private data class Memory(val id: String, val content: Any, val tags: List<String>, val salience: Float, val storedAt: Long)

    private val memories = ConcurrentHashMap<String, Memory>()

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "store" -> handleStore(request, tensor)
            "retrieve" -> handleRetrieve(request, tensor)
            "search" -> handleSearch(request, tensor)
            "forget" -> handleForget(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown memory action: ${request.action}"
            )
        }
    }

    private fun Memory.toMap() = mapOf("memoryId" to id, "content" to content, "tags" to tags, "salience" to salience, "storedAt" to storedAt)

    private fun handleStore(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val content = request.parameters["content"]
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Content required")
        val tags = (request.parameters["tags"] as? List<*>)?.map { it.toString() } ?: emptyList()
        val memory = Memory(UUID.randomUUID().toString(), content, tags, tensor.salience, System.currentTimeMillis())
        memories[memory.id] = memory
        return AIDResult.Success(memory.toMap())
    }

    private fun handleRetrieve(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val memoryId = request.parameters["memoryId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Memory ID required")
        val memory = memories[memoryId] ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No memory $memoryId")
        return AIDResult.Success(memory.toMap())
    }

    private fun handleSearch(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val query = request.parameters["query"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Search query required")
        val limit = (request.parameters["limit"] as? Number)?.toInt() ?: 10
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }.toSet()
        val results = memories.values
            .map { m ->
                val words = (m.content.toString() + " " + m.tags.joinToString(" ")).lowercase().split(Regex("\\W+")).toSet()
                m to terms.count { it in words }
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Memory, Int>> { it.second }.thenByDescending { it.first.salience })
            .take(limit)
            .map { it.first.toMap() + ("matches" to it.second) }
        return AIDResult.Success(mapOf("query" to query, "results" to results, "limit" to limit))
    }

    private fun handleForget(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val memoryId = request.parameters["memoryId"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Memory ID required")
        memories.remove(memoryId) ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "No memory $memoryId")
        return AIDResult.Success(mapOf("forgotten" to true, "memoryId" to memoryId))
    }
}

// ============= Analytics Endpoint =============

/**
 * Endpoint adapter for analytics: metrics are computed from events actually tracked here and,
 * when connected, from the hub's real endpoint health.
 */
class AnalyticsEndpoint(
    var healthSource: (() -> Map<String, EndpointHealth>)? = null
) : BaseEndpointAdapter(
    endpointName = "analytics",
    endpointDescription = "Analytics and monitoring endpoint",
    endpointCapabilities = setOf(
        ServiceCapability.ANALYTICS,
        ServiceCapability.PERFORMANCE,
        ServiceCapability.HEALTH_CHECK
    )
) {
    private data class TrackedEvent(val name: String, val properties: Map<*, *>, val timestamp: Long)

    private val events = java.util.concurrent.ConcurrentLinkedQueue<TrackedEvent>()
    private val startedAt = System.currentTimeMillis()

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return when (request.action) {
            "trackEvent" -> handleTrackEvent(request, tensor)
            "getMetrics" -> handleGetMetrics(request, tensor)
            "getHealth" -> handleGetHealth(request, tensor)
            "getDashboard" -> handleGetDashboard(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown analytics action: ${request.action}"
            )
        }
    }

    private fun handleTrackEvent(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val eventName = request.parameters["eventName"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Event name required")
        val properties = request.parameters["properties"] as? Map<*, *> ?: emptyMap<String, Any>()
        val event = TrackedEvent(eventName, properties, System.currentTimeMillis())
        events += event
        return AIDResult.Success(mapOf("tracked" to true, "eventName" to eventName, "timestamp" to event.timestamp))
    }

    private fun handleGetMetrics(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val metricNames = (request.parameters["metrics"] as? List<*>)?.map { it.toString() }?.toSet()
        val counts = events.groupingBy { it.name }.eachCount()
        return AIDResult.Success(mapOf(
            "metrics" to if (metricNames == null) counts else counts.filterKeys { it in metricNames },
            "totalEvents" to events.size
        ))
    }

    private fun handleGetHealth(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val services = healthSource?.invoke()
            ?: return AIDResult.Error(AIDErrorCode.ENDPOINT_NOT_AVAILABLE, "Analytics endpoint is not connected to a hub")
        return AIDResult.Success(mapOf(
            "healthy" to services.values.all { it.healthy },
            "uptime" to System.currentTimeMillis() - startedAt,
            "services" to services.mapValues { if (it.value.healthy) "healthy" else it.value.state.name.lowercase() }
        ))
    }

    private fun handleGetDashboard(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        return AIDResult.Success(mapOf(
            "dashboard" to mapOf(
                "eventsToday" to events.count { it.timestamp >= dayAgo },
                "totalEvents" to events.size,
                "topEvents" to events.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(5)
                    .associate { it.key to it.value }
            )
        ))
    }
}

// ============= Privacy Endpoint =============

/**
 * Endpoint adapter for privacy management: PII redaction, AES-GCM encryption with keys held in
 * memory, a configurable policy, and an audit log of the requests this endpoint served.
 */
class PrivacyEndpoint : BaseEndpointAdapter(
    endpointName = "privacy",
    endpointDescription = "Privacy and data protection endpoint",
    endpointCapabilities = setOf(
        ServiceCapability.PRIVACY,
        ServiceCapability.ENCRYPTION
    )
) {
    private val keys = ConcurrentHashMap<String, javax.crypto.SecretKey>()
    private val audit = java.util.concurrent.ConcurrentLinkedQueue<Map<String, Any>>()
    private val policy = ConcurrentHashMap<String, Any>(mapOf(
        "dataRetentionDays" to 30,
        "encryptionEnabled" to true,
        "anonymizationLevel" to "high"
    ))

    override suspend fun doInitialize(): AIDResult<Unit> {
        return AIDResult.Success(Unit)
    }

    override suspend fun doHandle(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        audit += mapOf("action" to request.action, "requestId" to request.id, "timestamp" to System.currentTimeMillis())
        return when (request.action) {
            "anonymize" -> handleAnonymize(request, tensor)
            "encrypt" -> handleEncrypt(request, tensor)
            "getPolicy" -> handleGetPolicy(request, tensor)
            "auditLog" -> handleAuditLog(request, tensor)
            else -> AIDResult.Error(
                AIDErrorCode.INVALID_REQUEST,
                "Unknown privacy action: ${request.action}"
            )
        }
    }

    private fun handleAnonymize(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val data = request.parameters["data"] as? String
            ?: return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Text data required")
        var redactions = 0
        val anonymized = PII_PATTERNS.fold(data) { text, (pattern, label) ->
            pattern.replace(text) { redactions++; "[$label]" }
        }
        return AIDResult.Success(mapOf("anonymized" to anonymized, "redactions" to redactions))
    }

    private fun handleEncrypt(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        val data = when (val d = request.parameters["data"]) {
            is ByteArray -> d
            is String -> d.toByteArray()
            else -> return AIDResult.Error(AIDErrorCode.INVALID_REQUEST, "Data required")
        }
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply { init(javax.crypto.Cipher.ENCRYPT_MODE, key) }
        val keyId = UUID.randomUUID().toString()
        keys[keyId] = key
        val encoder = java.util.Base64.getEncoder()
        return AIDResult.Success(mapOf(
            "keyId" to keyId,
            "iv" to encoder.encodeToString(cipher.iv),
            "ciphertext" to encoder.encodeToString(cipher.doFinal(data))
        ))
    }

    private fun handleGetPolicy(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        @Suppress("UNCHECKED_CAST")
        (request.parameters["update"] as? Map<String, Any>)?.let { policy.putAll(it) }
        return AIDResult.Success(mapOf("policy" to policy.toMap()))
    }

    private fun handleAuditLog(request: EndpointRequest, tensor: AIDTensor): AIDResult<Any> {
        return AIDResult.Success(mapOf("auditEntries" to audit.toList()))
    }

    private companion object {
        val PII_PATTERNS = listOf(
            Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+") to "EMAIL",
            Regex("\\+?\\d[\\d\\s().-]{7,}\\d") to "PHONE",
            Regex("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b") to "CARD"
        )
    }
}

// ============= Endpoint Factory =============

/**
 * Factory for creating and registering all standard endpoints
 */
object EndpointFactory {
    /**
     * Create all standard endpoints
     */
    fun createAllEndpoints(): List<BaseEndpointAdapter> = listOf(
        InferenceEndpoint(),
        VoiceEndpoint(),
        ImageEndpoint(),
        DocumentEndpoint(),
        CalendarEndpoint(),
        AutomationEndpoint(),
        MemoryEndpoint(),
        AnalyticsEndpoint(),
        PrivacyEndpoint()
    )

    /**
     * Register all endpoints with the integration hub, wiring automation and analytics to it
     */
    suspend fun registerAllEndpoints(hub: IntegrationHub): List<AIDResult<ServiceEndpoint>> {
        return createAllEndpoints().map { endpoint ->
            when (endpoint) {
                is AutomationEndpoint -> endpoint.dispatch = { name, action, params -> hub.invokeByName(name, action, params) }
                is AnalyticsEndpoint -> endpoint.healthSource = { hub.endpointHealth() }
                else -> {}
            }
            hub.registerEndpoint(
                name = endpoint.endpointName,
                description = endpoint.endpointDescription,
                capabilities = endpoint.endpointCapabilities,
                handler = endpoint
            )
        }
    }
}

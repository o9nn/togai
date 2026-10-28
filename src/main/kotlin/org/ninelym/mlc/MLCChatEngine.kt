package org.ninelym.mlc

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import org.ninelym.llm.ChatMessage as LlmMessage

/**
 * MLC Chat Engine
 *
 * Machine Learning Compilation chat framework for optimized inference.
 * Provides high-performance on-device LLM inference with TVM optimization.
 */

/**
 * Model initialization state
 */
enum class ModelInitState {
    UNINITIALIZED,
    LOADING,
    READY,
    FAILED
}

/**
 * Chat state during generation
 */
enum class ModelChatState {
    IDLE,
    GENERATING,
    READY,
    FAILED
}

/**
 * Message role in conversation
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * Chat message
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Model configuration
 */
data class MLCModelConfig(
    val modelPath: String,
    val modelLib: String,
    val tokenizerPath: String = "",
    val vocabPath: String = "",
    val maxContextLength: Int = 4096,
    val prefillChunkSize: Int = 512
)

/**
 * Generation parameters
 */
data class MLCGenerationParams(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512,
    val repeatPenalty: Float = 1.1f,
    val presencePenalty: Float = 0.0f,
    val frequencyPenalty: Float = 0.0f,
    val stopSequences: List<String> = emptyList()
)

/**
 * Model record for available models
 */
data class ModelRecord(
    val id: String,
    val name: String,
    val modelPath: String,
    val modelLib: String,
    val estimatedVRAM: Long,
    val quantization: String
)

/**
 * App configuration for model management
 */
data class MLCAppConfig(
    val modelCachePath: String,
    val availableModels: List<ModelRecord>,
    val defaultModelId: String? = null
)

/**
 * MLC Chat Engine implementation.
 *
 * The compiled MLC/TVM runtime is not packaged in this build, so generation is delegated to the
 * configured [LanguageModel] (a locally served model, e.g. MLC's own OpenAI-compatible
 * `mlc_llm serve`, llama.cpp server or Ollama). [MLCModelConfig.modelPath] names the served model.
 */
class MLCChatEngine(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {

    private val _modelInitState = MutableStateFlow(ModelInitState.UNINITIALIZED)
    val modelInitState: StateFlow<ModelInitState> = _modelInitState

    private val _chatState = MutableStateFlow(ModelChatState.IDLE)
    val chatState: StateFlow<ModelChatState> = _chatState

    private val isGenerating = AtomicBoolean(false)
    private val messageQueue = ArrayBlockingQueue<String>(100)
    private val conversationHistory = mutableListOf<ChatMessage>()

    private var currentConfig: MLCModelConfig? = null
    private var scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var model: LanguageModel? = null

    /**
     * Load and initialize model
     */
    suspend fun loadModel(config: MLCModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _modelInitState.value = ModelInitState.LOADING

            // Validate paths
            if (config.modelPath.isEmpty()) {
                _modelInitState.value = ModelInitState.FAILED
                return@withContext Result.failure(Exception("Model path is empty"))
            }

            if (File(config.modelPath).exists()) {
                _modelInitState.value = ModelInitState.FAILED
                return@withContext Result.failure(Exception(
                    "The MLC native runtime isn't packaged; serve ${config.modelPath} (e.g. with `mlc_llm serve`) " +
                        "and configure its endpoint instead"))
            }
            val base = modelProvider().getOrThrow()
            val selected = (base as? OpenAICompatibleModel)
                ?.let { OpenAICompatibleModel(it.config.copy(model = config.modelPath)) }
                ?: base.takeIf { it.modelId == config.modelPath }
                ?: throw Exception("Model ${config.modelPath} is not available from the configured provider")
            if (selected is OpenAICompatibleModel) {
                val served = selected.listModels().getOrThrow()
                if (served.isNotEmpty() && selected.modelId !in served) {
                    throw Exception("Model ${selected.modelId} is not served by ${selected.config.baseUrl} (serving: $served)")
                }
            }
            model = selected
            currentConfig = config

            _modelInitState.value = ModelInitState.READY
            _chatState.value = ModelChatState.READY

            Result.success(Unit)
        } catch (e: Exception) {
            _modelInitState.value = ModelInitState.FAILED
            Result.failure(e)
        }
    }

    /**
     * Generate response for a message
     */
    suspend fun generate(
        message: String,
        params: MLCGenerationParams = MLCGenerationParams(),
        systemPrompt: String? = null,
        onToken: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        if (_modelInitState.value != ModelInitState.READY) {
            return@withContext Result.failure(Exception("Model not loaded"))
        }

        if (!isGenerating.compareAndSet(false, true)) {
            return@withContext Result.failure(Exception("Generation already in progress"))
        }

        try {
            _chatState.value = ModelChatState.GENERATING

            // Add system prompt if provided and not already set
            if (systemPrompt != null && conversationHistory.none { it.role == MessageRole.SYSTEM }) {
                conversationHistory.add(0, ChatMessage(MessageRole.SYSTEM, systemPrompt))
            }

            // Add user message
            conversationHistory.add(ChatMessage(MessageRole.USER, message))

            val response = generateResponse(params, onToken).getOrElse {
                conversationHistory.removeAt(conversationHistory.lastIndex)
                throw it
            }

            // Add assistant response to history
            conversationHistory.add(ChatMessage(MessageRole.ASSISTANT, response))

            _chatState.value = ModelChatState.READY
            Result.success(response)
        } catch (e: Exception) {
            _chatState.value = ModelChatState.FAILED
            Result.failure(e)
        } finally {
            isGenerating.set(false)
        }
    }

    private suspend fun generateResponse(
        params: MLCGenerationParams,
        onToken: ((String) -> Unit)?
    ): Result<String> {
        val activeModel = model ?: return Result.failure(Exception("Model not loaded"))
        val messages = conversationHistory.map {
            when (it.role) {
                MessageRole.SYSTEM -> LlmMessage.system(it.content)
                MessageRole.USER -> LlmMessage.user(it.content)
                MessageRole.ASSISTANT -> LlmMessage.assistant(it.content)
            }
        }
        val options = GenerationOptions(
            temperature = params.temperature,
            topP = params.topP,
            maxTokens = params.maxTokens,
            stop = params.stopSequences
        )
        return coroutineScope {
            // stopGeneration() cancels the in-flight request
            val job = async { activeModel.chat(messages, options, onToken) }
            val watcher = launch { while (isGenerating.get()) delay(50); job.cancel() }
            try { job.await() } catch (e: CancellationException) {
                Result.failure(Exception("Generation stopped"))
            } finally { watcher.cancel() }
        }
    }

    /**
     * Send message (queued for processing)
     */
    fun sendMessage(message: String) {
        messageQueue.offer(message)
    }

    /**
     * Reset chat - clear conversation history
     */
    suspend fun resetChat() = withContext(Dispatchers.Default) {
        conversationHistory.clear()
        messageQueue.clear()
        _chatState.value = ModelChatState.READY
    }

    /**
     * Get conversation history
     */
    fun getHistory(): List<ChatMessage> = conversationHistory.toList()

    /**
     * Set conversation history (for restoring state)
     */
    fun setHistory(history: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(history)
    }

    /**
     * Stop current generation
     */
    fun stopGeneration() {
        isGenerating.set(false)
    }

    /**
     * Unload model and free resources
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        stopGeneration()
        conversationHistory.clear()
        messageQueue.clear()
        currentConfig = null
        model = null
        _modelInitState.value = ModelInitState.UNINITIALIZED
        _chatState.value = ModelChatState.IDLE
    }

    /**
     * Shutdown engine
     */
    fun shutdown() {
        scope.cancel()
        isGenerating.set(false)
        conversationHistory.clear()
        messageQueue.clear()
    }

    companion object {
        /**
         * Get available models from app config
         */
        fun getAvailableModels(config: MLCAppConfig): List<ModelRecord> {
            return config.availableModels
        }

        /**
         * Estimate if model fits in memory
         */
        fun canLoadModel(model: ModelRecord, availableMemory: Long): Boolean {
            return model.estimatedVRAM <= availableMemory
        }
    }
}

/**
 * MLC Chat State Manager
 * Manages chat state for multi-turn conversations
 */
class MLCChatState(
    private val engine: MLCChatEngine
) {
    private var currentModelId: String? = null

    suspend fun reloadChat(modelId: String, config: MLCModelConfig): Result<Unit> {
        engine.resetChat()
        val result = engine.loadModel(config)
        if (result.isSuccess) {
            currentModelId = modelId
        }
        return result
    }

    suspend fun requestReloadChat(modelId: String, config: MLCModelConfig): Result<Unit> {
        return reloadChat(modelId, config)
    }

    suspend fun requestResetChat(): Result<Unit> {
        engine.resetChat()
        return Result.success(Unit)
    }

    suspend fun requestTerminateChat(): Result<Unit> {
        engine.stopGeneration()
        engine.unloadModel()
        currentModelId = null
        return Result.success(Unit)
    }

    suspend fun requestGenerate(
        message: String,
        params: MLCGenerationParams = MLCGenerationParams(),
        onToken: ((String) -> Unit)? = null
    ): Result<String> {
        return engine.generate(message, params, onToken = onToken)
    }

    fun getCurrentModelId(): String? = currentModelId
}

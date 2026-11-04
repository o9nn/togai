package org.ninelym.executorch

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import org.ninelym.llm.ChatMessage
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels

/**
 * Executorch Runtime
 *
 * Meta's PyTorch runtime for efficient mobile inference.
 * Provides on-device execution of PyTorch models optimized for mobile.
 */

/**
 * Runtime state
 */
enum class ExecutorchState {
    UNINITIALIZED,
    LOADING,
    READY,
    GENERATING,
    ERROR
}

/**
 * Llama model configuration
 */
data class LlamaModelConfig(
    val modelPath: String,
    val tokenizerPath: String,
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val maxSeqLen: Int = 128,
    val systemPrompt: String = ""
)

/**
 * Generation statistics
 */
data class GenerationStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val totalTimeMs: Long,
    val tokensPerSecond: Float,
    val modelLoadTimeMs: Long = 0
)

/**
 * Llama callback interface (mirrors PyTorch Executorch)
 */
interface LlamaCallback {
    /**
     * Called when a new token is generated
     */
    fun onToken(token: String)

    /**
     * Called when generation is complete
     */
    fun onComplete(stats: GenerationStats)

    /**
     * Called on error
     */
    fun onError(message: String)
}

/**
 * Executorch Llama Module wrapper.
 *
 * Runs the .pte model through the Executorch JNI runtime when that library is packaged. Without
 * it, the module generates with the app's configured [LanguageModel] (a locally served or remote
 * model) rather than producing any canned output.
 */
class LlamaModule(
    private val modelPath: String,
    private val tokenizerPath: String,
    private val temperature: Float = 0.8f,
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {
    private var nativeHandle: Long = 0
    private var servedModel: LanguageModel? = null
    private val stopRequested = AtomicBoolean(false)
    private val isLoaded = AtomicBoolean(false)

    /**
     * Load the model
     * @return 0 on success, error code otherwise
     */
    fun load(): Int {
        return try {
            if (ExecutorchJni.isLibraryLoaded()) {
                nativeHandle = ExecutorchJni.llama_load(modelPath, tokenizerPath, temperature)
                if (nativeHandle != 0L) {
                    isLoaded.set(true)
                    0
                } else {
                    -1
                }
            } else {
                servedModel = modelProvider().getOrNull() ?: return -2
                isLoaded.set(true)
                0
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Generate response
     * @param prompt Input prompt
     * @param seqLen Maximum sequence length
     * @param callback Progress callback
     * @return Generated text
     */
    fun generate(prompt: String, seqLen: Int, callback: LlamaCallback, systemPrompt: String = ""): String {
        if (!isLoaded.get()) {
            callback.onError("Model not loaded")
            return ""
        }

        return try {
            if (ExecutorchJni.isLibraryLoaded() && nativeHandle != 0L) {
                val fullPrompt = if (systemPrompt.isNotEmpty()) {
                    "$systemPrompt\n\nUser: $prompt\nAssistant:"
                } else {
                    "User: $prompt\nAssistant:"
                }
                ExecutorchJni.llama_generate(nativeHandle, fullPrompt, seqLen, object : ExecutorchJni.NativeCallback {
                    override fun onToken(token: String) = callback.onToken(token)
                    override fun onComplete() {
                        callback.onComplete(GenerationStats(
                            promptTokens = prompt.split(" ").size,
                            generatedTokens = seqLen,
                            totalTimeMs = 0,
                            tokensPerSecond = 0f
                        ))
                    }
                    override fun onError(message: String) = callback.onError(message)
                })
            } else {
                generateWithServedModel(prompt, seqLen, systemPrompt, callback)
            }
        } catch (e: Exception) {
            callback.onError(e.message ?: "Unknown error")
            ""
        }
    }

    private fun generateWithServedModel(
        prompt: String,
        seqLen: Int,
        systemPrompt: String,
        callback: LlamaCallback
    ): String {
        val model = servedModel ?: run { callback.onError("Model not loaded"); return "" }
        stopRequested.set(false)
        val start = System.currentTimeMillis()
        var chunks = 0
        val messages = buildList {
            if (systemPrompt.isNotEmpty()) add(ChatMessage.system(systemPrompt))
            add(ChatMessage.user(prompt))
        }
        val result = runBlocking {
            val job = async {
                model.chat(messages, GenerationOptions(temperature = temperature, maxTokens = seqLen)) {
                    chunks++
                    callback.onToken(it)
                }
            }
            val watcher = launch { while (!stopRequested.get()) delay(50); job.cancel() }
            try { job.await() } catch (e: CancellationException) {
                Result.failure(Exception("Generation stopped"))
            } finally { watcher.cancel() }
        }
        return result.fold(
            onSuccess = { text ->
                val elapsed = System.currentTimeMillis() - start
                callback.onComplete(GenerationStats(
                    promptTokens = prompt.split(" ").size,
                    generatedTokens = chunks,
                    totalTimeMs = elapsed,
                    tokensPerSecond = if (elapsed > 0) chunks * 1000f / elapsed else 0f
                ))
                text
            },
            onFailure = { callback.onError(it.message ?: "Generation failed"); "" }
        )
    }

    /**
     * Stop current generation
     */
    fun stop() {
        stopRequested.set(true)
        if (ExecutorchJni.isLibraryLoaded() && nativeHandle != 0L) {
            ExecutorchJni.llama_stop(nativeHandle)
        }
    }

    /**
     * Unload and release resources
     */
    fun unload() {
        if (ExecutorchJni.isLibraryLoaded() && nativeHandle != 0L) {
            ExecutorchJni.llama_unload(nativeHandle)
            nativeHandle = 0
        }
        servedModel = null
        isLoaded.set(false)
    }

    /**
     * Check if model is loaded
     */
    fun isReady(): Boolean = isLoaded.get()
}

/**
 * Executorch JNI interface
 */
object ExecutorchJni {

    private var isLoaded = false

    interface NativeCallback {
        fun onToken(token: String)
        fun onComplete()
        fun onError(message: String)
    }

    fun loadLibrary(): Boolean {
        return try {
            if (!isLoaded) {
                System.loadLibrary("executorch-jni")
                isLoaded = true
            }
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }

    fun isLibraryLoaded(): Boolean = isLoaded

    // Native methods
    @JvmStatic
    external fun llama_load(modelPath: String, tokenizerPath: String, temperature: Float): Long

    @JvmStatic
    external fun llama_generate(handle: Long, prompt: String, seqLen: Int, callback: NativeCallback): String

    @JvmStatic
    external fun llama_stop(handle: Long)

    @JvmStatic
    external fun llama_unload(handle: Long)

    @JvmStatic
    external fun get_stats(handle: Long): LongArray  // [promptTokens, genTokens, timeMs]
}

/**
 * Executorch Runtime - high-level API
 */
class ExecutorchRuntime(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {

    private val _state = MutableStateFlow(ExecutorchState.UNINITIALIZED)
    val state: StateFlow<ExecutorchState> = _state

    private var llamaModule: LlamaModule? = null
    private var currentConfig: LlamaModelConfig? = null
    private val isGenerating = AtomicBoolean(false)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initialize runtime
     */
    fun initialize(): Result<Unit> {
        return try {
            ExecutorchJni.loadLibrary() // Try to load, ignore failure
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load model
     */
    suspend fun loadModel(config: LlamaModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = ExecutorchState.LOADING

            val module = LlamaModule(
                config.modelPath,
                config.tokenizerPath,
                config.temperature,
                modelProvider
            )

            val loadResult = module.load()
            if (loadResult == -2) {
                _state.value = ExecutorchState.ERROR
                return@withContext Result.failure(Exception(
                    "Executorch runtime isn't packaged and no language model is configured"))
            }
            if (loadResult != 0) {
                _state.value = ExecutorchState.ERROR
                return@withContext Result.failure(Exception("Model load failed with code: $loadResult"))
            }

            llamaModule = module
            currentConfig = config
            _state.value = ExecutorchState.READY

            Result.success(Unit)
        } catch (e: Exception) {
            _state.value = ExecutorchState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Generate response
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 128,
        onToken: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        val module = llamaModule
        if (module == null || !module.isReady()) {
            return@withContext Result.failure(Exception("Model not loaded"))
        }

        if (!isGenerating.compareAndSet(false, true)) {
            return@withContext Result.failure(Exception("Generation in progress"))
        }

        try {
            _state.value = ExecutorchState.GENERATING

            var result = ""
            var error: String? = null

            val callback = object : LlamaCallback {
                override fun onToken(token: String) {
                    result += token
                    onToken?.invoke(token)
                }

                override fun onComplete(stats: GenerationStats) {
                    // Generation complete
                }

                override fun onError(message: String) {
                    error = message
                }
            }

            module.generate(prompt, maxTokens, callback, currentConfig?.systemPrompt ?: "")

            _state.value = ExecutorchState.READY

            if (error != null) {
                Result.failure(Exception(error))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            _state.value = ExecutorchState.ERROR
            Result.failure(e)
        } finally {
            isGenerating.set(false)
        }
    }

    /**
     * Stop generation
     */
    fun stopGeneration() {
        llamaModule?.stop()
        isGenerating.set(false)
    }

    /**
     * Unload model
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        stopGeneration()
        llamaModule?.unload()
        llamaModule = null
        currentConfig = null
        _state.value = ExecutorchState.UNINITIALIZED
    }

    /**
     * Check if ready
     */
    fun isReady(): Boolean = llamaModule?.isReady() == true

    /**
     * Shutdown runtime
     */
    fun shutdown() {
        scope.cancel()
        llamaModule?.unload()
        llamaModule = null
        _state.value = ExecutorchState.UNINITIALIZED
    }
}

/**
 * Executorch Model Manager
 * Manages multiple Executorch models
 */
class ExecutorchModelManager {

    private val runtime = ExecutorchRuntime()
    private val modelConfigs = mutableMapOf<String, LlamaModelConfig>()
    private var currentModelId: String? = null

    /**
     * Register model configuration
     */
    fun registerModel(id: String, config: LlamaModelConfig) {
        modelConfigs[id] = config
    }

    /**
     * Load registered model
     */
    suspend fun loadModel(id: String): Result<Unit> {
        val config = modelConfigs[id]
            ?: return Result.failure(Exception("Model not registered: $id"))

        val result = runtime.loadModel(config)
        if (result.isSuccess) {
            currentModelId = id
        }
        return result
    }

    /**
     * Switch to different model
     */
    suspend fun switchModel(id: String): Result<Unit> {
        if (currentModelId == id && runtime.isReady()) {
            return Result.success(Unit)
        }

        runtime.unloadModel()
        return loadModel(id)
    }

    /**
     * Generate with current model
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 128,
        onToken: ((String) -> Unit)? = null
    ): Result<String> {
        return runtime.generate(prompt, maxTokens, onToken)
    }

    /**
     * Get runtime state
     */
    fun getState(): ExecutorchState = runtime.state.value

    /**
     * Get current model ID
     */
    fun getCurrentModelId(): String? = currentModelId

    /**
     * Shutdown manager
     */
    fun shutdown() {
        runtime.shutdown()
        modelConfigs.clear()
        currentModelId = null
    }
}

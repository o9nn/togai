package org.ninelym.togai.integration

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ninelym.togai.util.ErrorHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * CogAI Bridge for Togai+
 *
 * Integrates with 9cog/cogai Python platform running in Termux to provide:
 * - Multi-provider LLM Gateway (OpenAI, Anthropic, Groq, Ollama)
 * - Speech Recognition and Synthesis
 * - Attention Detection
 * - App Prediction
 *
 * @see https://github.com/9cog/cogai
 */
class CogAIBridge private constructor(
    private val context: Context,
    private val termux: TermuxIntegrationService
) {

    companion object {
        private const val TAG = "CogAIBridge"

        // CogAI Configuration
        private const val COGAI_REPO = "https://github.com/9cog/cogai.git"
        private const val COGAI_DIR = "~/cogai"
        private const val COGAI_PORT = 8080
        private const val COGAI_HOST = "127.0.0.1"

        // API Endpoints
        private const val API_BASE = "http://$COGAI_HOST:$COGAI_PORT"
        private const val API_CHAT = "$API_BASE/v1/chat/completions"
        private const val API_SPEECH_RECOGNIZE = "$API_BASE/v1/audio/transcriptions"
        private const val API_SPEECH_SYNTHESIZE = "$API_BASE/v1/audio/speech"
        private const val API_ATTENTION = "$API_BASE/v1/attention/state"
        private const val API_PREDICT = "$API_BASE/v1/predict/apps"
        private const val API_HEALTH = "$API_BASE/health"

        @Volatile
        private var instance: CogAIBridge? = null

        fun getInstance(context: Context): CogAIBridge {
            return instance ?: synchronized(this) {
                instance ?: CogAIBridge(
                    context.applicationContext,
                    TermuxIntegrationService.getInstance(context)
                ).also { instance = it }
            }
        }
    }

    // JSON serializer
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // Server state
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    enum class ServerState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    // ========== Setup & Lifecycle ==========

    /**
     * Check if CogAI is installed in Termux
     */
    suspend fun isInstalled(): Boolean {
        val result = termux.executeCommand("test -d $COGAI_DIR && echo 'exists'")
        return result.stdout.contains("exists")
    }

    /**
     * Install CogAI in Termux
     */
    suspend fun install(
        onProgress: (String) -> Unit = {}
    ): InstallResult {
        onProgress("Checking Termux status...")

        val termuxStatus = termux.checkTermuxStatus()
        if (!termuxStatus.isTermuxInstalled) {
            return InstallResult.Error("Termux is not installed")
        }

        try {
            // Install Python if needed
            onProgress("Installing Python dependencies...")
            val pythonCheck = termux.executeCommand("which python")
            if (!pythonCheck.isSuccess) {
                val installPython = termux.installPackage("python")
                if (!installPython.isSuccess) {
                    return InstallResult.Error("Failed to install Python: ${installPython.stderr}")
                }
            }

            // Install git if needed
            val gitCheck = termux.executeCommand("which git")
            if (!gitCheck.isSuccess) {
                termux.installPackage("git")
            }

            // Clone CogAI repository
            onProgress("Cloning CogAI repository...")
            if (!isInstalled()) {
                val cloneResult = termux.gitClone(COGAI_REPO, COGAI_DIR)
                if (!cloneResult.isSuccess) {
                    return InstallResult.Error("Failed to clone CogAI: ${cloneResult.stderr}")
                }
            } else {
                // Update existing installation
                onProgress("Updating CogAI...")
                termux.git("pull", COGAI_DIR)
            }

            // Install Python dependencies
            onProgress("Installing Python packages...")
            val pipResult = termux.executeCommand(
                "cd $COGAI_DIR && pip install -r requirements.txt",
                timeout = 5.minutes
            )
            if (!pipResult.isSuccess) {
                return InstallResult.Error("Failed to install dependencies: ${pipResult.stderr}")
            }

            // Create config file
            onProgress("Configuring CogAI...")
            createDefaultConfig()

            onProgress("Installation complete!")
            return InstallResult.Success

        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "CogAI installation failed", e)
            return InstallResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Create default CogAI configuration
     */
    private suspend fun createDefaultConfig() {
        val config = """
{
    "server": {
        "host": "$COGAI_HOST",
        "port": $COGAI_PORT
    },
    "llm": {
        "default_provider": "ollama",
        "providers": {
            "ollama": {
                "base_url": "http://127.0.0.1:11434",
                "default_model": "llama3.2"
            }
        }
    },
    "speech": {
        "recognition": {
            "engine": "local",
            "model": "base"
        },
        "synthesis": {
            "engine": "local"
        }
    },
    "attention": {
        "enabled": true,
        "camera_index": 0
    },
    "prediction": {
        "enabled": true,
        "history_days": 30
    }
}
        """.trimIndent()

        termux.executeCommand("mkdir -p $COGAI_DIR/config")
        termux.executeCommand("cat > $COGAI_DIR/config/config.json << 'EOF'\n$config\nEOF")
    }

    /**
     * Start the CogAI server
     */
    suspend fun startServer(): Boolean {
        if (_serverState.value == ServerState.RUNNING) {
            return true
        }

        _serverState.value = ServerState.STARTING

        try {
            // Check if already running
            if (isServerRunning()) {
                _serverState.value = ServerState.RUNNING
                return true
            }

            // Start server in background
            val result = termux.executeCommand(
                "cd $COGAI_DIR && nohup python -m cogai.server > /tmp/cogai.log 2>&1 &",
                background = true
            )

            // Wait for server to start
            var attempts = 0
            while (attempts < 30) {
                delay(1000)
                if (isServerRunning()) {
                    _serverState.value = ServerState.RUNNING
                    ErrorHandler.logInfo(TAG, "CogAI server started successfully")
                    return true
                }
                attempts++
            }

            _serverState.value = ServerState.ERROR
            ErrorHandler.logError(TAG, "CogAI server failed to start within timeout", null)
            return false

        } catch (e: Exception) {
            _serverState.value = ServerState.ERROR
            ErrorHandler.logError(TAG, "Failed to start CogAI server", e)
            return false
        }
    }

    /**
     * Stop the CogAI server
     */
    suspend fun stopServer(): Boolean {
        return try {
            termux.executeCommand("pkill -f 'python -m cogai.server'")
            _serverState.value = ServerState.STOPPED
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to stop CogAI server", e)
            false
        }
    }

    /**
     * Check if server is running
     */
    suspend fun isServerRunning(): Boolean {
        val result = termux.httpRequest(
            method = "GET",
            url = API_HEALTH
        )
        return result.isSuccess && result.stdout.contains("ok")
    }

    // ========== LLM Gateway ==========

    /**
     * Chat completion request
     */
    @Serializable
    data class ChatRequest(
        val model: String = "llama3.2",
        val messages: List<ChatMessage>,
        val temperature: Float = 0.7f,
        val max_tokens: Int = 1024,
        val stream: Boolean = false,
        val provider: String? = null
    )

    @Serializable
    data class ChatMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class ChatResponse(
        val id: String = "",
        val choices: List<ChatChoice> = emptyList(),
        val usage: TokenUsage? = null,
        val error: String? = null
    )

    @Serializable
    data class ChatChoice(
        val index: Int = 0,
        val message: ChatMessage,
        val finish_reason: String? = null
    )

    @Serializable
    data class TokenUsage(
        val prompt_tokens: Int = 0,
        val completion_tokens: Int = 0,
        val total_tokens: Int = 0
    )

    /**
     * Send a chat completion request through CogAI's LLM Gateway
     *
     * Supports multiple providers: ollama, openai, anthropic, groq
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String = "llama3.2",
        provider: String? = null,
        temperature: Float = 0.7f,
        maxTokens: Int = 1024
    ): ChatResponse {
        ensureServerRunning()

        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            max_tokens = maxTokens,
            provider = provider
        )

        val result = termux.httpRequest(
            method = "POST",
            url = API_CHAT,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(request)
        )

        return if (result.isSuccess) {
            try {
                json.decodeFromString<ChatResponse>(result.stdout)
            } catch (e: Exception) {
                ChatResponse(error = "Failed to parse response: ${e.message}")
            }
        } else {
            ChatResponse(error = result.stderr)
        }
    }

    /**
     * Simple chat helper - single message
     */
    suspend fun ask(
        prompt: String,
        systemPrompt: String? = null,
        model: String = "llama3.2",
        provider: String? = null
    ): String {
        val messages = mutableListOf<ChatMessage>()

        if (systemPrompt != null) {
            messages.add(ChatMessage(role = "system", content = systemPrompt))
        }
        messages.add(ChatMessage(role = "user", content = prompt))

        val response = chat(messages, model, provider)

        return response.choices.firstOrNull()?.message?.content
            ?: response.error
            ?: "No response"
    }

    /**
     * Stream chat completion (returns Flow)
     */
    fun chatStream(
        messages: List<ChatMessage>,
        model: String = "llama3.2",
        provider: String? = null
    ): Flow<String> = flow {
        ensureServerRunning()

        val request = ChatRequest(
            model = model,
            messages = messages,
            stream = true,
            provider = provider
        )

        // Use curl with streaming
        val command = """
            curl -s -N -X POST '$API_CHAT' \
            -H 'Content-Type: application/json' \
            -d '${json.encodeToString(request).replace("'", "\\'")}'
        """.trimIndent()

        termux.observeCommandOutput(command).collect { chunk ->
            // Parse SSE data
            if (chunk.startsWith("data: ") && !chunk.contains("[DONE]")) {
                try {
                    val data = chunk.removePrefix("data: ").trim()
                    val parsed = json.decodeFromString<ChatResponse>(data)
                    parsed.choices.firstOrNull()?.message?.content?.let { emit(it) }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ========== Speech Services ==========

    @Serializable
    data class TranscriptionRequest(
        val audio_path: String,
        val language: String? = null,
        val model: String = "base"
    )

    @Serializable
    data class TranscriptionResponse(
        val text: String = "",
        val language: String? = null,
        val duration: Float? = null,
        val error: String? = null
    )

    /**
     * Transcribe audio file to text
     */
    suspend fun transcribe(
        audioPath: String,
        language: String? = null
    ): TranscriptionResponse {
        ensureServerRunning()

        val request = TranscriptionRequest(
            audio_path = audioPath,
            language = language
        )

        val result = termux.httpRequest(
            method = "POST",
            url = API_SPEECH_RECOGNIZE,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(request)
        )

        return if (result.isSuccess) {
            try {
                json.decodeFromString<TranscriptionResponse>(result.stdout)
            } catch (e: Exception) {
                TranscriptionResponse(error = "Failed to parse response: ${e.message}")
            }
        } else {
            TranscriptionResponse(error = result.stderr)
        }
    }

    @Serializable
    data class SynthesisRequest(
        val text: String,
        val voice: String = "default",
        val output_path: String? = null
    )

    @Serializable
    data class SynthesisResponse(
        val audio_path: String = "",
        val duration: Float? = null,
        val error: String? = null
    )

    /**
     * Synthesize text to speech
     */
    suspend fun synthesize(
        text: String,
        voice: String = "default",
        outputPath: String? = null
    ): SynthesisResponse {
        ensureServerRunning()

        val request = SynthesisRequest(
            text = text,
            voice = voice,
            output_path = outputPath
        )

        val result = termux.httpRequest(
            method = "POST",
            url = API_SPEECH_SYNTHESIZE,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(request)
        )

        return if (result.isSuccess) {
            try {
                json.decodeFromString<SynthesisResponse>(result.stdout)
            } catch (e: Exception) {
                SynthesisResponse(error = "Failed to parse response: ${e.message}")
            }
        } else {
            SynthesisResponse(error = result.stderr)
        }
    }

    // ========== Attention Detection ==========

    @Serializable
    data class AttentionState(
        val is_present: Boolean = false,
        val is_attentive: Boolean = false,
        val gaze_direction: GazeDirection? = null,
        val proximity: Float? = null,
        val confidence: Float = 0f,
        val error: String? = null
    )

    @Serializable
    data class GazeDirection(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f
    )

    /**
     * Get current attention state
     */
    suspend fun getAttentionState(): AttentionState {
        ensureServerRunning()

        val result = termux.httpRequest(
            method = "GET",
            url = API_ATTENTION
        )

        return if (result.isSuccess) {
            try {
                json.decodeFromString<AttentionState>(result.stdout)
            } catch (e: Exception) {
                AttentionState(error = "Failed to parse response: ${e.message}")
            }
        } else {
            AttentionState(error = result.stderr)
        }
    }

    /**
     * Observe attention state changes
     */
    fun observeAttention(
        intervalMs: Long = 500
    ): Flow<AttentionState> = flow {
        while (true) {
            emit(getAttentionState())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    // ========== App Prediction ==========

    @Serializable
    data class AppPrediction(
        val package_name: String,
        val app_name: String,
        val confidence: Float,
        val reason: String? = null
    )

    @Serializable
    data class PredictionResponse(
        val predictions: List<AppPrediction> = emptyList(),
        val context: PredictionContext? = null,
        val error: String? = null
    )

    @Serializable
    data class PredictionContext(
        val time_of_day: String? = null,
        val day_of_week: String? = null,
        val location: String? = null
    )

    /**
     * Get predicted apps based on usage patterns
     */
    suspend fun getPredictedApps(
        limit: Int = 5
    ): PredictionResponse {
        ensureServerRunning()

        val result = termux.httpRequest(
            method = "GET",
            url = "$API_PREDICT?limit=$limit"
        )

        return if (result.isSuccess) {
            try {
                json.decodeFromString<PredictionResponse>(result.stdout)
            } catch (e: Exception) {
                PredictionResponse(error = "Failed to parse response: ${e.message}")
            }
        } else {
            PredictionResponse(error = result.stderr)
        }
    }

    // ========== Utility ==========

    /**
     * Ensure server is running before making requests
     */
    private suspend fun ensureServerRunning() {
        if (_serverState.value != ServerState.RUNNING) {
            if (!startServer()) {
                throw IllegalStateException("CogAI server is not running and could not be started")
            }
        }
    }

    /**
     * Get server logs
     */
    suspend fun getServerLogs(lines: Int = 50): String {
        val result = termux.executeCommand("tail -n $lines /tmp/cogai.log")
        return result.stdout
    }

    /**
     * Configure LLM provider
     */
    suspend fun configureProvider(
        provider: String,
        apiKey: String? = null,
        baseUrl: String? = null
    ): Boolean {
        val configUpdate = buildString {
            append("cd $COGAI_DIR && python -c \"")
            append("import json; ")
            append("c = json.load(open('config/config.json')); ")
            append("c['llm']['providers']['$provider'] = {")
            if (apiKey != null) append("'api_key': '$apiKey', ")
            if (baseUrl != null) append("'base_url': '$baseUrl', ")
            append("}; ")
            append("json.dump(c, open('config/config.json', 'w'), indent=2)")
            append("\"")
        }

        val result = termux.executeCommand(configUpdate)
        return result.isSuccess
    }

    /**
     * Set default LLM provider
     */
    suspend fun setDefaultProvider(provider: String): Boolean {
        val configUpdate = """
            cd $COGAI_DIR && python -c "
            import json
            c = json.load(open('config/config.json'))
            c['llm']['default_provider'] = '$provider'
            json.dump(c, open('config/config.json', 'w'), indent=2)
            "
        """.trimIndent()

        val result = termux.executeCommand(configUpdate)
        return result.isSuccess
    }

    /**
     * Get available LLM providers
     */
    suspend fun getAvailableProviders(): List<String> {
        val result = termux.executeCommand(
            "cd $COGAI_DIR && python -c \"import json; print(','.join(json.load(open('config/config.json'))['llm']['providers'].keys()))\""
        )
        return if (result.isSuccess) {
            result.stdout.trim().split(",").filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    // Result types
    sealed class InstallResult {
        object Success : InstallResult()
        data class Error(val message: String) : InstallResult()
    }
}

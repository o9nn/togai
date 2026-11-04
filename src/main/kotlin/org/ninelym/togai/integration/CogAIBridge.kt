package org.ninelym.togai.integration

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
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
class CogAIBridge internal constructor(
    private val context: Context,
    private val termux: TermuxIntegrationService,
    /** Base URL of the CogAI gateway; the default is the gateway CogAI runs inside Termux. */
    private val apiBase: String = DEFAULT_API_BASE
) {

    companion object {
        private const val TAG = "CogAIBridge"

        // CogAI Configuration
        private const val COGAI_REPO = "https://github.com/9cog/cogai.git"
        private const val COGAI_DIR = "~/cogai"
        private const val COGAI_PORT = 8080
        private const val COGAI_HOST = "127.0.0.1"

        // Gateway address inside Termux
        const val DEFAULT_API_BASE = "http://$COGAI_HOST:$COGAI_PORT"

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

    // API Endpoints
    private val API_CHAT = "$apiBase/v1/chat/completions"
    private val API_SPEECH_RECOGNIZE = "$apiBase/v1/audio/transcriptions"
    private val API_SPEECH_SYNTHESIZE = "$apiBase/v1/audio/speech"
    private val API_ATTENTION = "$apiBase/v1/attention/state"
    private val API_PREDICT = "$apiBase/v1/predict/apps"
    private val API_HEALTH = "$apiBase/health"

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
        /** The full message (non-streamed responses). */
        val message: ChatMessage? = null,
        /** The next piece of the message (OpenAI-style streamed chunks). */
        val delta: ChatDelta? = null,
        val finish_reason: String? = null
    )

    @Serializable
    data class ChatDelta(
        val role: String? = null,
        val content: String? = null
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

        // A failure is raised, never returned as if it were the model's answer
        response.error?.let { throw CogAIException("CogAI request failed: $it") }
        return response.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: throw CogAIException("CogAI returned no answer")
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
        val command = "curl -s -N -X POST ${shellQuote(API_CHAT)} -H 'Content-Type: application/json' " +
            "-d ${shellQuote(json.encodeToString(request))}"

        // Output arrives in arbitrary pieces; reassemble whole SSE lines before parsing them
        val pending = StringBuilder()
        suspend fun FlowCollector<String>.handle(line: String) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) return
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) return
            val parsed = runCatching { json.decodeFromString<ChatResponse>(data) }.getOrNull() ?: return
            parsed.error?.let { throw CogAIException("CogAI stream failed: $it") }
            val choice = parsed.choices.firstOrNull()
            (choice?.delta?.content ?: choice?.message?.content)?.takeIf { it.isNotEmpty() }?.let { emit(it) }
        }
        termux.observeCommandOutput(command).collect { chunk ->
            pending.append(chunk)
            while (true) {
                val end = pending.indexOf("\n")
                if (end < 0) break
                val line = pending.substring(0, end)
                pending.delete(0, end + 1)
                handle(line)
            }
        }
        if (pending.isNotEmpty()) handle(pending.toString())
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
        // Values travel as one JSON argument; the program text is fixed, so nothing in them can run
        val settings = buildJsonObject {
            put("provider", provider)
            if (apiKey != null) put("api_key", apiKey)
            if (baseUrl != null) put("base_url", baseUrl)
        }
        val program = """
import json, sys
s = json.loads(sys.argv[1])
c = json.load(open('config/config.json'))
c['llm']['providers'][s['provider']] = {k: v for k, v in s.items() if k != 'provider'}
json.dump(c, open('config/config.json', 'w'), indent=2)
        """.trimIndent()
        val result = termux.executeCommand(
            "cd ${shellPath(COGAI_DIR)} && python -c ${shellQuote(program)} ${shellQuote(settings.toString())}"
        )
        return result.isSuccess
    }

    /**
     * Set default LLM provider
     */
    suspend fun setDefaultProvider(provider: String): Boolean {
        val program = """
import json, sys
c = json.load(open('config/config.json'))
c['llm']['default_provider'] = sys.argv[1]
json.dump(c, open('config/config.json', 'w'), indent=2)
        """.trimIndent()

        val result = termux.executeCommand("cd ${shellPath(COGAI_DIR)} && python -c ${shellQuote(program)} ${shellQuote(provider)}")
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

/** Raised when the CogAI server can't produce a model answer. */
class CogAIException(message: String) : RuntimeException(message)

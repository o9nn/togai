package org.ninelym.togai.integration

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import org.ninelym.togai.util.ErrorHandler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Togai+ Unified Automation Engine
 *
 * Orchestrates all Togai+ integrations for complex multi-step workflows:
 * - Termux (Linux commands, scripts, SSH, Git)
 * - CogAI (LLM Gateway, Speech, Attention)
 * - Total Commander (File operations, cloud storage) [Future]
 * - System Intelligence (Screen context, OCR) [Future]
 */
class TogaiPlusOrchestrator private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TogaiPlusOrchestrator"

        @Volatile
        private var instance: TogaiPlusOrchestrator? = null

        fun getInstance(context: Context): TogaiPlusOrchestrator {
            return instance ?: synchronized(this) {
                instance ?: TogaiPlusOrchestrator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // Service instances
    val termux: TermuxIntegrationService by lazy {
        TermuxIntegrationService.getInstance(context)
    }

    val cogai: CogAIBridge by lazy {
        CogAIBridge.getInstance(context)
    }

    // Orchestrator state
    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    data class OrchestratorState(
        val isInitialized: Boolean = false,
        val termuxAvailable: Boolean = false,
        val cogaiAvailable: Boolean = false,
        val activeWorkflows: Int = 0,
        val lastError: String? = null
    )

    // ========== Initialization ==========

    /**
     * Initialize all Togai+ services
     */
    suspend fun initialize(
        installCogAI: Boolean = true,
        onProgress: (String) -> Unit = {}
    ): InitResult {
        try {
            onProgress("Checking Termux...")
            val termuxStatus = termux.checkTermuxStatus()

            if (!termuxStatus.isTermuxInstalled) {
                return InitResult.Error("Termux is not installed. Please install from F-Droid.")
            }

            _state.update { it.copy(termuxAvailable = true) }

            if (installCogAI) {
                onProgress("Setting up CogAI...")
                if (!cogai.isInstalled()) {
                    val installResult = cogai.install(onProgress)
                    if (installResult is CogAIBridge.InstallResult.Error) {
                        return InitResult.Error("CogAI installation failed: ${installResult.message}")
                    }
                }

                onProgress("Starting CogAI server...")
                if (cogai.startServer()) {
                    _state.update { it.copy(cogaiAvailable = true) }
                }
            }

            _state.update { it.copy(isInitialized = true) }
            onProgress("Togai+ ready!")

            return InitResult.Success(
                termuxVersion = termuxStatus.termuxVersion,
                cogaiRunning = _state.value.cogaiAvailable
            )

        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Initialization failed", e)
            _state.update { it.copy(lastError = e.message) }
            return InitResult.Error(e.message ?: "Unknown error")
        }
    }

    sealed class InitResult {
        data class Success(
            val termuxVersion: String?,
            val cogaiRunning: Boolean
        ) : InitResult()

        data class Error(val message: String) : InitResult()
    }

    // ========== Workflow Engine ==========

    /**
     * Available integration services
     */
    enum class Service {
        TERMUX,
        COGAI_LLM,
        COGAI_SPEECH,
        COGAI_ATTENTION,
        FILE_SYSTEM,
        NOTIFICATION
    }

    /**
     * Workflow step definition
     */
    @Serializable
    data class WorkflowStep(
        val id: String,
        val service: String,
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val dependsOn: List<String> = emptyList(),
        val continueOnError: Boolean = false
    )

    /**
     * Workflow execution result
     */
    data class WorkflowResult(
        val success: Boolean,
        val stepResults: Map<String, StepResult>,
        val totalTimeMs: Long,
        val errors: List<String>
    )

    data class StepResult(
        val stepId: String,
        val success: Boolean,
        val output: String,
        val timeMs: Long
    )

    /**
     * Execute a workflow
     */
    suspend fun executeWorkflow(
        steps: List<WorkflowStep>,
        onStepComplete: (StepResult) -> Unit = {}
    ): WorkflowResult {
        val startTime = System.currentTimeMillis()
        val results = mutableMapOf<String, StepResult>()
        val errors = mutableListOf<String>()

        _state.update { it.copy(activeWorkflows = it.activeWorkflows + 1) }

        try {
            for (step in steps) {
                // Check dependencies
                val dependenciesMet = step.dependsOn.all { depId ->
                    results[depId]?.success == true
                }

                if (!dependenciesMet && !step.continueOnError) {
                    errors.add("Step '${step.id}' skipped: dependencies not met")
                    continue
                }

                // Execute step
                val stepStart = System.currentTimeMillis()
                val result = executeStep(step, results)
                val stepTime = System.currentTimeMillis() - stepStart

                val stepResult = StepResult(
                    stepId = step.id,
                    success = result.first,
                    output = result.second,
                    timeMs = stepTime
                )

                results[step.id] = stepResult
                onStepComplete(stepResult)

                if (!result.first) {
                    errors.add("Step '${step.id}' failed: ${result.second}")
                    if (!step.continueOnError) {
                        break
                    }
                }
            }

        } finally {
            _state.update { it.copy(activeWorkflows = it.activeWorkflows - 1) }
        }

        return WorkflowResult(
            success = errors.isEmpty(),
            stepResults = results,
            totalTimeMs = System.currentTimeMillis() - startTime,
            errors = errors
        )
    }

    /**
     * Execute a single workflow step
     */
    private suspend fun executeStep(
        step: WorkflowStep,
        previousResults: Map<String, StepResult>
    ): Pair<Boolean, String> {
        // Interpolate variables from previous results
        val params = step.params.mapValues { (_, value) ->
            interpolateVariables(value, previousResults)
        }

        return when (step.service.uppercase()) {
            "TERMUX" -> executeTermuxStep(step.action, params)
            "COGAI_LLM", "LLM" -> executeLLMStep(step.action, params)
            "COGAI_SPEECH", "SPEECH" -> executeSpeechStep(step.action, params)
            "FILE" -> executeFileStep(step.action, params)
            else -> false to "Unknown service: ${step.service}"
        }
    }

    /**
     * Interpolate {{stepId.output}} variables
     */
    private fun interpolateVariables(
        template: String,
        results: Map<String, StepResult>
    ): String {
        var result = template
        val pattern = """\{\{(\w+)\.output\}\}""".toRegex()

        pattern.findAll(template).forEach { match ->
            val stepId = match.groupValues[1]
            val replacement = results[stepId]?.output ?: ""
            result = result.replace(match.value, replacement)
        }

        return result
    }

    private suspend fun executeTermuxStep(
        action: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return when (action.lowercase()) {
            "command", "exec" -> {
                val command = params["command"] ?: return false to "Missing 'command' parameter"
                val result = termux.executeCommand(command)
                result.isSuccess to (if (result.isSuccess) result.stdout else result.stderr)
            }
            "git_clone" -> {
                val url = params["url"] ?: return false to "Missing 'url' parameter"
                val dir = params["directory"]
                val result = termux.gitClone(url, dir)
                result.isSuccess to result.stdout
            }
            "git_status" -> {
                val path = params["path"] ?: "."
                val status = termux.getGitStatus(path)
                true to status.toString()
            }
            "python" -> {
                val code = params["code"] ?: return false to "Missing 'code' parameter"
                val result = termux.python(code)
                result.isSuccess to result.stdout
            }
            "install_package" -> {
                val pkg = params["package"] ?: return false to "Missing 'package' parameter"
                val result = termux.installPackage(pkg)
                result.isSuccess to result.stdout
            }
            else -> false to "Unknown Termux action: $action"
        }
    }

    private suspend fun executeLLMStep(
        action: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return when (action.lowercase()) {
            "chat", "ask" -> {
                val prompt = params["prompt"] ?: return false to "Missing 'prompt' parameter"
                val system = params["system"]
                val model = params["model"] ?: "llama3.2"
                val provider = params["provider"]

                val response = cogai.ask(prompt, system, model, provider)
                true to response
            }
            "summarize" -> {
                val text = params["text"] ?: return false to "Missing 'text' parameter"
                val response = cogai.ask(
                    prompt = "Summarize the following text concisely:\n\n$text",
                    systemPrompt = "You are a helpful assistant that summarizes text clearly and concisely."
                )
                true to response
            }
            "analyze" -> {
                val text = params["text"] ?: return false to "Missing 'text' parameter"
                val response = cogai.ask(
                    prompt = "Analyze the following and provide insights:\n\n$text",
                    systemPrompt = "You are an expert analyst. Provide clear, actionable insights."
                )
                true to response
            }
            else -> false to "Unknown LLM action: $action"
        }
    }

    private suspend fun executeSpeechStep(
        action: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return when (action.lowercase()) {
            "transcribe" -> {
                val audioPath = params["audio_path"] ?: return false to "Missing 'audio_path' parameter"
                val result = cogai.transcribe(audioPath)
                (result.error == null) to (result.text.ifEmpty { result.error ?: "No transcription" })
            }
            "synthesize", "speak" -> {
                val text = params["text"] ?: return false to "Missing 'text' parameter"
                val voice = params["voice"] ?: "default"
                val result = cogai.synthesize(text, voice)
                (result.error == null) to (result.audio_path.ifEmpty { result.error ?: "No audio generated" })
            }
            else -> false to "Unknown Speech action: $action"
        }
    }

    private suspend fun executeFileStep(
        action: String,
        params: Map<String, String>
    ): Pair<Boolean, String> {
        return when (action.lowercase()) {
            "read" -> {
                val path = params["path"] ?: return false to "Missing 'path' parameter"
                val result = termux.executeCommand("cat '$path'")
                result.isSuccess to result.stdout
            }
            "write" -> {
                val path = params["path"] ?: return false to "Missing 'path' parameter"
                val content = params["content"] ?: return false to "Missing 'content' parameter"
                val result = termux.executeCommand("cat > '$path' << 'TOGAI_EOF'\n$content\nTOGAI_EOF")
                result.isSuccess to "File written to $path"
            }
            "list" -> {
                val path = params["path"] ?: "."
                val result = termux.executeCommand("ls -la '$path'")
                result.isSuccess to result.stdout
            }
            "find" -> {
                val pattern = params["pattern"] ?: "*"
                val path = params["path"] ?: "."
                val files = termux.findFiles(path, pattern)
                true to files.joinToString("\n")
            }
            else -> false to "Unknown File action: $action"
        }
    }

    // ========== Predefined Workflows ==========

    /**
     * Clone a repo and summarize its TODOs
     */
    suspend fun analyzeRepoTodos(
        repoUrl: String,
        targetDir: String = "~/repo_analysis"
    ): WorkflowResult {
        val steps = listOf(
            WorkflowStep(
                id = "clone",
                service = "TERMUX",
                action = "git_clone",
                params = mapOf("url" to repoUrl, "directory" to targetDir)
            ),
            WorkflowStep(
                id = "find_todos",
                service = "TERMUX",
                action = "command",
                params = mapOf("command" to "grep -rn 'TODO\\|FIXME' $targetDir --include='*.kt' --include='*.java' --include='*.py' --include='*.js' 2>/dev/null | head -50"),
                dependsOn = listOf("clone")
            ),
            WorkflowStep(
                id = "summarize",
                service = "LLM",
                action = "summarize",
                params = mapOf("text" to "{{find_todos.output}}"),
                dependsOn = listOf("find_todos")
            )
        )

        return executeWorkflow(steps)
    }

    /**
     * Setup a Python project with AI assistance
     */
    suspend fun setupPythonProject(
        projectName: String,
        description: String,
        packages: List<String> = emptyList()
    ): WorkflowResult {
        val steps = mutableListOf(
            WorkflowStep(
                id = "create_dir",
                service = "TERMUX",
                action = "command",
                params = mapOf("command" to "mkdir -p ~/$projectName && cd ~/$projectName")
            ),
            WorkflowStep(
                id = "create_venv",
                service = "TERMUX",
                action = "command",
                params = mapOf("command" to "cd ~/$projectName && python -m venv venv"),
                dependsOn = listOf("create_dir")
            ),
            WorkflowStep(
                id = "generate_readme",
                service = "LLM",
                action = "ask",
                params = mapOf(
                    "prompt" to "Generate a README.md for a Python project called '$projectName'. Description: $description. Include sections for Installation, Usage, and Contributing. Use markdown format.",
                    "system" to "You are a technical writer. Output only the README content, no explanations."
                )
            ),
            WorkflowStep(
                id = "write_readme",
                service = "FILE",
                action = "write",
                params = mapOf(
                    "path" to "~/$projectName/README.md",
                    "content" to "{{generate_readme.output}}"
                ),
                dependsOn = listOf("generate_readme", "create_dir")
            )
        )

        if (packages.isNotEmpty()) {
            steps.add(
                WorkflowStep(
                    id = "install_packages",
                    service = "TERMUX",
                    action = "command",
                    params = mapOf("command" to "cd ~/$projectName && source venv/bin/activate && pip install ${packages.joinToString(" ")}"),
                    dependsOn = listOf("create_venv")
                )
            )
        }

        return executeWorkflow(steps)
    }

    /**
     * Monitor server and alert on issues
     */
    suspend fun monitorServer(
        host: String,
        checkIntervalSeconds: Int = 60,
        onAlert: (String) -> Unit
    ) = coroutineScope {
        launch {
            while (isActive) {
                val pingResult = termux.pingHost(host, count = 2)

                if (!pingResult.isReachable) {
                    val analysis = cogai.ask(
                        prompt = "Server $host is not responding. Ping output: ${pingResult.rawOutput}. What might be wrong and what should I check?",
                        systemPrompt = "You are a DevOps expert. Provide brief, actionable troubleshooting steps."
                    )
                    onAlert("🚨 Server $host is DOWN!\n\nAI Analysis:\n$analysis")
                }

                delay(checkIntervalSeconds * 1000L)
            }
        }
    }

    /**
     * Process files with AI
     */
    suspend fun processFilesWithAI(
        directory: String,
        pattern: String,
        prompt: String
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val files = termux.findFiles(directory, pattern)

        for (file in files.take(10)) { // Limit to 10 files
            val contentResult = termux.executeCommand("cat '$file'")
            if (contentResult.isSuccess) {
                val analysis = cogai.ask(
                    prompt = "$prompt\n\nFile: $file\n\nContent:\n${contentResult.stdout.take(2000)}",
                    systemPrompt = "You are a code analyst. Be concise."
                )
                results[file] = analysis
            }
        }

        return results
    }

    // ========== Quick Actions ==========

    /**
     * Ask AI a question with system context
     */
    suspend fun ask(prompt: String): String {
        // Get some context
        val contextResult = termux.executeCommand("pwd && date && uname -a")
        val context = if (contextResult.isSuccess) contextResult.stdout else ""

        return cogai.ask(
            prompt = prompt,
            systemPrompt = """You are Togai+, an AI assistant integrated with a Linux environment via Termux.
                |Current context:
                |$context
                |
                |You can help with coding, system administration, file management, and more.
                |Be concise and practical.""".trimMargin()
        )
    }

    /**
     * Execute a natural language command
     */
    suspend fun execute(naturalLanguageCommand: String): String {
        // Ask AI to convert to shell command
        val shellCommand = cogai.ask(
            prompt = "Convert this request to a single shell command (output ONLY the command, nothing else): $naturalLanguageCommand",
            systemPrompt = "You are a shell expert. Output only the command, no explanations or markdown."
        ).trim()

        // Safety check - don't execute dangerous commands
        val dangerous = listOf("rm -rf /", "mkfs", ":(){:|:&};:", "dd if=/dev/zero")
        if (dangerous.any { shellCommand.contains(it) }) {
            return "⚠️ Refusing to execute potentially dangerous command: $shellCommand"
        }

        // Execute the command
        val result = termux.executeCommand(shellCommand)

        return buildString {
            appendLine("Command: $shellCommand")
            appendLine("Exit code: ${result.exitCode}")
            if (result.stdout.isNotBlank()) {
                appendLine("Output:\n${result.stdout}")
            }
            if (result.stderr.isNotBlank()) {
                appendLine("Errors:\n${result.stderr}")
            }
        }
    }
}

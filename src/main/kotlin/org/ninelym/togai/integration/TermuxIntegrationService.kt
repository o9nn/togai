package org.ninelym.togai.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.ninelym.togai.util.ErrorHandler
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Termux Integration Service for Togai+
 *
 * Provides Linux environment integration through Termux, enabling:
 * - Shell command execution
 * - Script running (Python, Node, Ruby, etc.)
 * - SSH client operations
 * - Git workflow support
 * - Cron-based task scheduling
 * - Local server hosting
 */
class TermuxIntegrationService private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TermuxIntegration"

        // Termux package identifiers
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_API_PACKAGE = "com.termux.api"
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux/.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"

        // Result codes
        const val RESULT_SUCCESS = 0
        const val RESULT_TERMUX_NOT_INSTALLED = -1
        const val RESULT_PERMISSION_DENIED = -2
        const val RESULT_TIMEOUT = -3
        const val RESULT_EXECUTION_ERROR = -4

        @Volatile
        private var instance: TermuxIntegrationService? = null

        fun getInstance(context: Context): TermuxIntegrationService {
            return instance ?: synchronized(this) {
                instance ?: TermuxIntegrationService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * Result of a command execution
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val executionTimeMs: Long = 0
    ) {
        val isSuccess: Boolean get() = exitCode == 0

        fun toSummary(): String = buildString {
            appendLine("Exit Code: $exitCode")
            if (stdout.isNotBlank()) appendLine("Output: $stdout")
            if (stderr.isNotBlank()) appendLine("Errors: $stderr")
            appendLine("Time: ${executionTimeMs}ms")
        }
    }

    /**
     * Server types that can be hosted
     */
    enum class ServerType(val defaultPort: Int) {
        HTTP(8080),
        WEBSOCKET(8081),
        SSH(8022)
    }

    /**
     * Termux availability status
     */
    data class TermuxStatus(
        val isTermuxInstalled: Boolean,
        val isTermuxApiInstalled: Boolean,
        val termuxVersion: String? = null,
        val hasRunCommandPermission: Boolean = false
    ) {
        val isFullyAvailable: Boolean
            get() = isTermuxInstalled && isTermuxApiInstalled && hasRunCommandPermission
    }

    // Shared directory for communication with Termux
    private val sharedDir: File by lazy {
        File(context.filesDir, "termux_shared").apply { mkdirs() }
    }

    /**
     * Check Termux availability and permissions
     */
    fun checkTermuxStatus(): TermuxStatus {
        val pm = context.packageManager

        val termuxInstalled = try {
            pm.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val termuxApiInstalled = try {
            pm.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val termuxVersion = if (termuxInstalled) {
            try {
                pm.getPackageInfo(TERMUX_PACKAGE, 0).versionName
            } catch (e: Exception) {
                null
            }
        } else null

        // Check RUN_COMMAND permission (requires Termux >= 0.118)
        val hasPermission = context.checkCallingOrSelfPermission(
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED

        return TermuxStatus(
            isTermuxInstalled = termuxInstalled,
            isTermuxApiInstalled = termuxApiInstalled,
            termuxVersion = termuxVersion,
            hasRunCommandPermission = hasPermission
        )
    }

    /**
     * Execute a shell command in Termux
     *
     * @param command The command to execute
     * @param workingDir Working directory (relative to Termux home)
     * @param timeout Maximum execution time
     * @param background Run in background without waiting for result
     * @return CommandResult with stdout, stderr, and exit code
     */
    suspend fun executeCommand(
        command: String,
        workingDir: String? = null,
        timeout: Duration = 30.seconds,
        background: Boolean = false
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // Check Termux availability
        val status = checkTermuxStatus()
        if (!status.isTermuxInstalled) {
            return@withContext CommandResult(
                exitCode = RESULT_TERMUX_NOT_INSTALLED,
                stdout = "",
                stderr = "Termux is not installed. Please install Termux from F-Droid.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        try {
            // Create unique ID for this execution
            val executionId = "${System.currentTimeMillis()}_${(0..9999).random()}"
            val stdoutFile = File(sharedDir, "stdout_$executionId.txt")
            val stderrFile = File(sharedDir, "stderr_$executionId.txt")
            val exitCodeFile = File(sharedDir, "exit_$executionId.txt")

            // Build the wrapped command that captures output
            val wrappedCommand = buildString {
                if (workingDir != null) {
                    append("cd $workingDir && ")
                }
                append("($command) > ${stdoutFile.absolutePath} 2> ${stderrFile.absolutePath}; ")
                append("echo \\$? > ${exitCodeFile.absolutePath}")
            }

            // Send command to Termux
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
                action = TERMUX_RUN_COMMAND_ACTION
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", wrappedCommand))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
            }

            context.startService(intent)

            ErrorHandler.logDebug(TAG, "Sent command to Termux: $command")

            if (background) {
                return@withContext CommandResult(
                    exitCode = 0,
                    stdout = "Command sent to background",
                    stderr = "",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            // Wait for result with timeout
            val timeoutMs = timeout.inWholeMilliseconds
            val pollInterval = 100L
            var elapsed = 0L

            while (elapsed < timeoutMs) {
                if (exitCodeFile.exists()) {
                    // Command completed
                    val exitCode = exitCodeFile.readText().trim().toIntOrNull() ?: -1
                    val stdout = if (stdoutFile.exists()) stdoutFile.readText() else ""
                    val stderr = if (stderrFile.exists()) stderrFile.readText() else ""

                    // Cleanup temp files
                    stdoutFile.delete()
                    stderrFile.delete()
                    exitCodeFile.delete()

                    return@withContext CommandResult(
                        exitCode = exitCode,
                        stdout = stdout.trim(),
                        stderr = stderr.trim(),
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                delay(pollInterval)
                elapsed += pollInterval
            }

            // Timeout reached
            CommandResult(
                exitCode = RESULT_TIMEOUT,
                stdout = "",
                stderr = "Command timed out after ${timeout.inWholeSeconds} seconds",
                executionTimeMs = System.currentTimeMillis() - startTime
            )

        } catch (e: SecurityException) {
            ErrorHandler.logError(TAG, "Permission denied for Termux command", e)
            CommandResult(
                exitCode = RESULT_PERMISSION_DENIED,
                stdout = "",
                stderr = "Permission denied: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to execute Termux command", e)
            CommandResult(
                exitCode = RESULT_EXECUTION_ERROR,
                stdout = "",
                stderr = "Execution error: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Run a script file in Termux
     *
     * @param scriptPath Path to the script (relative to Termux home or absolute)
     * @param args Arguments to pass to the script
     * @param interpreter Interpreter to use (e.g., "python", "node", "ruby")
     * @param timeout Maximum execution time
     */
    suspend fun runScript(
        scriptPath: String,
        args: List<String> = emptyList(),
        interpreter: String? = null,
        timeout: Duration = 60.seconds
    ): CommandResult {
        val command = buildString {
            if (interpreter != null) {
                append("$interpreter ")
            }
            append(scriptPath)
            if (args.isNotEmpty()) {
                append(" ")
                append(args.joinToString(" ") { "\"$it\"" })
            }
        }

        return executeCommand(command, timeout = timeout)
    }

    /**
     * Install a package using pkg
     */
    suspend fun installPackage(packageName: String): CommandResult {
        return executeCommand("pkg install -y $packageName", timeout = 120.seconds)
    }

    /**
     * Update package repository
     */
    suspend fun updatePackages(): CommandResult {
        return executeCommand("pkg update -y && pkg upgrade -y", timeout = 300.seconds)
    }

    /**
     * Check if a package is installed
     */
    suspend fun isPackageInstalled(packageName: String): Boolean {
        val result = executeCommand("pkg list-installed | grep -q $packageName")
        return result.isSuccess
    }

    /**
     * Execute Git command
     */
    suspend fun git(
        command: String,
        repoPath: String? = null,
        timeout: Duration = 60.seconds
    ): CommandResult {
        val fullCommand = if (repoPath != null) {
            "cd $repoPath && git $command"
        } else {
            "git $command"
        }
        return executeCommand(fullCommand, timeout = timeout)
    }

    /**
     * Clone a Git repository
     */
    suspend fun gitClone(
        repoUrl: String,
        targetDir: String? = null,
        branch: String? = null
    ): CommandResult {
        val command = buildString {
            append("git clone")
            if (branch != null) {
                append(" -b $branch")
            }
            append(" $repoUrl")
            if (targetDir != null) {
                append(" $targetDir")
            }
        }
        return executeCommand(command, timeout = 300.seconds)
    }

    /**
     * Execute SSH command on remote server
     */
    suspend fun ssh(
        host: String,
        command: String,
        user: String = "root",
        port: Int = 22,
        identityFile: String? = null,
        timeout: Duration = 30.seconds
    ): CommandResult {
        val sshCommand = buildString {
            append("ssh -o StrictHostKeyChecking=no -o ConnectTimeout=10")
            if (identityFile != null) {
                append(" -i $identityFile")
            }
            append(" -p $port $user@$host '$command'")
        }
        return executeCommand(sshCommand, timeout = timeout)
    }

    /**
     * Start a simple HTTP server
     */
    suspend fun startHttpServer(
        port: Int = 8080,
        directory: String = "."
    ): CommandResult {
        return executeCommand(
            "cd $directory && python -m http.server $port",
            background = true
        )
    }

    /**
     * Schedule a cron job
     *
     * @param schedule Cron schedule (e.g., "0 * * * *" for hourly)
     * @param command Command to execute
     * @return Result with the cron entry ID
     */
    suspend fun scheduleCronJob(
        schedule: String,
        command: String
    ): CommandResult {
        // Escape special characters in command
        val escapedCommand = command.replace("\"", "\\\"")
        val cronEntry = "$schedule $escapedCommand"

        // Add to crontab
        val result = executeCommand(
            "(crontab -l 2>/dev/null; echo \"$cronEntry\") | crontab -"
        )

        return if (result.isSuccess) {
            result.copy(stdout = "Cron job scheduled: $cronEntry")
        } else {
            result
        }
    }

    /**
     * List all cron jobs
     */
    suspend fun listCronJobs(): CommandResult {
        return executeCommand("crontab -l")
    }

    /**
     * Remove a cron job by pattern
     */
    suspend fun removeCronJob(pattern: String): CommandResult {
        return executeCommand(
            "crontab -l | grep -v '$pattern' | crontab -"
        )
    }

    /**
     * Execute Python code directly
     */
    suspend fun python(
        code: String,
        timeout: Duration = 30.seconds
    ): CommandResult {
        val escapedCode = code.replace("\"", "\\\"").replace("\n", "\\n")
        return executeCommand("python -c \"$escapedCode\"", timeout = timeout)
    }

    /**
     * Execute Node.js code directly
     */
    suspend fun node(
        code: String,
        timeout: Duration = 30.seconds
    ): CommandResult {
        val escapedCode = code.replace("\"", "\\\"").replace("\n", "\\n")
        return executeCommand("node -e \"$escapedCode\"", timeout = timeout)
    }

    /**
     * Observe command output in real-time (streaming)
     */
    fun observeCommandOutput(command: String): Flow<String> = flow {
        val executionId = "${System.currentTimeMillis()}_${(0..9999).random()}"
        val outputFile = File(sharedDir, "stream_$executionId.txt")

        // Start command with output redirected to file
        val wrappedCommand = "$command >> ${outputFile.absolutePath} 2>&1"

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = TERMUX_RUN_COMMAND_ACTION
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", wrappedCommand))
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }

        context.startService(intent)

        // Stream output as it arrives
        var lastPosition = 0L
        while (true) {
            if (outputFile.exists() && outputFile.length() > lastPosition) {
                val newContent = outputFile.inputStream().use { stream ->
                    stream.skip(lastPosition)
                    stream.bufferedReader().readText()
                }
                if (newContent.isNotEmpty()) {
                    emit(newContent)
                    lastPosition = outputFile.length()
                }
            }
            delay(100)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get Termux home directory path
     */
    fun getTermuxHome(): String = "/data/data/com.termux/files/home"

    /**
     * Get Termux usr directory path
     */
    fun getTermuxUsr(): String = "/data/data/com.termux/files/usr"

    /**
     * Check if a command/binary exists in Termux
     */
    suspend fun commandExists(command: String): Boolean {
        val result = executeCommand("which $command")
        return result.isSuccess && result.stdout.isNotBlank()
    }

    /**
     * Get environment variable from Termux
     */
    suspend fun getEnv(variable: String): String? {
        val result = executeCommand("echo \$$variable")
        return if (result.isSuccess && result.stdout.isNotBlank()) {
            result.stdout.trim()
        } else null
    }

    /**
     * Set environment variable in Termux (persists to .bashrc)
     */
    suspend fun setEnv(variable: String, value: String): CommandResult {
        return executeCommand(
            "echo 'export $variable=\"$value\"' >> ~/.bashrc && export $variable=\"$value\""
        )
    }
}

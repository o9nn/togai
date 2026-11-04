package org.ninelym.togai.integration

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.ninelym.togai.integration.TermuxIntegrationService.CommandResult
import kotlin.time.Duration.Companion.seconds

/**
 * Togai+ AI Extensions for Termux Integration
 *
 * High-level AI-friendly operations that combine multiple Termux commands
 * into intelligent workflows.
 */

/**
 * Execute multiple commands in parallel
 */
suspend fun TermuxIntegrationService.executeParallel(
    commands: List<String>
): List<CommandResult> = coroutineScope {
    commands.map { command ->
        async { executeCommand(command) }
    }.awaitAll()
}

/**
 * Execute commands in sequence, stopping on first error
 */
suspend fun TermuxIntegrationService.executeSequence(
    commands: List<String>,
    stopOnError: Boolean = true
): List<CommandResult> {
    val results = mutableListOf<CommandResult>()
    for (command in commands) {
        val result = executeCommand(command)
        results.add(result)
        if (stopOnError && !result.isSuccess) {
            break
        }
    }
    return results
}

/**
 * Setup a Python project with virtual environment
 */
suspend fun TermuxIntegrationService.setupPythonProject(
    projectPath: String,
    requirements: List<String> = emptyList(),
    pythonVersion: String = "python"
): CommandResult {
    val commands = mutableListOf(
        "mkdir -p $projectPath",
        "cd $projectPath && $pythonVersion -m venv venv",
        "cd $projectPath && source venv/bin/activate && pip install --upgrade pip"
    )

    if (requirements.isNotEmpty()) {
        val reqString = requirements.joinToString(" ")
        commands.add("cd $projectPath && source venv/bin/activate && pip install $reqString")
    }

    val results = executeSequence(commands)
    val lastResult = results.last()

    return if (results.all { it.isSuccess }) {
        CommandResult(
            exitCode = 0,
            stdout = "Python project setup complete at $projectPath\n" +
                    "Installed packages: ${requirements.joinToString(", ").ifEmpty { "none" }}",
            stderr = "",
            executionTimeMs = results.sumOf { it.executionTimeMs }
        )
    } else {
        lastResult
    }
}

/**
 * Setup a Node.js project
 */
suspend fun TermuxIntegrationService.setupNodeProject(
    projectPath: String,
    packages: List<String> = emptyList(),
    useYarn: Boolean = false
): CommandResult {
    val pm = if (useYarn) "yarn" else "npm"
    val installCmd = if (useYarn) "yarn add" else "npm install"

    val commands = mutableListOf(
        "mkdir -p $projectPath",
        "cd $projectPath && $pm init -y"
    )

    if (packages.isNotEmpty()) {
        val pkgString = packages.joinToString(" ")
        commands.add("cd $projectPath && $installCmd $pkgString")
    }

    val results = executeSequence(commands)

    return if (results.all { it.isSuccess }) {
        CommandResult(
            exitCode = 0,
            stdout = "Node.js project setup complete at $projectPath",
            stderr = "",
            executionTimeMs = results.sumOf { it.executionTimeMs }
        )
    } else {
        results.last()
    }
}

/**
 * Find all TODO comments in a Git repository
 */
suspend fun TermuxIntegrationService.findTodosInRepo(
    repoPath: String
): CommandResult {
    return executeCommand(
        "cd $repoPath && grep -rn 'TODO\\|FIXME\\|HACK\\|XXX' --include='*.kt' --include='*.java' --include='*.py' --include='*.js' --include='*.ts' . 2>/dev/null || true"
    )
}

/**
 * Get git repository status summary
 */
suspend fun TermuxIntegrationService.getGitStatus(
    repoPath: String
): GitStatusSummary {
    val statusResult = git("status --porcelain", repoPath)
    val branchResult = git("branch --show-current", repoPath)
    val logResult = git("log --oneline -5", repoPath)

    val lines = statusResult.stdout.lines().filter { it.isNotBlank() }
    val modified = lines.count { it.startsWith(" M") || it.startsWith("M ") }
    val added = lines.count { it.startsWith("A ") || it.startsWith("??") }
    val deleted = lines.count { it.startsWith(" D") || it.startsWith("D ") }

    return GitStatusSummary(
        branch = branchResult.stdout.trim(),
        modifiedFiles = modified,
        addedFiles = added,
        deletedFiles = deleted,
        recentCommits = logResult.stdout.lines().filter { it.isNotBlank() },
        isClean = lines.isEmpty()
    )
}

data class GitStatusSummary(
    val branch: String,
    val modifiedFiles: Int,
    val addedFiles: Int,
    val deletedFiles: Int,
    val recentCommits: List<String>,
    val isClean: Boolean
) {
    override fun toString(): String = buildString {
        appendLine("Branch: $branch")
        appendLine("Status: ${if (isClean) "Clean" else "Has changes"}")
        if (!isClean) {
            appendLine("  Modified: $modifiedFiles, Added: $addedFiles, Deleted: $deletedFiles")
        }
        appendLine("Recent commits:")
        recentCommits.forEach { appendLine("  $it") }
    }
}

/**
 * Quick system health check
 */
suspend fun TermuxIntegrationService.systemHealthCheck(): SystemHealth {
    val results = executeParallel(
        listOf(
            "df -h | head -5",
            "free -h 2>/dev/null || echo 'N/A'",
            "uptime",
            "uname -a"
        )
    )

    return SystemHealth(
        diskUsage = results.getOrNull(0)?.stdout ?: "Unknown",
        memoryUsage = results.getOrNull(1)?.stdout ?: "Unknown",
        uptime = results.getOrNull(2)?.stdout?.trim() ?: "Unknown",
        systemInfo = results.getOrNull(3)?.stdout?.trim() ?: "Unknown"
    )
}

data class SystemHealth(
    val diskUsage: String,
    val memoryUsage: String,
    val uptime: String,
    val systemInfo: String
) {
    override fun toString(): String = buildString {
        appendLine("=== System Health ===")
        appendLine("System: $systemInfo")
        appendLine("Uptime: $uptime")
        appendLine("\nDisk Usage:\n$diskUsage")
        appendLine("\nMemory:\n$memoryUsage")
    }
}

/**
 * Ping a host to check connectivity
 */
suspend fun TermuxIntegrationService.pingHost(
    host: String,
    count: Int = 3
): PingResult {
    val result = executeCommand("ping -c $count $host", timeout = 30.seconds)

    val avgLatency = if (result.isSuccess) {
        // Parse avg latency from ping output
        val regex = """rtt min/avg/max/mdev = [\d.]+/([\d.]+)/""".toRegex()
        regex.find(result.stdout)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    } else null

    return PingResult(
        host = host,
        isReachable = result.isSuccess,
        averageLatencyMs = avgLatency,
        rawOutput = result.stdout
    )
}

data class PingResult(
    val host: String,
    val isReachable: Boolean,
    val averageLatencyMs: Double?,
    val rawOutput: String
) {
    override fun toString(): String = buildString {
        appendLine("Host: $host")
        appendLine("Reachable: $isReachable")
        if (averageLatencyMs != null) {
            appendLine("Latency: ${averageLatencyMs}ms")
        }
    }
}

/**
 * Download a file using curl or wget
 */
suspend fun TermuxIntegrationService.downloadFile(
    url: String,
    outputPath: String,
    useCurl: Boolean = true
): CommandResult {
    val command = if (useCurl) {
        "curl -L -o $outputPath '$url'"
    } else {
        "wget -O $outputPath '$url'"
    }
    return executeCommand(command, timeout = 300.seconds)
}

/**
 * Create a simple REST API call
 */
suspend fun TermuxIntegrationService.httpRequest(
    method: String = "GET",
    url: String,
    headers: Map<String, String> = emptyMap(),
    body: String? = null
): CommandResult {
    val command = buildString {
        append("curl -s -X $method")
        headers.forEach { (key, value) ->
            append(" -H '$key: $value'")
        }
        if (body != null) {
            append(" -d '${body.replace("'", "\\'")}'")
        }
        append(" '$url'")
    }
    return executeCommand(command)
}

/**
 * Monitor a log file in real-time
 */
fun TermuxIntegrationService.tailLog(
    logPath: String,
    lines: Int = 50
) = observeCommandOutput("tail -f -n $lines $logPath")

/**
 * Search for files matching a pattern
 */
suspend fun TermuxIntegrationService.findFiles(
    directory: String,
    pattern: String,
    maxDepth: Int? = null
): List<String> {
    val depthArg = if (maxDepth != null) "-maxdepth $maxDepth" else ""
    val result = executeCommand("find $directory $depthArg -name '$pattern' 2>/dev/null")
    return if (result.isSuccess) {
        result.stdout.lines().filter { it.isNotBlank() }
    } else {
        emptyList()
    }
}

/**
 * Archive a directory
 */
suspend fun TermuxIntegrationService.createArchive(
    sourcePath: String,
    archivePath: String,
    format: ArchiveFormat = ArchiveFormat.TAR_GZ
): CommandResult {
    val command = when (format) {
        ArchiveFormat.TAR_GZ -> "tar -czvf $archivePath -C $(dirname $sourcePath) $(basename $sourcePath)"
        ArchiveFormat.ZIP -> "zip -r $archivePath $sourcePath"
        ArchiveFormat.TAR -> "tar -cvf $archivePath -C $(dirname $sourcePath) $(basename $sourcePath)"
    }
    return executeCommand(command, timeout = 300.seconds)
}

enum class ArchiveFormat {
    TAR_GZ, ZIP, TAR
}

/**
 * Extract an archive
 */
suspend fun TermuxIntegrationService.extractArchive(
    archivePath: String,
    destinationPath: String
): CommandResult {
    val command = when {
        archivePath.endsWith(".tar.gz") || archivePath.endsWith(".tgz") ->
            "tar -xzvf $archivePath -C $destinationPath"
        archivePath.endsWith(".zip") ->
            "unzip $archivePath -d $destinationPath"
        archivePath.endsWith(".tar") ->
            "tar -xvf $archivePath -C $destinationPath"
        archivePath.endsWith(".7z") ->
            "7z x $archivePath -o$destinationPath"
        else ->
            "tar -xf $archivePath -C $destinationPath"
    }
    return executeCommand("mkdir -p $destinationPath && $command")
}

/**
 * Run a web scraping task with Python
 */
suspend fun TermuxIntegrationService.scrapeUrl(
    url: String,
    selector: String? = null
): CommandResult {
    val pythonCode = if (selector != null) {
        """
import urllib.request
from html.parser import HTMLParser
html = urllib.request.urlopen('$url').read().decode()
print(html)
        """.trimIndent()
    } else {
        """
import urllib.request
html = urllib.request.urlopen('$url').read().decode()
print(html)
        """.trimIndent()
    }
    return python(pythonCode, timeout = 30.seconds)
}

/**
 * Execute a workflow defined as a list of steps
 */
suspend fun TermuxIntegrationService.executeWorkflow(
    steps: List<WorkflowStep>
): WorkflowResult {
    val results = mutableMapOf<String, CommandResult>()
    val errors = mutableListOf<String>()

    for (step in steps) {
        // Check dependencies
        val dependenciesMet = step.dependsOn.all { depId ->
            results[depId]?.isSuccess == true
        }

        if (!dependenciesMet) {
            errors.add("Step '${step.id}' skipped: dependencies not met")
            continue
        }

        val result = executeCommand(step.command, timeout = step.timeout)
        results[step.id] = result

        if (!result.isSuccess && step.required) {
            errors.add("Required step '${step.id}' failed: ${result.stderr}")
            break
        }
    }

    return WorkflowResult(
        stepResults = results,
        errors = errors,
        success = errors.isEmpty() || errors.all { it.contains("skipped") }
    )
}

data class WorkflowStep(
    val id: String,
    val command: String,
    val dependsOn: List<String> = emptyList(),
    val required: Boolean = true,
    val timeout: kotlin.time.Duration = 30.seconds
)

data class WorkflowResult(
    val stepResults: Map<String, CommandResult>,
    val errors: List<String>,
    val success: Boolean
) {
    override fun toString(): String = buildString {
        appendLine("=== Workflow Result ===")
        appendLine("Success: $success")
        appendLine("\nSteps:")
        stepResults.forEach { (id, result) ->
            appendLine("  $id: ${if (result.isSuccess) "OK" else "FAILED"}")
        }
        if (errors.isNotEmpty()) {
            appendLine("\nErrors:")
            errors.forEach { appendLine("  - $it") }
        }
    }
}

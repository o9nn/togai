package org.ninelym.togai.integration

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * AI-Powered Extensions for Total Commander Integration
 *
 * Combines Total Commander file operations with CogAI intelligence
 * for smart file management and organization.
 */

// ========== AI-Powered Organization ==========

/**
 * Organize files by AI-determined categories
 */
suspend fun TotalCommanderService.organizeWithAI(
    sourceDir: String,
    destDir: String,
    cogai: CogAIBridge
): OrganizeResult {
    val files = listDirectory(sourceDir, recursive = false)
        .filter { it.isFile }

    if (files.isEmpty()) {
        return OrganizeResult(0, 0, emptyMap(), emptyList())
    }

    // Get AI to categorize files
    val fileList = files.take(50).joinToString("\n") { "${it.name} (${formatSize(it.size)})" }

    val categorization = cogai.ask(
        prompt = """Categorize these files into folders. Output ONLY a JSON object mapping filename to category folder.
Categories to use: Documents, Images, Videos, Music, Archives, Code, Data, Other

Files:
$fileList

Output format: {"filename.ext": "Category", ...}""",
        systemPrompt = "You are a file organization assistant. Output only valid JSON, no explanations."
    )

    // Parse AI response
    val categoryMap = parseCategoryJson(categorization)

    var moved = 0
    var failed = 0
    val errors = mutableListOf<String>()
    val categoryCounts = mutableMapOf<String, Int>()

    for (file in files) {
        val category = categoryMap[file.name] ?: guessCategory(file)
        val targetDir = "$destDir/$category"

        try {
            File(targetDir).mkdirs()
            val sourceFile = File(file.path)
            val destFile = File(targetDir, file.name)

            if (sourceFile.renameTo(destFile)) {
                moved++
                categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            } else {
                failed++
                errors.add("Failed to move ${file.name}")
            }
        } catch (e: Exception) {
            failed++
            errors.add("Error moving ${file.name}: ${e.message}")
        }
    }

    return OrganizeResult(moved, failed, categoryCounts, errors)
}

data class OrganizeResult(
    val filesMoved: Int,
    val filesFailed: Int,
    val categoryCounts: Map<String, Int>,
    val errors: List<String>
) {
    override fun toString(): String = buildString {
        appendLine("=== Organization Complete ===")
        appendLine("Files moved: $filesMoved")
        appendLine("Files failed: $filesFailed")
        appendLine("\nBy category:")
        categoryCounts.forEach { (cat, count) ->
            appendLine("  $cat: $count files")
        }
        if (errors.isNotEmpty()) {
            appendLine("\nErrors:")
            errors.forEach { appendLine("  - $it") }
        }
    }
}

private fun parseCategoryJson(json: String): Map<String, String> {
    return try {
        val cleaned = json.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val result = mutableMapOf<String, String>()
        val regex = """"([^"]+)":\s*"([^"]+)"""".toRegex()

        regex.findAll(cleaned).forEach { match ->
            result[match.groupValues[1]] = match.groupValues[2]
        }

        result
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun guessCategory(file: TotalCommanderService.FileInfo): String {
    return when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "Images"
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> "Videos"
        "mp3", "wav", "flac", "aac", "ogg", "m4a" -> "Music"
        "pdf", "doc", "docx", "txt", "rtf", "odt" -> "Documents"
        "zip", "rar", "7z", "tar", "gz" -> "Archives"
        "kt", "java", "py", "js", "ts", "cpp", "c", "h", "xml", "json" -> "Code"
        "csv", "xls", "xlsx", "db", "sql" -> "Data"
        else -> "Other"
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

// ========== Smart File Search ==========

/**
 * Search files using natural language
 */
suspend fun TotalCommanderService.searchWithAI(
    query: String,
    directory: String,
    cogai: CogAIBridge
): List<TotalCommanderService.FileInfo> {
    // Get AI to generate search pattern
    val searchStrategy = cogai.ask(
        prompt = """Convert this natural language file search to a search strategy.

Query: "$query"
Directory: $directory

Output a JSON object with:
- patterns: array of glob patterns to search (e.g., ["*.pdf", "report*"])
- extensions: array of file extensions (e.g., ["pdf", "docx"])
- keywords: array of keywords to look for in filenames
- recursive: true/false

Output only valid JSON.""",
        systemPrompt = "You are a file search assistant. Output only JSON."
    )

    // Parse strategy
    val patterns = parseSearchPatterns(searchStrategy)
    val extensions = parseSearchExtensions(searchStrategy)
    val keywords = parseSearchKeywords(searchStrategy)
    val recursive = searchStrategy.contains("\"recursive\": true", ignoreCase = true)

    // Execute searches
    val results = mutableSetOf<TotalCommanderService.FileInfo>()

    // Search by patterns
    patterns.forEach { pattern ->
        results.addAll(searchFiles(directory, pattern, recursive))
    }

    // Search by extensions
    extensions.forEach { ext ->
        results.addAll(searchFiles(directory, "*.$ext", recursive))
    }

    // Filter by keywords if we have results
    val finalResults = if (keywords.isNotEmpty() && results.isNotEmpty()) {
        results.filter { file ->
            keywords.any { keyword ->
                file.name.contains(keyword, ignoreCase = true)
            }
        }
    } else {
        results.toList()
    }

    return finalResults.sortedByDescending { it.lastModified }
}

private fun parseSearchPatterns(json: String): List<String> {
    val regex = """"patterns":\s*\[([^\]]+)\]""".toRegex()
    val match = regex.find(json) ?: return emptyList()
    return """"([^"]+)"""".toRegex()
        .findAll(match.groupValues[1])
        .map { it.groupValues[1] }
        .toList()
}

private fun parseSearchExtensions(json: String): List<String> {
    val regex = """"extensions":\s*\[([^\]]+)\]""".toRegex()
    val match = regex.find(json) ?: return emptyList()
    return """"([^"]+)"""".toRegex()
        .findAll(match.groupValues[1])
        .map { it.groupValues[1] }
        .toList()
}

private fun parseSearchKeywords(json: String): List<String> {
    val regex = """"keywords":\s*\[([^\]]+)\]""".toRegex()
    val match = regex.find(json) ?: return emptyList()
    return """"([^"]+)"""".toRegex()
        .findAll(match.groupValues[1])
        .map { it.groupValues[1] }
        .toList()
}

// ========== File Analysis ==========

/**
 * Analyze directory with AI
 */
suspend fun TotalCommanderService.analyzeDirectoryWithAI(
    directory: String,
    cogai: CogAIBridge
): DirectoryAnalysis {
    val files = listDirectory(directory, recursive = true)
    val stats = getStorageStats(directory)

    // Build summary
    val filesByType = files.filter { it.isFile }
        .groupBy { guessCategory(it) }
        .mapValues { it.value.size }

    val largestFiles = files
        .filter { it.isFile }
        .sortedByDescending { it.size }
        .take(10)

    val oldestFiles = files
        .filter { it.isFile }
        .sortedBy { it.lastModified }
        .take(10)

    val duplicateCandidates = files
        .filter { it.isFile }
        .groupBy { "${it.name}_${it.size}" }
        .filter { it.value.size > 1 }
        .flatMap { it.value }

    // Get AI analysis
    val summary = buildString {
        appendLine("Directory: $directory")
        appendLine("Total files: ${files.count { it.isFile }}")
        appendLine("Total directories: ${files.count { it.isDirectory }}")
        appendLine("Total size: ${stats.formatSize(stats.usedSpace)}")
        appendLine("\nFiles by type:")
        filesByType.forEach { (type, count) -> appendLine("  $type: $count") }
        appendLine("\nLargest files:")
        largestFiles.take(5).forEach {
            appendLine("  ${it.name} (${formatSize(it.size)})")
        }
    }

    val aiInsights = cogai.ask(
        prompt = """Analyze this directory and provide insights:

$summary

Provide:
1. Overall assessment (is it organized?)
2. Recommendations for cleanup
3. Potential space savings
4. Any concerns (old files, large files, duplicates)

Be concise and actionable.""",
        systemPrompt = "You are a file management expert. Provide practical advice."
    )

    return DirectoryAnalysis(
        path = directory,
        totalFiles = files.count { it.isFile },
        totalDirectories = files.count { it.isDirectory },
        totalSize = stats.usedSpace,
        filesByType = filesByType,
        largestFiles = largestFiles,
        oldestFiles = oldestFiles,
        duplicateCandidates = duplicateCandidates,
        aiInsights = aiInsights
    )
}

data class DirectoryAnalysis(
    val path: String,
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSize: Long,
    val filesByType: Map<String, Int>,
    val largestFiles: List<TotalCommanderService.FileInfo>,
    val oldestFiles: List<TotalCommanderService.FileInfo>,
    val duplicateCandidates: List<TotalCommanderService.FileInfo>,
    val aiInsights: String
) {
    override fun toString(): String = buildString {
        appendLine("=== Directory Analysis: $path ===")
        appendLine("Files: $totalFiles | Directories: $totalDirectories")
        appendLine("Size: ${formatSize(totalSize)}")
        appendLine("\nBy Type:")
        filesByType.forEach { (type, count) -> appendLine("  $type: $count") }
        appendLine("\nPotential Duplicates: ${duplicateCandidates.size}")
        appendLine("\n--- AI Insights ---")
        appendLine(aiInsights)
    }
}

// ========== Smart Backup ==========

/**
 * Create smart backup with AI naming
 */
suspend fun TotalCommanderService.smartBackup(
    sourceDir: String,
    backupBaseDir: String,
    cogai: CogAIBridge,
    includeDescription: Boolean = true
): BackupResult {
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
    val sourceName = File(sourceDir).name

    // Get AI to suggest backup name
    val backupName = if (includeDescription) {
        val files = listDirectory(sourceDir, recursive = false).take(20)
        val fileList = files.joinToString(", ") { it.name }

        val suggestion = cogai.ask(
            prompt = "Suggest a short (2-4 words, no spaces, use underscores) descriptive backup name for a folder containing: $fileList. Output only the name, nothing else.",
            systemPrompt = "You are a file naming assistant. Output only the suggested name."
        ).trim().replace(" ", "_").take(30)

        "${sourceName}_${suggestion}_$timestamp"
    } else {
        "${sourceName}_$timestamp"
    }

    val backupDir = "$backupBaseDir/$backupName"
    val archivePath = "$backupDir.zip"

    // Create backup
    File(backupBaseDir).mkdirs()

    val syncResult = syncFolders(sourceDir, backupDir, TotalCommanderService.SyncMode.MIRROR)

    // Create archive
    val archiveCreated = if (syncResult.success) {
        zip(listOf(backupDir), archivePath)
    } else false

    // Cleanup uncompressed backup if archive created
    if (archiveCreated) {
        File(backupDir).deleteRecursively()
    }

    return BackupResult(
        success = syncResult.success,
        backupPath = if (archiveCreated) archivePath else backupDir,
        isCompressed = archiveCreated,
        fileCount = syncResult.filesCopied,
        totalSize = getDirectorySize(sourceDir),
        duration = syncResult.durationMs
    )
}

data class BackupResult(
    val success: Boolean,
    val backupPath: String,
    val isCompressed: Boolean,
    val fileCount: Int,
    val totalSize: Long,
    val duration: Long
) {
    override fun toString(): String = buildString {
        appendLine("=== Backup ${if (success) "Complete" else "Failed"} ===")
        appendLine("Path: $backupPath")
        appendLine("Files: $fileCount")
        appendLine("Size: ${formatSize(totalSize)}")
        appendLine("Compressed: $isCompressed")
        appendLine("Duration: ${duration}ms")
    }
}

// ========== Cleanup Helpers ==========

/**
 * Find and optionally delete empty directories
 */
fun TotalCommanderService.findEmptyDirectories(
    directory: String
): List<TotalCommanderService.FileInfo> {
    return listDirectory(directory, recursive = true)
        .filter { it.isDirectory }
        .filter { dir ->
            File(dir.path).listFiles()?.isEmpty() == true
        }
}

/**
 * Find old files (not modified in N days)
 */
fun TotalCommanderService.findOldFiles(
    directory: String,
    daysOld: Int = 365
): List<TotalCommanderService.FileInfo> {
    val cutoff = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)

    return listDirectory(directory, recursive = true)
        .filter { it.isFile && it.lastModified < cutoff }
        .sortedBy { it.lastModified }
}

/**
 * Find large files
 */
fun TotalCommanderService.findLargeFiles(
    directory: String,
    minSizeMB: Long = 100
): List<TotalCommanderService.FileInfo> {
    val minBytes = minSizeMB * 1_000_000

    return listDirectory(directory, recursive = true)
        .filter { it.isFile && it.size >= minBytes }
        .sortedByDescending { it.size }
}

/**
 * Calculate potential space savings
 */
suspend fun TotalCommanderService.calculateSpaceSavings(
    directory: String
): SpaceSavingsReport {
    val emptyDirs = findEmptyDirectories(directory)
    val oldFiles = findOldFiles(directory, 365)
    val largeFiles = findLargeFiles(directory, 100)

    val oldFilesSize = oldFiles.sumOf { it.size }
    val largeFilesSize = largeFiles.sumOf { it.size }

    return SpaceSavingsReport(
        emptyDirectories = emptyDirs.size,
        oldFiles = oldFiles.size,
        oldFilesSize = oldFilesSize,
        largeFiles = largeFiles.size,
        largeFilesSize = largeFilesSize,
        potentialSavings = oldFilesSize // Conservative estimate
    )
}

data class SpaceSavingsReport(
    val emptyDirectories: Int,
    val oldFiles: Int,
    val oldFilesSize: Long,
    val largeFiles: Int,
    val largeFilesSize: Long,
    val potentialSavings: Long
) {
    override fun toString(): String = buildString {
        appendLine("=== Space Savings Report ===")
        appendLine("Empty directories: $emptyDirectories")
        appendLine("Old files (>1 year): $oldFiles (${formatSize(oldFilesSize)})")
        appendLine("Large files (>100MB): $largeFiles (${formatSize(largeFilesSize)})")
        appendLine("Potential savings: ${formatSize(potentialSavings)}")
    }
}

// ========== Watch & React ==========

/**
 * Watch directory and process new files with AI
 */
fun TotalCommanderService.watchAndProcess(
    directory: String,
    cogai: CogAIBridge,
    onNewFile: suspend (TotalCommanderService.FileInfo, String) -> Unit
): Flow<Pair<TotalCommanderService.FileInfo, String>> = flow {
    watchDirectory(directory).collect { event ->
        if (event.type == TotalCommanderService.ChangeType.CREATED) {
            val fileInfo = getFileInfo(event.path)
            if (fileInfo != null && fileInfo.isFile) {
                // Get AI description
                val description = cogai.ask(
                    prompt = "Describe what this file likely contains based on its name: ${fileInfo.name}. Be brief (1 sentence).",
                    systemPrompt = "You are a file analyst."
                )

                emit(fileInfo to description)
                onNewFile(fileInfo, description)
            }
        }
    }
}

// ========== Integration with Orchestrator ==========

/**
 * Add Total Commander service to orchestrator
 */
fun TogaiPlusOrchestrator.withTotalCommander(): TotalCommanderService {
    return TotalCommanderService.getInstance(
        // Access context through termux service's context
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Context
    )
}

package org.ninelym.togai.integration

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.ninelym.togai.util.ErrorHandler
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Total Commander Integration Service for Togai+
 *
 * Provides advanced file management capabilities through Total Commander:
 * - File operations (copy, move, delete, rename)
 * - Cloud storage access (Google Drive, Dropbox, OneDrive, etc.)
 * - LAN/Network shares (SMB/CIFS, FTP, SFTP, WebDAV)
 * - Archive handling (ZIP, RAR, 7z)
 * - Directory watching and synchronization
 *
 * @see https://www.ghisler.com/androidapi.htm
 */
class TotalCommanderService private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "TotalCommanderService"

        // Total Commander package
        private const val TC_PACKAGE = "com.ghisler.android.TotalCommander"
        private const val TC_ACTIVITY = "com.ghisler.android.TotalCommander.TotalCommander"

        // Intent actions
        private const val ACTION_OPEN = "com.ghisler.android.TotalCommander.ACTION_OPEN"
        private const val ACTION_SEND = "com.ghisler.android.TotalCommander.ACTION_SEND"

        // Cloud plugin packages
        private const val PLUGIN_GDRIVE = "com.ghisler.tcplugins.GoogleDrive"
        private const val PLUGIN_DROPBOX = "com.ghisler.tcplugins.Dropbox"
        private const val PLUGIN_ONEDRIVE = "com.ghisler.tcplugins.OneDrive"
        private const val PLUGIN_WEBDAV = "com.ghisler.tcplugins.WebDAV"
        private const val PLUGIN_SFTP = "com.ghisler.tcplugins.SFTP"
        private const val PLUGIN_LAN = "com.ghisler.android.TotalCommander"

        @Volatile
        private var instance: TotalCommanderService? = null

        fun getInstance(context: Context): TotalCommanderService {
            return instance ?: synchronized(this) {
                instance ?: TotalCommanderService(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // ========== Data Classes ==========

    /**
     * File operation types
     */
    enum class OperationType {
        COPY,
        MOVE,
        DELETE,
        RENAME,
        MKDIR,
        ARCHIVE,
        EXTRACT,
        VIEW,
        EDIT,
        SEND,
        SYNC
    }

    /**
     * Cloud storage providers
     */
    enum class CloudProvider(val pluginPackage: String, val urlScheme: String) {
        GOOGLE_DRIVE(PLUGIN_GDRIVE, "gdrive://"),
        DROPBOX(PLUGIN_DROPBOX, "dropbox://"),
        ONEDRIVE(PLUGIN_ONEDRIVE, "onedrive://"),
        WEBDAV(PLUGIN_WEBDAV, "webdav://"),
        SFTP(PLUGIN_SFTP, "sftp://"),
        FTP(TC_PACKAGE, "ftp://"),
        SMB(PLUGIN_LAN, "smb://")
    }

    /**
     * Sync modes
     */
    enum class SyncMode {
        MIRROR,      // Make destination identical to source
        TWO_WAY,     // Sync changes both ways
        BACKUP,      // Copy new/changed files to destination
        UPDATE       // Update existing files only
    }

    /**
     * Archive formats
     */
    enum class ArchiveFormat(val extension: String) {
        ZIP(".zip"),
        SEVEN_ZIP(".7z"),
        TAR_GZ(".tar.gz"),
        TAR(".tar"),
        RAR(".rar")
    }

    /**
     * File information
     */
    data class FileInfo(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val isHidden: Boolean = false,
        val extension: String = ""
    ) {
        val isFile: Boolean get() = !isDirectory

        companion object {
            fun fromFile(file: File): FileInfo = FileInfo(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified(),
                isHidden = file.isHidden,
                extension = file.extension
            )
        }
    }

    /**
     * File operation request
     */
    data class FileOperation(
        val type: OperationType,
        val sources: List<String>,
        val destination: String? = null,
        val options: FileOperationOptions = FileOperationOptions()
    )

    /**
     * File operation options
     */
    data class FileOperationOptions(
        val overwrite: Boolean = false,
        val recursive: Boolean = true,
        val preserveTimestamp: Boolean = true,
        val includeHidden: Boolean = false,
        val archiveFormat: ArchiveFormat = ArchiveFormat.ZIP,
        val compressionLevel: Int = 5, // 0-9
        val password: String? = null
    )

    /**
     * File change event for watching
     */
    data class FileChangeEvent(
        val path: String,
        val type: ChangeType,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class ChangeType {
        CREATED,
        MODIFIED,
        DELETED
    }

    /**
     * Sync report
     */
    data class SyncReport(
        val success: Boolean,
        val filesScanned: Int,
        val filesCopied: Int,
        val filesDeleted: Int,
        val filesSkipped: Int,
        val errors: List<String>,
        val durationMs: Long
    )

    /**
     * TC availability status
     */
    data class TCStatus(
        val isInstalled: Boolean,
        val version: String? = null,
        val installedPlugins: List<CloudProvider> = emptyList()
    )

    // ========== Status & Availability ==========

    /**
     * Check Total Commander availability
     */
    fun checkStatus(): TCStatus {
        val pm = context.packageManager

        val isInstalled = try {
            pm.getPackageInfo(TC_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

        val version = if (isInstalled) {
            try {
                pm.getPackageInfo(TC_PACKAGE, 0).versionName
            } catch (e: Exception) {
                null
            }
        } else null

        val installedPlugins = CloudProvider.values().filter { provider ->
            try {
                pm.getPackageInfo(provider.pluginPackage, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

        return TCStatus(
            isInstalled = isInstalled,
            version = version,
            installedPlugins = installedPlugins
        )
    }

    /**
     * Check if Total Commander is installed
     */
    fun isInstalled(): Boolean = checkStatus().isInstalled

    // ========== File Operations ==========

    /**
     * Execute a file operation via Total Commander
     */
    fun executeOperation(operation: FileOperation): Boolean {
        if (!isInstalled()) {
            ErrorHandler.logError(TAG, "Total Commander is not installed", null)
            return false
        }

        return when (operation.type) {
            OperationType.COPY -> copyFiles(operation.sources, operation.destination!!, operation.options)
            OperationType.MOVE -> moveFiles(operation.sources, operation.destination!!, operation.options)
            OperationType.DELETE -> deleteFiles(operation.sources)
            OperationType.RENAME -> renameFile(operation.sources.first(), operation.destination!!)
            OperationType.MKDIR -> createDirectory(operation.destination!!)
            OperationType.ARCHIVE -> createArchive(operation.sources, operation.destination!!, operation.options)
            OperationType.EXTRACT -> extractArchive(operation.sources.first(), operation.destination!!, operation.options)
            OperationType.VIEW -> viewFile(operation.sources.first())
            OperationType.EDIT -> editFile(operation.sources.first())
            OperationType.SEND -> sendFiles(operation.sources)
            OperationType.SYNC -> false // Use syncFolders() instead
        }
    }

    /**
     * Open a path in Total Commander
     */
    fun openPath(path: String): Boolean {
        return try {
            val intent = Intent(ACTION_OPEN).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse("file://$path")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to open path in TC", e)
            false
        }
    }

    /**
     * Open cloud storage in Total Commander
     */
    fun openCloud(provider: CloudProvider, path: String = "/"): Boolean {
        return try {
            val uri = "${provider.urlScheme}$path"
            val intent = Intent(ACTION_OPEN).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to open cloud storage", e)
            false
        }
    }

    /**
     * Open LAN/SMB share
     */
    fun openNetworkShare(
        host: String,
        share: String? = null,
        username: String? = null,
        password: String? = null
    ): Boolean {
        return try {
            val uri = buildString {
                append("smb://")
                if (username != null) {
                    append(username)
                    if (password != null) append(":$password")
                    append("@")
                }
                append(host)
                if (share != null) append("/$share")
            }

            val intent = Intent(ACTION_OPEN).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to open network share", e)
            false
        }
    }

    /**
     * Copy files
     */
    private fun copyFiles(
        sources: List<String>,
        destination: String,
        options: FileOperationOptions
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                setPackage(TC_PACKAGE)
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(sources.map { Uri.parse("file://$it") })
                )
                putExtra("dest", destination)
                putExtra("action", "copy")
                putExtra("overwrite", options.overwrite)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to copy files", e)
            false
        }
    }

    /**
     * Move files
     */
    private fun moveFiles(
        sources: List<String>,
        destination: String,
        options: FileOperationOptions
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                setPackage(TC_PACKAGE)
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(sources.map { Uri.parse("file://$it") })
                )
                putExtra("dest", destination)
                putExtra("action", "move")
                putExtra("overwrite", options.overwrite)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to move files", e)
            false
        }
    }

    /**
     * Delete files
     */
    private fun deleteFiles(paths: List<String>): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse("file://${paths.first()}")
                if (paths.size > 1) {
                    putExtra("files", paths.toTypedArray())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to delete files", e)
            false
        }
    }

    /**
     * Rename file
     */
    private fun renameFile(source: String, newName: String): Boolean {
        return try {
            val file = File(source)
            val newFile = File(file.parent, newName)
            file.renameTo(newFile)
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to rename file", e)
            false
        }
    }

    /**
     * Create directory
     */
    private fun createDirectory(path: String): Boolean {
        return try {
            File(path).mkdirs()
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to create directory", e)
            false
        }
    }

    /**
     * View file in Total Commander
     */
    private fun viewFile(path: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse("file://$path")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to view file", e)
            false
        }
    }

    /**
     * Edit file in Total Commander's editor
     */
    private fun editFile(path: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setPackage(TC_PACKAGE)
                data = Uri.parse("file://$path")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to edit file", e)
            false
        }
    }

    /**
     * Send files via TC
     */
    private fun sendFiles(paths: List<String>): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList(paths.map { Uri.parse("file://$it") })
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Send files"))
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to send files", e)
            false
        }
    }

    // ========== Archive Operations ==========

    /**
     * Create archive
     */
    private fun createArchive(
        sources: List<String>,
        archivePath: String,
        options: FileOperationOptions
    ): Boolean {
        return try {
            val intent = Intent().apply {
                setPackage(TC_PACKAGE)
                action = "com.ghisler.android.TotalCommander.ACTION_PACK"
                putExtra("files", sources.toTypedArray())
                putExtra("archive", archivePath)
                putExtra("format", options.archiveFormat.extension)
                putExtra("level", options.compressionLevel)
                if (options.password != null) {
                    putExtra("password", options.password)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to create archive", e)
            false
        }
    }

    /**
     * Extract archive
     */
    private fun extractArchive(
        archivePath: String,
        destination: String,
        options: FileOperationOptions
    ): Boolean {
        return try {
            val intent = Intent().apply {
                setPackage(TC_PACKAGE)
                action = "com.ghisler.android.TotalCommander.ACTION_UNPACK"
                data = Uri.parse("file://$archivePath")
                putExtra("dest", destination)
                if (options.password != null) {
                    putExtra("password", options.password)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Failed to extract archive", e)
            false
        }
    }

    // ========== Directory Operations ==========

    /**
     * List directory contents
     */
    fun listDirectory(
        path: String,
        recursive: Boolean = false,
        includeHidden: Boolean = false
    ): List<FileInfo> {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            return emptyList()
        }

        return if (recursive) {
            directory.walkTopDown()
                .filter { includeHidden || !it.isHidden }
                .map { FileInfo.fromFile(it) }
                .toList()
        } else {
            directory.listFiles()
                ?.filter { includeHidden || !it.isHidden }
                ?.map { FileInfo.fromFile(it) }
                ?: emptyList()
        }
    }

    /**
     * Search for files
     */
    fun searchFiles(
        directory: String,
        pattern: String,
        recursive: Boolean = true,
        includeHidden: Boolean = false
    ): List<FileInfo> {
        val dir = File(directory)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex(RegexOption.IGNORE_CASE)

        return if (recursive) {
            dir.walkTopDown()
                .filter { file ->
                    (includeHidden || !file.isHidden) &&
                            regex.matches(file.name)
                }
                .map { FileInfo.fromFile(it) }
                .toList()
        } else {
            dir.listFiles()
                ?.filter { file ->
                    (includeHidden || !file.isHidden) &&
                            regex.matches(file.name)
                }
                ?.map { FileInfo.fromFile(it) }
                ?: emptyList()
        }
    }

    /**
     * Get file info
     */
    fun getFileInfo(path: String): FileInfo? {
        val file = File(path)
        return if (file.exists()) FileInfo.fromFile(file) else null
    }

    /**
     * Check if path exists
     */
    fun exists(path: String): Boolean = File(path).exists()

    /**
     * Get directory size
     */
    fun getDirectorySize(path: String): Long {
        val dir = File(path)
        return if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            dir.length()
        }
    }

    // ========== File Watching ==========

    /**
     * Watch directory for changes
     */
    fun watchDirectory(
        path: String,
        intervalMs: Long = 1000,
        recursive: Boolean = false
    ): Flow<FileChangeEvent> = flow {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Invalid directory: $path")
        }

        // Initial state
        val fileStates = mutableMapOf<String, Long>()

        fun scanDirectory(): Map<String, Long> {
            val files = if (recursive) {
                directory.walkTopDown().filter { it.isFile }
            } else {
                directory.listFiles()?.filter { it.isFile }?.asSequence() ?: emptySequence()
            }
            return files.associate { it.absolutePath to it.lastModified() }
        }

        // Initial scan
        fileStates.putAll(scanDirectory())

        while (true) {
            delay(intervalMs)

            val currentFiles = scanDirectory()

            // Check for new and modified files
            currentFiles.forEach { (path, modified) ->
                val previousModified = fileStates[path]
                when {
                    previousModified == null -> {
                        emit(FileChangeEvent(path, ChangeType.CREATED))
                    }
                    previousModified != modified -> {
                        emit(FileChangeEvent(path, ChangeType.MODIFIED))
                    }
                }
            }

            // Check for deleted files
            fileStates.keys
                .filter { it !in currentFiles }
                .forEach { path ->
                    emit(FileChangeEvent(path, ChangeType.DELETED))
                }

            // Update state
            fileStates.clear()
            fileStates.putAll(currentFiles)
        }
    }.flowOn(Dispatchers.IO)

    // ========== Synchronization ==========

    /**
     * Synchronize two folders
     */
    suspend fun syncFolders(
        source: String,
        destination: String,
        mode: SyncMode = SyncMode.MIRROR
    ): SyncReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var filesScanned = 0
        var filesCopied = 0
        var filesDeleted = 0
        var filesSkipped = 0
        val errors = mutableListOf<String>()

        try {
            val sourceDir = File(source)
            val destDir = File(destination)

            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                return@withContext SyncReport(
                    success = false,
                    filesScanned = 0,
                    filesCopied = 0,
                    filesDeleted = 0,
                    filesSkipped = 0,
                    errors = listOf("Source directory does not exist: $source"),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            destDir.mkdirs()

            // Get all files from source
            val sourceFiles = sourceDir.walkTopDown()
                .filter { it.isFile }
                .toList()

            filesScanned = sourceFiles.size

            // Copy/update files
            for (sourceFile in sourceFiles) {
                val relativePath = sourceFile.relativeTo(sourceDir).path
                val destFile = File(destDir, relativePath)

                try {
                    val shouldCopy = when (mode) {
                        SyncMode.MIRROR, SyncMode.BACKUP -> {
                            !destFile.exists() ||
                                    sourceFile.lastModified() > destFile.lastModified()
                        }
                        SyncMode.TWO_WAY -> {
                            !destFile.exists() ||
                                    sourceFile.lastModified() != destFile.lastModified()
                        }
                        SyncMode.UPDATE -> {
                            destFile.exists() &&
                                    sourceFile.lastModified() > destFile.lastModified()
                        }
                    }

                    if (shouldCopy) {
                        destFile.parentFile?.mkdirs()
                        sourceFile.copyTo(destFile, overwrite = true)
                        destFile.setLastModified(sourceFile.lastModified())
                        filesCopied++
                    } else {
                        filesSkipped++
                    }
                } catch (e: Exception) {
                    errors.add("Failed to copy ${sourceFile.path}: ${e.message}")
                }
            }

            // For mirror mode, delete files not in source
            if (mode == SyncMode.MIRROR) {
                val destFiles = destDir.walkTopDown()
                    .filter { it.isFile }
                    .toList()

                for (destFile in destFiles) {
                    val relativePath = destFile.relativeTo(destDir).path
                    val sourceFile = File(sourceDir, relativePath)

                    if (!sourceFile.exists()) {
                        try {
                            destFile.delete()
                            filesDeleted++
                        } catch (e: Exception) {
                            errors.add("Failed to delete ${destFile.path}: ${e.message}")
                        }
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("Sync error: ${e.message}")
        }

        SyncReport(
            success = errors.isEmpty(),
            filesScanned = filesScanned,
            filesCopied = filesCopied,
            filesDeleted = filesDeleted,
            filesSkipped = filesSkipped,
            errors = errors,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    // ========== Convenience Methods ==========

    /**
     * Get common directories
     */
    object Paths {
        val downloads: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath

        val documents: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        ).absolutePath

        val pictures: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        ).absolutePath

        val music: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        ).absolutePath

        val movies: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).absolutePath

        val dcim: String get() = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM
        ).absolutePath

        val external: String get() = Environment.getExternalStorageDirectory().absolutePath
    }

    /**
     * Quick copy
     */
    fun copy(source: String, destination: String): Boolean {
        return executeOperation(
            FileOperation(
                type = OperationType.COPY,
                sources = listOf(source),
                destination = destination
            )
        )
    }

    /**
     * Quick move
     */
    fun move(source: String, destination: String): Boolean {
        return executeOperation(
            FileOperation(
                type = OperationType.MOVE,
                sources = listOf(source),
                destination = destination
            )
        )
    }

    /**
     * Quick delete
     */
    fun delete(path: String): Boolean {
        return executeOperation(
            FileOperation(
                type = OperationType.DELETE,
                sources = listOf(path)
            )
        )
    }

    /**
     * Quick zip
     */
    fun zip(sources: List<String>, archivePath: String, password: String? = null): Boolean {
        return executeOperation(
            FileOperation(
                type = OperationType.ARCHIVE,
                sources = sources,
                destination = archivePath,
                options = FileOperationOptions(
                    archiveFormat = ArchiveFormat.ZIP,
                    password = password
                )
            )
        )
    }

    /**
     * Quick unzip
     */
    fun unzip(archivePath: String, destination: String, password: String? = null): Boolean {
        return executeOperation(
            FileOperation(
                type = OperationType.EXTRACT,
                sources = listOf(archivePath),
                destination = destination,
                options = FileOperationOptions(password = password)
            )
        )
    }

    /**
     * Find files by extension
     */
    fun findByExtension(directory: String, extension: String): List<FileInfo> {
        return searchFiles(directory, "*.$extension")
    }

    /**
     * Get all images
     */
    fun findImages(directory: String = Paths.pictures): List<FileInfo> {
        val extensions = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        return extensions.flatMap { findByExtension(directory, it) }
    }

    /**
     * Get all documents
     */
    fun findDocuments(directory: String = Paths.documents): List<FileInfo> {
        val extensions = listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")
        return extensions.flatMap { findByExtension(directory, it) }
    }

    /**
     * Get all videos
     */
    fun findVideos(directory: String = Paths.movies): List<FileInfo> {
        val extensions = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm")
        return extensions.flatMap { findByExtension(directory, it) }
    }

    /**
     * Get storage stats
     */
    fun getStorageStats(path: String = Paths.external): StorageStats {
        val file = File(path)
        return StorageStats(
            totalSpace = file.totalSpace,
            freeSpace = file.freeSpace,
            usedSpace = file.totalSpace - file.freeSpace
        )
    }

    data class StorageStats(
        val totalSpace: Long,
        val freeSpace: Long,
        val usedSpace: Long
    ) {
        val usedPercent: Float get() = (usedSpace.toFloat() / totalSpace) * 100

        fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
                bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
                bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
                else -> "$bytes B"
            }
        }

        override fun toString(): String = buildString {
            appendLine("Total: ${formatSize(totalSpace)}")
            appendLine("Used: ${formatSize(usedSpace)} (${usedPercent.toInt()}%)")
            appendLine("Free: ${formatSize(freeSpace)}")
        }
    }
}

package org.ninelym.layla.sharing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Sharing Service
 * 
 * Manages content sharing capabilities for conversations, images, and data export.
 * 
 * Features:
 * - Share text conversations
 * - Share generated images
 * - Export data in multiple formats
 * - Rich content sharing
 * - Share templates
 * - Direct social media integration
 * - Cloud storage integration (optional)
 * - Collaborative features
 * 
 * Phase 3: Integration
 */
class SharingService(
    /** Needed to launch Android share intents; without it, sharing fails rather than pretending. */
    private val context: android.content.Context? = null,
    /** Looks up real conversations for export. */
    private val conversations: ((String) -> org.ninelym.layla.chat.Conversation?)? = null,
    private val exportDir: File = File(System.getProperty("java.io.tmpdir"), "exports")
) {
    
    // State flows for reactive updates
    private val _sharingState = MutableStateFlow<SharingState>(SharingState.IDLE)
    val sharingState: StateFlow<SharingState> = _sharingState.asStateFlow()
    
    private val _shareHistory = MutableStateFlow<List<ShareRecord>>(emptyList())
    val shareHistory: StateFlow<List<ShareRecord>> = _shareHistory.asStateFlow()
    
    /**
     * Share text content
     */
    suspend fun shareText(
        text: String,
        title: String? = null,
        format: ShareFormat = ShareFormat.PLAIN_TEXT
    ): Result<ShareResult> {
        return try {
            _sharingState.value = SharingState.PREPARING
            
            val formattedContent = formatContent(text, format)
            
            val shareData = ShareData(
                content = formattedContent,
                type = ShareType.TEXT,
                format = format,
                title = title
            )
            
            val result = executeShare(shareData)
            
            // Record share in history
            recordShare(shareData, result)
            
            _sharingState.value = SharingState.IDLE
            Result.success(result)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Share an image file
     */
    suspend fun shareImage(
        imagePath: String,
        caption: String? = null,
        format: ShareFormat = ShareFormat.IMAGE
    ): Result<ShareResult> {
        return try {
            _sharingState.value = SharingState.PREPARING
            
            val file = File(imagePath)
            if (!file.exists()) {
                return Result.failure(Exception("Image file not found: $imagePath"))
            }
            
            val shareData = ShareData(
                content = imagePath,
                type = ShareType.IMAGE,
                format = format,
                title = caption,
                mimeType = "image/*"
            )
            
            val result = executeShare(shareData)
            
            // Record share in history
            recordShare(shareData, result)
            
            _sharingState.value = SharingState.IDLE
            Result.success(result)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Share multiple items (text and/or images)
     */
    suspend fun shareMultiple(
        items: List<ShareItem>,
        title: String? = null
    ): Result<ShareResult> {
        return try {
            _sharingState.value = SharingState.PREPARING
            
            val shareData = ShareData(
                content = items.joinToString("\n") { it.content },
                type = ShareType.MULTIPLE,
                format = ShareFormat.MIXED,
                title = title
            )
            
            val result = executeShare(shareData)
            
            // Record share in history
            recordShare(shareData, result)
            
            _sharingState.value = SharingState.IDLE
            Result.success(result)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Export conversation to file
     */
    suspend fun exportConversation(
        conversationId: String,
        format: ExportFormat = ExportFormat.JSON,
        includeMetadata: Boolean = true
    ): Result<File> {
        return try {
            _sharingState.value = SharingState.EXPORTING
            
            val lookup = conversations ?: throw IllegalStateException("No conversation source configured")
            val conversation = lookup(conversationId) ?: throw IllegalArgumentException("Conversation not found: $conversationId")
            val conversationData = formatConversation(conversation, format, includeMetadata)
            
            val exportFile = createExportFile(conversationId, format, conversationData)
            
            _sharingState.value = SharingState.IDLE
            Result.success(exportFile)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Export data to file
     */
    suspend fun exportData(
        data: Map<String, Any>,
        fileName: String,
        format: ExportFormat = ExportFormat.JSON
    ): Result<File> {
        return try {
            _sharingState.value = SharingState.EXPORTING
            
            val formattedData = formatDataForExport(data, format)
            val exportFile = createExportFile(fileName, format, formattedData)
            
            _sharingState.value = SharingState.IDLE
            Result.success(exportFile)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Share to specific platform
     */
    suspend fun shareToplatform(
        content: String,
        platform: SocialPlatform,
        options: ShareOptions = ShareOptions()
    ): Result<ShareResult> {
        return try {
            _sharingState.value = SharingState.SHARING
            
            val shareData = ShareData(
                content = content,
                type = ShareType.TEXT,
                format = ShareFormat.PLAIN_TEXT,
                platform = platform,
                title = options.title
            )
            
            val result = shareToPlatform(shareData, platform, options)
            
            // Record share in history
            recordShare(shareData, result)
            
            _sharingState.value = SharingState.IDLE
            Result.success(result)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Share to cloud storage
     */
    suspend fun shareToCloud(
        filePath: String,
        cloudProvider: CloudProvider,
        destinationPath: String? = null
    ): Result<ShareResult> {
        return try {
            _sharingState.value = SharingState.UPLOADING
            
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }
            
            val result = uploadToCloud(file, cloudProvider, destinationPath)
            
            _sharingState.value = SharingState.IDLE
            Result.success(result)
        } catch (e: Exception) {
            _sharingState.value = SharingState.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Create a share template
     */
    fun createTemplate(
        name: String,
        template: String,
        variables: List<String> = emptyList()
    ): ShareTemplate {
        return ShareTemplate(
            id = "template_${System.currentTimeMillis()}",
            name = name,
            template = template,
            variables = variables,
            createdAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Apply template to content
     */
    fun applyTemplate(template: ShareTemplate, values: Map<String, String>): String {
        var result = template.template
        values.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }
    
    /**
     * Get share history
     */
    fun getShareHistory(limit: Int = 50): List<ShareRecord> {
        return _shareHistory.value.take(limit)
    }
    
    /**
     * Clear share history
     */
    fun clearShareHistory(): Result<Unit> {
        return try {
            _shareHistory.value = emptyList()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Private helper methods
    
    private fun formatContent(content: String, format: ShareFormat): String {
        return when (format) {
            ShareFormat.PLAIN_TEXT -> content
            ShareFormat.MARKDOWN -> content // Already markdown
            ShareFormat.HTML -> convertToHtml(content)
            ShareFormat.JSON -> """{"content": "$content"}"""
            else -> content
        }
    }
    
    private fun convertToHtml(markdown: String): String {
        // Placeholder: Simple markdown to HTML conversion
        return "<html><body><p>${markdown.replace("\n", "</p><p>")}</p></body></html>"
    }
    
    private fun formatConversation(
        conversation: org.ninelym.layla.chat.Conversation,
        format: ExportFormat,
        includeMetadata: Boolean
    ): String {
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val messages = conversation.messages
        return when (format) {
            ExportFormat.JSON -> buildString {
                append("{\n  \"conversationId\": \"${esc(conversation.id)}\",\n  \"title\": \"${esc(conversation.title)}\",\n  \"messages\": [\n")
                append(messages.joinToString(",\n") {
                    "    {\"role\": \"${it.role.name.lowercase()}\", \"content\": \"${esc(it.content)}\", \"timestamp\": ${it.timestamp}}"
                })
                append("\n  ]")
                if (includeMetadata) append(",\n  \"metadata\": {\"exportedAt\": ${System.currentTimeMillis()}, \"messageCount\": ${messages.size}}")
                append("\n}")
            }
            ExportFormat.MARKDOWN -> "# ${conversation.title}\n\n" +
                messages.joinToString("\n\n") { "**${it.role.name.lowercase()}:** ${it.content}" }
            ExportFormat.CSV -> "role,timestamp,content\n" +
                messages.joinToString("\n") { "${it.role.name.lowercase()},${it.timestamp},\"${it.content.replace("\"", "\"\"")}\"" }
            ExportFormat.HTML -> "<html><body><h1>${conversation.title}</h1>" +
                messages.joinToString("") { "<p><b>${it.role.name.lowercase()}:</b> ${it.content}</p>" } + "</body></html>"
            ExportFormat.TXT -> messages.joinToString("\n\n") { "${it.role.name.lowercase()}: ${it.content}" }
            ExportFormat.PDF -> throw UnsupportedOperationException("PDF export isn't supported; use MARKDOWN or HTML")
        }
    }
    
    private fun shareIntent(shareData: ShareData, targetPackage: String? = null): android.content.Intent {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        if (shareData.type == ShareType.IMAGE) {
            val ctx = context!!
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(shareData.content))
            intent.type = shareData.mimeType ?: "image/*"
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareData.title?.let { intent.putExtra(android.content.Intent.EXTRA_TEXT, it) }
        } else {
            intent.type = "text/plain"
            intent.putExtra(android.content.Intent.EXTRA_TEXT, shareData.content)
            shareData.title?.let { intent.putExtra(android.content.Intent.EXTRA_SUBJECT, it) }
        }
        targetPackage?.let { intent.setPackage(it) }
        return intent
    }
    
    /**
     * Opens Android's share sheet. The result records that the sheet was shown; whether the
     * user completed the share is up to them.
     */
    private fun executeShare(shareData: ShareData): ShareResult {
        val ctx = context ?: throw IllegalStateException("Sharing needs an Android Context")
        val chooser = android.content.Intent.createChooser(shareIntent(shareData), shareData.title)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
        return ShareResult(
            success = true,
            sharedTo = "system share sheet",
            timestamp = System.currentTimeMillis(),
            message = "Share sheet opened"
        )
    }
    
    private fun shareToPlatform(
        shareData: ShareData,
        platform: SocialPlatform,
        options: ShareOptions
    ): ShareResult {
        val ctx = context ?: throw IllegalStateException("Sharing needs an Android Context")
        val pkg = when (platform) {
            SocialPlatform.TWITTER -> "com.twitter.android"
            SocialPlatform.FACEBOOK -> "com.facebook.katana"
            SocialPlatform.LINKEDIN -> "com.linkedin.android"
            SocialPlatform.WHATSAPP -> "com.whatsapp"
            SocialPlatform.TELEGRAM -> "org.telegram.messenger"
            SocialPlatform.EMAIL -> null
        }
        val intent = if (platform == SocialPlatform.EMAIL) {
            android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
                .putExtra(android.content.Intent.EXTRA_TEXT, shareData.content)
                .putExtra(android.content.Intent.EXTRA_SUBJECT, shareData.title)
        } else shareIntent(shareData, pkg)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(ctx.packageManager) == null) {
            throw IllegalStateException("${platform.name} is not installed")
        }
        ctx.startActivity(intent)
        return ShareResult(
            success = true,
            sharedTo = platform.name,
            timestamp = System.currentTimeMillis(),
            message = "Opened ${platform.name} to share"
        )
    }
    
    private fun uploadToCloud(
        file: File,
        cloudProvider: CloudProvider,
        destinationPath: String?
    ): ShareResult {
        // No cloud SDK is bundled, so nothing can be uploaded
        throw UnsupportedOperationException("Upload to ${cloudProvider.name} isn't available: no ${cloudProvider.name} client is configured")
    }
    
    private fun createExportFile(
        name: String,
        format: ExportFormat,
        content: String
    ): File {
        val extension = when (format) {
            ExportFormat.JSON -> "json"
            ExportFormat.CSV -> "csv"
            ExportFormat.TXT -> "txt"
            ExportFormat.MARKDOWN -> "md"
            ExportFormat.HTML -> "html"
            ExportFormat.PDF -> "pdf"
        }
        
        val fileName = "${name}_${System.currentTimeMillis()}.$extension"
        exportDir.mkdirs()
        
        val file = File(exportDir, fileName)
        file.writeText(content)
        
        return file
    }
    
    private fun formatDataForExport(data: Map<String, Any>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> formatAsJson(data)
            ExportFormat.CSV -> formatAsCsv(data)
            ExportFormat.TXT -> formatAsText(data)
            ExportFormat.MARKDOWN -> formatAsMarkdown(data)
            ExportFormat.HTML -> formatAsHtml(data)
            ExportFormat.PDF -> formatAsJson(data) // Placeholder
        }
    }
    
    private fun formatAsJson(data: Map<String, Any>): String {
        return data.entries.joinToString(",\n", "{\n", "\n}") { (key, value) ->
            "  \"$key\": \"$value\""
        }
    }
    
    private fun formatAsCsv(data: Map<String, Any>): String {
        val headers = data.keys.joinToString(",")
        val values = data.values.joinToString(",")
        return "$headers\n$values"
    }
    
    private fun formatAsText(data: Map<String, Any>): String {
        return data.entries.joinToString("\n") { (key, value) ->
            "$key: $value"
        }
    }
    
    private fun formatAsMarkdown(data: Map<String, Any>): String {
        return data.entries.joinToString("\n") { (key, value) ->
            "**$key**: $value"
        }
    }
    
    private fun formatAsHtml(data: Map<String, Any>): String {
        val rows = data.entries.joinToString("\n") { (key, value) ->
            "<tr><td>$key</td><td>$value</td></tr>"
        }
        return "<html><body><table>$rows</table></body></html>"
    }
    
    private fun recordShare(shareData: ShareData, result: ShareResult) {
        val record = ShareRecord(
            id = "share_${System.currentTimeMillis()}",
            type = shareData.type,
            format = shareData.format,
            platform = shareData.platform,
            success = result.success,
            timestamp = result.timestamp
        )
        _shareHistory.value = listOf(record) + _shareHistory.value
    }
}

// Data classes

data class ShareData(
    val content: String,
    val type: ShareType,
    val format: ShareFormat,
    val title: String? = null,
    val mimeType: String? = null,
    val platform: SocialPlatform? = null
)

data class ShareItem(
    val content: String,
    val type: ShareType,
    val mimeType: String? = null
)

data class ShareResult(
    val success: Boolean,
    val sharedTo: String,
    val timestamp: Long,
    val message: String,
    val url: String? = null
)

data class ShareRecord(
    val id: String,
    val type: ShareType,
    val format: ShareFormat,
    val platform: SocialPlatform?,
    val success: Boolean,
    val timestamp: Long
)

data class ShareTemplate(
    val id: String,
    val name: String,
    val template: String,
    val variables: List<String>,
    val createdAt: Long
)

data class ShareOptions(
    val title: String? = null,
    val hashtags: List<String> = emptyList(),
    val mentions: List<String> = emptyList()
)

enum class ShareType {
    TEXT,
    IMAGE,
    FILE,
    MULTIPLE
}

enum class ShareFormat {
    PLAIN_TEXT,
    MARKDOWN,
    HTML,
    JSON,
    IMAGE,
    MIXED
}

enum class ExportFormat {
    JSON,
    CSV,
    TXT,
    MARKDOWN,
    HTML,
    PDF
}

enum class SocialPlatform {
    TWITTER,
    FACEBOOK,
    LINKEDIN,
    WHATSAPP,
    TELEGRAM,
    EMAIL
}

enum class CloudProvider {
    GOOGLE_DRIVE,
    DROPBOX,
    ONEDRIVE,
    S3
}

enum class SharingState {
    IDLE,
    PREPARING,
    SHARING,
    UPLOADING,
    EXPORTING,
    ERROR
}

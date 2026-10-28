package org.ninelym.layla.document

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import org.ninelym.llm.ask
import org.ninelym.llm.askJson
import java.io.File
import java.util.zip.ZipFile

/**
 * Document Processing Service
 * 
 * Provides comprehensive document processing capabilities including PDF reading,
 * text extraction, annotation, summarization, and AI-powered document analysis.
 * 
 * Features:
 * - PDF reading and annotation
 * - Text extraction from multiple document formats
 * - Document summarization using AI
 * - Document Q&A capabilities
 * - Multi-document analysis
 * - Automatic categorization
 * 
 * Phase 3: Integration
 */
class DocumentProcessingService(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() },
    /** Vision-capable endpoint used to read scanned/PDF pages. */
    private val visionProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() }
) {
    
    // State flows for reactive updates
    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents.asStateFlow()
    
    private val _activeDocument = MutableStateFlow<Document?>(null)
    val activeDocument: StateFlow<Document?> = _activeDocument.asStateFlow()
    
    private val _processingStatus = MutableStateFlow<ProcessingStatus>(ProcessingStatus.IDLE)
    val processingStatus: StateFlow<ProcessingStatus> = _processingStatus.asStateFlow()
    
    /**
     * Load a document from file path
     */
    suspend fun loadDocument(filePath: String): Result<Document> {
        return try {
            _processingStatus.value = ProcessingStatus.LOADING
            
            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }
            
            val document = Document(
                id = generateDocumentId(),
                filePath = filePath,
                fileName = file.name,
                fileSize = file.length(),
                mimeType = detectMimeType(file),
                pageCount = 0, // Will be populated after parsing
                createdAt = System.currentTimeMillis(),
                lastModified = file.lastModified()
            )
            
            // Add to document list
            _documents.value = _documents.value + document
            _activeDocument.value = document
            _processingStatus.value = ProcessingStatus.IDLE
            
            Result.success(document)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Extract text from a document
     */
    suspend fun extractText(documentId: String): Result<String> {
        return try {
            _processingStatus.value = ProcessingStatus.EXTRACTING
            
            val document = _documents.value.find { it.id == documentId }
                ?: return Result.failure(Exception("Document not found: $documentId"))
            
            val extractedText = when (document.mimeType) {
                "application/pdf" -> extractTextFromPdf(document.filePath)
                "text/plain" -> extractTextFromPlainText(document.filePath)
                "application/msword", 
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> 
                    extractTextFromWord(document.filePath)
                else -> return Result.failure(Exception("Unsupported document type: ${document.mimeType}"))
            }
            
            // Update document with extracted text
            updateDocument(documentId) { it.copy(extractedText = extractedText) }
            
            _processingStatus.value = ProcessingStatus.IDLE
            Result.success(extractedText)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Summarize a document using AI
     */
    suspend fun summarizeDocument(
        documentId: String,
        maxLength: Int = 500,
        useAI: Boolean = true
    ): Result<DocumentSummary> {
        return try {
            _processingStatus.value = ProcessingStatus.SUMMARIZING
            
            val document = _documents.value.find { it.id == documentId }
                ?: return Result.failure(Exception("Document not found: $documentId"))
            
            // Extract text if not already extracted
            val text = document.extractedText ?: extractText(documentId).getOrThrow()
            
            val summary = if (useAI) {
                generateAISummary(text, maxLength)
            } else {
                generateExtractiveSummary(text, maxLength)
            }
            
            val documentSummary = DocumentSummary(
                documentId = documentId,
                summary = summary,
                keyPoints = if (useAI) extractKeyPoints(text) else extractiveKeyPoints(text),
                wordCount = text.split("\\s+".toRegex()).size,
                generatedAt = System.currentTimeMillis()
            )
            
            _processingStatus.value = ProcessingStatus.IDLE
            Result.success(documentSummary)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Ask questions about a document using AI
     */
    suspend fun askQuestion(
        documentId: String,
        question: String,
        contextWindow: Int = 2000
    ): Result<DocumentAnswer> {
        return try {
            _processingStatus.value = ProcessingStatus.QUERYING
            
            val document = _documents.value.find { it.id == documentId }
                ?: return Result.failure(Exception("Document not found: $documentId"))
            
            // Extract text if not already extracted
            val text = document.extractedText ?: extractText(documentId).getOrThrow()
            
            // Find relevant context using semantic search
            val relevantContext = findRelevantContext(text, question, contextWindow)
            
            // Generate answer using AI
            val answer = generateAnswer(question, relevantContext)
            
            val documentAnswer = DocumentAnswer(
                documentId = documentId,
                question = question,
                answer = answer,
                confidence = null,
                relevantPassages = listOf(relevantContext),
                timestamp = System.currentTimeMillis()
            )
            
            _processingStatus.value = ProcessingStatus.IDLE
            Result.success(documentAnswer)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Analyze multiple documents together
     */
    suspend fun analyzeMultipleDocuments(
        documentIds: List<String>,
        analysisType: AnalysisType = AnalysisType.COMPARISON
    ): Result<MultiDocumentAnalysis> {
        return try {
            _processingStatus.value = ProcessingStatus.ANALYZING
            
            val documents = documentIds.mapNotNull { id ->
                _documents.value.find { it.id == id }
            }
            
            if (documents.size != documentIds.size) {
                return Result.failure(Exception("Some documents not found"))
            }
            
            // Extract text from all documents
            val documentTexts = documents.map { doc ->
                doc.extractedText ?: extractText(doc.id).getOrThrow()
            }
            
            val analysis = when (analysisType) {
                AnalysisType.COMPARISON -> compareDocuments(documents, documentTexts)
                AnalysisType.SYNTHESIS -> synthesizeDocuments(documents, documentTexts)
                AnalysisType.COMMONALITY -> findCommonalities(documents, documentTexts)
            }
            
            _processingStatus.value = ProcessingStatus.IDLE
            Result.success(analysis)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Automatically categorize a document
     */
    suspend fun categorizeDocument(documentId: String): Result<DocumentCategory> {
        return try {
            _processingStatus.value = ProcessingStatus.CATEGORIZING
            
            val document = _documents.value.find { it.id == documentId }
                ?: return Result.failure(Exception("Document not found: $documentId"))
            
            // Extract text if not already extracted
            val text = document.extractedText ?: extractText(documentId).getOrThrow()
            
            // The model reads the content and categorizes it
            val documentCategory = classifyDocument(documentId, text, document.fileName)
            
            // Update document with category
            updateDocument(documentId) { it.copy(category = documentCategory) }
            
            _processingStatus.value = ProcessingStatus.IDLE
            Result.success(documentCategory)
        } catch (e: Exception) {
            _processingStatus.value = ProcessingStatus.ERROR
            Result.failure(e)
        }
    }
    
    /**
     * Add annotation to a document
     */
    fun addAnnotation(
        documentId: String,
        annotation: Annotation
    ): Result<Unit> {
        return try {
            val document = _documents.value.find { it.id == documentId }
                ?: return Result.failure(Exception("Document not found: $documentId"))
            
            val updatedAnnotations = document.annotations + annotation
            updateDocument(documentId) { it.copy(annotations = updatedAnnotations) }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all annotations for a document
     */
    fun getAnnotations(documentId: String): List<Annotation> {
        return _documents.value.find { it.id == documentId }?.annotations ?: emptyList()
    }
    
    /**
     * Delete a document
     */
    fun deleteDocument(documentId: String): Result<Unit> {
        return try {
            _documents.value = _documents.value.filter { it.id != documentId }
            if (_activeDocument.value?.id == documentId) {
                _activeDocument.value = null
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get document by ID
     */
    fun getDocument(documentId: String): Document? {
        return _documents.value.find { it.id == documentId }
    }
    
    // Private helper methods
    
    private fun generateDocumentId(): String {
        return "doc_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    private fun detectMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * PDF pages are rendered and read by the vision model (works for scanned documents too).
     */
    private suspend fun extractTextFromPdf(filePath: String): String {
        val vision = visionProvider().getOrThrow()
        val descriptor = android.os.ParcelFileDescriptor.open(File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val pages = android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
            }
        }
        val text = StringBuilder()
        for (png in pages) {
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(vision.describeImage(png, "Transcribe all text on this page exactly, preserving reading order. Output only the text.",
                options = GenerationOptions(temperature = 0f, maxTokens = 2048)).getOrThrow())
        }
        return text.toString()
    }
    
    private fun extractTextFromPlainText(filePath: String): String {
        return File(filePath).readText()
    }
    
    /**
     * DOCX is a zip of WordprocessingML; paragraphs are `<w:p>` and text runs are `<w:t>`.
     */
    private fun extractTextFromWord(filePath: String): String {
        if (filePath.endsWith(".doc", ignoreCase = true)) {
            throw UnsupportedOperationException("Legacy binary .doc files aren't supported; save the document as .docx")
        }
        ZipFile(filePath).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: throw IllegalArgumentException("Not a DOCX file: $filePath")
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            return xml.split("</w:p>").map { paragraph ->
                Regex("<w:t[^>]*>([^<]*)</w:t>").findAll(paragraph).joinToString("") { it.groupValues[1] }
            }.filter { it.isNotBlank() }.joinToString("\n")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        }
    }
    
    /** Keeps prompts within a small model's context window. */
    private fun excerpt(text: String, limit: Int = 12_000) = if (text.length <= limit) text else text.take(limit) + "\n[...truncated]"
    
    private suspend fun generateAISummary(text: String, maxLength: Int): String =
        modelProvider().getOrThrow().ask(
            "Summarize the document in at most $maxLength characters. Output only the summary.",
            excerpt(text)
        ).getOrThrow()
    
    private fun generateExtractiveSummary(text: String, maxLength: Int): String {
        // Simple extractive summary: take first sentences up to maxLength
        val sentences = text.split(". ")
        var summary = ""
        for (sentence in sentences) {
            if (summary.length + sentence.length > maxLength) break
            summary += "$sentence. "
        }
        return summary.trim()
    }
    
    private suspend fun extractKeyPoints(text: String): List<String> =
        modelProvider().getOrThrow().ask(
            "List the 3-7 key points of the document, one per line, without numbering or bullets.",
            excerpt(text)
        ).getOrThrow().lines().map { it.trim().trimStart('-', '*', '•', ' ') }.filter { it.isNotBlank() }
    
    private fun extractiveKeyPoints(text: String): List<String> = text.split(". ").take(5).map { it.trim() }
    
    /**
     * Picks the passages that share the most terms with the question, up to [contextWindow] chars.
     */
    private fun findRelevantContext(text: String, question: String, contextWindow: Int): String {
        val terms = question.lowercase().split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val passages = text.split(Regex("\\n\\s*\\n|(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val ranked = passages.withIndex().sortedByDescending { (_, p) ->
            val words = p.lowercase().split(Regex("\\W+")).toSet()
            terms.count { it in words }
        }
        val chosen = mutableListOf<IndexedValue<String>>()
        var size = 0
        for (p in ranked) {
            if (size + p.value.length > contextWindow) break
            chosen += p
            size += p.value.length + 1
        }
        return chosen.sortedBy { it.index }.joinToString("\n") { it.value }.ifBlank { text.take(contextWindow) }
    }
    
    private suspend fun generateAnswer(question: String, context: String): String =
        modelProvider().getOrThrow().ask(
            "Answer the question using only the document excerpts. If they don't contain the answer, say so.",
            "Document excerpts:\n$context\n\nQuestion: $question"
        ).getOrThrow()
    
    private suspend fun analyzeWithModel(
        documents: List<Document>,
        texts: List<String>,
        type: AnalysisType,
        instruction: String
    ): MultiDocumentAnalysis {
        val perDocument = 12_000 / documents.size.coerceAtLeast(1)
        val corpus = documents.zip(texts).joinToString("\n\n") { (doc, text) ->
            "=== ${doc.fileName} ===\n${excerpt(text, perDocument)}"
        }
        val json = modelProvider().getOrThrow().askJson(
            "$instruction Respond as JSON: {\"findings\": string, \"insights\": [string]}.",
            corpus
        ).getOrThrow().jsonObject
        return MultiDocumentAnalysis(
            documentIds = documents.map { it.id },
            analysisType = type,
            findings = json["findings"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Model analysis had no findings"),
            insights = json.stringList("insights"),
            timestamp = System.currentTimeMillis()
        )
    }
    
    private suspend fun compareDocuments(documents: List<Document>, texts: List<String>) =
        analyzeWithModel(documents, texts, AnalysisType.COMPARISON, "Compare these documents: key differences and similarities.")
    
    private suspend fun synthesizeDocuments(documents: List<Document>, texts: List<String>) =
        analyzeWithModel(documents, texts, AnalysisType.SYNTHESIS, "Synthesize these documents into one unified perspective.")
    
    private suspend fun findCommonalities(documents: List<Document>, texts: List<String>) =
        analyzeWithModel(documents, texts, AnalysisType.COMMONALITY, "Identify the themes, patterns and concepts these documents share.")
    
    private suspend fun classifyDocument(documentId: String, text: String, fileName: String): DocumentCategory {
        val json = modelProvider().getOrThrow().askJson(
            "Categorize the document. Respond as JSON: {\"category\": string, \"subcategories\": [string], \"tags\": [string]}. " +
                "Use a broad category such as Technical, Business, Legal, Academic, Personal or General.",
            "File name: $fileName\n\n${excerpt(text, 6000)}"
        ).getOrThrow().jsonObject
        return DocumentCategory(
            documentId = documentId,
            primaryCategory = json["category"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Model did not return a category"),
            subcategories = json.stringList("subcategories"),
            tags = json.stringList("tags"),
            confidence = null
        )
    }
    
    private fun Map<String, JsonElement>.stringList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    
    private fun updateDocument(documentId: String, transform: (Document) -> Document) {
        _documents.value = _documents.value.map { doc ->
            if (doc.id == documentId) transform(doc) else doc
        }
        if (_activeDocument.value?.id == documentId) {
            _activeDocument.value = _documents.value.find { it.id == documentId }
        }
    }
}

// Data classes

data class Document(
    val id: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val pageCount: Int,
    val createdAt: Long,
    val lastModified: Long,
    val extractedText: String? = null,
    val annotations: List<Annotation> = emptyList(),
    val category: DocumentCategory? = null
)

data class Annotation(
    val id: String = "ann_${System.currentTimeMillis()}",
    val page: Int,
    val text: String,
    val type: AnnotationType,
    val position: AnnotationPosition,
    val createdAt: Long = System.currentTimeMillis()
)

data class AnnotationPosition(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

enum class AnnotationType {
    HIGHLIGHT,
    NOTE,
    BOOKMARK,
    UNDERLINE,
    STRIKETHROUGH
}

data class DocumentSummary(
    val documentId: String,
    val summary: String,
    val keyPoints: List<String>,
    val wordCount: Int,
    val generatedAt: Long
)

data class DocumentAnswer(
    val documentId: String,
    val question: String,
    val answer: String,
    /** Null: the model doesn't report a calibrated confidence. */
    val confidence: Float?,
    val relevantPassages: List<String>,
    val timestamp: Long
)

data class MultiDocumentAnalysis(
    val documentIds: List<String>,
    val analysisType: AnalysisType,
    val findings: String,
    val insights: List<String>,
    val timestamp: Long
)

data class DocumentCategory(
    val documentId: String,
    val primaryCategory: String,
    val subcategories: List<String>,
    val tags: List<String>,
    val confidence: Float?
)

enum class AnalysisType {
    COMPARISON,
    SYNTHESIS,
    COMMONALITY
}

enum class ProcessingStatus {
    IDLE,
    LOADING,
    EXTRACTING,
    SUMMARIZING,
    QUERYING,
    ANALYZING,
    CATEGORIZING,
    ERROR
}

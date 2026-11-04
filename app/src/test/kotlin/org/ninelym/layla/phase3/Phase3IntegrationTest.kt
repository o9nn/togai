package org.ninelym.layla.phase3

import org.junit.Test
import org.junit.Before
import org.junit.After
import kotlinx.coroutines.runBlocking
import org.ninelym.layla.document.DocumentProcessingService
import org.ninelym.layla.document.AnalysisType
import org.ninelym.layla.billing.BillingService
import org.ninelym.layla.sharing.SharingService
import org.ninelym.layla.sharing.ShareFormat
import org.ninelym.layla.sharing.ExportFormat
import org.ninelym.layla.tasker.AdvancedTaskerWorkflowService
import org.ninelym.layla.tasker.WorkflowStep
import org.ninelym.layla.tasker.WorkflowAction
import org.ninelym.layla.cloud.CloudSyncService
import org.ninelym.layla.cloud.SyncDataType
import org.junit.Assume.assumeTrue
import org.ninelym.layla.chat.ChatService
import org.ninelym.layla.inference.LaylaInferenceService
import org.ninelym.layla.notification.NotificationService
import org.ninelym.llm.ModelEndpointConfig
import org.ninelym.llm.OpenAICompatibleModel
import java.io.File
import org.ninelym.testing.KeyValueHttpServer

/**
 * Phase 3 Integration Tests
 * 
 * Model-dependent tests use the served model (TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL) and are
 * skipped without one. Cloud sync runs against a real in-process HTTP key/value store.
 */
class Phase3IntegrationTest {
    
    private lateinit var documentService: DocumentProcessingService
    private lateinit var billingService: BillingService
    private lateinit var sharingService: SharingService
    private lateinit var workflowService: AdvancedTaskerWorkflowService
    private lateinit var cloudSyncService: CloudSyncService
    private lateinit var notifications: NotificationService
    private lateinit var syncServer: KeyValueHttpServer
    private val config = ModelEndpointConfig.fromEnvironment()
    
    private fun requireModel() = assumeTrue("no live model configured", config != null)
    
    @Before
    fun setup() {
        syncServer = KeyValueHttpServer()
        notifications = NotificationService()
        documentService = DocumentProcessingService()
        billingService = BillingService()
        sharingService = SharingService()
        workflowService = AdvancedTaskerWorkflowService(
            notifier = { message -> notifications.showSimpleNotification("Workflow", message) }
        )
        cloudSyncService = CloudSyncService(serverUrl = syncServer.url)
    }
    
    @After
    fun teardown() {
        billingService.disconnect()
        cloudSyncService.disconnect()
        syncServer.close()
    }
    
    // ===== Document Processing Service Tests =====
    
    @Test
    fun testDocumentLoading(): Unit = runBlocking {
        // Create a temporary test file
        val testFile = File.createTempFile("test_doc", ".txt")
        testFile.writeText("This is a test document for processing.")
        
        val result = documentService.loadDocument(testFile.absolutePath)
        
        assert(result.isSuccess) { "Document loading should succeed" }
        val document = result.getOrThrow()
        assert(document.fileName == testFile.name) { "Document name should match" }
        assert(document.fileSize > 0) { "Document should have size" }
        
        testFile.delete()
    }
    
    @Test
    fun testTextExtraction(): Unit = runBlocking {
        val testFile = File.createTempFile("test_extract", ".txt")
        val testContent = "Sample text for extraction test."
        testFile.writeText(testContent)
        
        val loadResult = documentService.loadDocument(testFile.absolutePath)
        val document = loadResult.getOrThrow()
        
        val extractResult = documentService.extractText(document.id)
        
        assert(extractResult.isSuccess) { "Text extraction should succeed" }
        val extractedText = extractResult.getOrThrow()
        assert(extractedText.isNotEmpty()) { "Extracted text should not be empty" }
        
        testFile.delete()
    }
    
    @Test
    fun testDocumentSummarization(): Unit = runBlocking {
        requireModel()
        val testFile = File.createTempFile("test_summary", ".txt")
        testFile.writeText("This is a long document that needs to be summarized. " +
                "It contains multiple sentences. Each sentence adds more information.")
        
        val document = documentService.loadDocument(testFile.absolutePath).getOrThrow()
        val result = documentService.summarizeDocument(document.id)
        
        assert(result.isSuccess) { "Summarization should succeed" }
        val summary = result.getOrThrow()
        assert(summary.summary.isNotEmpty()) { "Summary should not be empty" }
        assert(summary.keyPoints.isNotEmpty()) { "Key points should be extracted" }
        assert(summary.wordCount > 0) { "Word count should be positive" }
        
        testFile.delete()
    }
    
    @Test
    fun testDocumentQA(): Unit = runBlocking {
        requireModel()
        val testFile = File.createTempFile("test_qa", ".txt")
        testFile.writeText("The capital of France is Paris. It is known for the Eiffel Tower.")
        
        val document = documentService.loadDocument(testFile.absolutePath).getOrThrow()
        val result = documentService.askQuestion(document.id, "What is the capital of France?")
        
        assert(result.isSuccess) { "Q&A should succeed" }
        val answer = result.getOrThrow()
        assert(answer.answer.isNotEmpty()) { "Answer should not be empty" }
        assert(answer.answer.contains("Paris", ignoreCase = true)) { "The model should answer from the document" }
        
        testFile.delete()
    }
    
    @Test
    fun testMultiDocumentAnalysis(): Unit = runBlocking {
        requireModel()
        val doc1 = File.createTempFile("doc1", ".txt")
        val doc2 = File.createTempFile("doc2", ".txt")
        
        doc1.writeText("Document 1 content about AI and machine learning.")
        doc2.writeText("Document 2 content about machine learning and neural networks.")
        
        val document1 = documentService.loadDocument(doc1.absolutePath).getOrThrow()
        val document2 = documentService.loadDocument(doc2.absolutePath).getOrThrow()
        
        val result = documentService.analyzeMultipleDocuments(
            listOf(document1.id, document2.id),
            AnalysisType.COMPARISON
        )
        
        assert(result.isSuccess) { "Multi-document analysis should succeed" }
        val analysis = result.getOrThrow()
        assert(analysis.findings.isNotEmpty()) { "Analysis findings should not be empty" }
        assert(analysis.insights.isNotEmpty()) { "Analysis insights should be provided" }
        
        doc1.delete()
        doc2.delete()
    }
    
    @Test
    fun testDocumentCategorization(): Unit = runBlocking {
        requireModel()
        val testFile = File.createTempFile("test_category", ".txt")
        testFile.writeText("This is a technical document about software development and API design.")
        
        val document = documentService.loadDocument(testFile.absolutePath).getOrThrow()
        val result = documentService.categorizeDocument(document.id)
        
        assert(result.isSuccess) { "Categorization should succeed" }
        val category = result.getOrThrow()
        assert(category.primaryCategory.isNotEmpty()) { "Primary category should be assigned" }
        assert(category.tags.isNotEmpty() || category.subcategories.isNotEmpty()) { "The model should tag the document" }
        
        testFile.delete()
    }
    
    // ===== Billing Service Tests =====
    
    @Test
    fun testBillingNeverFakesPurchases(): Unit = runBlocking {
        // No Play Billing client is bundled: nothing may report a purchase, license or product
        assert(billingService.initialize().isFailure) { "Billing must not connect without Play Billing" }
        assert(billingService.products.value.isEmpty()) { "No invented products" }
        assert(billingService.purchase("premium_monthly").isFailure) { "No purchase without Play Billing" }
        assert(billingService.verifyLicense().isFailure) { "No unverified license reported as valid" }
        assert(billingService.applyPromoCode("SAVE20").isFailure) { "No invented promo offer" }
        assert(!billingService.hasPremiumSubscription()) { "No subscription exists" }
    }
    
    // ===== Sharing Service Tests =====
    
    @Test
    fun testTextSharingNeedsAndroidContext(): Unit = runBlocking {
        // Without an Android Context no share sheet can open, so sharing must not report success
        val result = sharingService.shareText(text = "Test content to share", title = "Test Share", format = ShareFormat.PLAIN_TEXT)
        assert(result.isFailure) { "Sharing without a Context must fail" }
    }
    
    @Test
    fun testConversationExportContainsTheModelsReply(): Unit = runBlocking {
        requireModel()
        val inference = LaylaInferenceService({ Result.success(OpenAICompatibleModel(config!!)) })
        val chat = ChatService(inference)
        val conversationId = chat.createConversation("Export test")
        chat.sendMessage(conversationId, "Name one planet.").getOrThrow()
        val reply = chat.getConversation(conversationId)!!.messages.last()
        assert(reply.role == org.ninelym.layla.chat.MessageRole.ASSISTANT && reply.content.isNotBlank()) { "Model should reply" }
        
        val exporter = SharingService(conversations = { chat.getConversation(it) })
        val file = exporter.exportConversation(conversationId, ExportFormat.JSON).getOrThrow()
        val json = file.readText()
        assert(file.extension == "json") { "Export should be JSON format" }
        assert(json.contains("Name one planet.")) { "Export should contain the user's message" }
        val messages = kotlinx.serialization.json.Json.parseToJsonElement(json)
            .let { it as kotlinx.serialization.json.JsonObject }["messages"] as kotlinx.serialization.json.JsonArray
        val assistant = messages.map { it as kotlinx.serialization.json.JsonObject }
            .single { (it["role"] as kotlinx.serialization.json.JsonPrimitive).content == "assistant" }
        assert((assistant["content"] as kotlinx.serialization.json.JsonPrimitive).content == reply.content) {
            "Export should contain the model's actual reply"
        }
        file.delete()
    }
    
    @Test
    fun testShareTemplate() {
        val template = sharingService.createTemplate(
            name = "Test Template",
            template = "Hello {{name}}, welcome to {{service}}!",
            variables = listOf("name", "service")
        )
        
        assert(template.name == "Test Template") { "Template name should match" }
        assert(template.variables.size == 2) { "Should have 2 variables" }
        
        val applied = sharingService.applyTemplate(
            template,
            mapOf("name" to "John", "service" to "Layla")
        )
        
        assert(applied == "Hello John, welcome to Layla!") { "Template should be applied correctly" }
    }
    
    @Test
    fun testMultipleFormatExport(): Unit = runBlocking {
        val data = mapOf(
            "name" to "Test",
            "value" to "123",
            "status" to "active"
        )
        
        // Test JSON export
        val jsonResult = sharingService.exportData(data, "test", ExportFormat.JSON)
        assert(jsonResult.isSuccess) { "JSON export should succeed" }
        
        // Test CSV export
        val csvResult = sharingService.exportData(data, "test", ExportFormat.CSV)
        assert(csvResult.isSuccess) { "CSV export should succeed" }
        
        // Test Markdown export
        val mdResult = sharingService.exportData(data, "test", ExportFormat.MARKDOWN)
        assert(mdResult.isSuccess) { "Markdown export should succeed" }
        
        // Cleanup
        jsonResult.getOrNull()?.delete()
        csvResult.getOrNull()?.delete()
        mdResult.getOrNull()?.delete()
    }
    
    // ===== Advanced Workflow Service Tests =====
    
    @Test
    fun testWorkflowCreation() {
        val workflow = workflowService.createWorkflow(
            name = "Test Workflow",
            description = "A test workflow",
            steps = listOf(
                WorkflowStep(
                    id = "step1",
                    name = "First Step",
                    action = WorkflowAction.AIInference("Test prompt")
                )
            )
        )
        
        assert(workflow.name == "Test Workflow") { "Workflow name should match" }
        assert(workflow.steps.size == 1) { "Should have 1 step" }
    }
    
    @Test
    fun testWorkflowExecution(): Unit = runBlocking {
        val workflow = workflowService.createWorkflow(
            name = "Simple Workflow",
            description = "Test execution",
            steps = listOf(
                WorkflowStep(
                    id = "notify",
                    name = "Send Notification",
                    action = WorkflowAction.SendNotification("Test notification")
                )
            )
        )
        
        val result = workflowService.executeWorkflow(workflow.id)
        
        assert(result.isSuccess) { "Workflow execution should succeed" }
        val execution = result.getOrThrow()
        assert(execution.workflowId == workflow.id) { "Execution should be for correct workflow" }
        assert(execution.status == org.ninelym.layla.tasker.WorkflowStatus.COMPLETED) { "Workflow should complete: ${execution.error}" }
        assert(notifications.notifications.value.any { it.body == "Test notification" }) { "Notification should be delivered" }
    }
    
    @Test
    fun testWorkflowAnalytics() {
        val workflow = workflowService.createWorkflow(
            name = "Analytics Test",
            description = "Test analytics",
            steps = listOf()
        )
        
        val analytics = workflowService.analyzeWorkflow(workflow.id)
        
        assert(analytics.workflowId == workflow.id) { "Analytics should be for correct workflow" }
        assert(analytics.totalExecutions >= 0) { "Should have execution count" }
        assert(analytics.successRate >= 0.0) { "Should have success rate" }
    }
    
    // ===== Cloud Sync Service Tests =====
    
    @Test
    fun testCloudSyncDisabledByDefault() {
        val stats = cloudSyncService.getSyncStatistics()
        
        assert(!stats.enabled) { "Cloud sync should be disabled by default" }
        assert(stats.deviceCount == 0) { "Should have no synced devices initially" }
    }
    
    @Test
    fun testEnableCloudSync(): Unit = runBlocking {
        cloudSyncService.setSyncEnabled(true)
        assert(cloudSyncService.initialize("test_user").isSuccess) { "Should connect to the sync server" }
        
        val noServer = CloudSyncService()
        noServer.setSyncEnabled(true)
        assert(noServer.initialize("test_user").isFailure) { "Without a server, sync must not report success" }
    }
    
    @Test
    fun testDeviceRegistration(): Unit = runBlocking {
        cloudSyncService.setSyncEnabled(true)
        cloudSyncService.initialize("test_user")
        
        val result = cloudSyncService.registerDevice(
            deviceName = "Test Device",
            deviceType = org.ninelym.layla.cloud.DeviceType.PHONE
        )
        
        assert(result.isSuccess) { "Device registration should succeed" }
        val device = result.getOrThrow()
        assert(device.name == "Test Device") { "Device name should match" }
    }
    
    @Test
    fun testCloudSyncOperations(): Unit = runBlocking {
        cloudSyncService.setSyncEnabled(true)
        cloudSyncService.initialize("test_user").getOrThrow()
        
        val upload = cloudSyncService.syncToCloud(SyncDataType.PREFERENCES, mapOf("theme" to "midnight-blue")).getOrThrow()
        assert(upload.bytesTransferred > 0) { "Data should reach the server" }
        val onServer = String(syncServer.stored.getValue("/test_user/preferences"))
        assert(!onServer.contains("midnight-blue")) { "Server must only see ciphertext" }
        
        val downloaded = cloudSyncService.syncFromCloud(SyncDataType.PREFERENCES).getOrThrow()
        assert(downloaded["theme"] == "midnight-blue") { "Round trip should restore the data" }
    }
    
    // ===== Integration Tests =====
    
    @Test
    fun testDocumentToExportIntegration(): Unit = runBlocking {
        requireModel()
        val testFile = File.createTempFile("integration_test", ".txt")
        testFile.writeText("The Eiffel Tower in Paris was completed in 1889 for the World's Fair.")
        
        val document = documentService.loadDocument(testFile.absolutePath).getOrThrow()
        val summary = documentService.summarizeDocument(document.id).getOrThrow()
        assert(summary.summary.isNotBlank()) { "The model should summarize the document" }
        
        val exported = sharingService.exportData(mapOf("summary" to summary.summary), "summary", ExportFormat.MARKDOWN)
        assert(exported.isSuccess) { "Exporting the summary should succeed" }
        exported.getOrNull()?.delete()
        testFile.delete()
    }
    
    @Test
    fun testWorkflowWithBilling(): Unit = runBlocking {
        // Create workflow that checks premium status
        val workflow = workflowService.createWorkflow(
            name = "Premium Feature Workflow",
            description = "Workflow that requires premium",
            steps = listOf(
                WorkflowStep(
                    id = "check_premium",
                    name = "Check Premium",
                    action = WorkflowAction.Custom { context ->
                        billingService.hasPremiumSubscription()
                    }
                )
            )
        )
        
        val result = workflowService.executeWorkflow(workflow.id)
        assert(result.isSuccess) { "Workflow execution should succeed" }
    }
    
    @Test
    fun testFullPhase3Integration(): Unit = runBlocking {
        requireModel()
        cloudSyncService.setSyncEnabled(true)
        cloudSyncService.initialize("test_user").getOrThrow()
        
        val testFile = File.createTempFile("full_test", ".txt")
        testFile.writeText("Complete Phase 3 integration test content about photosynthesis in plants.")
        val document = documentService.loadDocument(testFile.absolutePath).getOrThrow()
        val summary = documentService.summarizeDocument(document.id).getOrThrow()
        
        // The workflow's AI step is answered by the model, using the summary as input
        val workflow = workflowService.createWorkflow(
            name = "Integration Workflow",
            description = "Full integration test",
            steps = listOf(
                WorkflowStep(
                    id = "process",
                    name = "Process Document",
                    action = WorkflowAction.AIInference("Give this summary a short title: {{summary}}")
                )
            )
        )
        val execution = workflowService.executeWorkflow(workflow.id, mapOf("summary" to summary.summary)).getOrThrow()
        assert(execution.status == org.ninelym.layla.tasker.WorkflowStatus.COMPLETED) { "Workflow should complete" }
        
        val sync = cloudSyncService.syncToCloud(SyncDataType.DOCUMENTS, mapOf("summary" to summary.summary)).getOrThrow()
        assert(sync.bytesTransferred > 0) { "Cloud sync should upload the summary" }
        
        testFile.delete()
    }
}

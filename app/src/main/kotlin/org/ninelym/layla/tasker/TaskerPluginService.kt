package org.ninelym.layla.tasker

import org.ninelym.layla.inference.LaylaInferenceService
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.ask
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Tasker Plugin Service
 * Phase 2: Enhancement - Task automation integration
 * 
 * Provides Tasker integration for AI-powered automation workflows.
 */
class TaskerPluginService(
    private val inferenceService: LaylaInferenceService = LaylaInferenceService(),
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() },
    private val outputDir: File = File(System.getProperty("java.io.tmpdir"))
) {

    private val actions = ConcurrentHashMap<String, TaskerAction>()
    private val events = ConcurrentHashMap<String, TaskerEvent>()
    private val activeInferences = ConcurrentHashMap<String, InferenceTask>()
    
    private var isInitialized = false
    
    /**
     * Initialize Tasker plugin service
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            registerDefaultActions()
            registerDefaultEvents()
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Execute Tasker action
     */
    suspend fun executeAction(
        actionId: String,
        parameters: Map<String, String>
    ): Result<String> {
        if (!isInitialized) {
            return Result.failure(Exception("Service not initialized"))
        }
        
        val action = actions[actionId]
            ?: return Result.failure(Exception("Action not found: $actionId"))
        
        return try {
            val result = action.execute(parameters)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Perform AI inference in background
     */
    suspend fun inferInBackground(
        prompt: String,
        contextId: String? = null,
        priority: InferencePriority = InferencePriority.NORMAL
    ): Result<String> {
        val taskId = generateTaskId()
        val task = InferenceTask(
            id = taskId,
            prompt = prompt,
            contextId = contextId,
            priority = priority,
            startTime = System.currentTimeMillis(),
            status = TaskStatus.RUNNING
        )
        
        activeInferences[taskId] = task
        
        return try {
            val fullPrompt = if (contextId != null) "Context: $contextId\n$prompt" else prompt
            val response = inferenceService.infer(fullPrompt).map { it.text }
            
            activeInferences.remove(taskId)
            
            // Trigger completion event
            response.onSuccess { text ->
                triggerEvent("task_complete", mapOf("taskId" to taskId, "result" to text))
            }
            
            response
        } catch (e: Exception) {
            activeInferences.remove(taskId)
            Result.failure(e)
        }
    }
    
    /**
     * Get active inference tasks
     */
    fun getActiveTasks(): List<InferenceTask> {
        return activeInferences.values.toList()
    }
    
    /**
     * Cancel inference task
     */
    fun cancelTask(taskId: String): Result<Unit> {
        return try {
            activeInferences.remove(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Register custom action
     */
    fun registerAction(action: TaskerAction): Result<Unit> {
        return try {
            actions[action.id] = action
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Register event listener
     */
    fun registerEvent(event: TaskerEvent): Result<Unit> {
        return try {
            events[event.id] = event
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Trigger event
     */
    private fun triggerEvent(eventId: String, data: Map<String, String>) {
        events[eventId]?.trigger?.invoke(data)
    }
    
    /**
     * Get available actions
     */
    fun getAvailableActions(): List<TaskerActionInfo> {
        return actions.values.map { action ->
            TaskerActionInfo(
                id = action.id,
                name = action.name,
                description = action.description,
                parameters = action.parameters
            )
        }
    }
    
    /**
     * Get available events
     */
    fun getAvailableEvents(): List<TaskerEventInfo> {
        return events.values.map { event ->
            TaskerEventInfo(
                id = event.id,
                name = event.name,
                description = event.description
            )
        }
    }
    
    private fun registerDefaultActions() {
        // AI Inference Action: the configured model answers the prompt
        actions["ai_inference"] = TaskerAction(
            id = "ai_inference",
            name = "AI Inference",
            description = "Perform AI inference with given prompt",
            parameters = listOf("prompt", "context"),
            execute = { params ->
                val prompt = params["prompt"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("prompt is required")
                val context = params["context"].orEmpty()
                val model = modelProvider().getOrThrow()
                model.ask(
                    "You are an automation assistant invoked from Tasker. Reply concisely with just the result." +
                        if (context.isNotBlank()) "\nContext: $context" else "",
                    prompt
                ).getOrThrow()
            }
        )
        
        // Text Processing Action: the model applies the requested operation
        actions["process_text"] = TaskerAction(
            id = "process_text",
            name = "Process Text",
            description = "Process text with AI",
            parameters = listOf("text", "operation"),
            execute = { params ->
                val text = params["text"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("text is required")
                val operation = params["operation"] ?: "summarize"
                modelProvider().getOrThrow().ask(
                    "Apply this operation to the user's text: $operation. Output only the result.",
                    text
                ).getOrThrow()
            }
        )
        
        // Generate Image Action: the served image model renders it; returns the file path
        actions["generate_image"] = TaskerAction(
            id = "generate_image",
            name = "Generate Image",
            description = "Generate image from text prompt",
            parameters = listOf("prompt", "style"),
            execute = { params ->
                val prompt = params["prompt"]?.takeIf { it.isNotBlank() }
                    ?: throw IllegalArgumentException("prompt is required")
                val style = params["style"]?.takeIf { it.isNotBlank() }
                val bytes = LanguageModels.currentEndpoint().getOrThrow()
                    .generateImage(if (style != null) "$prompt, in $style style" else prompt).getOrThrow()
                File(outputDir, "tasker_image_${System.currentTimeMillis()}.png").apply { writeBytes(bytes) }.absolutePath
            }
        )
    }
    
    private fun registerDefaultEvents() {
        // Task Completed Event
        events["task_complete"] = TaskerEvent(
            id = "task_complete",
            name = "Task Completed",
            description = "Triggered when background task completes"
        )
        
        // Inference Ready Event
        events["inference_ready"] = TaskerEvent(
            id = "inference_ready",
            name = "Inference Ready",
            description = "Triggered when inference service is ready"
        )
    }
    
    private fun generateTaskId(): String {
        return "task_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    }
}

/**
 * Tasker action
 */
data class TaskerAction(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<String>,
    val execute: suspend (Map<String, String>) -> String
)

/**
 * Tasker event
 */
data class TaskerEvent(
    val id: String,
    val name: String,
    val description: String,
    val trigger: (Map<String, String>) -> Unit = {}
)

/**
 * Inference task
 */
data class InferenceTask(
    val id: String,
    val prompt: String,
    val contextId: String?,
    val priority: InferencePriority,
    val startTime: Long,
    val status: TaskStatus
)

/**
 * Task status
 */
enum class TaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Inference priority
 */
enum class InferencePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Action info
 */
data class TaskerActionInfo(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<String>
)

/**
 * Event info
 */
data class TaskerEventInfo(
    val id: String,
    val name: String,
    val description: String
)

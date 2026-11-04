package org.ninelym.togai.integration

import kotlinx.serialization.json.jsonObject

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * AI-Powered Extensions for System Intelligence
 *
 * Combines screen reading capabilities with CogAI for:
 * - Intelligent screen summarization
 * - Context-aware assistance
 * - Smart action suggestions
 * - Voice-controlled navigation
 */

// ========== Screen Analysis ==========

/**
 * Summarize current screen content with AI
 */
suspend fun SystemIntelligenceService.summarizeScreenWithAI(
    cogai: CogAIBridge
): ScreenSummary {
    val content = getScreenContent()
        ?: throw IllegalStateException("Screen content is unavailable (is the accessibility service enabled?)")

    val screenText = content.allText.take(3000) // Limit for API
    val interactiveCount = content.interactiveElements.size
    val buttons = content.interactiveElements
        .filter { it.isClickable }
        .mapNotNull { it.label }
        .take(10)

    val prompt = buildString {
        appendLine("Analyze this Android screen. Respond with a JSON object:")
        appendLine("{\"summary\": \"1-2 sentences\", \"keyPoints\": [\"...\"], \"suggestedActions\": [\"...\"]}")
        appendLine()
        appendLine("App: ${content.packageName}")
        appendLine("Window: ${content.windowTitle ?: "Unknown"}")
        appendLine("Buttons/Actions available: ${buttons.joinToString(", ")}")
        appendLine()
        appendLine("Screen text:")
        appendLine(screenText)
    }

    // Re-ask the model if its output can't be parsed; never substitute a summary of our own
    var lastError: Exception? = null
    repeat(3) {
        val response = cogai.ask(
            prompt = prompt,
            systemPrompt = "You are a helpful assistant analyzing Android screens. Be concise and practical. Output only JSON."
        )
        try {
            return parseScreenSummary(response, content)
        } catch (e: IllegalStateException) {
            lastError = e
        }
    }
    throw CogAIException("Model did not produce a usable screen summary: ${lastError?.message}")
}

data class ScreenSummary(
    val summary: String,
    val keyPoints: List<String>,
    val suggestedActions: List<String>,
    val appName: String? = null,
    val screenType: ScreenType = ScreenType.UNKNOWN
)

enum class ScreenType {
    HOME,
    SETTINGS,
    LIST,
    DETAIL,
    FORM,
    DIALOG,
    MEDIA,
    BROWSER,
    CHAT,
    UNKNOWN
}

private fun parseScreenSummary(
    aiResponse: String,
    content: SystemIntelligenceService.ScreenContent
): ScreenSummary {
    val start = aiResponse.indexOf('{')
    val end = aiResponse.lastIndexOf('}')
    if (start < 0 || end < start) throw IllegalStateException("not JSON: ${aiResponse.take(200)}")
    val obj = try {
        kotlinx.serialization.json.Json.parseToJsonElement(aiResponse.substring(start, end + 1)).jsonObject
    } catch (e: Exception) {
        throw IllegalStateException("invalid JSON: ${e.message}")
    }
    fun list(key: String) = (obj[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
        ?: emptyList()
    val summary = (obj["summary"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("no summary in model output")

    return ScreenSummary(
        summary = summary,
        keyPoints = list("keyPoints"),
        suggestedActions = list("suggestedActions"),
        appName = content.packageName.split(".").lastOrNull(),
        screenType = detectScreenType(content)
    )
}

private fun detectScreenType(content: SystemIntelligenceService.ScreenContent): ScreenType {
    val pkg = content.packageName.lowercase()
    val title = content.windowTitle?.lowercase() ?: ""
    val hasScrollable = content.interactiveElements.any { it.isScrollable }
    val buttonCount = content.interactiveElements.count { it.isClickable }

    return when {
        pkg.contains("launcher") -> ScreenType.HOME
        pkg.contains("settings") || title.contains("settings") -> ScreenType.SETTINGS
        pkg.contains("browser") || pkg.contains("chrome") -> ScreenType.BROWSER
        pkg.contains("whatsapp") || pkg.contains("messenger") || pkg.contains("telegram") -> ScreenType.CHAT
        pkg.contains("youtube") || pkg.contains("spotify") || pkg.contains("music") -> ScreenType.MEDIA
        hasScrollable && buttonCount > 10 -> ScreenType.LIST
        buttonCount < 5 && content.textContent.size > 10 -> ScreenType.DETAIL
        content.textContent.any { it.isEditable } -> ScreenType.FORM
        title.contains("dialog") || buttonCount <= 3 -> ScreenType.DIALOG
        else -> ScreenType.UNKNOWN
    }
}

// ========== Context-Aware Assistance ==========

/**
 * Get context-aware help for current screen
 */
suspend fun SystemIntelligenceService.getContextualHelp(
    userQuestion: String,
    cogai: CogAIBridge
): String {
    val content = getScreenContent()
        ?: return cogai.ask(
            prompt = "The user asked: $userQuestion\n\n(Their screen content could not be read.)",
            systemPrompt = "You are a helpful Android assistant. Answer the question as well as you can, " +
                "and mention that you couldn't see their screen."
        )

    val prompt = buildString {
        appendLine("The user is viewing this Android screen and has a question.")
        appendLine()
        appendLine("App: ${content.packageName}")
        appendLine("Screen: ${content.windowTitle ?: content.activityName ?: "Unknown"}")
        appendLine()
        appendLine("Available actions: ${content.interactiveElements.filter { it.isClickable }.mapNotNull { it.label }.take(15).joinToString(", ")}")
        appendLine()
        appendLine("Visible text (excerpt):")
        appendLine(content.allText.take(1500))
        appendLine()
        appendLine("User question: $userQuestion")
    }

    return cogai.ask(
        prompt = prompt,
        systemPrompt = "You are a helpful Android assistant. Answer the user's question based on what's visible on their screen. Be specific and actionable."
    )
}

/**
 * Suggest next actions based on context
 */
suspend fun SystemIntelligenceService.suggestNextActions(
    cogai: CogAIBridge,
    recentApps: List<String> = emptyList()
): List<ActionSuggestion> {
    val content = getScreenContent()
    val mediaInfo = getCurrentMediaInfo()
    val predictedApps = getPredictedApps(3)

    val context = buildString {
        appendLine("Current context:")
        if (content != null) {
            appendLine("- App: ${content.packageName}")
            appendLine("- Screen: ${content.windowTitle ?: "Unknown"}")
        }
        if (mediaInfo != null) {
            appendLine("- Playing: $mediaInfo")
        }
        if (recentApps.isNotEmpty()) {
            appendLine("- Recent apps: ${recentApps.joinToString(", ")}")
        }
        appendLine("- Predicted apps: ${predictedApps.map { it.appName }.joinToString(", ")}")
    }

    val response = cogai.ask(
        prompt = """Based on this context, suggest 3-5 actions the user might want to take next.

$context

Format each suggestion as: [ACTION_TYPE] Description
Action types: OPEN_APP, NAVIGATE, SEARCH, CREATE, SEND, PLAY, SETTINGS, OTHER""",
        systemPrompt = "You are a proactive assistant. Suggest practical, contextual actions."
    )

    return parseActionSuggestions(response)
}

data class ActionSuggestion(
    val type: ActionType,
    val description: String,
    /** Null: the model doesn't report a confidence. */
    val confidence: Float? = null
)

enum class ActionType {
    OPEN_APP,
    NAVIGATE,
    SEARCH,
    CREATE,
    SEND,
    PLAY,
    SETTINGS,
    OTHER
}

private fun parseActionSuggestions(response: String): List<ActionSuggestion> {
    val suggestions = mutableListOf<ActionSuggestion>()
    val regex = """\[(\w+)\]\s*(.+)""".toRegex()

    response.lines().forEach { line ->
        regex.find(line)?.let { match ->
            val typeStr = match.groupValues[1].uppercase()
            val description = match.groupValues[2].trim()

            val type = try {
                ActionType.valueOf(typeStr)
            } catch (e: Exception) {
                ActionType.OTHER
            }

            suggestions.add(ActionSuggestion(type, description))
        }
    }

    return suggestions.take(5)
}

// ========== Voice Navigation ==========

/**
 * Execute voice command on current screen
 */
suspend fun SystemIntelligenceService.executeVoiceCommand(
    command: String,
    cogai: CogAIBridge
): VoiceCommandResult {
    val content = getScreenContent() ?: return VoiceCommandResult(
        success = false,
        message = "Unable to read screen",
        action = null
    )

    val availableButtons = content.interactiveElements
        .filter { it.isClickable }
        .mapNotNull { it.label }
        .distinct()

    val prompt = buildString {
        appendLine("User voice command: \"$command\"")
        appendLine()
        appendLine("Current app: ${content.packageName}")
        appendLine("Available clickable elements: ${availableButtons.joinToString(", ")}")
        appendLine()
        appendLine("What action should be taken? Respond with ONE of:")
        appendLine("CLICK: <exact button text>")
        appendLine("SCROLL: UP or DOWN")
        appendLine("BACK")
        appendLine("HOME")
        appendLine("TYPE: <text to type>")
        appendLine("NONE: <explanation if command cannot be executed>")
    }

    // Keep the model's wording: button labels and explanations are matched/shown as written
    val response = cogai.ask(
        prompt = prompt,
        systemPrompt = "You are a voice command interpreter. Output only the action in the exact format specified."
    ).trim()

    return executeInterpretedCommand(response, content)
}

data class VoiceCommandResult(
    val success: Boolean,
    val message: String,
    val action: String?
)

private fun SystemIntelligenceService.executeInterpretedCommand(
    interpreted: String,
    content: SystemIntelligenceService.ScreenContent
): VoiceCommandResult {
    val keyword = interpreted.substringBefore(":").trim().uppercase()
    val argument = interpreted.substringAfter(":", "").trim()
    return when (keyword) {
        "CLICK" -> {
            val clicked = clickByText(argument)
            VoiceCommandResult(
                success = clicked,
                message = if (clicked) "Clicked '$argument'" else "Could not find '$argument'",
                action = "CLICK"
            )
        }
        "SCROLL" -> {
            val direction = argument.uppercase()
            val forward = direction != "UP"
            val scrolled = scroll(forward)
            VoiceCommandResult(
                success = scrolled,
                message = if (scrolled) "Scrolled ${direction.lowercase()}" else "Could not scroll",
                action = "SCROLL"
            )
        }
        "BACK" -> {
            val success = goBack()
            VoiceCommandResult(success, if (success) "Went back" else "Could not go back", "BACK")
        }
        "HOME" -> {
            val success = goHome()
            VoiceCommandResult(success, if (success) "Went home" else "Could not go home", "HOME")
        }
        "TYPE" -> VoiceCommandResult(false, "Typing text is not supported by this service (asked to type '$argument')", "TYPE")
        "NONE" -> VoiceCommandResult(false, argument, null)
        else -> VoiceCommandResult(false, "Could not interpret the model's answer: $interpreted", null)
    }
}

// ========== Smart Monitoring ==========

/**
 * Monitor screen for specific content and alert
 */
fun SystemIntelligenceService.monitorForContent(
    keywords: List<String>,
    onFound: (SystemIntelligenceService.ScreenContent, List<String>) -> Unit
): Flow<Pair<SystemIntelligenceService.ScreenContent, List<String>>> = flow {
    observeScreenChanges().collect { content ->
        val foundKeywords = keywords.filter { keyword ->
            content.allText.contains(keyword, ignoreCase = true)
        }

        if (foundKeywords.isNotEmpty()) {
            emit(content to foundKeywords)
            onFound(content, foundKeywords)
        }
    }
}

/**
 * Watch for app changes
 */
fun SystemIntelligenceService.observeAppChanges(): Flow<AppChangeEvent> = flow {
    var lastPackage = ""

    observeScreenChanges().collect { content ->
        if (content.packageName != lastPackage) {
            emit(AppChangeEvent(
                previousApp = lastPackage,
                currentApp = content.packageName,
                timestamp = content.timestamp
            ))
            lastPackage = content.packageName
        }
    }
}

data class AppChangeEvent(
    val previousApp: String,
    val currentApp: String,
    val timestamp: Long
)

// ========== Screen Recording Companion ==========

/**
 * Generate AI commentary for screen activity
 */
suspend fun SystemIntelligenceService.generateActivityLog(
    durationSeconds: Int,
    cogai: CogAIBridge
): ActivityLog {
    val events = mutableListOf<ActivityLogEntry>()
    val startTime = System.currentTimeMillis()

    coroutineScope {
        val job = launch {
            observeScreenChanges().collect { content ->
                events.add(ActivityLogEntry(
                    timestamp = System.currentTimeMillis() - startTime,
                    app = content.packageName,
                    screen = content.windowTitle ?: content.activityName,
                    textPreview = content.allText.take(100)
                ))
            }
        }

        delay(durationSeconds * 1000L)
        job.cancel()
    }

    // Generate AI summary
    val summary = if (events.isNotEmpty()) {
        val eventList = events.takeLast(20).joinToString("\n") { entry ->
            "${entry.timestamp / 1000}s: ${entry.app} - ${entry.screen ?: "Unknown"}"
        }

        cogai.ask(
            prompt = "Summarize this user activity log:\n$eventList",
            systemPrompt = "You are an activity analyst. Provide a brief summary of what the user was doing."
        )
    } else {
        "No activity recorded"
    }

    return ActivityLog(
        durationMs = durationSeconds * 1000L,
        events = events,
        summary = summary,
        appsSwitched = events.map { it.app }.distinct().size
    )
}

data class ActivityLogEntry(
    val timestamp: Long,
    val app: String,
    val screen: String?,
    val textPreview: String
)

data class ActivityLog(
    val durationMs: Long,
    val events: List<ActivityLogEntry>,
    val summary: String,
    val appsSwitched: Int
)

// ========== Integration with Orchestrator ==========

/**
 * Add System Intelligence to orchestrator workflow steps
 */
suspend fun TogaiPlusOrchestrator.executeSystemIntelligenceStep(
    action: String,
    params: Map<String, String>,
    helper: SystemIntelligenceHelper,
    cogai: CogAIBridge
): Pair<Boolean, String> {
    val service = helper.service
        ?: return false to "System Intelligence service not available. Enable Accessibility Service."

    return when (action.lowercase()) {
        "get_screen_text", "ocr" -> {
            val text = service.extractTextFromScreen()
            (text.isNotBlank()) to text.ifBlank { "No text found on screen" }
        }
        "summarize_screen" -> {
            val summary = service.summarizeScreenWithAI(cogai)
            true to summary.summary
        }
        "get_context" -> {
            val context = service.getScreenContext()
            (context != null) to (context ?: "No screen content available")
        }
        "get_media" -> {
            val media = service.getCurrentMediaInfo()
            (media != null) to (media?.toString() ?: "No media playing")
        }
        "predict_apps" -> {
            val limit = params["limit"]?.toIntOrNull() ?: 5
            val predictions = service.getPredictedApps(limit)
            true to predictions.joinToString("\n") { "${it.appName} (${(it.confidence * 100).toInt()}%)" }
        }
        "voice_command" -> {
            val command = params["command"] ?: return false to "Missing 'command' parameter"
            val result = service.executeVoiceCommand(command, cogai)
            result.success to result.message
        }
        "click" -> {
            val text = params["text"] ?: return false to "Missing 'text' parameter"
            val clicked = service.clickByText(text)
            clicked to if (clicked) "Clicked '$text'" else "Could not find '$text'"
        }
        "scroll" -> {
            val direction = params["direction"]?.lowercase() ?: "down"
            val scrolled = service.scroll(forward = direction != "up")
            scrolled to if (scrolled) "Scrolled $direction" else "Could not scroll"
        }
        "back" -> {
            val success = service.goBack()
            success to if (success) "Went back" else "Failed"
        }
        "home" -> {
            val success = service.goHome()
            success to if (success) "Went home" else "Failed"
        }
        else -> false to "Unknown System Intelligence action: $action"
    }
}

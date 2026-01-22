package org.ninelym.togai.integration

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.ninelym.togai.util.ErrorHandler
import java.util.*

/**
 * System Intelligence Service for Togai+
 *
 * Provides deep Android system integration for:
 * - Screen context understanding (current app, UI elements, text)
 * - OCR-like text extraction from any screen
 * - Now Playing media detection
 * - App usage prediction based on patterns
 * - Real-time screen change observation
 *
 * Requires Accessibility Service permission.
 */
class SystemIntelligenceService : AccessibilityService() {

    companion object {
        private const val TAG = "SystemIntelligence"

        // Singleton reference for external access
        @Volatile
        private var instance: SystemIntelligenceService? = null

        fun getInstance(): SystemIntelligenceService? = instance

        /**
         * Check if accessibility service is enabled
         */
        fun isServiceEnabled(context: Context): Boolean {
            val serviceName = ComponentName(context, SystemIntelligenceService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            return enabledServices.contains(serviceName.flattenToString())
        }

        /**
         * Open accessibility settings
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * Check if usage stats permission is granted
         */
        fun hasUsageStatsPermission(context: Context): Boolean {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
                time
            )
            return stats != null && stats.isNotEmpty()
        }

        /**
         * Open usage stats settings
         */
        fun openUsageStatsSettings(context: Context) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    // ========== Data Classes ==========

    /**
     * Screen content snapshot
     */
    data class ScreenContent(
        val packageName: String,
        val activityName: String?,
        val windowTitle: String?,
        val textContent: List<TextElement>,
        val interactiveElements: List<UIElement>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val allText: String get() = textContent.joinToString("\n") { it.text }

        val summary: String get() = buildString {
            appendLine("App: $packageName")
            if (windowTitle != null) appendLine("Window: $windowTitle")
            appendLine("Text elements: ${textContent.size}")
            appendLine("Interactive elements: ${interactiveElements.size}")
        }
    }

    /**
     * Text element on screen
     */
    data class TextElement(
        val text: String,
        val bounds: Rect?,
        val className: String?,
        val isHeading: Boolean = false,
        val isEditable: Boolean = false
    )

    /**
     * Interactive UI element
     */
    data class UIElement(
        val text: String?,
        val contentDescription: String?,
        val className: String?,
        val bounds: Rect?,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isCheckable: Boolean,
        val isChecked: Boolean = false
    ) {
        val label: String get() = text ?: contentDescription ?: className ?: "Unknown"
    }

    /**
     * Media information
     */
    data class MediaInfo(
        val title: String?,
        val artist: String?,
        val album: String?,
        val packageName: String?,
        val isPlaying: Boolean,
        val duration: Long?,
        val position: Long?
    ) {
        override fun toString(): String = buildString {
            if (title != null) append("\"$title\"")
            if (artist != null) append(" by $artist")
            if (album != null) append(" ($album)")
            append(if (isPlaying) " [Playing]" else " [Paused]")
        }
    }

    /**
     * App prediction
     */
    data class AppPrediction(
        val packageName: String,
        val appName: String,
        val confidence: Float,
        val reason: PredictionReason,
        val lastUsed: Long?
    )

    enum class PredictionReason {
        TIME_PATTERN,      // Used frequently at this time
        RECENT_USE,        // Recently used
        FREQUENT_USE,      // Most frequently used
        SEQUENCE_PATTERN,  // Often used after current app
        CONTEXT_MATCH      // Matches current context
    }

    // ========== State ==========

    private val _screenContent = MutableStateFlow<ScreenContent?>(null)
    val screenContent: StateFlow<ScreenContent?> = _screenContent.asStateFlow()

    private val _screenChanges = MutableSharedFlow<ScreenContent>(replay = 1)

    private var currentPackage: String = ""
    private var currentActivity: String? = null

    // ========== Lifecycle ==========

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Configure service
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        ErrorHandler.logInfo(TAG, "System Intelligence Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        ErrorHandler.logInfo(TAG, "System Intelligence Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                currentPackage = event.packageName?.toString() ?: ""
                currentActivity = event.className?.toString()
                captureScreen()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Debounce content changes
                captureScreen()
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Could track focus changes if needed
            }
        }
    }

    override fun onInterrupt() {
        ErrorHandler.logDebug(TAG, "Service interrupted")
    }

    // ========== Screen Capture ==========

    /**
     * Capture current screen content
     */
    private fun captureScreen() {
        val rootNode = rootInActiveWindow ?: return

        try {
            val textElements = mutableListOf<TextElement>()
            val interactiveElements = mutableListOf<UIElement>()
            var windowTitle: String? = null

            // Traverse the accessibility tree
            traverseNode(rootNode, textElements, interactiveElements)

            // Try to get window title
            windowTitle = rootNode.findAccessibilityNodeInfosByViewId(
                "$currentPackage:id/action_bar_title"
            ).firstOrNull()?.text?.toString()
                ?: rootNode.findAccessibilityNodeInfosByViewId(
                    "$currentPackage:id/toolbar_title"
                ).firstOrNull()?.text?.toString()

            val content = ScreenContent(
                packageName = currentPackage,
                activityName = currentActivity,
                windowTitle = windowTitle,
                textContent = textElements,
                interactiveElements = interactiveElements
            )

            _screenContent.value = content

            // Emit to flow (non-blocking)
            CoroutineScope(Dispatchers.Default).launch {
                _screenChanges.emit(content)
            }

        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Error capturing screen", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Traverse accessibility node tree
     */
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        textElements: MutableList<TextElement>,
        interactiveElements: MutableList<UIElement>,
        depth: Int = 0
    ) {
        if (depth > 30) return // Prevent infinite recursion

        // Extract text
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val hasText = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        if (hasText) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val displayText = text ?: contentDesc ?: ""

            if (displayText.isNotBlank()) {
                textElements.add(
                    TextElement(
                        text = displayText,
                        bounds = bounds,
                        className = node.className?.toString(),
                        isHeading = node.isHeading,
                        isEditable = node.isEditable
                    )
                )
            }
        }

        // Extract interactive elements
        if (node.isClickable || node.isScrollable || node.isCheckable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            interactiveElements.add(
                UIElement(
                    text = text,
                    contentDescription = contentDesc,
                    className = node.className?.toString(),
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked
                )
            )
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, textElements, interactiveElements, depth + 1)
            child.recycle()
        }
    }

    // ========== Public API ==========

    /**
     * Get current screen content
     */
    fun getScreenContent(): ScreenContent? {
        captureScreen()
        return _screenContent.value
    }

    /**
     * Extract all text from current screen
     */
    fun extractTextFromScreen(): String {
        val content = getScreenContent() ?: return ""
        return content.allText
    }

    /**
     * Get screen context summary
     */
    fun getScreenContext(): String {
        val content = getScreenContent() ?: return "No screen content available"
        return content.summary
    }

    /**
     * Observe screen changes
     */
    fun observeScreenChanges(): Flow<ScreenContent> = _screenChanges.asSharedFlow()

    /**
     * Find text on screen
     */
    fun findText(query: String, ignoreCase: Boolean = true): List<TextElement> {
        val content = _screenContent.value ?: return emptyList()
        return content.textContent.filter {
            it.text.contains(query, ignoreCase)
        }
    }

    /**
     * Find clickable elements
     */
    fun findClickableElements(labelContains: String? = null): List<UIElement> {
        val content = _screenContent.value ?: return emptyList()
        return content.interactiveElements.filter { element ->
            element.isClickable && (labelContains == null ||
                    element.label.contains(labelContains, ignoreCase = true))
        }
    }

    /**
     * Get current app info
     */
    fun getCurrentApp(): Pair<String, String?> {
        return currentPackage to currentActivity
    }

    // ========== Media Detection ==========

    /**
     * Get currently playing media info
     */
    fun getCurrentMediaInfo(): MediaInfo? {
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE)
                    as? MediaSessionManager ?: return null

            val controllers = mediaSessionManager.getActiveSessions(
                ComponentName(this, NotificationListenerService::class.java)
            )

            for (controller in controllers) {
                val metadata = controller.metadata ?: continue
                val playbackState = controller.playbackState

                val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

                return MediaInfo(
                    title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    packageName = controller.packageName,
                    isPlaying = isPlaying,
                    duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
                    position = playbackState?.position
                )
            }
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Error getting media info", e)
        }

        return null
    }

    /**
     * Check if media is currently playing
     */
    fun isMediaPlaying(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isMusicActive
    }

    // ========== App Prediction ==========

    /**
     * Get predicted apps based on usage patterns
     */
    fun getPredictedApps(limit: Int = 5): List<AppPrediction> {
        val predictions = mutableListOf<AppPrediction>()

        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE)
                    as UsageStatsManager

            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -7) // Last 7 days
            val startTime = calendar.timeInMillis

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats.isNullOrEmpty()) {
                return emptyList()
            }

            // Current hour for time-based prediction
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            // Score each app
            val appScores = mutableMapOf<String, Float>()
            val appLastUsed = mutableMapOf<String, Long>()

            for (stat in usageStats) {
                if (stat.totalTimeInForeground < 60000) continue // Skip apps used < 1 min

                val packageName = stat.packageName

                // Skip system apps
                if (packageName.startsWith("com.android") ||
                    packageName.startsWith("com.google.android.inputmethod")) continue

                val score = calculateAppScore(stat, currentHour)
                appScores[packageName] = (appScores[packageName] ?: 0f) + score
                appLastUsed[packageName] = maxOf(
                    appLastUsed[packageName] ?: 0L,
                    stat.lastTimeUsed
                )
            }

            // Get app names and create predictions
            val pm = packageManager
            appScores.entries
                .sortedByDescending { it.value }
                .take(limit)
                .forEach { (packageName, score) ->
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()

                        predictions.add(
                            AppPrediction(
                                packageName = packageName,
                                appName = appName,
                                confidence = (score / 100f).coerceIn(0f, 1f),
                                reason = determineReason(score, appLastUsed[packageName]),
                                lastUsed = appLastUsed[packageName]
                            )
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        // App might be uninstalled
                    }
                }

        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "Error predicting apps", e)
        }

        return predictions
    }

    private fun calculateAppScore(stat: UsageStats, currentHour: Int): Float {
        var score = 0f

        // Frequency score
        val totalMinutes = stat.totalTimeInForeground / 60000f
        score += totalMinutes.coerceAtMost(100f) * 0.3f

        // Recency score
        val hoursSinceUse = (System.currentTimeMillis() - stat.lastTimeUsed) / 3600000f
        score += (24f - hoursSinceUse.coerceAtMost(24f)) * 2f

        // Time pattern score (simplified)
        val lastUseHour = Calendar.getInstance().apply {
            timeInMillis = stat.lastTimeUsed
        }.get(Calendar.HOUR_OF_DAY)

        if (Math.abs(lastUseHour - currentHour) <= 2) {
            score += 20f // Bonus for apps used around this time
        }

        return score
    }

    private fun determineReason(score: Float, lastUsed: Long?): PredictionReason {
        val hoursSinceUse = lastUsed?.let {
            (System.currentTimeMillis() - it) / 3600000f
        } ?: Float.MAX_VALUE

        return when {
            hoursSinceUse < 1 -> PredictionReason.RECENT_USE
            score > 50 -> PredictionReason.FREQUENT_USE
            else -> PredictionReason.TIME_PATTERN
        }
    }

    // ========== Actions ==========

    /**
     * Click on an element by text
     */
    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        try {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    node.recycle()
                    if (result) return true
                }
                node.recycle()
            }
        } finally {
            rootNode.recycle()
        }

        return false
    }

    /**
     * Scroll in a direction
     */
    fun scroll(forward: Boolean = true): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        try {
            val scrollables = mutableListOf<AccessibilityNodeInfo>()
            findScrollables(rootNode, scrollables)

            for (node in scrollables) {
                val action = if (forward)
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

                if (node.performAction(action)) {
                    scrollables.forEach { it.recycle() }
                    return true
                }
            }

            scrollables.forEach { it.recycle() }
        } finally {
            rootNode.recycle()
        }

        return false
    }

    private fun findScrollables(
        node: AccessibilityNodeInfo,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isScrollable) {
            result.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollables(child, result)
            child.recycle()
        }
    }

    /**
     * Go back
     */
    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Go home
     */
    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Open recent apps
     */
    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Open notifications
     */
    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * Take screenshot (Android 9+)
     */
    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else {
            false
        }
    }
}

/**
 * Helper class for accessing System Intelligence without service instance
 */
class SystemIntelligenceHelper(private val context: Context) {

    val isServiceEnabled: Boolean
        get() = SystemIntelligenceService.isServiceEnabled(context)

    val hasUsageStatsPermission: Boolean
        get() = SystemIntelligenceService.hasUsageStatsPermission(context)

    val service: SystemIntelligenceService?
        get() = SystemIntelligenceService.getInstance()

    fun openAccessibilitySettings() {
        SystemIntelligenceService.openAccessibilitySettings(context)
    }

    fun openUsageStatsSettings() {
        SystemIntelligenceService.openUsageStatsSettings(context)
    }

    fun getScreenContent(): SystemIntelligenceService.ScreenContent? {
        return service?.getScreenContent()
    }

    fun extractText(): String {
        return service?.extractTextFromScreen() ?: ""
    }

    fun getCurrentMedia(): SystemIntelligenceService.MediaInfo? {
        return service?.getCurrentMediaInfo()
    }

    fun getPredictedApps(limit: Int = 5): List<SystemIntelligenceService.AppPrediction> {
        return service?.getPredictedApps(limit) ?: emptyList()
    }

    fun observeScreen(): Flow<SystemIntelligenceService.ScreenContent>? {
        return service?.observeScreenChanges()
    }
}

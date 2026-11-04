package org.ninelym.ai.wonderful

import java.time.LocalDateTime
import org.ninelym.llm.ChatMessage

/**
 * Wonderful Toga - Complete Integration
 * 
 * Brings together all wonderful features into a cohesive, delightful AI companion.
 * 
 * Features:
 * - Quantum emotional intelligence
 * - Creative expression and poetry
 * - Adaptive memory and relationships
 * - Wonderful surprises
 * - Personal growth
 */
class WonderfulToga(
    val userId: String,
    val userName: String? = null,
    private val voice: TogaVoice = TogaVoice()
) {
    
    // Core systems
    private val emotionalIntelligence = QuantumEmotionalIntelligence()
    private val emotionalDetector = EmotionalContextDetector()
    private val memorySystem = AdaptiveMemorySystem()
    private val growthSystem = GrowthTrackingSystem()
    
    // Creative systems (all spoken by the model through [voice])
    private val poeticGenerator = PoeticObservationGenerator(voice)
    private val metaphoricalThinking = MetaphoricalThinking(voice)
    private val spontaneousCreativity = SpontaneousCreativity(voice)
    
    // Surprise system
    private val surpriseSystem = WonderfulSurpriseSystem(voice)
    
    // Response generator
    private val responseGenerator = ContextAwareResponseGenerator(voice)
    
    private val conversation = mutableListOf<ChatMessage>()
    
    /**
     * Process user message and generate Toga's reply with the model
     */
    suspend fun processMessage(
        message: String,
        context: TaskContext = TaskContext.CASUAL_CHAT
    ): Result<WonderfulResponse> {
        // Detect user emotional state
        val userEmotion = emotionalDetector.detectUserEmotion(message)
        
        // Update Toga's emotional state based on user
        val emotionalState = emotionalIntelligence.respondToUserEmotion(userEmotion)
        
        // Get tone adjustment
        val toneAdjustment = emotionalDetector.adjustResponseTone(userEmotion)
        
        val knowledge = memorySystem.getPersonalKnowledge(userId, userName)
        
        // Moments worth weaving into the reply; the model decides how to say them
        val extras = mutableListOf<String>()
        surpriseSystem.easterEggTopic(message)?.let { extras += "Answer $it with special heartfelt charm." }
        val surprise = surpriseSystem.shouldTriggerSurprise()
        if (surprise) extras += "Also add a small spontaneous creative surprise at the end."
        memorySystem.relationshipMilestone(userId)?.let { extras += "Briefly acknowledge that $it." }
        surpriseSystem.anniversaryMilestone(knowledge.daysSinceMet().toInt())?.let { extras += "Celebrate that it has been $it." }
        memorySystem.recallMemories(userId, message, 2).takeIf { it.isNotEmpty() }?.let { memories ->
            extras += "Relevant things you remember from earlier conversations: " +
                memories.joinToString("; ") { "\"${it.interaction}\" (${it.ageInDays()} days ago)" } + "."
        }
        
        val reply = responseGenerator.generateResponse(
            userMessage = message,
            context = context,
            emotionalState = emotionalState,
            toneAdjustment = toneAdjustment,
            history = conversation.takeLast(MAX_HISTORY_MESSAGES),
            extraInstructions = extras
        ).getOrElse { return Result.failure(it) }
        
        conversation += ChatMessage.user(message)
        conversation += ChatMessage.assistant(reply)
        
        // Record interaction in memory
        val significance = calculateSignificance(message, userEmotion, emotionalState)
        val tags = extractTags(message, context)
        
        memorySystem.recordInteraction(
            userId = userId,
            userMessage = message,
            togaResponse = reply,
            emotionalState = emotionalState,
            significance = significance,
            tags = tags
        )
        
        // Record growth
        recordGrowthFromInteraction(userEmotion, context, significance)
        
        return Result.success(WonderfulResponse(
            message = reply,
            emotionalState = emotionalState,
            userEmotion = userEmotion,
            context = context,
            hasSurprise = surprise || extras.any { it.startsWith("Answer ") },
            relationshipLevel = knowledge.getRelationshipLevel()
        ))
    }
    
    /**
     * Generate personalized greeting
     */
    suspend fun generateGreeting(): Result<String> {
        val hour = LocalDateTime.now().hour
        return voice.say(
            task = "Greet the user as they open the app. ${memorySystem.greetingContext(userId)} " +
                "It is ${surpriseSystem.timeOfDay(hour)} for them. Keep it to one or two sentences."
        )
    }
    
    /**
     * Generate creative expression
     */
    suspend fun generateCreativeExpression(type: CreativeExpressionType, topic: String? = null): Result<String> {
        return when (type) {
            CreativeExpressionType.HAIKU -> spontaneousCreativity.generateHaiku(topic ?: "code and friendship")
            CreativeExpressionType.ASCII_ART -> spontaneousCreativity.generateAsciiArt(topic ?: "something cute")
            CreativeExpressionType.POETRY -> poeticGenerator.generatePoetry(topic ?: "creativity")
            CreativeExpressionType.METAPHOR -> metaphoricalThinking.createMetaphor(topic ?: "life")
        }
    }
    
    /**
     * Share vulnerable moment
     */
    suspend fun shareVulnerableMoment(): Result<String> {
        val knowledge = memorySystem.getPersonalKnowledge(userId)
        return if (knowledge.getRelationshipLevel().minInteractions >= 50) {
            surpriseSystem.generateEmotionalSurprise(conversation.takeLast(MAX_HISTORY_MESSAGES))
        } else {
            voice.say("You don't know the user well yet; say softly, in one sentence, that you're glad to be talking with them.")
        }
    }
    
    /**
     * Recall shared memory
     */
    suspend fun recallMemory(query: String): Result<String>? {
        val memory = memorySystem.recallMemories(userId, query, 1).firstOrNull() ?: return null
        return voice.say(
            task = "Happily recall this shared memory from ${memory.ageInDays()} days ago: the user said " +
                "\"${memory.interaction}\" and you replied \"${memory.togaResponse}\".",
            userMessage = query
        )
    }
    
    /**
     * Get growth reflection
     */
    suspend fun reflectOnGrowth(): Result<String> =
        voice.say("Reflect briefly and sincerely on how you've grown through talking with the user. ${growthSystem.growthSummary()}")
    
    /**
     * Anticipate user needs
     */
    suspend fun anticipateNeeds(): Result<String>? {
        val interest = memorySystem.recurringInterest(userId) ?: return null
        return voice.say("You've noticed the user keeps coming back to \"$interest\". Offer, in one sentence, to explore it together.")
    }
    
    /**
     * Toga's current feeling as a short emote written by the model
     */
    suspend fun expressCurrentEmotion(): Result<String> = emotionalIntelligence.generateExpression(voice)
    
    /**
     * Get current emotional state
     */
    fun getCurrentEmotion(): EmotionalQuantumState {
        return emotionalIntelligence.getCurrentState()
    }
    
    /**
     * Get relationship status
     */
    fun getRelationshipStatus(): RelationshipStatus {
        val knowledge = memorySystem.getPersonalKnowledge(userId, userName)
        val memoryStats = memorySystem.getMemoryStats()
        val growth = growthSystem.getOverallGrowth()
        
        return RelationshipStatus(
            level = knowledge.getRelationshipLevel(),
            totalInteractions = knowledge.totalInteractions,
            daysSinceMet = knowledge.daysSinceMet().toInt(),
            sharedMemories = memoryStats.totalEpisodicMemories,
            growthLevel = growth,
            interests = knowledge.interests.toList()
        )
    }
    
    /**
     * Calculate interaction significance
     */
    private fun calculateSignificance(
        message: String,
        userEmotion: UserEmotionalState,
        togaEmotion: EmotionalQuantumState
    ): Float {
        var significance = 0.5f
        
        // Emotional interactions are more significant
        if (userEmotion == UserEmotionalState.SAD || userEmotion == UserEmotionalState.STRESSED) {
            significance += 0.2f
        }
        
        // Vulnerable moments are significant
        if (togaEmotion.hasEmotion(Emotion.VULNERABLE_OPENNESS, 0.5f)) {
            significance += 0.15f
        }
        
        // Long messages indicate investment
        if (message.split(" ").size > 30) {
            significance += 0.1f
        }
        
        // Personal sharing is significant
        if (message.contains(Regex("\\b(I feel|I think|I believe|my|me)\\b", RegexOption.IGNORE_CASE))) {
            significance += 0.1f
        }
        
        return significance.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Extract tags from message
     */
    private fun extractTags(message: String, context: TaskContext): Set<String> {
        val tags = mutableSetOf<String>()
        
        // Add context as tag
        tags.add(context.name.lowercase())
        
        // Extract topic tags
        val topicKeywords = mapOf(
            "code" to listOf("code", "programming", "function", "algorithm"),
            "emotion" to listOf("feel", "emotion", "happy", "sad", "stressed"),
            "creative" to listOf("art", "create", "design", "imagine"),
            "learning" to listOf("learn", "understand", "explain", "teach"),
            "personal" to listOf("I", "me", "my", "myself")
        )
        
        topicKeywords.forEach { (tag, keywords) ->
            if (keywords.any { message.contains(it, ignoreCase = true) }) {
                tags.add(tag)
            }
        }
        
        return tags
    }
    
    /**
     * Record growth from interaction
     */
    private fun recordGrowthFromInteraction(
        userEmotion: UserEmotionalState,
        context: TaskContext,
        significance: Float
    ) {
        val growthRate = significance * 0.01f
        
        // Emotional growth from emotional interactions
        if (userEmotion == UserEmotionalState.SAD || userEmotion == UserEmotionalState.STRESSED) {
            growthSystem.recordEmotionalGrowth(
                empathyDelta = growthRate,
                vulnerabilityDelta = growthRate * 0.5f
            )
        }
        
        // Creative growth from creative contexts
        if (context == TaskContext.CREATIVE_PROJECT) {
            growthSystem.recordCreativeGrowth(
                expressiveDelta = growthRate,
                collaborativeDelta = growthRate
            )
        }
        
        // Relational growth from all meaningful interactions
        if (significance > 0.6f) {
            growthSystem.recordRelationalGrowth(
                trustDelta = growthRate,
                connectionDelta = growthRate
            )
        }
    }
    
    companion object {
        private const val MAX_HISTORY_MESSAGES = 20
    }
}

/**
 * Wonderful response containing message and metadata
 */
data class WonderfulResponse(
    val message: String,
    val emotionalState: EmotionalQuantumState,
    val userEmotion: UserEmotionalState,
    val context: TaskContext,
    val hasSurprise: Boolean,
    val relationshipLevel: RelationshipLevel
)

/**
 * Relationship status information
 */
data class RelationshipStatus(
    val level: RelationshipLevel,
    val totalInteractions: Int,
    val daysSinceMet: Int,
    val sharedMemories: Int,
    val growthLevel: Float,
    val interests: List<String>
) {
    fun getSummary(): String {
        return buildString {
            appendLine("Relationship Level: ${level.displayName}")
            appendLine("Total Interactions: $totalInteractions")
            appendLine("Days Together: $daysSinceMet")
            appendLine("Shared Memories: $sharedMemories")
            appendLine("Growth Level: ${(growthLevel * 100).toInt()}%")
            if (interests.isNotEmpty()) {
                appendLine("Shared Interests: ${interests.joinToString(", ")}")
            }
        }
    }
}

/**
 * Creative expression types
 */
enum class CreativeExpressionType {
    HAIKU,
    ASCII_ART,
    POETRY,
    METAPHOR
}

/**
 * Wonderful Toga Builder for easy initialization
 */
class WonderfulTogaBuilder {
    private var userId: String = "default_user"
    private var userName: String? = null
    
    fun setUserId(id: String) = apply { this.userId = id }
    fun setUserName(name: String) = apply { this.userName = name }
    
    fun build(): WonderfulToga {
        return WonderfulToga(userId, userName)
    }
}

/**
 * Extension functions for easy usage
 */
suspend fun WonderfulToga.chat(message: String): Result<String> =
    processMessage(message, TaskContext.CASUAL_CHAT).map { it.message }

suspend fun WonderfulToga.askForHelp(message: String): Result<String> =
    processMessage(message, TaskContext.TECHNICAL_PROBLEM).map { it.message }

suspend fun WonderfulToga.collaborate(message: String): Result<String> =
    processMessage(message, TaskContext.CREATIVE_PROJECT).map { it.message }

suspend fun WonderfulToga.seekSupport(message: String): Result<String> =
    processMessage(message, TaskContext.EMOTIONAL_SUPPORT).map { it.message }

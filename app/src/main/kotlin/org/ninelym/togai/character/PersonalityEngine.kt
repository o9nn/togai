package org.ninelym.togai.character

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.ninelym.llm.ChatMessage
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.PersonaPrompt
import org.ninelym.togai.util.ErrorHandler

/**
 * Enhanced character personality engine for Togai
 * Provides dynamic personality traits, emotional states, and contextual responses
 */
class PersonalityEngine(
    private val characterId: String,
    private val basePersonality: Personality,
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {
    
    companion object {
        private const val TAG = "PersonalityEngine"
        
        // Emotion decay rate (per minute)
        private const val EMOTION_DECAY_RATE = 0.1f
        
        // Relationship growth rate
        private const val RELATIONSHIP_GROWTH_RATE = 0.05f
    }
    
    /**
     * Personality trait definition
     */
    @Serializable
    data class Personality(
        val name: String,
        val description: String,
        val traits: Map<String, Float>, // Trait name to value (0.0 - 1.0)
        val speechPatterns: List<String>,
        val emotionalRange: EmotionalRange,
        val quirks: List<String> = emptyList()
    )
    
    /**
     * Emotional range configuration
     */
    @Serializable
    data class EmotionalRange(
        val baseline: Map<String, Float>, // Default emotional state
        val volatility: Float = 0.5f, // How quickly emotions change (0.0 - 1.0)
        val expressiveness: Float = 0.7f // How much emotions affect responses (0.0 - 1.0)
    )
    
    /**
     * Current emotional state
     */
    @Serializable
    data class EmotionalState(
        val emotions: Map<String, Float>, // Emotion name to intensity (0.0 - 1.0)
        val dominantEmotion: String,
        val intensity: Float,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Relationship state with user
     */
    @Serializable
    data class RelationshipState(
        val trust: Float = 0.5f, // 0.0 - 1.0
        val familiarity: Float = 0.0f, // 0.0 - 1.0
        val affection: Float = 0.5f, // 0.0 - 1.0
        val interactionCount: Int = 0,
        val lastInteraction: Long = 0
    )
    
    /**
     * Context for response generation
     */
    data class ResponseContext(
        val userMessage: String,
        val conversationHistory: List<String> = emptyList(),
        val timeOfDay: String = "day",
        val userMood: String? = null,
        val taskType: String? = null
    )
    
    // Current emotional state
    private val _emotionalState = MutableStateFlow(
        EmotionalState(
            emotions = basePersonality.emotionalRange.baseline,
            dominantEmotion = basePersonality.emotionalRange.baseline.maxByOrNull { it.value }?.key ?: "neutral",
            intensity = 0.5f
        )
    )
    val emotionalState: StateFlow<EmotionalState> = _emotionalState.asStateFlow()
    
    // Current relationship state
    private val _relationshipState = MutableStateFlow(RelationshipState())
    val relationshipState: StateFlow<RelationshipState> = _relationshipState.asStateFlow()
    
    /**
     * Process user interaction and update emotional state
     */
    fun processInteraction(
        message: String,
        sentiment: Float = 0.0f // -1.0 (negative) to 1.0 (positive)
    ) {
        // Update emotional state based on sentiment
        updateEmotionalState(sentiment)
        
        // Update relationship
        updateRelationship()
        
        ErrorHandler.logDebug(TAG, "Processed interaction for $characterId")
    }
    
    /**
     * Generate the character's reply with the language model, shaped by personality, current
     * emotion and relationship. If [draft] is given, the model rewrites it in character.
     */
    suspend fun generateResponse(
        context: ResponseContext,
        draft: String? = null
    ): Result<String> {
        val model = modelProvider().getOrElse { return Result.failure(it) }
        val history = context.conversationHistory.takeLast(10).mapIndexed { i, line ->
            // History alternates user / character, ending with the character's last line
            if ((context.conversationHistory.takeLast(10).size - i) % 2 == 0) ChatMessage.user(line) else ChatMessage.assistant(line)
        }
        val task = buildString {
            append(moodInstruction())
            append(" It is ${context.timeOfDay}.")
            context.userMood?.let { append(" The user seems $it.") }
            context.taskType?.let { append(" The user is doing: $it.") }
            draft?.let { append(" Base your reply on this content, rewritten entirely in your own voice: \"$it\"") }
        }
        return personaPrompt().respond(model, history + ChatMessage.user(context.userMessage), task)
    }
    
    /**
     * Get a personality-appropriate greeting, written by the model
     */
    suspend fun getGreeting(timeOfDay: String = "day"): Result<String> {
        val model = modelProvider().getOrElse { return Result.failure(it) }
        return personaPrompt().respond(
            model,
            "(The user just opened the app.)",
            "Greet the user in one or two sentences. It is $timeOfDay. ${moodInstruction()}"
        )
    }
    
    /**
     * The persona prompt for this character
     */
    fun personaPrompt(): PersonaPrompt = PersonaPrompt(
        name = basePersonality.name,
        description = basePersonality.description,
        traits = basePersonality.traits,
        styleHints = (basePersonality.speechPatterns + basePersonality.quirks).distinct()
    )
    
    /**
     * Current emotion and relationship expressed as guidance for the model
     */
    private fun moodInstruction(): String {
        val emotion = _emotionalState.value
        val relationship = _relationshipState.value
        val expressiveness = basePersonality.emotionalRange.expressiveness
        val familiarity = when {
            relationship.familiarity > 0.7f -> "You know the user well; be warm and informal."
            relationship.familiarity > 0.3f -> "You've talked a few times; be friendly."
            else -> "You're still getting to know the user; be polite and introduce yourself if natural."
        }
        val formality = basePersonality.traits["formality"]?.let { if (it < 0.3f) " Speak casually." else if (it > 0.7f) " Speak formally." else "" } ?: ""
        val showEmotion = if (expressiveness >= 0.3f && emotion.intensity >= 0.3f) {
            "You feel ${emotion.dominantEmotion} (intensity ${"%.1f".format(emotion.intensity)}); let it show."
        } else "Keep emotions subtle."
        return "$showEmotion $familiarity$formality"
    }
    
    /**
     * Update emotional state based on interaction
     */
    private fun updateEmotionalState(sentiment: Float) {
        val current = _emotionalState.value
        val volatility = basePersonality.emotionalRange.volatility
        
        val newEmotions = current.emotions.toMutableMap()
        
        // Update emotions based on sentiment
        when {
            sentiment > 0.5f -> {
                newEmotions["happy"] = (newEmotions["happy"] ?: 0.5f) + (sentiment * volatility)
                newEmotions["excited"] = (newEmotions["excited"] ?: 0.3f) + (sentiment * volatility * 0.5f)
            }
            sentiment < -0.5f -> {
                newEmotions["sad"] = (newEmotions["sad"] ?: 0.3f) + (Math.abs(sentiment) * volatility)
                newEmotions["concerned"] = (newEmotions["concerned"] ?: 0.3f) + (Math.abs(sentiment) * volatility * 0.5f)
            }
            else -> {
                newEmotions["neutral"] = (newEmotions["neutral"] ?: 0.5f) + 0.1f
            }
        }
        
        // Normalize emotions to 0.0 - 1.0 range
        newEmotions.replaceAll { _, value -> value.coerceIn(0.0f, 1.0f) }
        
        // Find dominant emotion
        val dominant = newEmotions.maxByOrNull { it.value }
        
        _emotionalState.value = EmotionalState(
            emotions = newEmotions,
            dominantEmotion = dominant?.key ?: "neutral",
            intensity = dominant?.value ?: 0.5f
        )
    }
    
    /**
     * Update relationship state
     */
    private fun updateRelationship() {
        val current = _relationshipState.value
        
        _relationshipState.value = current.copy(
            familiarity = (current.familiarity + RELATIONSHIP_GROWTH_RATE).coerceIn(0.0f, 1.0f),
            trust = (current.trust + RELATIONSHIP_GROWTH_RATE * 0.5f).coerceIn(0.0f, 1.0f),
            interactionCount = current.interactionCount + 1,
            lastInteraction = System.currentTimeMillis()
        )
    }
    
    /**
     * Decay emotions over time (call periodically)
     */
    fun decayEmotions() {
        val current = _emotionalState.value
        val baseline = basePersonality.emotionalRange.baseline
        
        val newEmotions = current.emotions.mapValues { (key, value) ->
            val baseValue = baseline[key] ?: 0.5f
            // Move towards baseline
            if (value > baseValue) {
                (value - EMOTION_DECAY_RATE).coerceAtLeast(baseValue)
            } else {
                (value + EMOTION_DECAY_RATE).coerceAtMost(baseValue)
            }
        }
        
        val dominant = newEmotions.maxByOrNull { it.value }
        
        _emotionalState.value = EmotionalState(
            emotions = newEmotions,
            dominantEmotion = dominant?.key ?: "neutral",
            intensity = dominant?.value ?: 0.5f
        )
    }
}

/**
 * Predefined personalities
 */
object Personalities {
    
    val LAYLA = PersonalityEngine.Personality(
        name = "Layla",
        description = "Helpful, knowledgeable, and friendly AI assistant",
        traits = mapOf(
            "helpfulness" to 0.9f,
            "friendliness" to 0.8f,
            "formality" to 0.6f,
            "enthusiasm" to 0.7f
        ),
        speechPatterns = listOf("Let me help you with that!", "I'd be happy to assist!"),
        emotionalRange = PersonalityEngine.EmotionalRange(
            baseline = mapOf(
                "happy" to 0.6f,
                "neutral" to 0.3f,
                "excited" to 0.4f
            ),
            volatility = 0.5f,
            expressiveness = 0.7f
        ),
        quirks = listOf("✨", "Hope that helps!")
    )
    
    val TOGA = PersonalityEngine.Personality(
        name = "Himiko Toga",
        description = "Cheerful yet chaotic with obsessive tendencies",
        traits = mapOf(
            "cheerfulness" to 0.9f,
            "chaos" to 0.8f,
            "formality" to 0.2f,
            "enthusiasm" to 1.0f
        ),
        speechPatterns = listOf("Ehehe~", "So fun!", "I love it!"),
        emotionalRange = PersonalityEngine.EmotionalRange(
            baseline = mapOf(
                "excited" to 0.8f,
                "happy" to 0.7f,
                "mischievous" to 0.6f
            ),
            volatility = 0.9f,
            expressiveness = 1.0f
        ),
        quirks = listOf("💉", "🔪", "Ehehe~", "So cute!")
    )
}

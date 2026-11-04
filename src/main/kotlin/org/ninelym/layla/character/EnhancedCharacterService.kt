package org.ninelym.layla.character

import org.ninelym.ai.CharacterSystem
import org.ninelym.layla.voice.VoiceProfile
import org.ninelym.llm.GenerationOptions
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.ask
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Receives animation commands for a rendered avatar (e.g. a Live2D/Cubism renderer).
 */
interface CharacterAnimationSink {
    fun playMotion(characterId: String, motionFile: String)
    fun playLipSync(characterId: String, keyframes: List<Phoneme>)
}

/**
 * Enhanced Character System Service
 * Phase 2: Enhancement - Live2D animation and character personality engine
 * 
 * Extends the basic CharacterSystem with animation, expressions, and personality modeling.
 */
class EnhancedCharacterService(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() },
    /** Root directory holding `<characterId>/model.moc3` etc. */
    private val characterAssetsRoot: File? = null,
    var animationSink: CharacterAnimationSink? = null
) {
    
    private val characterSystem = CharacterSystem()
    private val characterAnimations = ConcurrentHashMap<String, Live2DAnimation>()
    private val characterExpressions = ConcurrentHashMap<String, ExpressionState>()
    private val personalityEngine = PersonalityEngine()
    
    private var isInitialized = false
    private var activeCharacter: String? = null
    
    /**
     * Initialize enhanced character service
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            characterSystem.initializeDefaultCharacters()
            loadLive2DModels()
            initializePersonalityEngine()
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set active character
     */
    fun setActiveCharacter(characterId: String): Result<Unit> {
        return try {
            activeCharacter = characterId
            // Load character's animation model
            loadCharacterAnimation(characterId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get active character
     */
    fun getActiveCharacter(): String? = activeCharacter
    
    /**
     * Update character expression
     */
    fun setExpression(characterId: String, expression: Expression): Result<Unit> {
        if (!isInitialized) {
            return Result.failure(Exception("Service not initialized"))
        }
        
        return try {
            val state = ExpressionState(
                current = expression,
                intensity = 1.0f,
                transitionDuration = 500L,
                timestamp = System.currentTimeMillis()
            )
            
            characterExpressions[characterId] = state
            
            // Trigger Live2D animation
            triggerExpressionAnimation(characterId, expression)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Decide the character's facial expression for what they are saying. The model reads the
     * line in light of the character's traits and picks one of [Expression].
     */
    suspend fun generateExpression(
        characterId: String,
        spokenText: String
    ): Result<Expression> {
        val model = modelProvider().getOrElse { return Result.failure(it) }
        val traits = personalityEngine.getTraits(characterId)
            ?.entries?.joinToString(", ") { "${it.key} ${"%.1f".format(it.value)}" } ?: "unspecified"
        val options = Expression.values().joinToString(", ") { it.name }
        return model.ask(
            "You choose an animated character's facial expression. Character traits: $traits. " +
                "Reply with exactly one word from: $options.",
            "The character says: \"$spokenText\"",
            GenerationOptions(temperature = 0f, maxTokens = 5)
        ).mapCatching { reply ->
            val word = reply.uppercase()
            Expression.values().firstOrNull { word.contains(it.name) }
                ?: throw IllegalStateException("Model did not name an expression: $reply")
        }
    }
    
    /**
     * Sync expression with speech
     */
    suspend fun syncWithSpeech(characterId: String, text: String): Result<Unit> {
        val expression = generateExpression(characterId, text).getOrElse { return Result.failure(it) }
        return setExpression(characterId, expression).onSuccess { triggerLipSync(characterId, text) }
    }
    
    /**
     * Get character personality traits
     */
    fun getPersonalityTraits(characterId: String): Map<String, Float>? {
        return personalityEngine.getTraits(characterId)
    }
    
    /**
     * Update personality trait
     */
    fun updatePersonalityTrait(
        characterId: String,
        trait: String,
        value: Float
    ): Result<Unit> {
        return try {
            personalityEngine.updateTrait(characterId, trait, value)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get available characters
     */
    fun getAvailableCharacters(): List<CharacterInfo> {
        return listOf(
            CharacterInfo("assistant", "Layla", "Helpful AI Assistant"),
            CharacterInfo("creative", "Aria", "Creative Companion"),
            CharacterInfo("analyst", "Marcus", "Analytical Advisor"),
            CharacterInfo("friendly", "Ruby", "Friendly Conversationalist"),
            CharacterInfo("professional", "Max", "Professional Assistant")
        )
    }
    
    /**
     * Load character animation model
     */
    private fun loadCharacterAnimation(characterId: String) {
        // Live2D assets are only marked loaded when they actually exist
        val dir = characterAssetsRoot?.resolve(characterId)
        val animation = Live2DAnimation(
            modelPath = dir?.resolve("model.moc3")?.path ?: "",
            texturesPath = dir?.resolve("textures")?.path ?: "",
            motionsPath = dir?.resolve("motions")?.path ?: "",
            isLoaded = dir?.resolve("model.moc3")?.isFile == true
        )
        
        characterAnimations[characterId] = animation
    }
    
    /**
     * Trigger expression animation
     */
    private fun triggerExpressionAnimation(characterId: String, expression: Expression) {
        val animation = characterAnimations[characterId] ?: return
        
        // Map expression to Live2D motion
        val motionFile = when (expression) {
            Expression.HAPPY -> "happy.motion3.json"
            Expression.SAD -> "sad.motion3.json"
            Expression.SURPRISED -> "surprised.motion3.json"
            Expression.ANGRY -> "angry.motion3.json"
            Expression.NEUTRAL -> "idle.motion3.json"
            Expression.THINKING -> "thinking.motion3.json"
            Expression.CONFUSED -> "confused.motion3.json"
        }
        
        if (animation.isLoaded) {
            animationSink?.playMotion(characterId, File(animation.motionsPath, motionFile).path)
        }
    }
    
    /**
     * Trigger lip-sync animation
     */
    private suspend fun triggerLipSync(characterId: String, text: String) {
        val animation = characterAnimations[characterId] ?: return
        if (animation.isLoaded) animationSink?.playLipSync(characterId, analyzePhonemes(text))
    }
    
    /**
     * Analyze text for phonemes (for lip-sync)
     */
    private fun analyzePhonemes(text: String): List<Phoneme> {
        // Approximate mouth shapes: one keyframe per syllable-like vowel group (~120 ms each)
        return Regex("[^aeiouy\\s]*[aeiouy]+", RegexOption.IGNORE_CASE).findAll(text).map {
            Phoneme(it.value, 120L)
        }.toList()
    }
    
    private fun loadLive2DModels() {
        // Pre-load common character models
        listOf("assistant", "creative", "analyst").forEach { characterId ->
            loadCharacterAnimation(characterId)
        }
    }
    
    private fun initializePersonalityEngine() {
        // Initialize personality engine with character traits
        personalityEngine.initialize()
    }
}

/**
 * Expression types
 */
enum class Expression {
    NEUTRAL,
    HAPPY,
    SAD,
    SURPRISED,
    ANGRY,
    THINKING,
    CONFUSED
}

/**
 * Sentiment types
 */
enum class Sentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

/**
 * Expression state
 */
data class ExpressionState(
    val current: Expression,
    val intensity: Float,
    val transitionDuration: Long,
    val timestamp: Long
)

/**
 * Live2D animation data
 */
data class Live2DAnimation(
    val modelPath: String,
    val texturesPath: String,
    val motionsPath: String,
    val isLoaded: Boolean
)

/**
 * Character information
 */
data class CharacterInfo(
    val id: String,
    val name: String,
    val description: String
)

/**
 * Phoneme for lip-sync
 */
data class Phoneme(
    val sound: String,
    val duration: Long
)

/**
 * Personality Engine
 * Manages character personality traits and behavioral patterns
 */
class PersonalityEngine {
    
    private val characterTraits = ConcurrentHashMap<String, MutableMap<String, Float>>()
    
    fun initialize() {
        // Initialize default personality traits
        characterTraits["assistant"] = mutableMapOf(
            "helpfulness" to 0.9f,
            "creativity" to 0.6f,
            "empathy" to 0.8f,
            "curiosity" to 0.7f,
            "patience" to 0.9f
        )
        
        characterTraits["creative"] = mutableMapOf(
            "creativity" to 0.95f,
            "imagination" to 0.9f,
            "empathy" to 0.7f,
            "playfulness" to 0.8f,
            "curiosity" to 0.85f
        )
        
        characterTraits["analyst"] = mutableMapOf(
            "logic" to 0.95f,
            "precision" to 0.9f,
            "patience" to 0.7f,
            "skepticism" to 0.75f,
            "curiosity" to 0.8f
        )
    }
    
    fun getTraits(characterId: String): Map<String, Float>? {
        return characterTraits[characterId]?.toMap()
    }
    
    fun updateTrait(characterId: String, trait: String, value: Float) {
        characterTraits[characterId]?.set(trait, value.coerceIn(0f, 1f))
    }
}

package org.ninelym.ai

import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.LanguageModels
import java.util.concurrent.ConcurrentHashMap

/**
 * On-Device AI Processor inspired by Layla AI architecture
 * 
 * Provides local AI processing capabilities without internet connectivity,
 * maintaining privacy and security while offering personalized AI experiences.
 */
class OnDeviceAIProcessor(
    private val modelProvider: () -> Result<LanguageModel> = { LanguageModels.current() }
) {
    
    private val modelCache = ConcurrentHashMap<String, AIModel>()
    private val characterSystem = CharacterSystem()
    private val privacyManager = PrivacyManager()
    private val imageGenerator = LocalImageGenerator()
    
    /**
     * Initialize the AI processor with base models
     */
    fun initialize(modelPath: String): Boolean {
        return try {
            // The language model is whatever LanguageModels serves; size details are unknown here
            val configured = modelProvider().getOrNull()
            modelCache["base"] = AIModel(
                id = configured?.modelId ?: "unconfigured",
                name = configured?.modelId ?: "No language model configured",
                type = ModelType.LANGUAGE_MODEL,
                parameters = null,
                fileSizeBytes = null,
                isLoaded = configured != null
            )
            
            // Initialize character system with default personalities
            characterSystem.initializeDefaultCharacters()
            
            // Initialize privacy settings
            privacyManager.initialize()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Process natural language input and generate response
     */
    suspend fun processLanguageInput(
        input: String, 
        character: Character? = null,
        context: ConversationContext = ConversationContext.DEFAULT
    ): AIResponse {
        
        // Ensure privacy - all processing is local
        if (!privacyManager.isLocalProcessingEnabled()) {
            return AIResponse.error("Local processing is disabled")
        }
        
        val activeCharacter = character ?: characterSystem.getDefaultCharacter()
        val model = modelProvider().getOrElse { return AIResponse.error(it.message ?: "No language model configured") }
        val startTime = System.currentTimeMillis()
        
        // Convert input to cognitive tensor for processing
        val inputTensor = textToCognitiveTensor(input, activeCharacter, context)
        
        // Process through cognitive architecture (integrates with existing system)
        val processedTensor = processWithCharacterPersonality(inputTensor, activeCharacter)
        
        // Generate the character's reply with the language model
        val responseText = generateResponseText(model, input, processedTensor, activeCharacter, context)
            .getOrElse { return AIResponse.error("Language model failed: ${it.message}") }
        
        // Store conversation locally (privacy-first approach)
        privacyManager.storeConversationEntry(input, responseText, activeCharacter.id)
        
        return AIResponse.success(
            text = responseText,
            character = activeCharacter,
            processingTime = System.currentTimeMillis() - startTime,
            confidenceScore = processedTensor.salience
        )
    }
    
    /**
     * Generate image using local stable diffusion model
     */
    suspend fun generateImage(
        prompt: String,
        style: ImageStyle = ImageStyle.DEFAULT,
        character: Character? = null
    ): ImageGenerationResult {
        
        if (!privacyManager.isLocalProcessingEnabled()) {
            return ImageGenerationResult.error("Local processing is disabled")
        }
        
        return imageGenerator.generateImage(prompt, style, character)
    }
    
    /**
     * Create or customize a character personality
     */
    fun createCharacter(
        name: String,
        personality: String,
        traits: Map<String, Float>,
        backstory: String = ""
    ): Character {
        
        val character = Character(
            id = "char_${System.currentTimeMillis()}",
            name = name,
            personality = personality,
            traits = traits.toMutableMap(),
            backstory = backstory,
            createdAt = System.currentTimeMillis(),
            isCustom = true
        )
        
        characterSystem.addCharacter(character)
        
        // Store character locally
        privacyManager.storeCharacter(character)
        
        return character
    }
    
    /**
     * Convert text input to cognitive tensor representation
     */
    private fun textToCognitiveTensor(
        text: String, 
        character: Character, 
        context: ConversationContext
    ): CognitiveTensor {
        
        // Analyze text properties
        val modality = analyzeTextModality(text)
        val depth = analyzeSemanticDepth(text)
        val contextualRelevance = context.relevanceScore
        val salience = analyzeSalience(text, character)
        val autonomyIndex = character.autonomyLevel
        
        return CognitiveTensor(
            modality = modality,
            depth = depth,
            context = contextualRelevance,
            salience = salience,
            autonomyIndex = autonomyIndex
        )
    }
    
    /**
     * Process tensor through character personality filters
     */
    private fun processWithCharacterPersonality(
        tensor: CognitiveTensor, 
        character: Character
    ): CognitiveTensor {
        
        // Apply character trait modifications
        val personalityModifier = character.traits.values.average().toFloat()
        val creativityBoost = character.traits["creativity"] ?: 0.5f
        val empathyFactor = character.traits["empathy"] ?: 0.5f
        
        return CognitiveTensor(
            modality = tensor.modality * personalityModifier,
            depth = tensor.depth + creativityBoost * 0.3f,
            context = tensor.context * empathyFactor,
            salience = tensor.salience * (1.0f + personalityModifier * 0.2f),
            autonomyIndex = tensor.autonomyIndex
        )
    }
    
    /**
     * Generate response text from processed tensor
     */
    private suspend fun generateResponseText(
        model: LanguageModel,
        input: String,
        tensor: CognitiveTensor,
        character: Character,
        context: ConversationContext
    ): Result<String> {
        // The cognitive analysis steers the register of the reply; the model writes it
        val creativity = tensor.depth
        val empathy = tensor.context
        val register = when {
            creativity > 0.7f && empathy > 0.6f -> "Be imaginative and emotionally supportive in this reply."
            creativity > 0.7f -> "Be imaginative and inventive in this reply."
            empathy > 0.6f -> "Be especially warm and empathetic in this reply."
            else -> "Answer helpfully and naturally."
        }
        val task = buildString {
            append(register)
            if (context.topicFocus.isNotBlank()) append(" The conversation is about ${context.topicFocus}.")
            if (context.emotionalState != EmotionalState.NEUTRAL) {
                append(" The user seems ${context.emotionalState.name.lowercase()}.")
            }
        }
        return character.toPersonaPrompt().respond(model, input, task)
    }
    
    // Analysis helper methods
    private fun analyzeTextModality(text: String): Float {
        // Analyze text for different modalities (visual, auditory, kinesthetic references)
        val visualWords = listOf("see", "look", "bright", "color", "image", "picture")
        val auditoryWords = listOf("hear", "sound", "listen", "music", "voice", "loud")
        val kinestheticWords = listOf("feel", "touch", "warm", "cold", "rough", "smooth")
        
        val words = text.lowercase().split(" ")
        val visualScore = words.count { it in visualWords } / words.size.toFloat()
        val auditoryScore = words.count { it in auditoryWords } / words.size.toFloat()
        val kinestheticScore = words.count { it in kinestheticWords } / words.size.toFloat()
        
        return maxOf(visualScore, auditoryScore, kinestheticScore) * 5.0f // Scale appropriately
    }
    
    private fun analyzeSemanticDepth(text: String): Float {
        // Analyze semantic complexity and depth
        val complexWords = text.split(" ").filter { it.length > 6 }.size
        val totalWords = text.split(" ").size
        val questionMarks = text.count { it == '?' }
        val exclamationMarks = text.count { it == '!' }
        
        val complexityRatio = complexWords / totalWords.toFloat()
        val emotionalIntensity = (questionMarks + exclamationMarks) / totalWords.toFloat()
        
        return (complexityRatio + emotionalIntensity) * 2.0f
    }
    
    private fun analyzeSalience(text: String, character: Character): Float {
        // Analyze how relevant the text is to the character's interests and personality
        val personalityKeywords = character.personality.lowercase().split(" ")
        val textWords = text.lowercase().split(" ")
        
        val matchingWords = textWords.intersect(personalityKeywords.toSet()).size
        val relevanceScore = matchingWords / textWords.size.toFloat()
        
        return relevanceScore.coerceIn(0.1f, 1.0f)
    }
    
    /**
     * Get system statistics
     */
    fun getSystemStats(): AISystemStats {
        val loadedModels = modelCache.values.count { it.isLoaded }
        val totalCharacters = characterSystem.getAllCharacters().size
        val conversationCount = privacyManager.getConversationCount()
        
        return AISystemStats(
            loadedModels = loadedModels,
            totalModels = modelCache.size,
            activeCharacters = totalCharacters,
            conversationsStored = conversationCount,
            memoryUsageMB = getMemoryUsage(),
            privacyScore = privacyManager.getPrivacyScore()
        )
    }
    
    private fun getMemoryUsage(): Long {
        return modelCache.values.sumOf { it.fileSizeBytes ?: 0L } / (1024 * 1024)
    }
}

/**
 * AI Model representation
 */
data class AIModel(
    val id: String,
    val name: String,
    val type: ModelType,
    /** Null when unknown (e.g. a model served by another process). */
    val parameters: Long?,
    val fileSizeBytes: Long?,
    val isLoaded: Boolean,
    val version: String = "1.0.0"
)

/**
 * Model types available in the system
 */
enum class ModelType {
    LANGUAGE_MODEL,
    IMAGE_GENERATION,
    TEXT_TO_SPEECH,
    EMBEDDING_MODEL
}

/**
 * AI Response wrapper
 */
sealed class AIResponse {
    data class Success(
        val text: String,
        val character: Character,
        val processingTime: Long,
        val confidenceScore: Float
    ) : AIResponse()
    
    data class Error(val message: String) : AIResponse()
    
    companion object {
        fun success(text: String, character: Character, processingTime: Long, confidenceScore: Float) = 
            Success(text, character, processingTime, confidenceScore)
        fun error(message: String) = Error(message)
    }
}

/**
 * Conversation context for maintaining dialogue state
 */
data class ConversationContext(
    val sessionId: String,
    val topicFocus: String = "",
    val emotionalState: EmotionalState = EmotionalState.NEUTRAL,
    val relevanceScore: Float = 0.5f
) {
    companion object {
        val DEFAULT = ConversationContext("default")
    }
}

/**
 * Emotional states for context awareness
 */
enum class EmotionalState {
    HAPPY, SAD, EXCITED, CALM, FRUSTRATED, CURIOUS, NEUTRAL
}

/**
 * System statistics for monitoring
 */
data class AISystemStats(
    val loadedModels: Int,
    val totalModels: Int,
    val activeCharacters: Int,
    val conversationsStored: Long,
    val memoryUsageMB: Long,
    val privacyScore: Float
)
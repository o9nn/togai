package org.ninelym.ai

import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.OpenAICompatibleModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Image generator backed by a served Stable Diffusion model (an OpenAI-compatible
 * `/images/generations` endpoint such as a local stable-diffusion.cpp / LocalAI server).
 * [imageModels] maps this generator's model slots ("base", "anime", "artistic") to the model
 * ids the server serves.
 */
class LocalImageGenerator(
    private val endpointProvider: () -> Result<OpenAICompatibleModel> = { LanguageModels.currentEndpoint() },
    private val imageModels: Map<String, String> = mapOf("base" to DEFAULT_IMAGE_MODEL)
) {
    
    private val generationCache = ConcurrentHashMap<String, GeneratedImage>()
    private val modelCache = ConcurrentHashMap<String, StableDiffusionModel>()
    private val processingQueue = mutableListOf<ImageGenerationRequest>()
    private var isInitialized = false
    
    // Configuration
    private var maxCacheSize = 50
    private var defaultImageSize = ImageSize.MEDIUM
    private var qualityLevel = QualityLevel.BALANCED
    
    /**
     * Initialize the image generator with mobile-optimized models
     */
    fun initialize(): Boolean {
        return try {
            // Load mobile-optimized Stable Diffusion models
            loadMobileOptimizedModels()
            isInitialized = true
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate image from text prompt using local Stable Diffusion model
     */
    suspend fun generateImage(
        prompt: String,
        style: ImageStyle = ImageStyle.DEFAULT,
        character: Character? = null
    ): ImageGenerationResult {
        
        if (!isInitialized) {
            return ImageGenerationResult.error("Image generator not initialized")
        }
        
        // Check cache first
        val cacheKey = generateCacheKey(prompt, style, character?.id)
        generationCache[cacheKey]?.let { cached ->
            return ImageGenerationResult.success(
                image = cached,
                fromCache = true,
                processingTimeMs = 0,
                prompt = prompt
            )
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            
            // Select appropriate model based on style and character preferences
            val model = selectOptimalModel(style, character)
            
            // Process prompt with character personality if available
            val enhancedPrompt = if (character != null) {
                enhancePromptWithCharacter(prompt, character)
            } else {
                enhancePrompt(prompt, style)
            }
            
            // Generate image using mobile-optimized inference
            val generatedImage = runMobileInference(enhancedPrompt, style, model)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            generationTimes += processingTime
            modelCache.computeIfPresent(slotOf(model)) { _, m -> m.copy(isLoaded = true) }
            
            // Cache the result
            cacheGeneratedImage(cacheKey, generatedImage)
            
            ImageGenerationResult.success(
                image = generatedImage,
                fromCache = false,
                processingTimeMs = processingTime,
                prompt = enhancedPrompt
            )
            
        } catch (e: Exception) {
            ImageGenerationResult.error("Image generation failed: ${e.message}")
        }
    }
    
    /**
     * Generate multiple variations of an image
     */
    suspend fun generateVariations(
        basePrompt: String,
        variationCount: Int = 3,
        style: ImageStyle = ImageStyle.DEFAULT
    ): List<ImageGenerationResult> {
        
        val results = mutableListOf<ImageGenerationResult>()
        
        repeat(variationCount) { index ->
            val variationPrompt = createPromptVariation(basePrompt, index)
            val result = generateImage(variationPrompt, style)
            results.add(result)
        }
        
        return results
    }
    
    /**
     * Process image generation with different quality levels
     */
    suspend fun generateWithQuality(
        prompt: String,
        quality: QualityLevel,
        style: ImageStyle = ImageStyle.DEFAULT
    ): ImageGenerationResult {
        
        val originalQuality = qualityLevel
        qualityLevel = quality
        
        return try {
            generateImage(prompt, style)
        } finally {
            qualityLevel = originalQuality
        }
    }
    
    /**
     * Get generation statistics
     */
    fun getGenerationStats(): ImageGenerationStats {
        val cachedImages = generationCache.size
        val totalModels = modelCache.size
        val loadedModels = modelCache.values.count { it.isLoaded }
        
        // Calculate cache hit rate (simplified)
        val totalRequests = cachedImages + processingQueue.size
        val cacheHitRate = if (totalRequests > 0) {
            cachedImages.toFloat() / totalRequests.toFloat()
        } else 0f
        
        return ImageGenerationStats(
            cachedImages = cachedImages,
            totalModels = totalModels,
            loadedModels = loadedModels,
            cacheHitRate = cacheHitRate,
            averageGenerationTimeMs = calculateAverageGenerationTime(),
            memoryUsageMB = calculateMemoryUsage()
        )
    }
    
    /**
     * Clear image cache
     */
    fun clearCache(): Int {
        val count = generationCache.size
        generationCache.clear()
        return count
    }
    
    // Private implementation methods
    
    private val generationTimes = java.util.concurrent.ConcurrentLinkedQueue<Long>()
    
    private fun loadMobileOptimizedModels() {
        // One registry entry per configured served model; "loaded" once the server has produced an image with it
        val slotStyles = mapOf(
            "base" to listOf(ImageStyle.DEFAULT, ImageStyle.REALISTIC, ImageStyle.LANDSCAPE),
            "anime" to listOf(ImageStyle.ANIME, ImageStyle.CHARACTER_FOCUSED, ImageStyle.FANTASY),
            "artistic" to listOf(ImageStyle.ARTISTIC, ImageStyle.ABSTRACT, ImageStyle.VINTAGE)
        )
        require("base" in imageModels) { "imageModels must define a \"base\" model" }
        imageModels.forEach { (slot, modelId) ->
            modelCache[slot] = StableDiffusionModel(
                id = modelId,
                name = modelId,
                version = "served",
                fileSizeBytes = null,
                supportedStyles = slotStyles[slot] ?: ImageStyle.values().toList(),
                isLoaded = false,
                optimizedForMobile = false
            )
        }
    }
    
    private fun slotOf(model: StableDiffusionModel): String =
        modelCache.entries.first { it.value.id == model.id }.key
    
    private fun selectOptimalModel(style: ImageStyle, character: Character?): StableDiffusionModel {
        // Select model based on style preferences
        val preferredModel = when (style) {
            ImageStyle.ANIME, ImageStyle.CHARACTER_FOCUSED -> "anime"
            ImageStyle.ARTISTIC, ImageStyle.ABSTRACT -> "artistic"
            else -> "base"
        }
        
        // Consider character preferences if available
        character?.let { char ->
            val creativityLevel = char.traits["creativity"] ?: 0.5f
            if (creativityLevel > 0.8f && style != ImageStyle.REALISTIC) {
                return modelCache["artistic"] ?: modelCache["base"]!!
            }
        }
        
        return modelCache[preferredModel] ?: modelCache["base"]!!
    }
    
    private fun enhancePromptWithCharacter(prompt: String, character: Character): String {
        val basePrompt = enhancePrompt(prompt, ImageStyle.DEFAULT)
        
        // Add character-specific style modifiers
        val personalityModifiers = mutableListOf<String>()
        
        character.traits["creativity"]?.let { creativity ->
            if (creativity > 0.7f) personalityModifiers.add("creative")
            if (creativity > 0.8f) personalityModifiers.add("imaginative")
        }
        
        character.traits["empathy"]?.let { empathy ->
            if (empathy > 0.7f) personalityModifiers.add("emotional")
            if (empathy > 0.8f) personalityModifiers.add("heartfelt")
        }
        
        // Extract style preferences from character personality
        val personalityWords = character.personality.lowercase().split(" ")
        val styleKeywords = personalityWords.intersect(
            listOf("artistic", "creative", "colorful", "dreamy", "vibrant", "elegant", "modern", "classic")
        )
        
        val enhancedPrompt = StringBuilder(basePrompt)
        
        if (personalityModifiers.isNotEmpty()) {
            enhancedPrompt.append(", ").append(personalityModifiers.joinToString(", "))
        }
        
        if (styleKeywords.isNotEmpty()) {
            enhancedPrompt.append(", ").append(styleKeywords.joinToString(", ")).append(" style")
        }
        
        return enhancedPrompt.toString()
    }
    
    private fun enhancePrompt(prompt: String, style: ImageStyle): String {
        val stylePrompts = when (style) {
            ImageStyle.REALISTIC -> "$prompt, photorealistic, high detail, professional photography"
            ImageStyle.ARTISTIC -> "$prompt, artistic, painted style, vibrant colors, creative composition"
            ImageStyle.ANIME -> "$prompt, anime style, manga art, vibrant colors, detailed character design"
            ImageStyle.ABSTRACT -> "$prompt, abstract art, conceptual, flowing forms, creative interpretation"
            ImageStyle.VINTAGE -> "$prompt, vintage style, retro aesthetic, aged look, classic composition"
            ImageStyle.FANTASY -> "$prompt, fantasy art, magical, ethereal, detailed fantasy illustration"
            ImageStyle.CHARACTER_FOCUSED -> "$prompt, character portrait, detailed facial features, expressive"
            ImageStyle.LANDSCAPE -> "$prompt, landscape photography, wide vista, natural lighting, scenic"
            else -> "$prompt, high quality, detailed"
        }
        
        // Add quality modifiers based on current quality level
        val qualityModifier = when (qualityLevel) {
            QualityLevel.DRAFT -> "quick sketch"
            QualityLevel.BALANCED -> "good quality"
            QualityLevel.HIGH -> "high quality, detailed"
            QualityLevel.ULTRA -> "ultra high quality, masterpiece, highly detailed"
        }
        
        return "$stylePrompts, $qualityModifier"
    }
    
    private suspend fun runMobileInference(
        prompt: String,
        style: ImageStyle,
        model: StableDiffusionModel
    ): GeneratedImage {
        
        val imageSize = when (defaultImageSize) {
            ImageSize.SMALL -> Pair(256, 256)
            ImageSize.MEDIUM -> Pair(512, 512)
            ImageSize.LARGE -> Pair(768, 768)
            ImageSize.EXTRA_LARGE -> Pair(1024, 1024)
        }
        
        // Steps/guidance are recorded for reference; the server applies its own sampler settings
        val inferenceParams = MobileInferenceParams(
            width = imageSize.first,
            height = imageSize.second,
            steps = when (qualityLevel) {
                QualityLevel.DRAFT -> 10
                QualityLevel.BALANCED -> 20
                QualityLevel.HIGH -> 35
                QualityLevel.ULTRA -> 50
            },
            guidanceScale = 7.5f,
            seed = System.currentTimeMillis(),
            enableMemoryOptimizations = true,
            useMobileOptimizedScheduler = true
        )
        
        // The served model renders the image (PNG bytes)
        val imageData = endpointProvider().getOrThrow()
            .generateImage(prompt, "${inferenceParams.width}x${inferenceParams.height}", model.id)
            .getOrThrow()
        
        return GeneratedImage(
            id = generateImageId(prompt, style),
            prompt = prompt,
            style = style,
            modelUsed = model.id,
            imageData = imageData,
            width = inferenceParams.width,
            height = inferenceParams.height,
            generatedAt = System.currentTimeMillis(),
            inferenceParams = inferenceParams
        )
    }
    
    private fun createPromptVariation(basePrompt: String, variationIndex: Int): String {
        val variations = listOf(
            "$basePrompt, variation A",
            "$basePrompt, alternative style",
            "$basePrompt, different perspective",
            "$basePrompt, enhanced details",
            "$basePrompt, creative interpretation"
        )
        
        return variations.getOrNull(variationIndex) ?: "$basePrompt, variation ${variationIndex + 1}"
    }
    
    private fun generateCacheKey(prompt: String, style: ImageStyle, characterId: String?): String {
        val data = "${prompt}${style}${characterId ?: ""}".toByteArray()
        val digest = MessageDigest.getInstance("MD5").digest(data)
        return digest.fold("") { str, byte -> str + "%02x".format(byte) }
    }
    
    private fun generateImageId(prompt: String, style: ImageStyle): String {
        val data = "${prompt}${style}${System.currentTimeMillis()}".toByteArray()
        val digest = MessageDigest.getInstance("SHA-1").digest(data)
        return digest.fold("") { str, byte -> str + "%02x".format(byte) }.take(16)
    }
    
    private fun cacheGeneratedImage(cacheKey: String, image: GeneratedImage) {
        // Remove oldest images if cache is full
        while (generationCache.size >= maxCacheSize) {
            val oldestEntry = generationCache.entries.minByOrNull { it.value.generatedAt }
            oldestEntry?.let { generationCache.remove(it.key) }
        }
        
        generationCache[cacheKey] = image
    }
    
    private fun calculateAverageGenerationTime(): Long =
        if (generationTimes.isEmpty()) 0L else generationTimes.sum() / generationTimes.size
    
    private fun calculateMemoryUsage(): Long {
        val imageMemory = generationCache.values.sumOf { it.imageData.size }.toLong()
        val modelMemory = modelCache.values.filter { it.isLoaded }.sumOf { it.fileSizeBytes ?: 0L }
        return (imageMemory + modelMemory) / (1024 * 1024) // Convert to MB
    }
    
    companion object {
        const val DEFAULT_IMAGE_MODEL = "stable-diffusion"
    }
}

// Data classes for image generation

data class StableDiffusionModel(
    val id: String,
    val name: String,
    val version: String,
    /** Null for served models, whose size isn't known locally. */
    val fileSizeBytes: Long?,
    val supportedStyles: List<ImageStyle>,
    val isLoaded: Boolean,
    val optimizedForMobile: Boolean
)

data class GeneratedImage(
    val id: String,
    val prompt: String,
    val style: ImageStyle,
    val modelUsed: String,
    val imageData: ByteArray,
    val width: Int,
    val height: Int,
    val generatedAt: Long,
    val inferenceParams: MobileInferenceParams
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeneratedImage

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class MobileInferenceParams(
    val width: Int,
    val height: Int,
    val steps: Int,
    val guidanceScale: Float,
    val seed: Long,
    val enableMemoryOptimizations: Boolean,
    val useMobileOptimizedScheduler: Boolean
)

data class ImageGenerationRequest(
    val prompt: String,
    val style: ImageStyle,
    val characterId: String?,
    val requestTime: Long
)

data class ImageGenerationStats(
    val cachedImages: Int,
    val totalModels: Int,
    val loadedModels: Int,
    val cacheHitRate: Float,
    val averageGenerationTimeMs: Long,
    val memoryUsageMB: Long
)

enum class ImageStyle {
    DEFAULT,
    REALISTIC,
    ARTISTIC,
    ANIME,
    ABSTRACT,
    VINTAGE,
    FANTASY,
    CHARACTER_FOCUSED,
    LANDSCAPE
}

enum class ImageSize {
    SMALL,    // 256x256
    MEDIUM,   // 512x512
    LARGE,    // 768x768
    EXTRA_LARGE // 1024x1024
}

enum class QualityLevel {
    DRAFT,    // Fast, lower quality
    BALANCED, // Good balance of speed and quality
    HIGH,     // Higher quality, slower
    ULTRA     // Highest quality, slowest
}

sealed class ImageGenerationResult {
    data class Success(
        val image: GeneratedImage,
        val fromCache: Boolean,
        val processingTimeMs: Long,
        val prompt: String
    ) : ImageGenerationResult()
    
    data class Error(val message: String) : ImageGenerationResult()
    
    companion object {
        fun success(image: GeneratedImage, fromCache: Boolean, processingTimeMs: Long, prompt: String) =
            Success(image, fromCache, processingTimeMs, prompt)
        fun error(message: String) = Error(message)
    }
}
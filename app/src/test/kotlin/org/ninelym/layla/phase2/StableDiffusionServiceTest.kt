package org.ninelym.layla.phase2

import org.ninelym.layla.sd.StableDiffusionService
import org.ninelym.ai.ImageStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.ModelEndpointConfig

/**
 * Test suite for Stable Diffusion Service
 * Phase 2: Enhancement - Image Generation Tests
 */
class StableDiffusionServiceTest {
    
    private lateinit var service: StableDiffusionService
    
    private val mediaUrl = System.getenv("TOGAI_MEDIA_BASE_URL")
    
    /** Images are rendered by a served diffusion model at TOGAI_MEDIA_BASE_URL; skipped without one. */
    @Before
    fun setup() {
        assumeTrue("TOGAI_MEDIA_BASE_URL not set", mediaUrl != null)
        LanguageModels.configureMedia(ModelEndpointConfig(mediaUrl!!, "stable-diffusion", timeoutSeconds = 600))
        service = StableDiffusionService()
    }
    
    @After
    fun restoreModel() {
        ModelEndpointConfig.fromEnvironment()?.let { LanguageModels.configure(it) }
    }
    
    @Test
    fun `test service initialization`(): Unit = runBlocking {
        val result = service.initialize()
        assertTrue("Service should initialize successfully", result.isSuccess)
    }
    
    @Test
    fun `test image generation with default style`(): Unit = runBlocking {
        service.initialize()
        
        val result = service.generateImage(
            prompt = "A beautiful sunset over mountains"
        )
        
        assertTrue("Image generation should succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val image = result.getOrThrow()
        assertEquals("Prompt should match", "A beautiful sunset over mountains", image.prompt)
        val png = java.io.File(image.imagePath).readBytes()
        assertEquals("Rendered file should be a PNG", "PNG", String(png.copyOfRange(1, 4)))
    }
    
    @Test
    fun `test image generation with custom style`(): Unit = runBlocking {
        service.initialize()
        
        val result = service.generateImage(
            prompt = "A futuristic city",
            style = ImageStyle.FANTASY
        )
        
        assertTrue("Image generation should succeed", result.isSuccess)
        val image = result.getOrNull()
        assertEquals("Style should match", ImageStyle.FANTASY, image?.style)
    }
    
    @Test
    fun `test generation with parameters`(): Unit = runBlocking {
        service.initialize()
        
        val result = service.generateImage(
            prompt = "Abstract art",
            negativePrompt = "realistic, photographic",
            steps = 30,
            guidanceScale = 8.5f,
            seed = 12345L
        )
        
        assertTrue("Image generation with parameters should succeed", result.isSuccess)
    }
    
    @Test
    fun `test generation history tracking`(): Unit = runBlocking {
        service.initialize()
        
        // Generate multiple images
        service.generateImage("A lighthouse at night").getOrThrow()
        service.generateImage("A bowl of oranges").getOrThrow()
        
        val history = service.getHistory()
        assertEquals("History should contain 2 entries", 2, history.size)
    }
    
    @Test
    fun `test clear history`(): Unit = runBlocking {
        service.initialize()
        
        service.generateImage("Test image")
        val historyBefore = service.getHistory()
        assertTrue("History should have entries", historyBefore.isNotEmpty())
        
        service.clearHistory()
        val historyAfter = service.getHistory()
        assertTrue("History should be empty after clear", historyAfter.isEmpty())
    }
    
    @Test
    fun `test no active tasks remain after generation`(): Unit = runBlocking {
        service.initialize()
        service.generateImage("Test").getOrThrow()
        assertTrue(service.getActiveTasks().isEmpty())
    }
    
    @Test
    fun `test model loading`(): Unit = runBlocking {
        service.initialize()
        
        val result = service.loadModel("stable-diffusion")
        assertTrue("Model loading should succeed", result.isSuccess)
        
        val currentModel = service.getCurrentModel()
        assertEquals("Model name should match", "stable-diffusion", currentModel?.name)
    }
    
    @Test
    fun `test generation without initialization fails`(): Unit = runBlocking {
        val result = service.generateImage("Test prompt")
        assertTrue("Generation without initialization should fail", result.isFailure)
    }
    
    @Test
    fun `test task cancellation`(): Unit = runBlocking {
        service.initialize()
        
        val result = service.cancelTask("test_task_123")
        assertTrue("Cancelling an unknown task is a no-op", result.isSuccess)
    }
}

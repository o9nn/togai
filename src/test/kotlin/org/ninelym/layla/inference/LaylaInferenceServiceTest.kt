package org.ninelym.layla.inference

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assume.assumeTrue
import org.ninelym.llm.ModelEndpointConfig
import org.ninelym.llm.OpenAICompatibleModel
import kotlin.test.*

/**
 * Tests for LaylaInferenceService. Replies come from a real served model
 * (TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL); model-dependent tests are skipped without one.
 */
class LaylaInferenceServiceTest {
    
    private lateinit var service: LaylaInferenceService
    private val config = ModelEndpointConfig.fromEnvironment()
    
    @Before
    fun setup() {
        service = config?.let { c -> LaylaInferenceService({ Result.success(OpenAICompatibleModel(c)) }) }
            ?: LaylaInferenceService({ Result.failure(org.ninelym.llm.ModelNotConfiguredException()) })
    }
    
    private fun requireModel() = assumeTrue("no live model configured", config != null)
    
    @After
    fun teardown() {
        service.shutdown()
    }
    
    @Test
    fun `test service initialization`() = runBlocking {
        requireModel()
        val result = service.initialize()
        assertTrue(result.isSuccess, "Service should initialize successfully")
    }
    
    @Test
    fun `test model state transitions`() = runBlocking {
        requireModel()
        service.initialize()
        
        // Initial state should be Ready after initialization
        val state = service.modelState.value
        assertTrue(state is ModelState.Ready || state is ModelState.Unloaded)
    }
    
    @Test
    fun `test conversation history management`() = runBlocking {
        requireModel()
        service.initialize()
        
        // History should be empty initially
        assertTrue(service.getHistory().isEmpty())
        
        // Perform inference
        service.infer("Test prompt")
        
        // History should contain user prompt and assistant response
        val history = service.getHistory()
        assertTrue(history.size >= 2, "History should contain at least 2 turns")
        assertEquals("user", history.first().role)
    }
    
    @Test
    fun `test clear history`() = runBlocking {
        requireModel()
        service.initialize()
        service.infer("Test prompt")
        
        service.clearHistory()
        
        assertTrue(service.getHistory().isEmpty())
    }
    
    @Test
    fun `test inference without a model fails instead of answering`() = runBlocking {
        val unconfigured = LaylaInferenceService({ Result.failure(org.ninelym.llm.ModelNotConfiguredException()) })
        val result = unconfigured.infer("Test prompt")
        assertTrue(result.isFailure, "Inference must fail when no model is available")
        assertTrue(unconfigured.getHistory().isEmpty())
    }
}

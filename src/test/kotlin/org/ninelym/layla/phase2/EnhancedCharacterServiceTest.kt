package org.ninelym.layla.phase2

import org.ninelym.layla.character.EnhancedCharacterService
import org.ninelym.layla.character.Expression
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.ninelym.llm.ModelEndpointConfig
import org.ninelym.llm.OpenAICompatibleModel

/**
 * Test suite for Enhanced Character Service
 * Phase 2: Enhancement - Character System Tests
 */
class EnhancedCharacterServiceTest {
    
    private lateinit var service: EnhancedCharacterService
    private val config = ModelEndpointConfig.fromEnvironment()
    
    @Before
    fun setup() {
        // Expressions are chosen by the served model (TOGAI_LLM_*); model-dependent tests skip without one
        service = EnhancedCharacterService(modelProvider = {
            config?.let { Result.success(OpenAICompatibleModel(it)) }
                ?: Result.failure(org.ninelym.llm.ModelNotConfiguredException())
        })
    }
    
    private fun requireModel() = assumeTrue("no live model configured", config != null)
    
    @Test
    fun `test service initialization`() = runBlocking {
        val result = service.initialize()
        assertTrue("Service should initialize successfully", result.isSuccess)
    }
    
    @Test
    fun `test set active character`() = runBlocking {
        service.initialize()
        
        val result = service.setActiveCharacter("creative")
        assertTrue("Setting active character should succeed", result.isSuccess)
        
        val activeCharacter = service.getActiveCharacter()
        assertEquals("Active character should be 'creative'", "creative", activeCharacter)
    }
    
    @Test
    fun `test set character expression`() = runBlocking {
        service.initialize()
        service.setActiveCharacter("assistant")
        
        val result = service.setExpression("assistant", Expression.HAPPY)
        assertTrue("Setting expression should succeed", result.isSuccess)
    }
    
    @Test
    fun `test model chooses expression for a line`() = runBlocking {
        requireModel()
        service.initialize()
        
        val expression = service.generateExpression(
            characterId = "assistant",
            spokenText = "Great job on completing the task!"
        )
        
        assertTrue("The model should name one of the expressions", expression.isSuccess)
    }
    
    @Test
    fun `test sync expression with speech`() = runBlocking {
        requireModel()
        service.initialize()
        service.setActiveCharacter("creative")
        
        val result = service.syncWithSpeech(
            characterId = "creative",
            text = "Hello! How are you today?"
        )
        
        assertTrue("Syncing with speech should succeed", result.isSuccess)
    }
    
    @Test
    fun `test get personality traits`() = runBlocking {
        service.initialize()
        
        val traits = service.getPersonalityTraits("assistant")
        assertNotNull("Traits should not be null", traits)
        assertTrue("Should have helpfulness trait", traits?.containsKey("helpfulness") == true)
        assertTrue("Should have empathy trait", traits?.containsKey("empathy") == true)
    }
    
    @Test
    fun `test update personality trait`() = runBlocking {
        service.initialize()
        
        val result = service.updatePersonalityTrait(
            characterId = "assistant",
            trait = "helpfulness",
            value = 0.95f
        )
        
        assertTrue("Updating trait should succeed", result.isSuccess)
        
        val traits = service.getPersonalityTraits("assistant")
        assertEquals("Trait value should be updated", 0.95f, traits?.get("helpfulness"))
    }
    
    @Test
    fun `test get available characters`() = runBlocking {
        service.initialize()
        
        val characters = service.getAvailableCharacters()
        assertTrue("Should have multiple characters", characters.size >= 5)
        
        val characterIds = characters.map { it.id }
        assertTrue("Should have assistant", characterIds.contains("assistant"))
        assertTrue("Should have creative", characterIds.contains("creative"))
        assertTrue("Should have analyst", characterIds.contains("analyst"))
    }
    
    @Test
    fun `test expression fails without a model instead of guessing`() = runBlocking {
        val offline = EnhancedCharacterService(modelProvider = { Result.failure(org.ninelym.llm.ModelNotConfiguredException()) })
        offline.initialize()
        assertTrue(offline.generateExpression("creative", "This is wonderful!").isFailure)
    }
    
    @Test
    fun `test character-specific personality traits`() = runBlocking {
        service.initialize()
        
        val creativeTraits = service.getPersonalityTraits("creative")
        val analystTraits = service.getPersonalityTraits("analyst")
        
        // Creative should have high creativity
        assertTrue(
            "Creative character should have high creativity",
            (creativeTraits?.get("creativity") ?: 0f) > 0.9f
        )
        
        // Analyst should have high logic
        assertTrue(
            "Analyst character should have high logic",
            (analystTraits?.get("logic") ?: 0f) > 0.9f
        )
    }
    
    @Test
    fun `test expression without initialization fails`() = runBlocking {
        val result = service.setExpression("assistant", Expression.HAPPY)
        assertTrue("Setting expression without initialization should fail", result.isFailure)
    }
}

package org.ninelym.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.ninelym.cognitive.CognitiveEngine
import org.ninelym.llm.LanguageModels
import org.ninelym.llm.ModelEndpointConfig

/**
 * AI integration with the cognitive system. Text comes from the served chat model
 * (TOGAI_LLM_*), images from the served diffusion model (TOGAI_MEDIA_BASE_URL); tests that need
 * a model are skipped when it isn't configured.
 */
class AIIntegrationTest {

    private val chat = ModelEndpointConfig.fromEnvironment()
    private val mediaUrl = System.getenv("TOGAI_MEDIA_BASE_URL")

    /** Tests below point the process-wide model elsewhere; restore the configured chat model. */
    @After
    fun restoreModel() {
        chat?.let { LanguageModels.configure(it) }
    }

    private fun engineWithChatModel(): CognitiveEngine {
        assumeTrue("no live chat model configured", chat != null)
        LanguageModels.configure(chat!!)
        return CognitiveEngine().also { assertTrue(it.initializeAI()) }
    }

    private fun engineWithImageModel(): CognitiveEngine {
        assumeTrue("TOGAI_MEDIA_BASE_URL not set", mediaUrl != null)
        LanguageModels.configure(ModelEndpointConfig(mediaUrl!!, "stable-diffusion", timeoutSeconds = 600))
        return CognitiveEngine().also { assertTrue(it.initializeAI()) }
    }

    @Test
    fun characterCreation() {
        val engine = CognitiveEngine()
        engine.initializeAI()
        val character = engine.createCognitiveCharacter(
            name = "TestBot",
            personality = "Helpful test assistant",
            traits = mapOf("helpfulness" to 0.9f, "creativity" to 0.6f),
            backstory = "Created for testing purposes"
        )
        assertEquals("TestBot", character.name)
        assertEquals(0.9f, character.traits["helpfulness"]!!, 0.01f)
        assertTrue(Register.values().all { !character.voiceGuide[it].isNullOrBlank() })
    }

    @Test
    fun naturalLanguageIsAnsweredByTheModel() = runBlocking {
        val engine = engineWithChatModel()
        val response = engine.processNaturalLanguage("Hello, how are you today?", context = ConversationContext("test-session"))
        assertTrue("expected success, got $response", response is AIResponse.Success)
        assertTrue((response as AIResponse.Success).text.isNotBlank())
        assertTrue(engine.getAIIntegrationStats().aiSystemStats.loadedModels > 0)
        assertTrue(engine.getStatistics().totalAtoms > 0)
    }

    @Test
    fun differentCharactersGetTheirOwnReplies() = runBlocking {
        val engine = engineWithChatModel()
        val formal = engine.createCognitiveCharacter("Assistant", "Helpful and formal", mapOf("formality" to 0.9f))
        val casual = engine.createCognitiveCharacter("Friend", "Casual and friendly", mapOf("friendliness" to 0.9f))
        val r1 = engine.processNaturalLanguage("Good morning", character = formal)
        val r2 = engine.processNaturalLanguage("Good morning", character = casual)
        assertTrue(r1 is AIResponse.Success && r2 is AIResponse.Success)
        assertTrue((r1 as AIResponse.Success).text.isNotBlank() && (r2 as AIResponse.Success).text.isNotBlank())
        // Each reply was conditioned on that character's own persona
        assertNotEquals(formal.toPersonaPrompt().system(), casual.toPersonaPrompt().system())
        assertEquals("Friend", (r2 as AIResponse.Success).character.name)
        assertTrue(engine.getAIIntegrationStats().aiSystemStats.activeCharacters >= 2)
    }

    @Test
    fun noModelMeansAnErrorNotAReply() = runBlocking {
        LanguageModels.configure(ModelEndpointConfig("http://127.0.0.1:1/v1", "none", timeoutSeconds = 2))
        val engine = CognitiveEngine()
        engine.initializeAI()
        assertTrue(engine.processNaturalLanguage("Hello") is AIResponse.Error)
    }

    @Test
    fun imagesAreRenderedByTheServedDiffusionModel() = runBlocking {
        val engine = engineWithImageModel()
        val result = engine.generateImage("A colorful sunset over the sea", ImageStyle.ARTISTIC)
        assertTrue("expected success, got $result", result is ImageGenerationResult.Success)
        val image = (result as ImageGenerationResult.Success).image
        assertEquals("PNG", String(image.imageData.copyOfRange(1, 4)))
        assertTrue(result.prompt.contains("A colorful sunset over the sea"))
    }

    @Test
    fun privacyScoringIsInRange() {
        val engine = CognitiveEngine()
        engine.initializeAI()
        val score = engine.getAIIntegrationStats().aiSystemStats.privacyScore
        assertTrue(score in 0f..100f)
    }
}

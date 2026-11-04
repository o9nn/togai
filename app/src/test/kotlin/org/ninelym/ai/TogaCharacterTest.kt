package org.ninelym.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.ninelym.llm.LanguageModel
import org.ninelym.llm.ModelEndpointConfig
import org.ninelym.llm.OpenAICompatibleModel

/**
 * Himiko Toga character: her definition (traits, voice guidance) and her replies, which are
 * written by a served model (TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL; live tests skip without one).
 */
class TogaCharacterTest {

    private lateinit var characterSystem: CharacterSystem
    private lateinit var toga: Character
    private val config = ModelEndpointConfig.fromEnvironment()

    @Before
    fun setup() {
        characterSystem = CharacterSystem()
        characterSystem.initializeDefaultCharacters()
        toga = characterSystem.getCharacter("toga") ?: error("Toga character should be initialized")
    }

    private fun model(): LanguageModel {
        assumeTrue("no live model configured", config != null)
        return OpenAICompatibleModel(config!!)
    }

    @Test
    fun togaCharacterExists() {
        assertEquals("toga", toga.id)
        assertEquals("Himiko Toga", toga.name)
        assertFalse("Toga should be a built-in character", toga.isCustom)
    }

    @Test
    fun togaPersonalityTraits() {
        val expected = mapOf(
            "cheerfulness" to 0.95f, "obsessiveness" to 0.90f, "playfulness" to 0.92f, "chaos" to 0.95f,
            "vulnerability" to 0.70f, "identity_fluidity" to 0.88f, "twisted_love" to 0.85f,
            "cuteness_sensitivity" to 0.93f
        )
        expected.forEach { (trait, value) -> assertEquals(trait, value, toga.traits[trait]!!, 0.01f) }
        assertEquals(0.85f, toga.autonomyLevel, 0.01f)
    }

    @Test
    fun togaVoiceGuidanceCoversEveryRegister() {
        Register.values().forEach { register ->
            assertTrue("missing voice guidance for $register", !toga.voiceGuide[register].isNullOrBlank())
        }
        // Guidance describes how she talks; it goes into the model's prompt, not to the user
        val prompt = toga.toPersonaPrompt(Register.EMPATHIC).system()
        assertTrue(prompt.contains(toga.voiceGuide[Register.EMPATHIC]!!))
    }

    @Test
    fun togaRepliesAreWrittenByTheModel() = runBlocking {
        val served = model()
        val processor = OnDeviceAIProcessor({ Result.success(served) })
        assertTrue(processor.initialize("/models"))
        val input = "I just finished knitting a tiny hat for my cat!"
        val response = processor.processLanguageInput(input, toga)
        assertTrue("expected success, got $response", response is AIResponse.Success)
        val text = (response as AIResponse.Success).text
        assertTrue(text.isNotBlank())
        assertTrue("reply must not echo the input", text.trim() != input)
    }

    @Test
    fun togaConversationStoresModelReplies() = runBlocking {
        val sessionId = "toga-session-${System.currentTimeMillis()}"
        val conversation = characterSystem.startConversation("toga", sessionId)
        assertEquals("toga", conversation.character.id)
        assertEquals(0, conversation.getMessageCount())

        val userInput = "This is so cute!"
        val reply = toga.toPersonaPrompt().respond(model(), userInput).getOrThrow()
        assertTrue(characterSystem.addMessageToConversation(sessionId, userInput, reply))
        assertEquals(1, characterSystem.getConversation(sessionId)?.getMessageCount())
    }

    @Test
    fun togaCognitiveStateAndRetrieval() {
        val state = toga.getCurrentState()
        assertNotNull(state)
        assertEquals(0.85f, state.autonomyIndex, 0.01f)
        assertTrue(characterSystem.getCharactersByTrait("chaos", 0.9f).any { it.id == "toga" })
        assertTrue(characterSystem.getAllCharacters().any { it.id == "toga" })
    }
}

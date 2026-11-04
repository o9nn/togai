package org.ninelym.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.ninelym.ai.CharacterSystem
import org.ninelym.ai.toPersonaPrompt
import org.ninelym.ai.wonderful.CreativeExpressionType
import org.ninelym.ai.wonderful.TaskContext
import org.ninelym.ai.wonderful.TogaVoice
import org.ninelym.ai.wonderful.WonderfulToga
import org.ninelym.aid.core.AIDResult
import org.ninelym.aid.core.AIDTensor
import org.ninelym.aid.driver.BasePersonaDriver
import org.ninelym.aid.persona.PersonaFactory
import org.ninelym.layla.chat.ChatService
import org.ninelym.layla.inference.LaylaInferenceService
import org.ninelym.mlc.MLCChatEngine
import org.ninelym.mlc.MLCModelConfig
import org.ninelym.togai.character.Personalities
import org.ninelym.togai.character.PersonalityEngine
import java.util.Collections

/**
 * End-to-end provenance of character replies.
 *
 * Every character-facing entry point is driven through a model that passes each call straight to
 * the served model and records what the model wrote. A reply is accepted only if its text occurs
 * in what the served model actually produced, so any fixed greeting, canned line, template or
 * appended text added by the app fails the check. With the model unreachable, every entry point
 * must fail rather than answer.
 */
class CharacterProvenanceTest {

    /** Passes every call to [real] unchanged and remembers each reply it produced. */
    private class RecordingModel(private val real: LanguageModel) : LanguageModel {
        val produced: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override val modelId: String get() = real.modelId
        override suspend fun chat(
            messages: List<ChatMessage>,
            options: GenerationOptions,
            onToken: ((String) -> Unit)?
        ): Result<String> = real.chat(messages, options, onToken).onSuccess { produced += it }
    }

    private fun liveConfig(): ModelEndpointConfig {
        val config = ModelEndpointConfig.fromEnvironment()
        assumeTrue("no live model configured (TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL)", config != null)
        return config!!
    }

    private fun recorder() = RecordingModel(OpenAICompatibleModel(liveConfig()))

    /** A model endpoint nothing is listening on: every request fails to connect. */
    private val unreachable: () -> Result<LanguageModel> = {
        Result.success(OpenAICompatibleModel(ModelEndpointConfig("http://127.0.0.1:9/v1", "none", timeoutSeconds = 5)))
    }

    private fun RecordingModel.assertFromModel(entryPoint: String, reply: String) {
        val text = reply.trim()
        assertTrue("$entryPoint returned an empty reply", text.isNotEmpty())
        val snapshot = synchronized(produced) { produced.toList() }
        assertTrue(
            "$entryPoint returned text the served model did not produce.\nreply: $text\nmodel output: $snapshot",
            snapshot.any { it.contains(text) }
        )
    }

    @Test
    fun `Layla inference reply is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val service = LaylaInferenceService { Result.success(model) }
        service.initialize().getOrThrow()
        model.assertFromModel("LaylaInferenceService.infer", service.infer("Say hello to Dana.").getOrThrow().text)
    }

    @Test
    fun `chat service assistant message is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val inference = LaylaInferenceService { Result.success(model) }
        inference.initialize().getOrThrow()
        val chat = ChatService(inference)
        val conversation = chat.createConversation("provenance")
        model.assertFromModel("ChatService.sendMessage", chat.sendMessage(conversation, "What is a good name for a cat?").getOrThrow().content)
    }

    @Test
    fun `Toga personality engine reply is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val engine = PersonalityEngine("toga", Personalities.TOGA) { Result.success(model) }
        val reply = engine.generateResponse(PersonalityEngine.ResponseContext("I brought you a strawberry cake!")).getOrThrow()
        model.assertFromModel("PersonalityEngine.generateResponse", reply)
    }

    @Test
    fun `WonderfulToga reply, greeting and haiku are the model's text`(): Unit = runBlocking {
        val model = recorder()
        val toga = WonderfulToga("u1", "Sam", TogaVoice { Result.success(model) })
        model.assertFromModel("WonderfulToga.processMessage",
            toga.processMessage("I finally fixed the bug in my parser!", TaskContext.CELEBRATION).getOrThrow().message)
        model.assertFromModel("WonderfulToga.generateGreeting", toga.generateGreeting().getOrThrow())
        model.assertFromModel("WonderfulToga.generateCreativeExpression",
            toga.generateCreativeExpression(CreativeExpressionType.HAIKU).getOrThrow())
    }

    @Test
    fun `character system persona reply is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val characters = CharacterSystem().apply { initializeDefaultCharacters() }
        val toga = characters.getCharacter("toga") ?: error("toga character missing")
        model.assertFromModel("Character persona respond", toga.toPersonaPrompt().respond(model, "This is so cute!").getOrThrow())
    }

    @Test
    fun `AID persona driver output is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val driver = BasePersonaDriver(PersonaFactory.createChaotic("toga", "Toga")) { Result.success(model) }
        driver.initialize()
        val output = driver.process("Tell me about your favourite colour.", AIDTensor.neutral())
        assertTrue("persona driver failed: $output", output is AIDResult.Success)
        model.assertFromModel("BasePersonaDriver.process", (output as AIDResult.Success).data.content)
    }

    @Test
    fun `MLC engine reply is the model's text`(): Unit = runBlocking {
        val model = recorder()
        val engine = MLCChatEngine { Result.success(model) }
        engine.loadModel(MLCModelConfig(modelPath = liveConfig().model, modelLib = "served")).getOrThrow()
        model.assertFromModel("MLCChatEngine.generate", engine.generate("Name a fruit.").getOrThrow())
    }

    @Test
    fun `without a reachable model no character entry point answers`(): Unit = runBlocking {
        val inference = LaylaInferenceService(unreachable)
        assertTrue("LaylaInferenceService answered without a model", inference.infer("hello").isFailure)

        val chat = ChatService(LaylaInferenceService(unreachable))
        assertTrue("ChatService answered without a model", chat.sendMessage(chat.createConversation("x"), "hello").isFailure)

        val engine = PersonalityEngine("toga", Personalities.TOGA, unreachable)
        assertTrue("PersonalityEngine answered without a model",
            engine.generateResponse(PersonalityEngine.ResponseContext("hello")).isFailure)

        val toga = WonderfulToga("u1", "Sam", TogaVoice(unreachable))
        assertTrue("WonderfulToga replied without a model", toga.processMessage("hello", TaskContext.CELEBRATION).isFailure)
        assertTrue("WonderfulToga greeted without a model", toga.generateGreeting().isFailure)
        assertTrue("WonderfulToga wrote a haiku without a model",
            toga.generateCreativeExpression(CreativeExpressionType.HAIKU).isFailure)

        val driver = BasePersonaDriver(PersonaFactory.createChaotic("toga", "Toga"), unreachable)
        driver.initialize()
        assertTrue("persona driver answered without a model",
            driver.process("hello", AIDTensor.neutral()) !is AIDResult.Success)

        val characters = CharacterSystem().apply { initializeDefaultCharacters() }
        val persona = characters.getCharacter("toga")!!.toPersonaPrompt()
        assertTrue("character persona answered without a model", persona.respond(unreachable().getOrThrow(), "hello").isFailure)
    }
}

package org.ninelym.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.ninelym.executorch.ExecutorchRuntime
import org.ninelym.executorch.LlamaModelConfig
import org.ninelym.layla.inference.LaylaInferenceService
import org.ninelym.mlc.MLCChatEngine
import org.ninelym.mlc.MLCModelConfig

/**
 * The inference engines must answer with text produced by a real served model. Runs against
 * TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL; skipped when no model is configured.
 */
class ModelBackedEnginesTest {

    private val config = ModelEndpointConfig.fromEnvironment()

    private fun provider(): () -> Result<LanguageModel> {
        assumeTrue("no live model configured", config != null)
        val model = OpenAICompatibleModel(config!!)
        return { Result.success(model) }
    }

    private val brief = "Reply with one short sentence."

    @Test
    fun `Layla inference streams a model reply and keeps history`() = runBlocking {
        val service = LaylaInferenceService(provider())
        service.initialize().getOrThrow()
        val streamed = StringBuilder()
        val reply = service.infer("Say hello to Dana.") { streamed.append(it) }.getOrThrow()
        assertTrue(reply.text.isNotBlank())
        assertEquals(reply.text, streamed.toString())
        assertEquals(config!!.model, reply.modelName)
        assertEquals(2, service.getHistory().size)
    }

    @Test
    fun `MLC engine generates with the served model`() = runBlocking {
        val engine = MLCChatEngine(provider())
        engine.loadModel(MLCModelConfig(modelPath = config!!.model, modelLib = "served")).getOrThrow()
        val tokens = mutableListOf<String>()
        val reply = engine.generate("Name a fruit.", systemPrompt = brief) { tokens += it }.getOrThrow()
        assertTrue(reply.isNotBlank())
        assertEquals(reply, tokens.joinToString(""))
        assertEquals(3, engine.getHistory().size)
    }

    @Test
    fun `MLC engine rejects a model the server does not serve`() = runBlocking {
        val engine = MLCChatEngine(provider())
        assertTrue(engine.loadModel(MLCModelConfig(modelPath = "not-a-served-model", modelLib = "x")).isFailure)
    }

    @Test
    fun `Executorch runtime without native lib generates with the served model`() = runBlocking {
        val provide = provider()
        val runtime = ExecutorchRuntime(provide)
        runtime.loadModel(LlamaModelConfig(modelPath = "model.pte", tokenizerPath = "tok.bin", systemPrompt = brief)).getOrThrow()
        val reply = runtime.generate("Name a colour.", maxTokens = 30).getOrThrow()
        assertTrue(reply.isNotBlank())
    }

    @Test
    fun `engines fail rather than answer when no model is reachable`() = runBlocking {
        val dead = OpenAICompatibleModel(ModelEndpointConfig("http://127.0.0.1:1/v1", "none", timeoutSeconds = 2))
        val service = LaylaInferenceService({ Result.success(dead) })
        assertTrue(service.infer("hi").isFailure)
    }
}

class ModelBackedCharactersTest {

    private val config = ModelEndpointConfig.fromEnvironment()

    private fun provider(): () -> Result<LanguageModel> {
        assumeTrue("no live model configured", config != null)
        val model = OpenAICompatibleModel(config!!)
        return { Result.success(model) }
    }

    @Test
    fun `WonderfulToga replies, greets and makes haiku with the model`() = runBlocking {
        val toga = org.ninelym.ai.wonderful.WonderfulToga("u1", "Sam", org.ninelym.ai.wonderful.TogaVoice(provider()))
        val reply = toga.processMessage("I finally fixed the bug in my parser!",
            org.ninelym.ai.wonderful.TaskContext.CELEBRATION).getOrThrow()
        assertTrue(reply.message.isNotBlank())
        assertTrue(toga.generateGreeting().getOrThrow().isNotBlank())
        assertTrue(toga.generateCreativeExpression(org.ninelym.ai.wonderful.CreativeExpressionType.HAIKU).getOrThrow().isNotBlank())
        assertEquals(1, toga.getRelationshipStatus().totalInteractions)
    }

    @Test
    fun `persona driver answers through the model and keeps context`() = runBlocking {
        val spec = org.ninelym.aid.persona.PersonaFactory.createToga()
        val driver = org.ninelym.aid.driver.BasePersonaDriver(spec, provider())
        driver.initialize()
        val out = driver.process("What's 2 + 2?", org.ninelym.aid.core.AIDTensor.neutral())
        assertTrue(out is org.ninelym.aid.core.AIDResult.Success)
        val content = (out as org.ninelym.aid.core.AIDResult.Success).data.content
        assertTrue(content.isNotBlank())
        assertTrue("persona must not echo the input", content.trim() != "What's 2 + 2?")
    }

    @Test
    fun `persona driver reports an error when the model is unreachable`() = runBlocking {
        val dead = OpenAICompatibleModel(ModelEndpointConfig("http://127.0.0.1:1/v1", "none", timeoutSeconds = 2))
        val driver = org.ninelym.aid.driver.BasePersonaDriver(org.ninelym.aid.persona.PersonaFactory.createToga(), { Result.success(dead) })
        assertTrue(driver.process("hi", org.ninelym.aid.core.AIDTensor.neutral()) is org.ninelym.aid.core.AIDResult.Error)
    }
}

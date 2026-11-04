package org.ninelym.togai.integration

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.ninelym.llm.ModelEndpointConfig
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.URL
import java.util.Collections
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Provenance of replies through the CogAI bridge.
 *
 * On a device the bridge reaches the CogAI gateway by running curl inside Termux. Here the bridge's
 * own curl commands are run by `sh` on this machine, against a gateway that forwards every chat
 * request unchanged to the served model and records what the model answered. A reply is accepted
 * only if it is text the model produced; with the model unreachable the bridge must fail.
 */
class CogAIBridgeProvenanceTest {

    private val config = ModelEndpointConfig.fromEnvironment()
    private val modelOutput: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private var gateway: ServerSocket? = null

    @After
    fun stopGateway() {
        gateway?.close()
    }

    /** A CogAI-compatible gateway on a free port that relays chat to [upstreamBase] verbatim. */
    private fun startGateway(upstreamBase: String): String {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        gateway = server
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { client.use { serve(it, upstreamBase) } }
            }
        }
        return "http://127.0.0.1:${server.localPort}"
    }

    private fun serve(client: Socket, upstreamBase: String) {
        val input = client.getInputStream().buffered()
        fun readLine(): String {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c == -1 || c == '\n'.code) break
                if (c != '\r'.code) sb.append(c.toChar())
            }
            return sb.toString()
        }
        val path = readLine().split(" ").getOrElse(1) { "/" }
        var length = 0
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(":").trim().toInt()
        }
        val request = ByteArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
        val (status, body) = when (path) {
            "/health" -> 200 to "ok".toByteArray()
            "/v1/chat/completions" -> try {
                val upstream = (URL("$upstreamBase/chat/completions").openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; connectTimeout = 5_000; readTimeout = 600_000
                    setRequestProperty("Content-Type", "application/json")
                }
                upstream.outputStream.use { it.write(request) }
                val code = upstream.responseCode
                val bytes = (if (code < 400) upstream.inputStream else upstream.errorStream).readBytes()
                record(String(bytes))
                code to bytes
            } catch (e: Exception) {
                502 to """{"error":"upstream model unreachable: ${e.javaClass.simpleName}"}""".toByteArray()
            }
            else -> 404 to ByteArray(0)
        }
        val out = client.getOutputStream()
        out.write("HTTP/1.1 $status X\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    /** Keep the text the model produced, from a whole response or from a stream of SSE chunks. */
    private fun record(body: String) {
        val json = Json { ignoreUnknownKeys = true }
        val dataLines = body.lines().filter { it.startsWith("data:") }
        if (dataLines.isEmpty()) {
            runCatching {
                json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!
                    .jsonObject["content"]!!.jsonPrimitive.contentOrNull
            }.getOrNull()?.let { modelOutput += it }
        } else {
            modelOutput += dataLines.map { it.removePrefix("data:").trim() }.filter { it != "[DONE]" }.joinToString("") { data ->
                runCatching {
                    json.parseToJsonElement(data).jsonObject["choices"]!!.jsonArray[0].jsonObject["delta"]!!
                        .jsonObject["content"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: ""
            }
        }
    }

    /** Runs a command the way Termux would: through a POSIX shell, here on this machine. */
    private fun shell(command: String): ProcessBuilder = ProcessBuilder("sh", "-c", command).apply {
        // Requests go to 127.0.0.1 only
        listOf("http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "all_proxy").forEach { environment().remove(it) }
    }

    private fun bridge(apiBase: String): CogAIBridge {
        val termux = mockk<TermuxIntegrationService>()
        coEvery { termux.executeCommand(any(), any(), any(), any()) } coAnswers {
            val process = shell(firstArg()).start()
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            TermuxIntegrationService.CommandResult(process.waitFor(), out, err)
        }
        every { termux.observeCommandOutput(any()) } answers {
            val command = firstArg<String>()
            flow {
                val process = shell(command).redirectErrorStream(true).start()
                val buffer = CharArray(256)
                process.inputStream.reader().use { reader ->
                    while (true) {
                        val n = reader.read(buffer)
                        if (n < 0) break
                        emit(String(buffer, 0, n))
                    }
                }
                process.waitFor()
            }.flowOn(Dispatchers.IO)
        }
        return CogAIBridge(mockk<Context>(relaxed = true), termux, apiBase)
    }

    private fun live(): ModelEndpointConfig {
        assumeTrue("no live model configured (TOGAI_LLM_BASE_URL / TOGAI_LLM_MODEL)", config != null)
        return config!!
    }

    @Test
    fun `ask returns the model's text, including for messages with apostrophes`(): Unit = runBlocking {
        val model = live()
        val cogai = bridge(startGateway(model.baseUrl))
        val reply = cogai.ask("What's a good name for a cat? I'm curious.", model = model.model)
        assertTrue("empty reply", reply.isNotBlank())
        assertTrue("reply was not the model's text.\nreply: $reply\nmodel output: $modelOutput",
            modelOutput.any { it.contains(reply.trim()) })
    }

    @Test
    fun `chatStream yields exactly the text the model streamed`(): Unit = runBlocking {
        val model = live()
        val cogai = bridge(startGateway(model.baseUrl))
        val pieces = cogai.chatStream(
            listOf(CogAIBridge.ChatMessage("user", "Name two fruits, please - it's for a list.")), model = model.model
        ).toList()
        val streamed = pieces.joinToString("")
        assertTrue("nothing was streamed", streamed.isNotBlank())
        assertEquals(1, modelOutput.size)
        assertEquals("the stream must be the model's text, unchanged", modelOutput.single(), streamed)
    }

    @Test
    fun `without a reachable model the bridge does not answer`(): Unit = runBlocking {
        live()
        val cogai = bridge(startGateway("http://127.0.0.1:9/v1"))
        val failure = runCatching { cogai.ask("hello", model = "none") }
        assertTrue("CogAI answered without a model: $failure", failure.isFailure)
        assertTrue(modelOutput.isEmpty())
    }
}

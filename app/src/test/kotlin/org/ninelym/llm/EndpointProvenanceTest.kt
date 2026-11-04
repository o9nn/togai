package org.ninelym.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.ninelym.aid.core.AIDResult
import org.ninelym.aid.core.AIDTensor
import org.ninelym.aid.endpoint.ImageEndpoint
import org.ninelym.aid.endpoint.VoiceEndpoint
import org.ninelym.aid.hub.EndpointRequest
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Provenance of the voice and image endpoints' outputs.
 *
 * Each endpoint talks to the real served model through a relay that forwards every request
 * unchanged and records the model server's response. The endpoint's result must be exactly what
 * the model returned: the transcription is the speech model's text, the synthesized audio is the
 * TTS model's bytes, and the image description is the vision model's answer. With the model
 * unreachable, the endpoints must fail.
 */
class EndpointProvenanceTest {

    private class Exchange(val path: String, val status: Int, val body: ByteArray)

    private val exchanges: MutableList<Exchange> = Collections.synchronizedList(mutableListOf())
    private val relays = mutableListOf<ServerSocket>()

    @After
    fun stopRelays() = relays.forEach { it.close() }

    /** A loopback relay to [upstream] (e.g. http://127.0.0.1:8081) that records every response. */
    private fun relay(upstream: String): String {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        relays += server
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { client.use { forward(it, upstream) } }
            }
        }
        return "http://127.0.0.1:${server.localPort}"
    }

    private fun forward(client: Socket, upstream: String) {
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
        val (method, path) = readLine().split(" ").let { it[0] to it[1] }
        val headers = mutableListOf<Pair<String, String>>()
        var length = 0
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            val name = header.substringBefore(":").trim()
            val value = header.substringAfter(":").trim()
            if (name.equals("Content-Length", true)) length = value.toInt()
            if (name.lowercase() !in setOf("host", "connection", "content-length", "transfer-encoding")) headers += name to value
        }
        val request = ByteArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
        val (status, body) = try {
            val conn = (URL(upstream + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 5_000; readTimeout = 900_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (length > 0) { doOutput = true; outputStream.use { it.write(request) } }
            }
            val code = conn.responseCode
            code to (if (code < 400) conn.inputStream else conn.errorStream).readBytes()
        } catch (e: Exception) {
            502 to "upstream unreachable".toByteArray()
        }
        exchanges += Exchange(path, status, body)
        val out = client.getOutputStream()
        out.write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun modelResponses(path: String) = synchronized(exchanges) { exchanges.filter { it.path.endsWith(path) && it.status < 400 } }

    private val json = Json { ignoreUnknownKeys = true }

    private fun env(name: String): String {
        val value = System.getenv(name)
        assumeTrue("$name not set; no served model for this endpoint", value != null)
        return value!!
    }

    /** "http://host:port/v1" -> ("http://host:port", "/v1") */
    private fun split(baseUrl: String): Pair<String, String> {
        val url = URL(baseUrl)
        return "${url.protocol}://${url.host}:${url.port}" to url.path.trimEnd('/')
    }

    private fun mediaThroughRelay(): OpenAICompatibleModel {
        val (origin, prefix) = split(env("TOGAI_MEDIA_BASE_URL"))
        return OpenAICompatibleModel(ModelEndpointConfig(relay(origin) + prefix, "tts-1", timeoutSeconds = 900))
    }

    @Test
    fun `voice endpoint returns the TTS model's audio and the speech model's transcription`(): Unit = runBlocking {
        val voice = VoiceEndpoint({ Result.success(mediaThroughRelay()) })
        assertTrue(voice.initialize() is AIDResult.Success)

        val spoken = voice.handle(
            EndpointRequest(action = "synthesize", payload = null, parameters = mapOf("text" to "The library opens at nine.")),
            AIDTensor.neutral()
        )
        assertTrue("synthesis failed: $spoken", spoken is AIDResult.Success)
        val audioPath = ((spoken as AIDResult.Success).data as Map<*, *>)["audioPath"] as String
        val served = modelResponses("/audio/speech").single().body
        assertArrayEquals("the endpoint's audio must be the TTS model's bytes", served, File(audioPath).readBytes())

        val heard = voice.handle(
            EndpointRequest(action = "transcribe", payload = null, parameters = mapOf("audioPath" to audioPath)),
            AIDTensor.neutral()
        )
        assertTrue("transcription failed: $heard", heard is AIDResult.Success)
        val transcription = ((heard as AIDResult.Success).data as Map<*, *>)["transcription"] as String
        val modelText = json.parseToJsonElement(String(modelResponses("/audio/transcriptions").single().body))
            .jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("the endpoint's transcription must be the speech model's text", modelText.trim(), transcription.trim())
    }

    @Test
    fun `image endpoint description is the vision model's answer`(): Unit = runBlocking {
        val media = OpenAICompatibleModel(ModelEndpointConfig(env("TOGAI_MEDIA_BASE_URL"), "stable-diffusion", timeoutSeconds = 900))
        val (origin, prefix) = split(env("TOGAI_VISION_BASE_URL"))
        val vision = OpenAICompatibleModel(
            ModelEndpointConfig(relay(origin) + prefix, System.getenv("TOGAI_VISION_MODEL") ?: "nanollava", timeoutSeconds = 900)
        )
        val png = media.generateImage("a red apple on a wooden table", "512x512", "stable-diffusion", seed = 7).getOrThrow()
        val file = File.createTempFile("apple", ".png").apply { writeBytes(png); deleteOnExit() }

        val endpoint = ImageEndpoint(visionProvider = { Result.success(vision) })
        assertTrue(endpoint.initialize() is AIDResult.Success)
        val analyzed = endpoint.handle(
            EndpointRequest(action = "analyze", payload = null,
                parameters = mapOf("imagePath" to file.path, "prompt" to "What fruit is in this picture?")),
            AIDTensor.neutral()
        )
        assertTrue("analysis failed: $analyzed", analyzed is AIDResult.Success)
        val description = ((analyzed as AIDResult.Success).data as Map<*, *>)["description"] as String
        val modelText = json.parseToJsonElement(String(modelResponses("/chat/completions").single().body))
            .jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.contentOrNull
        assertEquals("the endpoint's description must be the vision model's answer", modelText?.trim(), description.trim())
    }

    @Test
    fun `voice and image endpoints fail without a reachable model`(): Unit = runBlocking {
        env("TOGAI_MEDIA_BASE_URL")
        val dead = OpenAICompatibleModel(ModelEndpointConfig("http://127.0.0.1:9/v1", "none", timeoutSeconds = 5))
        val voice = VoiceEndpoint({ Result.success(dead) })
        voice.initialize()
        val wav = File.createTempFile("silence", ".wav").apply { writeBytes(ByteArray(64)); deleteOnExit() }
        assertTrue(voice.handle(EndpointRequest(action = "synthesize", payload = null, parameters = mapOf("text" to "hello")), AIDTensor.neutral()) !is AIDResult.Success)
        assertTrue(voice.handle(EndpointRequest(action = "transcribe", payload = null, parameters = mapOf("audioPath" to wav.path)), AIDTensor.neutral()) !is AIDResult.Success)

        val image = ImageEndpoint(visionProvider = { Result.success(dead) })
        image.initialize()
        val png = File.createTempFile("blank", ".png").apply { writeBytes(ByteArray(64)); deleteOnExit() }
        assertTrue(image.handle(EndpointRequest(action = "analyze", payload = null, parameters = mapOf("imagePath" to png.path)), AIDTensor.neutral()) !is AIDResult.Success)
    }
}

package org.ninelym.testing

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A small real HTTP/1.1 key/value store for tests of networked services: `PUT path` stores the
 * body, `GET path` returns it (404 if absent), `GET /` answers 200. One request per connection.
 */
class KeyValueHttpServer : AutoCloseable {
    val stored = ConcurrentHashMap<String, ByteArray>()
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val url: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { client.use { handle(it) } }
            }
        }
    }

    private fun handle(client: Socket) {
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
        var length = 0
        while (true) {
            val header = readLine()
            if (header.isEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(":").trim().toInt()
        }
        val body = ByteArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
        val (status, response) = when {
            method == "PUT" -> { stored[path] = body; "200 OK" to ByteArray(0) }
            method == "GET" && path == "/" -> "200 OK" to ByteArray(0)
            method == "GET" -> stored[path]?.let { "200 OK" to it } ?: ("404 Not Found" to ByteArray(0))
            else -> "405 Method Not Allowed" to ByteArray(0)
        }
        val out = client.getOutputStream()
        out.write("HTTP/1.1 $status\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(response)
        out.flush()
    }

    override fun close() = socket.close()
}

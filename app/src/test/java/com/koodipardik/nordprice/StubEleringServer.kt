package com.koodipardik.nordprice

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A one-request-at-a-time HTTP stub, written on plain sockets because Android unit tests have no
 * `com.sun.net.httpserver` on their compile classpath and adding a server dependency for five
 * tests is not worth it.
 *
 * Each connection is answered once and closed, which is what `HttpURLConnection` expects from a
 * server that does not advertise keep-alive.
 */
class StubEleringServer(
    private val body: ByteArray,
    private val status: Int = 200,
    private val gzip: Boolean = false,
) : AutoCloseable {

    private val listener = ServerSocket(0)
    private val running = AtomicBoolean(true)

    /** Path + query of the last request, so tests can assert on the window that was asked for. */
    @Volatile
    var lastRequestLine: String? = null
        private set

    val baseUrl: String get() = "http://127.0.0.1:${listener.localPort}/api/nps/price"

    init {
        thread(isDaemon = true, name = "stub-elering") {
            while (running.get()) {
                val socket = runCatching { listener.accept() }.getOrNull() ?: return@thread
                runCatching { socket.use { serve(it) } }
            }
        }
    }

    private fun serve(socket: Socket) {
        val input = socket.getInputStream()
        val requestLine = buildString {
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                append(line).append('\n')
            }
        }.lineSequence().firstOrNull().orEmpty()
        lastRequestLine = requestLine

        val payload = if (gzip) gzipOf(body) else body
        val reason = if (status == 200) "OK" else "Unavailable"
        val headers = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json\r\n")
            if (gzip) append("Content-Encoding: gzip\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        val out = socket.getOutputStream()
        out.write(headers.toByteArray())
        if (status == 200) out.write(payload)
        out.flush()
    }

    private fun readLine(input: java.io.InputStream): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) return if (line.size() == 0) null else line.toString(Charsets.US_ASCII)
            if (byte == '\n'.code) return line.toString(Charsets.US_ASCII).trimEnd('\r')
            line.write(byte)
        }
    }

    override fun close() {
        running.set(false)
        runCatching { listener.close() }
    }

    private fun gzipOf(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}

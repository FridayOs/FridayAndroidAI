package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveTransport
import com.dark.tool_neuron.repo.gateway.live.LiveWebSocketTransport
import com.dark.tool_neuron.repo.gateway.live.WebSocketHandshake
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

// Exercises the REAL LiveWebSocketTransport (hand-rolled RFC 6455 read loop + close path) over a loopback
// socket — no TLS, via the injectable connector. Proves the producer completes on a normal peer close AND on a
// bare EOF, so collect returns instead of hanging (the production bug: awaitClose with no channel close).
class LiveWebSocketTransportStreamTest {

    // Unmasked server->client frame (payloads here are < 126 bytes, single-byte length).
    private fun serverFrame(opcode: Int, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(0x80 or opcode)
            write(payload.size)
            write(payload)
        }.toByteArray()

    private fun textFrame(s: String) = serverFrame(0x1, s.toByteArray(Charsets.UTF_8))
    private fun closeFrame(code: Int) = serverFrame(0x8, byteArrayOf((code shr 8).toByte(), (code and 0xFF).toByte()))

    // A fragment with an explicit opcode + FIN bit, so a multi-byte code point can be split across two frames.
    private fun fragment(opcode: Int, payload: ByteArray, fin: Boolean): ByteArray =
        ByteArrayOutputStream().apply {
            write((if (fin) 0x80 else 0x00) or opcode)
            write(payload.size)
            write(payload)
        }.toByteArray()

    // Boots a one-shot WS server: handshake, then whatever `afterHandshake` writes, then close. Returns the port.
    private fun startServer(afterHandshake: (OutputStream) -> Unit): ServerSocket {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        Thread {
            server.accept().use { sock ->
                val input = sock.getInputStream()
                val head = StringBuilder()
                var last4 = 0
                while (true) {
                    val b = input.read()
                    if (b < 0) return@use
                    head.append(b.toChar())
                    last4 = (last4 shl 8) or (b and 0xFF)
                    if (last4 == 0x0D0A0D0A) break
                }
                val key = head.lineSequence()
                    .firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim().orEmpty()
                val out = sock.getOutputStream()
                out.write(
                    ("HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: ${WebSocketHandshake.expectedAccept(key)}\r\n\r\n")
                        .toByteArray(Charsets.US_ASCII),
                )
                out.flush()
                afterHandshake(out)
                out.flush()
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun transportTo(port: Int) = LiveWebSocketTransport { _, _ ->
        Socket("127.0.0.1", port).apply { soTimeout = 3000 }
    }

    @Test
    fun completesOnNormalPeerClose_deliveringFramesInOrder() {
        val server = startServer { out ->
            out.write(textFrame("hello"))
            out.write(textFrame("world"))
            out.write(closeFrame(1000))
        }
        server.use {
            val received = runBlocking {
                withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() }
            }
            assertEquals(
                listOf(
                    LiveTransport.Incoming.Text("hello"),
                    LiveTransport.Incoming.Text("world"),
                    LiveTransport.Incoming.Closed(1000, ""),
                ),
                received,
            )
        }
    }

    @Test
    fun completesOnBareEof_withoutCloseFrame() {
        val server = startServer { out ->
            out.write(textFrame("solo"))
            // no close frame — server just drops the socket, forcing an EOF on the client read loop.
        }
        server.use {
            val received = runBlocking {
                withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() }
            }
            assertEquals(listOf(LiveTransport.Incoming.Text("solo")), received)
            assertTrue("EOF completes the flow instead of hanging", received.isNotEmpty())
        }
    }

    // A UTF-8 code point (é = 0xC3 0xA9) split across a TEXT fin=false + CONTINUATION fin=true frame must decode
    // as one glyph — the transport accumulates raw bytes and decodes once at FIN, not per fragment.
    @Test
    fun reassemblesUtf8CodePointSplitAcrossFragments() {
        val eBytes = "é".toByteArray(Charsets.UTF_8) // 0xC3, 0xA9
        val server = startServer { out ->
            out.write(fragment(0x1, byteArrayOf(eBytes[0]), fin = false)) // first UTF-8 byte alone
            out.write(fragment(0x0, byteArrayOf(eBytes[1]), fin = true))  // continuation completes the glyph
            out.write(closeFrame(1000))
        }
        server.use {
            val received = runBlocking {
                withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() }
            }
            assertEquals(
                listOf(LiveTransport.Incoming.Text("é"), LiveTransport.Incoming.Closed(1000, "")),
                received,
            )
        }
    }

    // A send that fails at the socket must surface (transport torn down), never be silently swallowed. Uses a fake
    // socket that completes the handshake then throws on the next write — fully deterministic, no thread races.
    @Test
    fun sendFailureAfterHandshake_isSurfacedNotSwallowed() {
        val transport = LiveWebSocketTransport { _, _ -> HandshakeThenFailSocket() }
        runBlocking {
            withTimeout(5000) {
                transport.open("localhost", 443, "/x") {
                    transport.sendText("setup") // write fails on the post-handshake fake output → transport records it
                }.toList()
            }
        }
        assertTrue("a write failure tears the transport down, never silently swallowed", transport.lastWriteFailed)
    }

    // hardenTls turns on RFC 2818 hostname verification; without it a raw SSLSocket checks the chain but not the host.
    @Test
    fun hardenTls_enablesHttpsEndpointIdentification() {
        val raw = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket() as SSLSocket
        raw.use {
            val hardened = LiveWebSocketTransport.hardenTls(it)
            assertEquals("HTTPS", hardened.sslParameters.endpointIdentificationAlgorithm)
        }
    }

    // In-memory socket: absorbs the handshake request, replies 101 with the correct accept via a piped input the
    // transport reads, then flips to fail mode so the FIRST post-handshake frame write (the setup send) throws.
    private class HandshakeThenFailSocket : Socket() {
        private val pin = java.io.PipedInputStream(8192)
        private val pout = java.io.PipedOutputStream(pin)
        private val reqAcc = ByteArrayOutputStream()
        private var failMode = false
        private val outStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                if (failMode) throw IOException("peer gone")
                reqAcc.write(b, off, len)
                if (reqAcc.toString("US-ASCII").contains("\r\n\r\n")) {
                    val key = reqAcc.toString("US-ASCII").lineSequence()
                        .firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                        ?.substringAfter(':')?.trim().orEmpty()
                    pout.write(
                        ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: ${WebSocketHandshake.expectedAccept(key)}\r\n\r\n")
                            .toByteArray(Charsets.US_ASCII),
                    )
                    pout.flush()
                    failMode = true
                }
            }
        }
        override fun getOutputStream(): OutputStream = outStream
        override fun getInputStream(): InputStream = pin
        override fun close() { runCatching { pout.close() }; runCatching { pin.close() } }
    }
}

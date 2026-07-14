package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveTransport
import com.dark.tool_neuron.repo.gateway.live.LiveWebSocketTransport
import com.dark.tool_neuron.repo.gateway.live.WebSocketFrame
import com.dark.tool_neuron.repo.gateway.live.WebSocketHandshake
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

// Exercises the REAL LiveWebSocketTransport over a loopback socket via the injectable connector (no TLS needed).
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

    // Boots a one-shot WS server: handshake, `afterHandshake` writes, then optionally reads the client's close code.
    private fun startServer(readClientClose: ((Int) -> Unit)? = null, afterHandshake: (OutputStream) -> Unit): ServerSocket {
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
                if (readClientClose != null) runCatching { readClientCloseCode(input) }.getOrNull()?.let(readClientClose)
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

    // A UTF-8 code point (é = 0xC3 0xA9) split across TEXT fin=false + CONTINUATION fin=true must decode as one glyph.
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

    // A failed send must surface as a TransportError on the flow (so the engine classifies it), not be swallowed.
    @Test
    fun sendFailureAfterHandshake_isSurfacedAsTransportError() {
        val transport = LiveWebSocketTransport { _, _ -> HandshakeThenFailSocket() }
        val received = runBlocking {
            withTimeout(5000) {
                transport.open("localhost", 443, "/x") { transport.sendText("setup") }.toList()
            }
        }
        assertTrue("write failure surfaces a TransportError event", received.any { it is LiveTransport.Incoming.TransportError })
        assertTrue("write failure tears the transport down, never silently swallowed", transport.lastWriteFailed)
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

    // In-memory socket: absorbs the handshake, replies 101, then fails the first post-handshake frame write.
    private class HandshakeThenFailSocket : Socket() {
        private val pin = PipedInputStream(8192)
        private val pout = PipedOutputStream(pin)
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

    // An orphan CONTINUATION (no TEXT/BINARY started it) is an RFC 6455 sequence violation.
    @Test
    fun orphanContinuationFrame_isRejected() {
        val server = startServer { out -> out.write(fragment(0x0, "x".toByteArray(), fin = true)) }
        server.use {
            assertThrows(WebSocketFrame.ProtocolException::class.java) {
                runBlocking { withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() } }
            }
        }
    }

    // A new data frame arriving while a fragmented message is still open is a sequence violation.
    @Test
    fun interleavedDataFrameDuringFragment_isRejected() {
        val server = startServer { out ->
            out.write(fragment(0x1, "a".toByteArray(), fin = false)) // open a TEXT fragment
            out.write(fragment(0x1, "b".toByteArray(), fin = true))  // another TEXT before the first finished
        }
        server.use {
            assertThrows(WebSocketFrame.ProtocolException::class.java) {
                runBlocking { withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() } }
            }
        }
    }

    // The aggregate of a fragmented message is capped (16 MiB) before appending — a fragment flood can't OOM.
    @Test
    fun fragmentedAggregateOverflow_isRejected() {
        val oneMib = ByteArray(1024 * 1024)
        val server = startServer { out ->
            out.write(bigFrame(0x1, oneMib, fin = false))
            repeat(17) { out.write(bigFrame(0x0, oneMib, fin = false)) } // > 16 MiB total, never FIN
        }
        server.use {
            assertThrows(WebSocketFrame.ProtocolException::class.java) {
                runBlocking { withTimeout(10000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() } }
            }
        }
    }

    // On a normal peer close the client echoes the received status back as its close-handshake reply.
    @Test
    fun peerClose_isEchoedWithSameStatus() {
        val codeBox = arrayOfNulls<Int>(1)
        val server = startServer(readClientClose = { codeBox[0] = it }) { out -> out.write(closeFrame(1001)) }
        server.use {
            runBlocking { withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() } }
        }
        assertEquals(1001, codeBox[0])
    }

    // A peer protocol violation (masked server frame) makes the client reply with a 1002 close.
    @Test
    fun protocolViolation_closesWith1002() {
        val codeBox = arrayOfNulls<Int>(1)
        // Masked TEXT frame (mask bit set) is illegal from a server.
        val masked = byteArrayOf(0x81.toByte(), 0x81.toByte(), 1, 2, 3, 4, 0x00)
        val server = startServer(readClientClose = { codeBox[0] = it }) { out -> out.write(masked) }
        server.use {
            assertThrows(WebSocketFrame.ProtocolException::class.java) {
                runBlocking { withTimeout(5000) { transportTo(it.localPort).open("localhost", 443, "/x") {}.toList() } }
            }
        }
        assertEquals(1002, codeBox[0])
    }

    // Reads client→server frames (masked), returns the first CLOSE frame's status code.
    private fun readClientCloseCode(input: InputStream): Int {
        while (true) {
            val b0 = input.read(); if (b0 < 0) throw IOException("eof before close")
            val opcode = b0 and 0x0F
            val b1 = input.read(); if (b1 < 0) throw IOException("eof")
            val masked = b1 and 0x80 != 0
            var len = b1 and 0x7F
            if (len == 126) len = (input.read() shl 8) or input.read()
            val mask = if (masked) ByteArray(4) { input.read().toByte() } else ByteArray(0)
            val payload = ByteArray(len) { input.read().toByte() }
            if (masked) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            if (opcode == 0x8) {
                return if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1005
            }
        }
    }

    // Unmasked server frame with extended (16-bit) length for payloads >= 126 bytes.
    private fun bigFrame(opcode: Int, payload: ByteArray, fin: Boolean): ByteArray =
        ByteArrayOutputStream().apply {
            write((if (fin) 0x80 else 0x00) or opcode)
            when {
                payload.size < 126 -> write(payload.size)
                payload.size < 65536 -> { write(126); write(payload.size ushr 8); write(payload.size and 0xFF) }
                else -> { write(127); for (s in 56 downTo 0 step 8) write((payload.size.toLong() ushr s and 0xFF).toInt()) }
            }
            write(payload)
        }.toByteArray()
}

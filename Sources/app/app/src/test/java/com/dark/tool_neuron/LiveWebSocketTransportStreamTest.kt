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
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

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
}

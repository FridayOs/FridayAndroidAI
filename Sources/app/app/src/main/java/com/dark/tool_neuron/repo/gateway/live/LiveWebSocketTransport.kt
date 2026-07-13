package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.atomic.AtomicBoolean

// Minimal RFC 6455 client over a system-trust TLS socket. The repo has no WebSocket dep (:networking is
// GET-only curl, HttpURLConnection can't upgrade), so the transport is hand-rolled here — no new dependency,
// and it connects straight to the user's Gemini host (never a FRIDAY proxy). Read frames arrive as a Flow;
// writes are synchronized so the send coroutine and control pings never interleave a partial frame.
// connect is injectable so a loopback test can exercise the real read loop / close path without TLS.
internal class LiveWebSocketTransport(
    private val connect: (host: String, port: Int) -> Socket = ::defaultConnect,
) : LiveTransport {

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    private val writeLock = Any()

    // Opens the socket + WebSocket upgrade, then emits every inbound message frame until close.
    // onReady fires once, right after a successful upgrade and before the read loop, so the caller can
    // send the mandatory first frame (Gemini's `setup`) before any server message is expected.
    // Blocking socket I/O runs on Dispatchers.IO; cancelling the collector tears the socket down.
    override fun open(host: String, port: Int, path: String, onReady: () -> Unit): Flow<LiveTransport.Incoming> = callbackFlow {
        val key = WebSocketHandshake.nonce()
        try {
            val sock = connect(host, port)
            socket = sock // set early so a handshake failure still tears the socket down
            val out = sock.outputStream
            val input = BufferedInputStream(sock.inputStream)

            out.write(WebSocketHandshake.request(host, path, key).toByteArray(Charsets.US_ASCII))
            out.flush()

            val head = readHandshakeResponse(input)
            if (!WebSocketHandshake.isValidResponse(head, key)) {
                val status = WebSocketHandshake.statusCode(head)
                throw LiveTransport.HandshakeException(status, "WebSocket upgrade rejected (HTTP $status)")
            }

            output = out
            onReady()

            val payload = StringBuilder()
            val binaryAcc = java.io.ByteArrayOutputStream()
            var accumulatingBinary = false
            while (!closed.get()) {
                val frame = WebSocketFrame.readFrame(input) ?: break
                when (frame.opcode) {
                    WebSocketFrame.OPCODE_TEXT, WebSocketFrame.OPCODE_BINARY,
                    WebSocketFrame.OPCODE_CONTINUATION -> {
                        if (frame.opcode == WebSocketFrame.OPCODE_BINARY) accumulatingBinary = true
                        if (frame.opcode == WebSocketFrame.OPCODE_TEXT) accumulatingBinary = false
                        if (accumulatingBinary) binaryAcc.write(frame.payload) else payload.append(String(frame.payload, Charsets.UTF_8))
                        if (frame.fin) {
                            if (accumulatingBinary) {
                                trySend(LiveTransport.Incoming.Binary(binaryAcc.toByteArray()))
                                binaryAcc.reset()
                            } else {
                                trySend(LiveTransport.Incoming.Text(payload.toString()))
                                payload.setLength(0)
                            }
                        }
                    }
                    WebSocketFrame.OPCODE_PING -> writeControl(WebSocketFrame.OPCODE_PONG, frame.payload)
                    WebSocketFrame.OPCODE_PONG -> {}
                    WebSocketFrame.OPCODE_CLOSE -> {
                        val (code, reason) = WebSocketFrame.parseClose(frame.payload)
                        trySend(LiveTransport.Incoming.Closed(code, reason))
                        break
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            this@LiveWebSocketTransport.close()
            throw t
        }

        // Peer close / EOF ended the read loop. Tear the socket down and COMPLETE the producer channel
        // (unqualified close() = ProducerScope.close) so the engine's collect returns instead of suspending
        // in awaitClose forever; the awaitClose block re-runs teardown on a collector cancel.
        this@LiveWebSocketTransport.close()
        close()
        awaitClose { this@LiveWebSocketTransport.close() }
    }.flowOn(Dispatchers.IO)

    // Client→server frames are always masked per RFC 6455 §5.3; the write lock keeps concurrent sends whole.
    override fun sendText(text: String) {
        val out = output ?: return
        synchronized(writeLock) {
            runCatching {
                out.write(WebSocketFrame.encode(WebSocketFrame.OPCODE_TEXT, text.toByteArray(Charsets.UTF_8)))
                out.flush()
            }
        }
    }

    private fun writeControl(opcode: Int, payload: ByteArray) {
        val out = output ?: return
        synchronized(writeLock) {
            runCatching {
                out.write(WebSocketFrame.encode(opcode, payload))
                out.flush()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val out = output
        synchronized(writeLock) {
            runCatching {
                out?.write(WebSocketFrame.encode(WebSocketFrame.OPCODE_CLOSE, WebSocketFrame.closePayload(1000, "")))
                out?.flush()
            }
        }
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    // Read the HTTP response head up to the CRLFCRLF terminator; the ws frames start right after.
    private fun readHandshakeResponse(input: InputStream): String {
        val sb = StringBuilder()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
            last4 = (last4 shl 8) or (b and 0xFF)
            if (last4 == 0x0D0A0D0A) break
        }
        return sb.toString()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 60000

        // Production connector: a system-trust TLS socket to the user's Gemini host, handshaken and ready to read.
        private fun defaultConnect(host: String, port: Int): Socket =
            (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket().let { it as SSLSocket }.apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                soTimeout = READ_TIMEOUT_MS
                startHandshake()
            }
    }
}

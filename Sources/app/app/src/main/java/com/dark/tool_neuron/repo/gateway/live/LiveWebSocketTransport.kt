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
internal class LiveWebSocketTransport {

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    private val writeLock = Any()

    sealed interface Incoming {
        data class Text(val text: String) : Incoming
        data class Binary(val bytes: ByteArray) : Incoming
        data class Closed(val code: Int, val reason: String) : Incoming
    }

    class HandshakeException(val httpStatus: Int, message: String) : Exception(message)

    // Opens the TLS socket + WebSocket upgrade, then emits every inbound message frame until close.
    // onReady fires once, right after a successful upgrade and before the read loop, so the caller can
    // send the mandatory first frame (Gemini's `setup`) before any server message is expected.
    // Blocking socket I/O runs on Dispatchers.IO; cancelling the collector tears the socket down.
    fun open(host: String, port: Int, path: String, onReady: () -> Unit = {}): Flow<Incoming> = callbackFlow {
        val key = WebSocketHandshake.nonce()
        val sock = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket() as SSLSocket
        try {
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            sock.soTimeout = READ_TIMEOUT_MS
            sock.startHandshake()
            val out = sock.outputStream
            val input = BufferedInputStream(sock.inputStream)

            out.write(WebSocketHandshake.request(host, path, key).toByteArray(Charsets.US_ASCII))
            out.flush()

            val head = readHandshakeResponse(input)
            if (!WebSocketHandshake.isValidResponse(head, key)) {
                val status = WebSocketHandshake.statusCode(head)
                throw HandshakeException(status, "WebSocket upgrade rejected (HTTP $status)")
            }

            socket = sock
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
                                trySend(Incoming.Binary(binaryAcc.toByteArray()))
                                binaryAcc.reset()
                            } else {
                                trySend(Incoming.Text(payload.toString()))
                                payload.setLength(0)
                            }
                        }
                    }
                    WebSocketFrame.OPCODE_PING -> writeControl(WebSocketFrame.OPCODE_PONG, frame.payload)
                    WebSocketFrame.OPCODE_PONG -> {}
                    WebSocketFrame.OPCODE_CLOSE -> {
                        val (code, reason) = WebSocketFrame.parseClose(frame.payload)
                        trySend(Incoming.Closed(code, reason))
                        break
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close()
            throw t
        }

        awaitClose { close() }
    }.flowOn(Dispatchers.IO)

    // Client→server frames are always masked per RFC 6455 §5.3; the write lock keeps concurrent sends whole.
    fun sendText(text: String) {
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

    fun close() {
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
    }
}

package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.util.concurrent.atomic.AtomicBoolean

// Hand-rolled RFC 6455 client over a system-trust TLS socket (repo has no WebSocket dep); connect is injectable so a loopback test drives the real read/close path.
internal class LiveWebSocketTransport(
    private val connect: (host: String, port: Int) -> Socket = ::defaultConnect,
) : LiveTransport {

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var emitIncoming: ((LiveTransport.Incoming) -> Unit)? = null
    private val writeLock = Any()

    // onReady fires once after a successful upgrade, before the read loop, so the caller sends Gemini's mandatory `setup` first frame.
    override fun open(host: String, port: Int, path: String, onReady: () -> Unit): Flow<LiveTransport.Incoming> = callbackFlow {
        val key = WebSocketHandshake.nonce()
        emitIncoming = { trySend(it) }
        try {
            val sock = connect(host, port)
            socket = sock // set early so a handshake failure still tears the socket down
            val out = sock.outputStream
            val input = BufferedInputStream(sock.inputStream)

            out.write(WebSocketHandshake.request(host, port, path, key).toByteArray(Charsets.US_ASCII))
            out.flush()

            val head = readHandshakeResponse(input)
            if (!WebSocketHandshake.isValidResponse(head, key)) {
                val status = WebSocketHandshake.statusCode(head)
                throw LiveTransport.HandshakeException(status, "WebSocket upgrade rejected (HTTP $status)")
            }

            output = out
            onReady()

            readLoop(input)
        } catch (ce: CancellationException) {
            throw ce
        } catch (pe: WebSocketFrame.ProtocolException) {
            // A peer protocol violation: close with 1002 then let the engine classify it as PROTOCOL.
            closeWith(1002)
            throw pe
        } catch (t: Throwable) {
            this@LiveWebSocketTransport.close()
            // A read failure caused by our own write-failure close: TransportError already emitted, complete normally.
            if (writeError == null) throw t
        }

        // Peer close / EOF ended the read loop: close the socket AND the producer channel (unqualified close()) so the engine's collect returns instead of hanging in awaitClose.
        this@LiveWebSocketTransport.close()
        close()
        awaitClose { this@LiveWebSocketTransport.close() }
    }.flowOn(Dispatchers.IO)

    // RFC 6455 assembly: orphan continuation / interleaved data frame reject, aggregate capped, UTF-8 decoded at FIN.
    private fun readLoop(input: InputStream) {
        val messageBytes = ByteArrayOutputStream()
        var messageIsBinary = false
        var fragmentOpen = false
        while (!closed.get()) {
            val frame = WebSocketFrame.readFrame(input) ?: break
            when (frame.opcode) {
                WebSocketFrame.OPCODE_TEXT, WebSocketFrame.OPCODE_BINARY -> {
                    if (fragmentOpen) throw WebSocketFrame.ProtocolException("data frame during open fragment")
                    messageBytes.reset()
                    messageIsBinary = frame.opcode == WebSocketFrame.OPCODE_BINARY
                    appendCapped(messageBytes, frame.payload)
                    if (frame.fin) emitMessage(messageBytes, messageIsBinary) else fragmentOpen = true
                }
                WebSocketFrame.OPCODE_CONTINUATION -> {
                    if (!fragmentOpen) throw WebSocketFrame.ProtocolException("orphan continuation")
                    appendCapped(messageBytes, frame.payload)
                    if (frame.fin) { emitMessage(messageBytes, messageIsBinary); fragmentOpen = false }
                }
                WebSocketFrame.OPCODE_PING -> writeControl(WebSocketFrame.OPCODE_PONG, frame.payload)
                WebSocketFrame.OPCODE_PONG -> {}
                WebSocketFrame.OPCODE_CLOSE -> {
                    val (code, reason) = WebSocketFrame.parseClose(frame.payload)
                    emitIncoming?.invoke(LiveTransport.Incoming.Closed(code, reason))
                    // Echo the received status (1005 no-status → 1000) as the close-handshake reply.
                    closeWith(if (code == 1005) 1000 else code)
                    break
                }
            }
        }
    }

    private fun appendCapped(acc: ByteArrayOutputStream, payload: ByteArray) {
        if (acc.size().toLong() + payload.size > MAX_MESSAGE_BYTES) {
            throw WebSocketFrame.ProtocolException("aggregate message exceeds $MAX_MESSAGE_BYTES bytes")
        }
        acc.write(payload)
    }

    private fun emitMessage(acc: ByteArrayOutputStream, binary: Boolean) {
        val bytes = acc.toByteArray()
        if (binary) emitIncoming?.invoke(LiveTransport.Incoming.Binary(bytes))
        else emitIncoming?.invoke(LiveTransport.Incoming.Text(String(bytes, Charsets.UTF_8)))
        acc.reset()
    }

    @Volatile private var writeError: Throwable? = null
    // Test/diagnostic: a send failed and tore the transport down (a silently swallowed send is the regression).
    internal val lastWriteFailed: Boolean get() = writeError != null

    // Client frames are masked (§5.3), write-locked whole; a write failure surfaces TransportError + closes (not swallowed).
    override fun sendText(text: String) = writeFrame(WebSocketFrame.OPCODE_TEXT, text.toByteArray(Charsets.UTF_8))

    private fun writeControl(opcode: Int, payload: ByteArray) = writeFrame(opcode, payload)

    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val out = output ?: return
        synchronized(writeLock) {
            try {
                out.write(WebSocketFrame.encode(opcode, payload))
                out.flush()
            } catch (t: Throwable) {
                writeError = t
                emitIncoming?.invoke(LiveTransport.Incoming.TransportError(t))
                close()
            }
        }
    }

    override fun close() = closeWith(1000)

    private fun closeWith(code: Int) {
        if (!closed.compareAndSet(false, true)) return
        val out = output
        synchronized(writeLock) {
            runCatching {
                out?.write(WebSocketFrame.encode(WebSocketFrame.OPCODE_CLOSE, WebSocketFrame.closePayload(code, "")))
                out?.flush()
            }
        }
        runCatching { socket?.close() }
        socket = null
        output = null
    }

    // Read the HTTP response head up to the CRLFCRLF terminator, capped so a hostile peer can't grow it unbounded.
    private fun readHandshakeResponse(input: InputStream): String {
        val sb = StringBuilder()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
            if (sb.length > MAX_HANDSHAKE_HEAD_BYTES) throw IOException("handshake response head too large")
            last4 = (last4 shl 8) or (b and 0xFF)
            if (last4 == 0x0D0A0D0A) break
        }
        return sb.toString()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 60000
        private const val MAX_HANDSHAKE_HEAD_BYTES = 16 * 1024
        private const val MAX_MESSAGE_BYTES = 16L * 1024 * 1024

        // RFC 2818 hostname verification: without it a raw SSLSocket checks the cert chain but not the host (MITM).
        internal fun hardenTls(socket: SSLSocket): SSLSocket = socket.apply {
            sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        }

        // Production connector: a system-trust, hostname-verified TLS socket to the user's Gemini host.
        private fun defaultConnect(host: String, port: Int): Socket =
            hardenTls((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket() as SSLSocket).apply {
                connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                soTimeout = READ_TIMEOUT_MS
                startHandshake()
            }
    }
}

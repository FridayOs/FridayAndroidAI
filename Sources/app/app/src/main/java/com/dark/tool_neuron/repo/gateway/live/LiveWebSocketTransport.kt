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

// Hand-rolled RFC 6455 client over a system-trust TLS socket (repo has no WebSocket dep); connect is injectable so a loopback test drives the real read/close path.
internal class LiveWebSocketTransport(
    private val connect: (host: String, port: Int) -> Socket = ::defaultConnect,
) : LiveTransport {

    private val closed = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    private val writeLock = Any()

    // onReady fires once after a successful upgrade, before the read loop, so the caller sends Gemini's mandatory `setup` first frame.
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

            val messageBytes = java.io.ByteArrayOutputStream()
            var messageIsBinary = false
            while (!closed.get()) {
                val frame = WebSocketFrame.readFrame(input) ?: break
                when (frame.opcode) {
                    WebSocketFrame.OPCODE_TEXT, WebSocketFrame.OPCODE_BINARY,
                    WebSocketFrame.OPCODE_CONTINUATION -> {
                        // Decode UTF-8 only at FIN so a multi-byte code point split across fragments isn't corrupted.
                        if (frame.opcode == WebSocketFrame.OPCODE_TEXT) { messageBytes.reset(); messageIsBinary = false }
                        if (frame.opcode == WebSocketFrame.OPCODE_BINARY) { messageBytes.reset(); messageIsBinary = true }
                        messageBytes.write(frame.payload)
                        if (frame.fin) {
                            val bytes = messageBytes.toByteArray()
                            if (messageIsBinary) trySend(LiveTransport.Incoming.Binary(bytes))
                            else trySend(LiveTransport.Incoming.Text(String(bytes, Charsets.UTF_8)))
                            messageBytes.reset()
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

        // Peer close / EOF ended the read loop: close the socket AND the producer channel (unqualified close()) so the engine's collect returns instead of hanging in awaitClose.
        this@LiveWebSocketTransport.close()
        close()
        awaitClose { this@LiveWebSocketTransport.close() }
    }.flowOn(Dispatchers.IO)

    @Volatile private var writeError: Throwable? = null
    // Test/diagnostic: a send failed and tore the transport down (a silently swallowed send is the regression).
    internal val lastWriteFailed: Boolean get() = writeError != null

    // Client frames are masked (§5.3), write-locked whole; a write failure closes the transport (not swallowed) so the engine classifies the drop.
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
                close()
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

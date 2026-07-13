package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.flow.Flow

// Transport seam so the session engine's connect → configure → stream → teardown flow is unit-testable off-device
// against a fake that scripts inbound frames (a real TLS WebSocket can't run in a JVM test). LiveWebSocketTransport
// is the hand-rolled RFC 6455 implementation over an SSLSocket.
interface LiveTransport {
    // onReady fires once, right after a successful upgrade and before any server frame, so the caller sends the
    // mandatory Gemini `setup` first frame. Emits every inbound message frame until close.
    fun open(host: String, port: Int, path: String, onReady: () -> Unit = {}): Flow<Incoming>
    fun sendText(text: String)
    fun close()

    sealed interface Incoming {
        data class Text(val text: String) : Incoming
        data class Binary(val bytes: ByteArray) : Incoming {
            override fun equals(other: Any?): Boolean = other is Binary && bytes.contentEquals(other.bytes)
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        // code follows RFC 6455 §7.4: 1000/1005 are clean ends; anything else is an abnormal drop.
        data class Closed(val code: Int, val reason: String) : Incoming
    }

    // Carries the pre-upgrade HTTP status so the engine classifies 401/403/404/429 into the retry taxonomy.
    class HandshakeException(val httpStatus: Int, message: String) : Exception(message)
}

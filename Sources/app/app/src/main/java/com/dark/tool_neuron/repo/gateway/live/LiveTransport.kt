package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.flow.Flow

// Transport seam so the engine's connect/configure/stream/teardown flow is unit-testable against a scripted fake.
interface LiveTransport {
    // onReady fires once after a successful upgrade, before any server frame, so the caller sends Gemini's `setup` first.
    fun open(host: String, port: Int, path: String, onReady: () -> Unit = {}): Flow<Incoming>
    fun sendText(text: String)
    fun close()

    sealed interface Incoming {
        data class Text(val text: String) : Incoming
        data class Binary(val bytes: ByteArray) : Incoming {
            override fun equals(other: Any?): Boolean = other is Binary && bytes.contentEquals(other.bytes)
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        // Only close code 1000 is a clean end; 1005 (no-status sentinel) and any other code are abnormal drops.
        data class Closed(val code: Int, val reason: String) : Incoming
        // A client-side send failed (broken pipe): surfaced so the engine classifies the cause instead of a silent EOF.
        data class TransportError(val cause: Throwable) : Incoming
    }

    // Carries the pre-upgrade HTTP status so the engine classifies 401/403/404/429 into the retry taxonomy.
    class HandshakeException(val httpStatus: Int, message: String) : Exception(message)
}

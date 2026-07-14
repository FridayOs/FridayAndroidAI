package com.dark.tool_neuron.repo.gateway.live

import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

// Pure RFC 6455 §4 opening-handshake builder + validator, separate from the socket so it's unit-testable off-device.
internal object WebSocketHandshake {

    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun nonce(rng: Random = Random.Default): String =
        Base64.getEncoder().encodeToString(ByteArray(16).also { rng.nextBytes(it) })

    // The GET upgrade request. Host authority carries the port when it isn't the default 443. path includes ?key=.
    fun request(host: String, port: Int, path: String, key: String): String = buildString {
        val authority = if (port == 443) host else "$host:$port"
        append("GET ").append(path).append(" HTTP/1.1\r\n")
        append("Host: ").append(authority).append("\r\n")
        append("Upgrade: websocket\r\n")
        append("Connection: Upgrade\r\n")
        append("Sec-WebSocket-Key: ").append(key).append("\r\n")
        append("Sec-WebSocket-Version: 13\r\n")
        append("\r\n")
    }

    fun expectedAccept(key: String): String {
        val sha = MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(sha)
    }

    // True only when the response is 101 with Upgrade: websocket + Connection: Upgrade AND a matching Accept digest.
    fun isValidResponse(responseHeaders: String, key: String): Boolean {
        val lines = responseHeaders.split("\r\n")
        if (lines.firstOrNull()?.contains(" 101") != true) return false
        if (!headerHasToken(lines, "Upgrade", "websocket")) return false
        if (!headerHasToken(lines, "Connection", "upgrade")) return false
        val accept = lines.firstOrNull { it.startsWith("Sec-WebSocket-Accept:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
        return accept == expectedAccept(key)
    }

    // RFC 6455 §4.1: match whole comma-separated tokens case-insensitively — "notupgrade"/"websocketevil" must NOT pass a substring check.
    private fun headerHasToken(lines: List<String>, name: String, token: String): Boolean =
        lines.filter { it.startsWith("$name:", ignoreCase = true) }
            .flatMap { it.substringAfter(':').split(',') }
            .any { it.trim().equals(token, ignoreCase = true) }

    // Extract the HTTP status code from the response head so the transport classifies 401/403/404/429 before upgrade.
    fun statusCode(responseHeaders: String): Int {
        val statusLine = responseHeaders.split("\r\n").firstOrNull() ?: return 0
        return statusLine.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
    }
}

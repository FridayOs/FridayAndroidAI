package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.WebSocketHandshake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

// RFC 6455 §4 handshake proof: request framing, the SHA1(key+GUID) accept check, and status extraction.
class WebSocketHandshakeTest {

    @Test
    fun request_hasUpgradeHeaders_andOmitsPort443() {
        val req = WebSocketHandshake.request("host.example", 443, "/ws?key=abc", "dGhlIHNhbXBsZSBub25jZQ==")
        assertTrue(req.startsWith("GET /ws?key=abc HTTP/1.1\r\n"))
        assertTrue("Host omits the default 443", req.contains("Host: host.example\r\n"))
        assertTrue(req.contains("Upgrade: websocket\r\n"))
        assertTrue(req.contains("Connection: Upgrade\r\n"))
        assertTrue(req.contains("Sec-WebSocket-Version: 13\r\n"))
        assertTrue(req.endsWith("\r\n\r\n"))
    }

    @Test
    fun request_includesNonDefaultPortInHostAuthority() {
        val req = WebSocketHandshake.request("my.proxy.example", 8443, "/v1beta/ws?key=abc", "dGhlIHNhbXBsZSBub25jZQ==")
        assertTrue("custom port must ride the Host authority", req.contains("Host: my.proxy.example:8443\r\n"))
    }

    @Test
    fun expectedAccept_matchesRfcExample() {
        // RFC 6455 §1.3 worked example: key "dGhlIHNhbXBsZSBub25jZQ==" → "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
        assertEquals(
            "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            WebSocketHandshake.expectedAccept("dGhlIHNhbXBsZSBub25jZQ=="),
        )
    }

    @Test
    fun isValidResponse_trueOn101WithMatchingAccept() {
        val key = "dGhlIHNhbXBsZSBub25jZQ=="
        val head = "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${WebSocketHandshake.expectedAccept(key)}\r\n\r\n"
        assertTrue(WebSocketHandshake.isValidResponse(head, key))
    }

    @Test
    fun isValidResponse_falseWhenMissingUpgradeOrConnectionHeaders() {
        val key = "dGhlIHNhbXBsZSBub25jZQ=="
        val accept = WebSocketHandshake.expectedAccept(key)
        val noUpgrade = "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n"
        val noConnection = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nSec-WebSocket-Accept: $accept\r\n\r\n"
        assertFalse(WebSocketHandshake.isValidResponse(noUpgrade, key))
        assertFalse(WebSocketHandshake.isValidResponse(noConnection, key))
    }

    @Test
    fun isValidResponse_falseOnBadAccept() {
        val head = "HTTP/1.1 101 Switching Protocols\r\nSec-WebSocket-Accept: wrong\r\n\r\n"
        assertFalse(WebSocketHandshake.isValidResponse(head, "dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test
    fun isValidResponse_falseOnNon101() {
        val key = "dGhlIHNhbXBsZSBub25jZQ=="
        val head = "HTTP/1.1 401 Unauthorized\r\nSec-WebSocket-Accept: ${WebSocketHandshake.expectedAccept(key)}\r\n\r\n"
        assertFalse(WebSocketHandshake.isValidResponse(head, key))
    }

    @Test
    fun statusCode_extractsHttpCode() {
        assertEquals(401, WebSocketHandshake.statusCode("HTTP/1.1 401 Unauthorized\r\n\r\n"))
        assertEquals(101, WebSocketHandshake.statusCode("HTTP/1.1 101 Switching Protocols\r\n\r\n"))
        assertEquals(429, WebSocketHandshake.statusCode("HTTP/1.1 429 Too Many Requests\r\n\r\n"))
    }

    @Test
    fun nonce_is16BytesBase64() {
        val n = WebSocketHandshake.nonce(Random(1))
        // 16 raw bytes base64-encode to 24 chars with '==' padding.
        assertEquals(24, n.length)
        assertTrue(n.endsWith("=="))
    }
}

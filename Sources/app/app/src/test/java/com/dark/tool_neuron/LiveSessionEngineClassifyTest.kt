package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveSessionEngine
import com.dark.tool_neuron.repo.gateway.live.LiveWebSocketTransport
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

// Transport-failure → retry taxonomy: auth/model/quota surface (no loop); DNS/TLS/timeout/IO become transient reconnects.
class LiveSessionEngineClassifyTest {

    @Test
    fun handshake401IsAuth() {
        val t = LiveWebSocketTransport.HandshakeException(401, "unauthorized")
        assertEquals(LiveErrorKind.AUTH, LiveSessionEngine.classifyTransport(t))
    }

    @Test
    fun handshake404IsInvalidModel() {
        val t = LiveWebSocketTransport.HandshakeException(404, "model not found")
        assertEquals(LiveErrorKind.INVALID_MODEL, LiveSessionEngine.classifyTransport(t))
    }

    @Test
    fun handshake429IsQuota() {
        val t = LiveWebSocketTransport.HandshakeException(429, "rate limited")
        assertEquals(LiveErrorKind.QUOTA, LiveSessionEngine.classifyTransport(t))
    }

    @Test
    fun handshake503IsRemoteClose() {
        val t = LiveWebSocketTransport.HandshakeException(503, "unavailable")
        assertEquals(LiveErrorKind.REMOTE_CLOSE, LiveSessionEngine.classifyTransport(t))
    }

    @Test
    fun timeoutIsTimeout() {
        assertEquals(LiveErrorKind.TIMEOUT, LiveSessionEngine.classifyTransport(SocketTimeoutException()))
    }

    @Test
    fun unknownHostIsNetwork() {
        assertEquals(LiveErrorKind.NETWORK, LiveSessionEngine.classifyTransport(UnknownHostException()))
    }

    @Test
    fun sslIsNetwork() {
        assertEquals(LiveErrorKind.NETWORK, LiveSessionEngine.classifyTransport(SSLException("bad cert")))
    }

    @Test
    fun ioIsNetwork() {
        assertEquals(LiveErrorKind.NETWORK, LiveSessionEngine.classifyTransport(IOException("reset")))
    }

    @Test
    fun otherIsProtocol() {
        assertEquals(LiveErrorKind.PROTOCOL, LiveSessionEngine.classifyTransport(IllegalStateException("weird")))
    }

    // Only NETWORK/TIMEOUT/REMOTE_CLOSE retry — auth/model/quota/permission never loop.
    @Test
    fun transientFlagMatchesRetryTaxonomy() {
        assertEquals(true, LiveErrorKind.NETWORK.transient)
        assertEquals(true, LiveErrorKind.TIMEOUT.transient)
        assertEquals(true, LiveErrorKind.REMOTE_CLOSE.transient)
        assertEquals(false, LiveErrorKind.AUTH.transient)
        assertEquals(false, LiveErrorKind.INVALID_MODEL.transient)
        assertEquals(false, LiveErrorKind.QUOTA.transient)
        assertEquals(false, LiveErrorKind.PERMISSION.transient)
    }
}

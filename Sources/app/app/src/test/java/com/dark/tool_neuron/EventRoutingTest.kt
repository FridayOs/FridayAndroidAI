package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.EventRouting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 (B2 fix): routing must send ANY payload carrying an envelope-distinctive key to
// EventGateway -- even a partial/malformed one -- rather than requiring all of
// signature+nonce+version. A partial signed payload falling through to the legacy unverified
// `fromPushData` path would let it publish without verification.
class EventRoutingTest {

    private val legacyPush = mapOf(
        "eventId" to "e1", "title" to "t", "body" to "b", "kind" to "task",
        "urgency" to "normal", "correlationId" to "c1",
    )

    @Test
    fun pureLegacyPush_routesLegacy() {
        assertFalse(EventRouting.isSignedEnvelopeNamespace(legacyPush))
    }

    @Test
    fun emptyPayload_routesLegacy() {
        assertFalse(EventRouting.isSignedEnvelopeNamespace(emptyMap()))
    }

    @Test
    fun eachDistinctiveKeyAlone_routesToGateway() {
        val keys = listOf("signature", "nonce", "sourceId", "sourceKind", "version", "type")
        for (key in keys) {
            val data = mapOf(key to "x")
            assertTrue("key '$key' alone must route to gateway", EventRouting.isSignedEnvelopeNamespace(data))
        }
    }

    @Test
    fun partialEnvelope_signatureButNoNonce_routesToGateway_notLegacy() {
        val partial = mapOf(
            "version" to "1", "eventId" to "ev-1", "sourceId" to "openclaw-1",
            "sourceKind" to "OPENCLAW", "signature" to "deadbeef",
            // nonce intentionally missing
        )
        assertTrue(EventRouting.isSignedEnvelopeNamespace(partial))
    }

    @Test
    fun blankValues_forDistinctiveKeys_doNotCountAsPresent() {
        val data = mapOf("signature" to "", "nonce" to "   ", "eventId" to "e1", "title" to "t")
        assertFalse(EventRouting.isSignedEnvelopeNamespace(data))
    }

    @Test
    fun fullEnvelope_routesToGateway() {
        val full = legacyPush + mapOf(
            "version" to "1", "sourceId" to "openclaw-1", "sourceKind" to "OPENCLAW",
            "nonce" to "n-1", "type" to "TASK_ASSIGNED", "signature" to "deadbeef",
        )
        assertTrue(EventRouting.isSignedEnvelopeNamespace(full))
    }
}

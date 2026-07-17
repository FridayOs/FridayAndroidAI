package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.EventSourceKind
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import com.dark.tool_neuron.repo.gateway.event.CorrelationTracker
import com.dark.tool_neuron.repo.gateway.event.DedupeStore
import com.dark.tool_neuron.repo.gateway.event.EventDeliverySink
import com.dark.tool_neuron.repo.gateway.event.EventGateway
import com.dark.tool_neuron.repo.gateway.event.EventSourceRegistry
import com.dark.tool_neuron.repo.gateway.event.EventSourceTrust
import com.dark.tool_neuron.repo.gateway.event.InboundEventConsent
import com.dark.tool_neuron.repo.gateway.event.InboundEventPolicy
import com.dark.tool_neuron.repo.gateway.event.InboundEventVerifier
import com.dark.tool_neuron.repo.gateway.event.NonceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// FRI-555 orchestrator: consent -> parse -> verify -> policy -> deliver, fail-closed at every
// stage. Uses the real verifier/policy (no seam interface for them) wired to fakes for their own
// collaborators (registry, consent, sink) so the pipeline wiring itself is under test.
class EventGatewayTest {

    private val key = "shared-secret-key".toByteArray(Charsets.UTF_8)
    private val fixedNow = 10_000L

    private class FakeConsent(private val enabled: Boolean) : InboundEventConsent {
        override fun inboundEnabled(): Boolean = enabled
    }

    private class FakeRegistry(private val trust: EventSourceTrust?) : EventSourceRegistry {
        override fun trustFor(sourceId: String): EventSourceTrust? = trust
    }

    private class RecordingSink : EventDeliverySink {
        val published = mutableListOf<InboundEvent>()
        val dismissedCorrelationIds = mutableListOf<String>()
        var dismissResult = true

        override fun publish(event: InboundEvent) {
            published += event
        }

        override fun dismissIfCorrelated(correlationId: String): Boolean {
            dismissedCorrelationIds += correlationId
            return dismissResult
        }
    }

    private fun hmacHex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    // Builds a fully-signed raw FCM payload; overrides are applied before signing so any field
    // tweak (including a deliberately wrong signature via a post-sign override) is reflected.
    private fun rawPayload(overrides: Map<String, String> = emptyMap(), sign: Boolean = true): Map<String, String> {
        val base = mapOf(
            "version" to "1",
            "eventId" to "ev-1",
            "sourceId" to "openclaw-1",
            "sourceKind" to "OPENCLAW",
            "issuedAt" to fixedNow.toString(),
            "nonce" to "n-1",
            "type" to "TASK_ASSIGNED",
            "correlationId" to "c-1",
            "title" to "Task ready",
            "body" to "Do the thing",
            "urgency" to "NORMAL",
            "signature" to "placeholder",
        )
        val merged = base + overrides
        if (!sign) return merged
        val env = InboundEventEnvelope.parse(merged)!!
        return merged + ("signature" to hmacHex(key, env.canonicalBytes()))
    }

    private fun buildGateway(
        consentEnabled: Boolean,
        registry: EventSourceRegistry = FakeRegistry(
            EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true),
        ),
        sink: RecordingSink = RecordingSink(),
        correlationTracker: CorrelationTracker = CorrelationTracker(),
    ): Pair<EventGateway, RecordingSink> {
        val verifier = InboundEventVerifier(registry, NonceStore(FakeEventStateStore()))
        val policy = InboundEventPolicy(DedupeStore(FakeEventStateStore()))
        val gateway = EventGateway(FakeConsent(consentEnabled), verifier, policy, sink, correlationTracker) { fixedNow }
        return gateway to sink
    }

    @Test
    fun accept_consentDisabled_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = false)
        val result = gateway.accept(rawPayload())
        assertFalse(result)
        assertTrue(sink.published.isEmpty())
        assertTrue(sink.dismissedCorrelationIds.isEmpty())
    }

    @Test
    fun accept_malformedPayload_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val malformed = rawPayload().minus("title")
        val result = gateway.accept(malformed)
        assertFalse(result)
        assertTrue(sink.published.isEmpty())
    }

    @Test
    fun accept_verificationRejected_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val badSig = rawPayload(overrides = mapOf("signature" to "deadbeef"), sign = false)
        val result = gateway.accept(badSig)
        assertFalse(result)
        assertTrue(sink.published.isEmpty())
    }

    @Test
    fun accept_delivers_publishesToSink_returnsTrue() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val result = gateway.accept(rawPayload())
        assertTrue(result)
        assertEquals(1, sink.published.size)
        assertEquals("ev-1", sink.published[0].eventId)
    }

    @Test
    fun accept_cancelWithMatchingCorrelation_dismissesAndReturnsTrue() {
        val sink = RecordingSink()
        val (gateway, _) = buildGateway(consentEnabled = true, sink = sink)
        val cancelPayload = rawPayload(overrides = mapOf("type" to "CANCEL", "correlationId" to "c-1"))
        val result = gateway.accept(cancelPayload)
        assertTrue(result)
        assertEquals(listOf("c-1"), sink.dismissedCorrelationIds)
        assertTrue(sink.published.isEmpty())
    }

    @Test
    fun accept_cancelWithNullCorrelation_returnsFalse_dismissNeverCalled() {
        val sink = RecordingSink()
        val (gateway, _) = buildGateway(consentEnabled = true, sink = sink)
        val cancelPayload = rawPayload(overrides = mapOf("type" to "CANCEL")).minus("correlationId")
        val result = gateway.accept(cancelPayload)
        assertFalse(result)
        assertTrue(sink.dismissedCorrelationIds.isEmpty())
    }

    @Test
    fun accept_redeliveryWithDifferentNonce_sameEventId_dropsSecondAsDuplicate() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val first = gateway.accept(rawPayload(overrides = mapOf("nonce" to "n-1")))
        assertTrue(first)
        val second = gateway.accept(rawPayload(overrides = mapOf("nonce" to "n-2")))
        assertFalse(second)
        assertEquals(1, sink.published.size)
    }

    // FRI-555 B2: a partial envelope (routed to the gateway per EventRouting because it carries
    // SOME envelope-distinctive key) must still fail closed at parse -- missing a required field
    // means InboundEventEnvelope.parse returns null, so accept() must return false with no
    // publish, regardless of which field is missing.
    @Test
    fun accept_partialEnvelope_missingNonce_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val partial = rawPayload().minus("nonce")
        assertFalse(gateway.accept(partial))
        assertTrue(sink.published.isEmpty())
    }

    @Test
    fun accept_partialEnvelope_missingVersion_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val partial = rawPayload().minus("version")
        assertFalse(gateway.accept(partial))
        assertTrue(sink.published.isEmpty())
    }

    @Test
    fun accept_partialEnvelope_missingSourceId_returnsFalse_noSinkCall() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val partial = rawPayload().minus("sourceId")
        assertFalse(gateway.accept(partial))
        assertTrue(sink.published.isEmpty())
    }

    // FRI-555 B3: a same-correlation envelope that is not strictly newer than the last accepted one
    // (e.g. a delayed PROGRESS arriving after a later-issued one) must drop with no publish, distinct
    // from dedupe (different eventId here, so DedupeStore alone would accept it).
    @Test
    fun accept_outOfOrderCorrelation_dropsStale_noPublish() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val first = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-1", "nonce" to "n-1", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue(first)
        val second = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-2", "nonce" to "n-2", "correlationId" to "c-1", "issuedAt" to "9000")),
        )
        assertFalse(second)
        assertEquals(1, sink.published.size)
    }

    @Test
    fun accept_equalTimestampSameCorrelation_dropsStale() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val first = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-1", "nonce" to "n-1", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue(first)
        val second = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-2", "nonce" to "n-2", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertFalse("not strictly newer must be rejected as stale", second)
        assertEquals(1, sink.published.size)
    }

    // FRI-555 B3: a verified CANCEL must clear the correlation tracker's key, so a subsequent
    // envelope for the same correlation is judged fresh again (a new task can reuse the correlation).
    // The CANCEL itself must be strictly newer than the last accepted envelope (same ordering guard
    // as Deliver) to be accepted in the first place -- see B3-rework tests below for the stale case.
    @Test
    fun accept_cancelClearsCorrelationTracker_subsequentEqualTimestampAccepted() {
        val (gateway, sink) = buildGateway(consentEnabled = true)
        val first = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-1", "nonce" to "n-1", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue(first)
        val cancel = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-2", "nonce" to "n-2", "type" to "CANCEL", "correlationId" to "c-1", "issuedAt" to "10001")),
        )
        assertTrue(cancel)
        val third = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-3", "nonce" to "n-3", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue("tracker cleared by cancel, so a timestamp equal to the original first is accepted again", third)
        assertEquals(2, sink.published.size)
    }

    // FRI-555 B3-rework (Codex QA H1): a legitimately-signed, non-replay CANCEL that arrives
    // network-reordered *after* a newer envelope for the same correlation (issuedAt <= last
    // accepted) must be dropped with no side effect -- it must not tear down an already-progressed
    // task just because it happens to be a CANCEL.
    @Test
    fun accept_staleCancelAfterNewerEnvelope_dropsNoSideEffect() {
        val sink = RecordingSink()
        val (gateway, _) = buildGateway(consentEnabled = true, sink = sink)
        val newer = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-1", "nonce" to "n-1", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue(newer)
        val staleCancel = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-2", "nonce" to "n-2", "type" to "CANCEL", "correlationId" to "c-1", "issuedAt" to "9000")),
        )
        assertFalse("a cancel not strictly newer than the last accepted envelope must be dropped as stale", staleCancel)
        assertTrue("stale cancel must never reach the sink", sink.dismissedCorrelationIds.isEmpty())
    }

    // FRI-555 B3-rework (Codex QA H1): a CANCEL strictly newer than the last accepted envelope for
    // the same correlation is still accepted and dismisses normally.
    @Test
    fun accept_newerCancelAfterPriorEnvelope_stillCancels() {
        val sink = RecordingSink()
        val (gateway, _) = buildGateway(consentEnabled = true, sink = sink)
        val first = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-1", "nonce" to "n-1", "correlationId" to "c-1", "issuedAt" to "10000")),
        )
        assertTrue(first)
        val newerCancel = gateway.accept(
            rawPayload(overrides = mapOf("eventId" to "ev-2", "nonce" to "n-2", "type" to "CANCEL", "correlationId" to "c-1", "issuedAt" to "10001")),
        )
        assertTrue("a cancel strictly newer than the last accepted envelope must still cancel", newerCancel)
        assertEquals(listOf("c-1"), sink.dismissedCorrelationIds)
    }
}

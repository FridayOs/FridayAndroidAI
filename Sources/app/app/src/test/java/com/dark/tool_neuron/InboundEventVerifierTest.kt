package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.EventSourceKind
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import com.dark.tool_neuron.model.friday.InboundEventType
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.EventSourceRegistry
import com.dark.tool_neuron.repo.gateway.event.EventSourceTrust
import com.dark.tool_neuron.repo.gateway.event.InboundEventVerifier
import com.dark.tool_neuron.repo.gateway.event.NonceStore
import com.dark.tool_neuron.repo.gateway.event.VerifyResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// FRI-555 verifier: HMAC-SHA256 signature check + registry trust gate + timestamp skew + replay,
// all fail-closed. Every case here must reject without throwing and without consuming a nonce
// slot on paths that fail before the nonce check (order asserted implicitly by the replay test).
class InboundEventVerifierTest {

    private val key = "shared-secret-key".toByteArray(Charsets.UTF_8)

    private class FakeRegistry(private val trust: EventSourceTrust?) : EventSourceRegistry {
        override fun trustFor(sourceId: String): EventSourceTrust? = trust
    }

    private fun hmacHex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    private fun envelope(
        key: ByteArray,
        sourceId: String = "openclaw-1",
        sourceKind: EventSourceKind = EventSourceKind.OPENCLAW,
        issuedAtMs: Long = 10_000L,
        nonce: String = "n-1",
        type: InboundEventType = InboundEventType.TASK_ASSIGNED,
        version: Int = 1,
    ): InboundEventEnvelope {
        val unsigned = InboundEventEnvelope(
            version = version,
            eventId = "ev-1",
            sourceId = sourceId,
            sourceKind = sourceKind,
            issuedAtMs = issuedAtMs,
            nonce = nonce,
            type = type,
            correlationId = null,
            title = "Task ready",
            body = "Do the thing",
            urgency = InboundUrgency.NORMAL,
            actionIntent = null,
            signature = "",
        )
        return unsigned.copy(signature = hmacHex(key, unsigned.canonicalBytes()))
    }

    @Test
    fun verify_validEnvelope_accepted() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key), nowMs = 10_000L)
        assertEquals(VerifyResult.Accepted, result)
    }

    // FRI-555 B5: version contract -- unsupported versions rejected before any registry/signature
    // work; only SUPPORTED_VERSION (1) passes.
    @Test
    fun verify_unsupportedVersion_zero_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key, version = 0), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("unsupported_version"), result)
    }

    @Test
    fun verify_unsupportedVersion_two_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key, version = 2), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("unsupported_version"), result)
    }

    @Test
    fun verify_unsupportedVersion_999_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key, version = 999), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("unsupported_version"), result)
    }

    @Test
    fun verify_unknownSource_rejected() {
        val verifier = InboundEventVerifier(FakeRegistry(null), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("unknown_source"), result)
    }

    @Test
    fun verify_disabledSource_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = false)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("disabled_source"), result)
    }

    @Test
    fun verify_sourceKindMismatch_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.HERMES, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key, sourceKind = EventSourceKind.OPENCLAW), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("source_kind_mismatch"), result)
    }

    @Test
    fun verify_missingTrustKey_rejected_noThrow() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, ByteArray(0), enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val result = verifier.verify(envelope(key), nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("missing_trust_key"), result)
    }

    @Test
    fun verify_badSignature_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()))
        val tampered = envelope(key).copy(body = "tampered body, signature stale now")
        val result = verifier.verify(tampered, nowMs = 10_000L)
        assertEquals(VerifyResult.Rejected("bad_signature"), result)
    }

    @Test
    fun verify_expiredTimestamp_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()), skewMs = 1_000L)
        // issuedAt=10_000, now=12_000 -> age 2_000 > skew 1_000
        val result = verifier.verify(envelope(key, issuedAtMs = 10_000L), nowMs = 12_000L)
        assertEquals(VerifyResult.Rejected("expired"), result)
    }

    @Test
    fun verify_futureTimestamp_rejected() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val verifier = InboundEventVerifier(FakeRegistry(trust), NonceStore(FakeEventStateStore()), skewMs = 1_000L)
        // issuedAt=10_000, now=8_000 -> age -2_000 < -skew 1_000
        val result = verifier.verify(envelope(key, issuedAtMs = 10_000L), nowMs = 8_000L)
        assertEquals(VerifyResult.Rejected("future_timestamp"), result)
    }

    @Test
    fun verify_replayedNonce_rejectedOnSecondUse() {
        val trust = EventSourceTrust("openclaw-1", EventSourceKind.OPENCLAW, key, enabled = true)
        val nonceStore = NonceStore(FakeEventStateStore())
        val verifier = InboundEventVerifier(FakeRegistry(trust), nonceStore)
        val env = envelope(key)
        assertTrue(verifier.verify(env, nowMs = 10_000L) is VerifyResult.Accepted)
        val second = verifier.verify(env, nowMs = 10_001L)
        assertEquals(VerifyResult.Rejected("replay"), second)
    }
}

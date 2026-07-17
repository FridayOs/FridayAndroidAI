package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

// FRI-555: pure JVM envelope verifier. Order matters — cheapest/least-sensitive checks first,
// nonce recording last (only a fully-valid envelope should ever consume a nonce slot). Any failure
// anywhere returns Rejected with a short machine reason; never throws, never partially applies.
class InboundEventVerifier internal constructor(
    private val registry: EventSourceRegistry,
    private val nonceStore: NonceStore,
    private val skewMs: Long,
) {
    // Dagger cannot resolve Kotlin default-parameter values on an @Inject constructor (it would
    // try to inject a raw java.lang.Long with no binding). A dedicated no-default constructor
    // keeps production wiring on SKEW_MS while tests use the internal constructor to inject a
    // custom skew.
    @Inject constructor(
        registry: EventSourceRegistry,
        nonceStore: NonceStore,
    ) : this(registry, nonceStore, SKEW_MS)

    fun verify(env: InboundEventEnvelope, nowMs: Long): VerifyResult {
        val trust = registry.trustFor(env.sourceId) ?: return VerifyResult.Rejected("unknown_source")
        if (!trust.enabled) return VerifyResult.Rejected("disabled_source")
        if (trust.kind != env.sourceKind) return VerifyResult.Rejected("source_kind_mismatch")
        if (trust.hmacKey.isEmpty()) return VerifyResult.Rejected("missing_trust_key")

        val expectedSig = runCatching { hmacHex(trust.hmacKey, env.canonicalBytes()) }
            .getOrElse { return VerifyResult.Rejected("signing_error") }
        if (!constantTimeEquals(expectedSig, env.signature.lowercase())) {
            return VerifyResult.Rejected("bad_signature")
        }

        val age = nowMs - env.issuedAtMs
        if (age > skewMs) return VerifyResult.Rejected("expired")
        if (age < -skewMs) return VerifyResult.Rejected("future_timestamp")

        if (!nonceStore.checkAndRecord(env.sourceId, env.nonce, nowMs)) return VerifyResult.Rejected("replay")

        return VerifyResult.Accepted
    }

    private fun hmacHex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(key, HMAC_ALGO))
        return mac.doFinal(data).joinToString("") { "%02x".format(it) }
    }

    // MessageDigest.isEqual is specified to run in time independent of where the arrays first
    // differ, which is why it (not `==`/contentEquals) is used to compare signatures.
    private fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean =
        MessageDigest.isEqual(
            expectedHex.toByteArray(Charsets.UTF_8),
            actualHex.toByteArray(Charsets.UTF_8),
        )

    companion object {
        const val SKEW_MS = 5 * 60 * 1000L // +/- 5 minutes
        private const val HMAC_ALGO = "HmacSHA256"
    }
}

sealed class VerifyResult {
    object Accepted : VerifyResult()
    data class Rejected(val reason: String) : VerifyResult()
}

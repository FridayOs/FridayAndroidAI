package com.dark.tool_neuron.repo.gateway.event

// FRI-555 (B2 fix): pure routing helper, extracted so it is unit-testable without a
// FirebaseMessagingService instance. A payload belongs in the signed-envelope namespace if it
// carries ANY envelope-distinctive key -- not all of them. Requiring all of signature+nonce+
// version let a partial/malformed envelope (e.g. signature present but nonce missing) fall
// through to the legacy unverified `fromPushData` path and publish without verification. Routing
// to EventGateway is safe even for a partial payload: InboundEventEnvelope.parse fails closed
// (returns null -> gateway drops, no side effect) on any missing required field.
object EventRouting {
    private val ENVELOPE_KEYS = setOf("signature", "nonce", "sourceId", "sourceKind", "version", "type")

    fun isSignedEnvelopeNamespace(data: Map<String, String>): Boolean =
        ENVELOPE_KEYS.any { key -> !data[key].isNullOrBlank() }
}

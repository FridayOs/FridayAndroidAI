package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.model.friday.EventSourceKind

// FRI-555: trust material for one allow-listed OpenClaw/Hermes source. `hmacKey` is the shared
// HMAC-SHA256 secret used to verify envelope signatures; it comes from encrypted local prefs only
// and must never be logged, serialized outward, or sent anywhere. `enabled=false` means the source
// is provisioned but currently revoked — treated identically to unknown for verification purposes.
data class EventSourceTrust(
    val sourceId: String,
    val kind: EventSourceKind,
    val hmacKey: ByteArray,
    val enabled: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventSourceTrust) return false
        return sourceId == other.sourceId &&
            kind == other.kind &&
            hmacKey.contentEquals(other.hmacKey) &&
            enabled == other.enabled
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + hmacKey.contentHashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}

// Fail-closed lookup seam: unknown sourceId -> null (caller must reject, not default-trust).
// FRI-583 owns writing the backing trust material into encrypted prefs; this interface only reads.
fun interface EventSourceRegistry {
    fun trustFor(sourceId: String): EventSourceTrust?
}

package com.dark.tool_neuron.repo.gateway.event

import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 B3: in-memory monotonic-ordering guard, keyed by sourceId|correlationId. Rejects a
// verified-but-out-of-order envelope (issuedAtMs not strictly newer than the last accepted one for
// the same correlation) so a delayed PROGRESS can't clobber a later COMPLETION. Distinct from
// NonceStore (replay) and DedupeStore (redelivery of the same eventId) — durability is NOT
// required here per plan (only replay/dedupe need to survive process death).
@Singleton
class CorrelationTracker @Inject constructor() {
    private val lastIssuedAtMs = HashMap<String, Long>()

    // No correlationId means the envelope carries no ordering constraint (nothing to compare
    // against) — always accepted.
    @Synchronized
    fun accept(sourceId: String, correlationId: String?, issuedAtMs: Long): Boolean {
        if (correlationId == null) return true
        val key = keyFor(sourceId, correlationId)
        val last = lastIssuedAtMs[key]
        if (last != null && issuedAtMs <= last) return false
        lastIssuedAtMs[key] = issuedAtMs
        return true
    }

    @Synchronized
    fun clear(sourceId: String, correlationId: String?) {
        if (correlationId == null) return
        lastIssuedAtMs.remove(keyFor(sourceId, correlationId))
    }

    private fun keyFor(sourceId: String, correlationId: String) = "$sourceId|$correlationId"
}

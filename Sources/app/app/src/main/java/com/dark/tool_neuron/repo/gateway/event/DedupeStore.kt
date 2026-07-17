package com.dark.tool_neuron.repo.gateway.event

import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 (B4 rework): bounded seen-eventId set for redelivery dedupe (distinct from NonceStore's
// replay-attack guard -- a redelivered eventId with a *different* nonce is a legitimate
// at-least-once retry, not a forgery, and must still be dropped so the user never sees a duplicate
// notification or duplicate action). Durable across process death via an injectable
// EventStateStore. DEDUPE_HORIZON_MS must stay >= InboundEventVerifier.SKEW_MS so a duplicate
// can't outlive dedupe memory while its timestamp is still within the verifier's accept window.
// Persisted line format: "atMs\teventId" (oldest-first, newline-joined); unparseable lines are
// dropped rather than crashing.
@Singleton
class DedupeStore internal constructor(
    private val store: EventStateStore,
    private val cap: Int,
    private val horizonMs: Long,
) {
    // Dagger cannot resolve Kotlin default-parameter values on an @Inject constructor. A dedicated
    // no-default constructor keeps production wiring on CAP/DEDUPE_HORIZON_MS while tests use the
    // internal constructor to inject custom bounds.
    @Inject constructor(store: EventStateStore) : this(store, CAP, DEDUPE_HORIZON_MS)

    private data class Seen(val atMs: Long, val eventId: String)

    @Synchronized
    fun firstSeen(eventId: String, nowMs: Long): Boolean {
        val entries = readEntries().toMutableList()
        evictStale(entries, nowMs)

        if (entries.any { it.eventId == eventId }) {
            store.write(KEY, serialize(entries))
            return false
        }

        entries.add(Seen(nowMs, eventId))
        while (entries.size > cap) entries.removeAt(0)
        store.write(KEY, serialize(entries))
        return true
    }

    private fun evictStale(entries: MutableList<Seen>, nowMs: Long) {
        entries.removeAll { nowMs - it.atMs > horizonMs }
    }

    private fun readEntries(): List<Seen> {
        val raw = store.read(KEY) ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { line -> parseLine(line) }
            .toList()
    }

    private fun parseLine(line: String): Seen? {
        val idx = line.indexOf('\t')
        if (idx < 0) return null
        val atMs = line.substring(0, idx).toLongOrNull() ?: return null
        val eventId = line.substring(idx + 1)
        if (eventId.isEmpty()) return null
        return Seen(atMs, eventId)
    }

    private fun serialize(entries: List<Seen>): String =
        entries.joinToString("\n") { "${it.atMs}\t${it.eventId}" }

    companion object {
        const val CAP = 512
        const val DEDUPE_HORIZON_MS = 30 * 60 * 1000L // 30 minutes; >= InboundEventVerifier.SKEW_MS
        private const val KEY = "fri555.dedupe"
    }
}

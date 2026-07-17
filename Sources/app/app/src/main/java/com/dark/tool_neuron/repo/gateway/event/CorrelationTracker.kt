package com.dark.tool_neuron.repo.gateway.event

import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 B3 (Codex-QA round 2 rework): durable, bounded monotonic-ordering + cancel-watermark
// guard, keyed by sourceId|correlationId, persisted via an injectable EventStateStore (same
// pattern as NonceStore/DedupeStore) so a cancel watermark survives process death. Rejects a
// verified envelope whose issuedAtMs is not strictly newer than the last accepted one for the same
// correlation -- this covers BOTH a delayed PROGRESS arriving after a later COMPLETION AND a
// delayed PROGRESS/STATUS arriving after that correlation's CANCEL (the cancel's issuedAt is
// recorded as a watermark via the same accept() call, never cleared, so nothing older can
// resurrect the task). Bounded by a time horizon (>= InboundEventVerifier's skew window, reusing
// DedupeStore.DEDUPE_HORIZON_MS) and a cap (oldest-first eviction) so unbounded distinct
// correlations can't grow storage forever. Persisted line format: "atMs\tissuedAtMs\tkey"
// (oldest-first, newline-joined); unparseable lines are dropped rather than crashing.
@Singleton
class CorrelationTracker internal constructor(
    private val store: EventStateStore,
    private val cap: Int,
    private val horizonMs: Long,
) {
    // Dagger cannot resolve Kotlin default-parameter values on an @Inject constructor. A dedicated
    // no-default constructor keeps production wiring on CAP/HORIZON_MS while tests use the internal
    // constructor to inject custom bounds.
    @Inject constructor(store: EventStateStore) : this(store, CAP, DedupeStore.DEDUPE_HORIZON_MS)

    private data class Watermark(val atMs: Long, val issuedAtMs: Long, val key: String)

    // No correlationId means the envelope carries no ordering constraint (nothing to compare
    // against) -- always accepted, no persistence needed.
    @Synchronized
    fun accept(sourceId: String, correlationId: String?, issuedAtMs: Long, nowMs: Long): Boolean {
        if (correlationId == null) return true
        val key = keyFor(sourceId, correlationId)

        val entries = readEntries().toMutableList()
        evictStale(entries, nowMs)

        val existing = entries.find { it.key == key }
        if (existing != null && issuedAtMs <= existing.issuedAtMs) {
            store.write(STATE_KEY, serialize(entries))
            return false
        }

        entries.removeAll { it.key == key }
        entries.add(Watermark(nowMs, issuedAtMs, key))
        while (entries.size > cap) entries.removeAt(0)
        store.write(STATE_KEY, serialize(entries))
        return true
    }

    private fun evictStale(entries: MutableList<Watermark>, nowMs: Long) {
        entries.removeAll { nowMs - it.atMs > horizonMs }
    }

    private fun readEntries(): List<Watermark> {
        val raw = store.read(STATE_KEY) ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { line -> parseLine(line) }
            .toList()
    }

    private fun parseLine(line: String): Watermark? {
        val firstTab = line.indexOf('\t')
        if (firstTab < 0) return null
        val secondTab = line.indexOf('\t', firstTab + 1)
        if (secondTab < 0) return null
        val atMs = line.substring(0, firstTab).toLongOrNull() ?: return null
        val issuedAtMs = line.substring(firstTab + 1, secondTab).toLongOrNull() ?: return null
        val key = line.substring(secondTab + 1)
        if (key.isEmpty()) return null
        return Watermark(atMs, issuedAtMs, key)
    }

    private fun serialize(entries: List<Watermark>): String =
        entries.joinToString("\n") { "${it.atMs}\t${it.issuedAtMs}\t${it.key}" }

    private fun keyFor(sourceId: String, correlationId: String) = "$sourceId|$correlationId"

    companion object {
        const val CAP = 512
        private const val STATE_KEY = "fri555.correlation"
    }
}

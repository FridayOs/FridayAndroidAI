package com.dark.tool_neuron.repo.gateway.event

import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 (B4 rework): bounded, time-windowed per-source replay guard, now durable across process
// death via an injectable EventStateStore. Pure JVM, no wall-clock reads -- every caller passes
// `nowMs` explicitly so tests fully control eviction. A nonce is "seen" once recorded; a repeat
// within the same source before eviction is a replay (false). Nonces are evicted once their record
// is older than windowMs *or* once a source's set exceeds capPerSource (oldest-first) -- both
// bounds exist so a flood of distinct nonces from one source can't grow storage unboundedly even
// inside the time window. Persisted line format per source key: "atMs\tnonce" (oldest-first,
// newline-joined); any unparseable line is dropped rather than crashing.
@Singleton
class NonceStore internal constructor(
    private val store: EventStateStore,
    private val windowMs: Long,
    private val capPerSource: Int,
) {
    // Dagger cannot resolve Kotlin default-parameter values on an @Inject constructor. A dedicated
    // no-default constructor keeps production wiring on WINDOW_MS/CAP_PER_SOURCE while tests use
    // the internal constructor (or the in-memory-map convenience constructor) to inject custom
    // bounds.
    @Inject constructor(store: EventStateStore) : this(store, WINDOW_MS, CAP_PER_SOURCE)

    private data class Seen(val atMs: Long, val nonce: String)

    @Synchronized
    fun checkAndRecord(sourceId: String, nonce: String, nowMs: Long): Boolean {
        val key = keyFor(sourceId)
        val entries = readEntries(key).toMutableList()
        evictStale(entries, nowMs)

        if (entries.any { it.nonce == nonce }) return false

        entries.add(Seen(nowMs, nonce))
        while (entries.size > capPerSource) entries.removeAt(0)
        store.write(key, serialize(entries))
        return true
    }

    private fun evictStale(entries: MutableList<Seen>, nowMs: Long) {
        entries.removeAll { nowMs - it.atMs > windowMs }
    }

    private fun readEntries(key: String): List<Seen> {
        val raw = store.read(key) ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { line -> parseLine(line) }
            .toList()
    }

    private fun parseLine(line: String): Seen? {
        val idx = line.indexOf('\t')
        if (idx < 0) return null
        val atMs = line.substring(0, idx).toLongOrNull() ?: return null
        val nonce = line.substring(idx + 1)
        if (nonce.isEmpty()) return null
        return Seen(atMs, nonce)
    }

    private fun serialize(entries: List<Seen>): String =
        entries.joinToString("\n") { "${it.atMs}\t${it.nonce}" }

    private fun keyFor(sourceId: String): String = "$KEY_PREFIX$sourceId"

    companion object {
        const val WINDOW_MS = 10 * 60 * 1000L // 10 minutes
        const val CAP_PER_SOURCE = 256
        private const val KEY_PREFIX = "fri555.nonce."
    }
}

package com.dark.tool_neuron.repo.gateway.event

// FRI-555: bounded, time-windowed per-source replay guard. Pure JVM, no wall-clock reads — every
// caller passes `nowMs` explicitly so tests fully control eviction. A nonce is "seen" once
// recorded; a repeat within the same source before eviction is a replay (false). Nonces are
// evicted once their record is older than WINDOW_MS *or* once a source's set exceeds CAP_PER_SOURCE
// (oldest-first) — both bounds exist so a flood of distinct nonces from one source can't grow
// memory unboundedly even inside the time window.
class NonceStore(
    private val windowMs: Long = WINDOW_MS,
    private val capPerSource: Int = CAP_PER_SOURCE,
) {
    private data class Seen(val nonce: String, val atMs: Long)

    private val bySource = HashMap<String, ArrayDeque<Seen>>()

    @Synchronized
    fun checkAndRecord(sourceId: String, nonce: String, nowMs: Long): Boolean {
        val entries = bySource.getOrPut(sourceId) { ArrayDeque() }
        evictStale(entries, nowMs)

        if (entries.any { it.nonce == nonce }) return false

        entries.addLast(Seen(nonce, nowMs))
        while (entries.size > capPerSource) entries.removeFirst()
        return true
    }

    private fun evictStale(entries: ArrayDeque<Seen>, nowMs: Long) {
        while (entries.isNotEmpty() && nowMs - entries.first().atMs > windowMs) {
            entries.removeFirst()
        }
    }

    companion object {
        const val WINDOW_MS = 10 * 60 * 1000L // 10 minutes
        const val CAP_PER_SOURCE = 256
    }
}

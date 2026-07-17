package com.dark.tool_neuron.repo.gateway.event

// FRI-555: bounded seen-eventId set for redelivery dedupe (distinct from NonceStore's replay-
// attack guard — a redelivered eventId with a *different* nonce is a legitimate at-least-once
// retry, not a forgery, and must still be dropped so the user never sees a duplicate notification
// or duplicate action). LRU-bounded so a single misbehaving/long-lived source can't grow this
// unboundedly; eviction just re-opens the (harmless) possibility of a very old id re-registering.
class DedupeStore(private val cap: Int = CAP) {

    // Insertion-ordered: iterator().next() is always the oldest surviving entry.
    private val seen = LinkedHashSet<String>()

    @Synchronized
    fun firstSeen(eventId: String): Boolean {
        if (!seen.add(eventId)) return false
        if (seen.size > cap) {
            val oldest = seen.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    companion object {
        const val CAP = 512
    }
}

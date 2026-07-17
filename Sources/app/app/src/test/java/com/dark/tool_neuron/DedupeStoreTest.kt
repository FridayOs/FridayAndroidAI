package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.DedupeStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 redelivery dedupe (B4): bounded, time-windowed eventId memory, durable via an injectable
// EventStateStore. Distinct from NonceStore's replay guard -- a redelivered eventId with a
// DIFFERENT nonce is a legitimate at-least-once retry and must still be dropped.
class DedupeStoreTest {

    @Test
    fun firstSeen_firstCall_returnsTrue() {
        val store = DedupeStore(FakeEventStateStore())
        assertTrue(store.firstSeen("ev-1", 1_000L))
    }

    @Test
    fun firstSeen_repeatWithinHorizon_returnsFalse_duplicate() {
        val store = DedupeStore(FakeEventStateStore())
        assertTrue(store.firstSeen("ev-1", 1_000L))
        assertFalse(store.firstSeen("ev-1", 1_500L))
    }

    @Test
    fun firstSeen_repeatAfterHorizonExpires_returnsTrue() {
        val store = DedupeStore(FakeEventStateStore(), cap = DedupeStore.CAP, horizonMs = 1_000L)
        assertTrue(store.firstSeen("ev-1", 1_000L))
        // now - atMs (2_500 - 1_000 = 1_500) > horizonMs(1_000) -> stale, evicted.
        assertTrue(store.firstSeen("ev-1", 2_500L))
    }

    @Test
    fun firstSeen_exceedsCap_evictsOldestFirst() {
        val store = DedupeStore(FakeEventStateStore(), cap = 2, horizonMs = Long.MAX_VALUE / 2)
        assertTrue(store.firstSeen("ev-1", 1_000L))
        assertTrue(store.firstSeen("ev-2", 1_001L))
        // Cap is 2; adding a 3rd eventId evicts the oldest (ev-1), which can then be reused.
        assertTrue(store.firstSeen("ev-3", 1_002L))
        assertTrue(store.firstSeen("ev-1", 1_003L))
        // ev-2 and ev-3 are still within the cap window and must still be rejected as duplicates.
        assertFalse(store.firstSeen("ev-3", 1_004L))
    }

    // FRI-555 B4: durability across process death -- a brand-new DedupeStore instance constructed
    // over the SAME backing EventStateStore must still reject the already-seen eventId.
    @Test
    fun firstSeen_survivesProcessRestart_sameBackingStore_stillRejectsDuplicate() {
        val backing = FakeEventStateStore()
        val first = DedupeStore(backing)
        assertTrue(first.firstSeen("ev-1", 1_000L))

        val second = DedupeStore(backing)
        assertFalse(second.firstSeen("ev-1", 1_500L))
    }

    // FRI-555 B4: unparseable persisted lines must be dropped, never crash the store.
    @Test
    fun firstSeen_corruptPersistedLines_ignoredNotCrashed() {
        val backing = FakeEventStateStore()
        backing.write("fri555.dedupe", "no-tab-here\n\tmissing-timestamp\nnot-long\tev-x")
        val store = DedupeStore(backing)
        // Every seed line is unparseable, so the store behaves as if empty.
        assertTrue(store.firstSeen("ev-1", 2_000L))
    }

    // FRI-555 (B4): adversarial defense-in-depth -- even if a raw persisted value somehow spanned
    // multiple physical lines (e.g. a legacy record written before InboundEventEnvelope.isCleanId
    // rejected embedded newlines at parse time), the genuine "atMs\teventId" prefix line must still
    // be read back and honored as seen. A corrupted trailing fragment must never cause an
    // already-seen eventId to be reported as a false "unseen" (which would let a duplicate through).
    @Test
    fun firstSeen_rawMultiLineValueWritten_neverFalseUnseenForRecordedPrefix() {
        val backing = FakeEventStateStore()
        backing.write("fri555.dedupe", "1000\tev-1\ninjected-garbage-line-not-a-valid-record")
        val store = DedupeStore(backing)
        assertFalse("prefix line 'ev-1' was genuinely recorded; read-back must still reject it as a duplicate", store.firstSeen("ev-1", 1_500L))
    }
}

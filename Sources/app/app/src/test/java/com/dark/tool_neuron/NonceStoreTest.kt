package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.NonceStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 replay guard (B4 rework): bounded, time-windowed per-source nonce tracking, now durable
// via an injectable EventStateStore. All calls pass an explicit nowMs so eviction (time-window and
// cap) is fully deterministic under test. FakeEventStateStore is an in-memory stand-in; process
// restart is simulated by constructing a NEW NonceStore over the SAME backing store instance.
class NonceStoreTest {

    @Test
    fun checkAndRecord_firstUse_returnsTrue() {
        val store = NonceStore(FakeEventStateStore())
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
    }

    @Test
    fun checkAndRecord_repeatWithinWindow_returnsFalse_replay() {
        val store = NonceStore(FakeEventStateStore())
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertFalse(store.checkAndRecord("src-1", "n-1", 1_500L))
    }

    @Test
    fun checkAndRecord_repeatAfterWindowExpires_returnsTrue() {
        val store = NonceStore(FakeEventStateStore(), windowMs = 1_000L, capPerSource = NonceStore.CAP_PER_SOURCE)
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        // now - atMs (2_500 - 1_000 = 1_500) > windowMs(1_000) -> stale, evicted, so "replay" is
        // actually allowed again since the original record no longer exists.
        assertTrue(store.checkAndRecord("src-1", "n-1", 2_500L))
    }

    @Test
    fun checkAndRecord_differentSources_areIsolated() {
        val store = NonceStore(FakeEventStateStore())
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertTrue(store.checkAndRecord("src-2", "n-1", 1_000L))
    }

    @Test
    fun checkAndRecord_exceedsCapPerSource_evictsOldestFirst() {
        val store = NonceStore(FakeEventStateStore(), windowMs = Long.MAX_VALUE / 2, capPerSource = 2)
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertTrue(store.checkAndRecord("src-1", "n-2", 1_001L))
        // Cap is 2; adding a 3rd nonce evicts the oldest (n-1), which can then be reused.
        assertTrue(store.checkAndRecord("src-1", "n-3", 1_002L))
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_003L))
        // n-2 and n-3 are still within the cap window and must still be rejected as replays.
        assertFalse(store.checkAndRecord("src-1", "n-3", 1_004L))
    }

    // FRI-555 B4: durability across process death -- a brand-new NonceStore instance constructed
    // over the SAME backing EventStateStore must still reject the already-recorded nonce.
    @Test
    fun checkAndRecord_survivesProcessRestart_sameBackingStore_stillRejectsReplay() {
        val backing = FakeEventStateStore()
        val first = NonceStore(backing)
        assertTrue(first.checkAndRecord("src-1", "n-1", 1_000L))

        val second = NonceStore(backing)
        assertFalse(second.checkAndRecord("src-1", "n-1", 1_500L))
    }

    // FRI-555 B4: unparseable persisted lines must be dropped, never crash the store.
    @Test
    fun checkAndRecord_corruptPersistedLines_ignoredNotCrashed() {
        val backing = FakeEventStateStore()
        backing.write("fri555.nonce.src-1", "no-tab-here\n\tmissing-timestamp\nnot-long\tn-x")
        val store = NonceStore(backing)
        // Every seed line is unparseable, so the store behaves as if empty.
        assertTrue(store.checkAndRecord("src-1", "n-1", 2_000L))
    }
}

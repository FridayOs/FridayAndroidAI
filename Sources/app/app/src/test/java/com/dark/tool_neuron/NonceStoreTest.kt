package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.NonceStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 replay guard: bounded, time-windowed per-source nonce tracking. All calls pass an
// explicit nowMs so eviction (time-window and cap) is fully deterministic under test.
class NonceStoreTest {

    @Test
    fun checkAndRecord_firstUse_returnsTrue() {
        val store = NonceStore()
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
    }

    @Test
    fun checkAndRecord_repeatWithinWindow_returnsFalse_replay() {
        val store = NonceStore()
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertFalse(store.checkAndRecord("src-1", "n-1", 1_500L))
    }

    @Test
    fun checkAndRecord_repeatAfterWindowExpires_returnsTrue() {
        val store = NonceStore(windowMs = 1_000L)
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        // now - atMs (2_500 - 1_000 = 1_500) > windowMs(1_000) -> stale, evicted, so "replay" is
        // actually allowed again since the original record no longer exists.
        assertTrue(store.checkAndRecord("src-1", "n-1", 2_500L))
    }

    @Test
    fun checkAndRecord_differentSources_areIsolated() {
        val store = NonceStore()
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertTrue(store.checkAndRecord("src-2", "n-1", 1_000L))
    }

    @Test
    fun checkAndRecord_exceedsCapPerSource_evictsOldestFirst() {
        val store = NonceStore(windowMs = Long.MAX_VALUE / 2, capPerSource = 2)
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_000L))
        assertTrue(store.checkAndRecord("src-1", "n-2", 1_001L))
        // Cap is 2; adding a 3rd nonce evicts the oldest (n-1), which can then be reused.
        assertTrue(store.checkAndRecord("src-1", "n-3", 1_002L))
        assertTrue(store.checkAndRecord("src-1", "n-1", 1_003L))
        // n-2 and n-3 are still within the cap window and must still be rejected as replays.
        assertFalse(store.checkAndRecord("src-1", "n-3", 1_004L))
    }
}

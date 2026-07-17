package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.CorrelationTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 B3 (Codex-QA round 2 rework): durable, bounded monotonic-ordering + cancel-watermark
// guard. clear() no longer exists -- a CANCEL's issuedAt is recorded as a permanent (until
// horizon-evicted) watermark via accept() itself, so a delayed event can never resurrect a
// cancelled task. Tests use the internal (store, cap, horizonMs) constructor to control bounds.
class CorrelationTrackerTest {

    private fun tracker(
        store: FakeEventStateStore = FakeEventStateStore(),
        cap: Int = CorrelationTracker.CAP,
        horizonMs: Long = Long.MAX_VALUE / 2,
    ) = CorrelationTracker(store, cap, horizonMs)

    @Test
    fun accept_firstForKey_returnsTrue() {
        assertTrue(tracker().accept("src-1", "c-1", 1000L, 1000L))
    }

    @Test
    fun accept_strictlyNewerTimestamp_returnsTrue() {
        val t = tracker()
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertTrue(t.accept("src-1", "c-1", 2000L, 2000L))
    }

    @Test
    fun accept_equalTimestamp_returnsFalse() {
        val t = tracker()
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertFalse(t.accept("src-1", "c-1", 1000L, 1001L))
    }

    @Test
    fun accept_olderTimestamp_returnsFalse() {
        val t = tracker()
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertFalse(t.accept("src-1", "c-1", 500L, 1001L))
    }

    @Test
    fun accept_differentCorrelationId_isIndependentKey() {
        val t = tracker()
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertTrue("different correlationId must not be blocked by c-1's state", t.accept("src-1", "c-2", 500L, 1000L))
    }

    @Test
    fun accept_differentSourceId_isIndependentKey() {
        val t = tracker()
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertTrue("same correlationId under a different sourceId must be independent", t.accept("src-2", "c-1", 500L, 1000L))
    }

    @Test
    fun accept_nullCorrelationId_alwaysReturnsTrue() {
        val t = tracker()
        assertTrue(t.accept("src-1", null, 1000L, 1000L))
        assertTrue(t.accept("src-1", null, 500L, 1000L))
        assertTrue(t.accept("src-1", null, 1000L, 1000L))
    }

    // FRI-555 B3 core fix: a CANCEL's issuedAt is recorded as a watermark via the SAME accept() call
    // used for delivery ordering -- there is no clear(), so a delayed event with an OLDER issuedAt
    // arriving after the cancel is rejected as stale, never resurrecting the cancelled task.
    @Test
    fun accept_cancelWatermarkRecorded_laterDelayedOlderEventRejected() {
        val t = tracker()
        // CANCEL arrives first (recorded as a watermark, same call used for delivery).
        assertTrue(t.accept("src-1", "c-1", 10_001L, 10_001L))
        // A delayed PROGRESS with an OLDER issuedAt arrives after -- must be rejected, not resurrected.
        assertFalse(t.accept("src-1", "c-1", 10_000L, 10_002L))
    }

    // Cross-source isolation for the cancel-watermark case specifically: an OpenClaw cancel's
    // watermark must never block a Hermes event sharing the same correlationId.
    @Test
    fun accept_cancelWatermark_doesNotAffectDifferentSourceSameCorrelation() {
        val t = tracker()
        assertTrue(t.accept("openclaw-1", "c-1", 10_001L, 10_001L))
        assertTrue(
            "a cancel watermark recorded for one sourceId must not block a delivery for a different " +
                "sourceId sharing the same correlationId",
            t.accept("hermes-1", "c-1", 10_000L, 10_002L),
        )
    }

    @Test
    fun accept_exceedsCap_evictsOldestFirst() {
        val t = tracker(cap = 2)
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        assertTrue(t.accept("src-1", "c-2", 1000L, 1001L))
        // Cap is 2; a 3rd distinct key evicts the oldest (c-1), which can then reuse an equal ts.
        assertTrue(t.accept("src-1", "c-3", 1000L, 1002L))
        assertTrue("c-1 was evicted by the cap, so its old watermark no longer blocks", t.accept("src-1", "c-1", 1000L, 1003L))
    }

    @Test
    fun accept_pastHorizon_watermarkEvicted_staleAllowedAgain() {
        val t = tracker(horizonMs = 1_000L)
        assertTrue(t.accept("src-1", "c-1", 1000L, 1000L))
        // now - atMs (2_500 - 1_000 = 1_500) > horizonMs(1_000) -> watermark evicted as stale.
        assertTrue(t.accept("src-1", "c-1", 1000L, 2_500L))
    }

    // FRI-555 B3: durability across process death -- a brand-new CorrelationTracker constructed over
    // the SAME backing EventStateStore must still honor a previously-recorded watermark (including a
    // CANCEL watermark), so a delayed/replayed event can't resurrect a task after a process restart.
    @Test
    fun accept_survivesProcessRestart_sameBackingStore_stillRejectsStale() {
        val backing = FakeEventStateStore()
        val first = tracker(backing)
        assertTrue(first.accept("src-1", "c-1", 10_001L, 10_001L))

        val second = tracker(backing)
        assertFalse(second.accept("src-1", "c-1", 10_000L, 10_002L))
    }

    // FRI-555 B3: unparseable persisted lines must be dropped, never crash the tracker.
    @Test
    fun accept_corruptPersistedLines_ignoredNotCrashed() {
        val backing = FakeEventStateStore()
        backing.write("fri555.correlation", "no-tabs-here\n\tmissing-fields\nnot-long\tnot-long\tkey")
        val t = tracker(backing)
        assertTrue("every seed line is unparseable, so the tracker behaves as if empty", t.accept("src-1", "c-1", 1000L, 1000L))
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.CorrelationTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 B3: pure unit coverage of the in-memory monotonic-ordering guard. Durability is not
// required here (unlike NonceStore/DedupeStore) since the plan only requires replay/dedupe to
// survive process death.
class CorrelationTrackerTest {

    @Test
    fun accept_firstForKey_returnsTrue() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
    }

    @Test
    fun accept_strictlyNewerTimestamp_returnsTrue() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertTrue(tracker.accept("src-1", "c-1", 2000L))
    }

    @Test
    fun accept_equalTimestamp_returnsFalse() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertFalse(tracker.accept("src-1", "c-1", 1000L))
    }

    @Test
    fun accept_olderTimestamp_returnsFalse() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertFalse(tracker.accept("src-1", "c-1", 500L))
    }

    @Test
    fun accept_differentCorrelationId_isIndependentKey() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertTrue("different correlationId must not be blocked by c-1's state", tracker.accept("src-1", "c-2", 500L))
    }

    @Test
    fun accept_differentSourceId_isIndependentKey() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertTrue("same correlationId under a different sourceId must be independent", tracker.accept("src-2", "c-1", 500L))
    }

    @Test
    fun accept_nullCorrelationId_alwaysReturnsTrue() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", null, 1000L))
        assertTrue(tracker.accept("src-1", null, 500L))
        assertTrue(tracker.accept("src-1", null, 1000L))
    }

    @Test
    fun clear_removesKey_subsequentOlderOrEqualTimestampAccepted() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        assertFalse(tracker.accept("src-1", "c-1", 1000L))
        tracker.clear("src-1", "c-1")
        assertTrue("cleared key must accept an equal timestamp again", tracker.accept("src-1", "c-1", 1000L))
    }

    @Test
    fun clear_nullCorrelationId_isNoOp() {
        val tracker = CorrelationTracker()
        assertTrue(tracker.accept("src-1", "c-1", 1000L))
        tracker.clear("src-1", null)
        assertFalse("clear(null) must not affect any tracked key", tracker.accept("src-1", "c-1", 1000L))
    }

    @Test
    fun clear_unknownKey_isSafeNoOp() {
        val tracker = CorrelationTracker()
        tracker.clear("src-1", "never-tracked")
    }
}

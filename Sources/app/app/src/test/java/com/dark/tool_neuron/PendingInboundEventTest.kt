package com.dark.tool_neuron

import com.dark.tool_neuron.data.PendingInboundEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// One-shot: a tapped event notification sets the display-lookup keys; the first consume() returns
// them and clears, every later consume() returns null until the next set().
class PendingInboundEventTest {

    @Test
    fun defaultIsNotPending() {
        assertNull(PendingInboundEvent().consume())
    }

    @Test
    fun setThenConsumeOnce() {
        val p = PendingInboundEvent()
        p.set("e1", "c1")
        assertEquals("e1" to "c1", p.pending.value)
        assertEquals("e1" to "c1", p.consume())
        assertNull(p.pending.value)
        assertNull("second consume must not re-fire", p.consume())
    }

    @Test
    fun nullCorrelationIsCarried() {
        val p = PendingInboundEvent()
        p.set("e2", null)
        assertEquals("e2" to null, p.consume())
    }

    @Test
    fun repeatedSetKeepsLatestUntilConsumed() {
        val p = PendingInboundEvent()
        p.set("e1", null)
        p.set("e2", "c2")
        assertEquals("e2" to "c2", p.consume())
        assertNull(p.consume())
    }
}

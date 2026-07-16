package com.dark.tool_neuron

import com.dark.tool_neuron.data.PendingAssistInvocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// One-shot flag: an assist invocation raises it; the first consume() returns true and clears it,
// every later consume() returns false until the next set().
class PendingAssistInvocationTest {

    @Test
    fun defaultIsNotPending() {
        assertFalse(PendingAssistInvocation().consume())
    }

    @Test
    fun setThenConsumeOnce() {
        val p = PendingAssistInvocation()
        p.set()
        assertTrue(p.pending.value)
        assertTrue(p.consume())
        assertFalse(p.pending.value)
        assertFalse("second consume must not re-fire", p.consume())
    }

    @Test
    fun repeatedSetIsIdempotentUntilConsumed() {
        val p = PendingAssistInvocation()
        p.set(); p.set(); p.set()
        assertTrue(p.consume())
        assertFalse(p.consume())
    }
}

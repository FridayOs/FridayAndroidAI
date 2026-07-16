package com.dark.tool_neuron

import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

// One-shot: a tapped event notification carries the full reconstructed event; the first consume()
// returns it and clears, every later consume() returns null until the next set().
class PendingInboundEventTest {

    private fun event(id: String, correlationId: String? = null) = InboundEvent(
        eventId = id,
        sourceType = InboundSource.PUSH,
        correlationId = correlationId,
        kind = InboundEventKind.STATUS,
        title = "t-$id",
        body = "b-$id",
        urgency = InboundUrgency.NORMAL,
        receivedAt = 0L,
    )

    @Test
    fun defaultIsNotPending() {
        assertNull(PendingInboundEvent().consume())
    }

    @Test
    fun setThenConsumeOnce() {
        val p = PendingInboundEvent()
        val e = event("e1", "c1")
        p.set(e)
        assertSame(e, p.pending.value)
        assertSame(e, p.consume())
        assertNull(p.pending.value)
        assertNull("second consume must not re-fire", p.consume())
    }

    @Test
    fun nullCorrelationIsCarried() {
        val p = PendingInboundEvent()
        val e = event("e2", null)
        p.set(e)
        assertEquals(null, p.consume()?.correlationId)
    }

    @Test
    fun repeatedSetKeepsLatestUntilConsumed() {
        val p = PendingInboundEvent()
        p.set(event("e1", null))
        val latest = event("e2", "c2")
        p.set(latest)
        assertSame(latest, p.consume())
        assertNull(p.consume())
    }
}

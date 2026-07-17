package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 B1: the pending inbound confirmation is resolved ONLY by an explicit confirm()/cancel()
// call — arm() never triggers any action executor, and it carries actionIntent purely as data for
// display. This suite has no executor dependency at all, which is itself the proof that nothing here
// can execute actionIntent: there is no seam through which it could reach one.
class InboundConfirmationCenterTest {

    private fun confirmationEvent(
        id: String = "e1",
        correlationId: String? = "c1",
        actionIntent: String? = "do-the-thing",
    ) = InboundEvent(
        eventId = id,
        sourceType = InboundSource.VERIFIED,
        correlationId = correlationId,
        kind = InboundEventKind.CONFIRMATION,
        title = "t",
        body = "b",
        urgency = InboundUrgency.HIGH,
        receivedAt = 0L,
        actionIntent = actionIntent,
    )

    @Test
    fun arm_publishesPendingSnapshot_withActionIntentAsDataOnly() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent())
        val pending = center.pending.value
        assertEquals("e1", pending!!.eventId)
        assertEquals("c1", pending.correlationId)
        assertEquals("do-the-thing", pending.actionIntent)
    }

    @Test
    fun confirm_matchingId_resolvesAndClearsPending() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1"))
        assertTrue(center.confirm("e1"))
        assertNull("resolved confirmation must clear the pending slot", center.pending.value)
    }

    @Test
    fun confirm_unknownOrStaleId_isSafeNoOp() {
        val center = InboundConfirmationCenter()
        assertFalse("nothing pending", center.confirm("e1"))
        center.arm(confirmationEvent(id = "e1"))
        assertFalse("mismatched id must not resolve the currently armed one", center.confirm("stale-id"))
        assertEquals("e1", center.pending.value!!.eventId)
    }

    @Test
    fun cancel_matchingId_resolvesAndClearsPending() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1"))
        assertTrue(center.cancel("e1"))
        assertNull(center.pending.value)
    }

    @Test
    fun cancel_unknownId_isSafeNoOp() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1"))
        assertFalse(center.cancel("other-id"))
        assertEquals("e1", center.pending.value!!.eventId)
    }

    @Test
    fun cancelByCorrelation_matchingCorrelation_resolvesAndClearsPending() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1", correlationId = "c-1"))
        assertTrue(center.cancelByCorrelation("c-1"))
        assertNull(center.pending.value)
    }

    @Test
    fun cancelByCorrelation_mismatchedCorrelation_isSafeNoOp() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1", correlationId = "c-1"))
        assertFalse(center.cancelByCorrelation("c-2"))
        assertEquals("e1", center.pending.value!!.eventId)
    }

    @Test
    fun rearm_supersedesPriorPending_implicitlyDropsIt() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1"))
        center.arm(confirmationEvent(id = "e2"))
        assertEquals("newer arm replaces the prior pending slot", "e2", center.pending.value!!.eventId)
        assertFalse("superseded id can no longer be resolved", center.confirm("e1"))
        assertTrue(center.confirm("e2"))
    }

    @Test
    fun confirmThenConfirmAgain_secondCallIsNoOp() {
        val center = InboundConfirmationCenter()
        center.arm(confirmationEvent(id = "e1"))
        assertTrue(center.confirm("e1"))
        assertFalse("already resolved once, second confirm must be a no-op", center.confirm("e1"))
    }
}

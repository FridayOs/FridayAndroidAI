package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.InboundActionBridge
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.dark.tool_neuron.repo.gateway.event.PendingInboundConfirmation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 B1: the pending inbound confirmation is DURABLE (persisted via EventStateStore), correlated,
// and resolved ONLY by an explicit confirm()/cancel() call which routes through InboundActionBridge --
// arm() never triggers any action executor. RecordingBridge below only RECORDS which callback fired and
// with what payload; it never executes actionIntent, which is itself the proof nothing here can reach an
// executor: there is no seam through which it could.
class InboundConfirmationCenterTest {

    private class RecordingBridge : InboundActionBridge {
        var confirmed: PendingInboundConfirmation? = null
        var cancelled: PendingInboundConfirmation? = null
        var confirmedCount = 0
        var cancelledCount = 0

        override fun onConfirmed(confirmation: PendingInboundConfirmation) {
            confirmed = confirmation
            confirmedCount++
        }

        override fun onCancelled(confirmation: PendingInboundConfirmation) {
            cancelled = confirmation
            cancelledCount++
        }
    }

    private fun center(
        store: FakeEventStateStore = FakeEventStateStore(),
        bridge: InboundActionBridge = RecordingBridge(),
        nowMs: () -> Long = { 1_000L },
    ) = InboundConfirmationCenter(store, bridge, nowMs)

    private fun confirmationEvent(
        id: String = "e1",
        correlationId: String? = "c1",
        actionIntent: String? = "do-the-thing",
        sourceId: String? = "src-1",
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
        sourceId = sourceId,
    )

    @Test
    fun arm_publishesPendingSnapshot_withActionIntentAsDataOnly() {
        val c = center()
        c.arm(confirmationEvent())
        val pending = c.pending.value
        assertEquals("e1", pending!!.eventId)
        assertEquals("c1", pending.correlationId)
        assertEquals("do-the-thing", pending.actionIntent)
        assertEquals(1_000L, pending.armedAtMs)
    }

    // B1: arm() persists to the shared store; a NEW center instance constructed over the SAME store
    // (simulating process recreation after death) restores the pending confirmation from disk -- still a
    // verified confirmation, never lost or silently downgraded.
    @Test
    fun processRecreation_restoresPendingFromSameStore() {
        val store = FakeEventStateStore()
        val first = center(store = store)
        first.arm(confirmationEvent(id = "e1", correlationId = "c1", sourceId = "src-1"))

        val recreated = center(store = store)
        val restored = recreated.pending.value
        assertEquals("e1", restored!!.eventId)
        assertEquals("c1", restored.correlationId)
        assertEquals("src-1", restored.sourceId)
        assertEquals("do-the-thing", restored.actionIntent)
        assertEquals("t", restored.title)
        assertEquals("b", restored.body)
        assertEquals(1_000L, restored.armedAtMs)
    }

    @Test
    fun noPendingPersisted_newCenterOverEmptyStore_hasNoPending() {
        val store = FakeEventStateStore()
        val c = center(store = store)
        assertNull(c.pending.value)
    }

    @Test
    fun confirm_matchingId_callsBridgeOnConfirmedOnce_andClearsPendingAndPersist() {
        val store = FakeEventStateStore()
        val bridge = RecordingBridge()
        val c = center(store = store, bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c1", sourceId = "src-1"))

        assertTrue(c.confirm("e1"))
        assertEquals(1, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
        assertEquals("e1", bridge.confirmed!!.eventId)
        assertEquals("c1", bridge.confirmed!!.correlationId)
        assertEquals("src-1", bridge.confirmed!!.sourceId)
        assertNull("resolved confirmation must clear the pending slot", c.pending.value)

        // Persist cleared too: a recreated center over the same store has nothing pending.
        val recreated = center(store = store, bridge = RecordingBridge())
        assertNull(recreated.pending.value)
    }

    @Test
    fun confirm_unknownOrStaleId_isSafeNoOp_neverCallsBridge() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        assertFalse("nothing pending", c.confirm("e1"))
        c.arm(confirmationEvent(id = "e1"))
        assertFalse("mismatched id must not resolve the currently armed one", c.confirm("stale-id"))
        assertEquals("e1", c.pending.value!!.eventId)
        assertEquals(0, bridge.confirmedCount)
    }

    @Test
    fun cancel_matchingId_callsBridgeOnCancelledOnly_neverOnConfirmed() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1"))
        assertTrue(c.cancel("e1"))
        assertEquals(1, bridge.cancelledCount)
        assertEquals(0, bridge.confirmedCount)
        assertNull(c.pending.value)
    }

    @Test
    fun cancel_unknownId_isSafeNoOp() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1"))
        assertFalse(c.cancel("other-id"))
        assertEquals("e1", c.pending.value!!.eventId)
        assertEquals(0, bridge.cancelledCount)
    }

    @Test
    fun cancelByCorrelation_matchingCorrelation_callsBridgeOnCancelled() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))
        assertTrue(c.cancelByCorrelation("src-1", "c-1"))
        assertEquals(1, bridge.cancelledCount)
        assertNull(c.pending.value)
    }

    @Test
    fun cancelByCorrelation_mismatchedCorrelation_isSafeNoOp() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))
        assertFalse(c.cancelByCorrelation("src-1", "c-2"))
        assertEquals("e1", c.pending.value!!.eventId)
        assertEquals(0, bridge.cancelledCount)
    }

    // FRI-555 B3: a cancel for the correct correlationId but a DIFFERENT sourceId (e.g. an
    // OpenClaw cancel racing a Hermes confirmation sharing the same correlationId) must be a safe
    // no-op -- cancelByCorrelation is scoped to BOTH keys, never correlationId alone.
    @Test
    fun cancelByCorrelation_mismatchedSourceId_matchingCorrelation_isSafeNoOp() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "hermes-1"))
        assertFalse(
            "a different sourceId sharing the correlationId must not resolve this pending confirmation",
            c.cancelByCorrelation("openclaw-1", "c-1"),
        )
        assertEquals("e1", c.pending.value!!.eventId)
        assertEquals(0, bridge.cancelledCount)
    }

    @Test
    fun rearm_supersedesPriorPending_implicitlyDropsIt_withoutCallingBridge() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1"))
        c.arm(confirmationEvent(id = "e2"))
        assertEquals("newer arm replaces the prior pending slot", "e2", c.pending.value!!.eventId)
        assertFalse("superseded id can no longer be resolved", c.confirm("e1"))
        assertTrue(c.confirm("e2"))
        assertEquals(1, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }

    @Test
    fun confirmThenConfirmAgain_secondCallIsNoOp_bridgeCalledOnce() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1"))
        assertTrue(c.confirm("e1"))
        assertFalse("already resolved once, second confirm must be a no-op", c.confirm("e1"))
        assertEquals(1, bridge.confirmedCount)
    }

    // A push-sourced CONFIRMATION is downgraded to STATUS before it ever reaches arm() (see
    // InboundEventCenter.publish's sourceType/kind gate) -- this test proves the center itself has no
    // path that arms from anything but an explicit arm(VERIFIED CONFIRMATION) call, so an
    // unverified/coerced event structurally never touches the bridge.
    @Test
    fun neverArmed_bridgeNeverCalled() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        assertNull(c.pending.value)
        assertFalse(c.confirm("anything"))
        assertFalse(c.cancel("anything"))
        assertEquals(0, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }
}

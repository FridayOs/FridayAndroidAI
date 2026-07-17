package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.CancelClaim
import com.dark.tool_neuron.repo.gateway.event.InboundActionBridge
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.dark.tool_neuron.repo.gateway.event.PendingInboundConfirmation
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // FRI-555 R4-1: thread-safe recording double for the concurrent races below -- AtomicInteger
    // counters only, no blocking/locking inside the callback (it runs under the center's monitor,
    // so blocking here would deadlock a racing caller).
    private class ThreadSafeRecordingBridge : InboundActionBridge {
        val confirmedCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)

        override fun onConfirmed(confirmation: PendingInboundConfirmation) {
            confirmedCount.incrementAndGet()
        }

        override fun onCancelled(confirmation: PendingInboundConfirmation) {
            cancelledCount.incrementAndGet()
        }
    }

    // FRI-555 R4-1: a UI confirm() (main thread) and an FCM signed CANCEL's cancelByCorrelation()
    // (FCM callback thread) racing on the SAME pending confirmation must produce exactly one
    // resolution -- never both onConfirmed AND onCancelled for one event. Iterated to shake the race.
    @Test
    fun confirmVsCancelByCorrelation_concurrentRace_exactlyOneWinner() {
        repeat(200) {
            val store = FakeEventStateStore()
            val bridge = ThreadSafeRecordingBridge()
            val c = center(store = store, bridge = bridge)
            c.arm(confirmationEvent(id = "e1", correlationId = "c1", sourceId = "src-1"))

            val barrier = CyclicBarrier(2)
            var confirmResult = false
            var cancelResult = false

            val threadA = Thread {
                barrier.await()
                confirmResult = c.confirm("e1")
            }
            val threadB = Thread {
                barrier.await()
                cancelResult = c.cancelByCorrelation("src-1", "c1")
            }
            threadA.start()
            threadB.start()
            threadA.join()
            threadB.join()

            assertTrue(
                "exactly one of confirm/cancel must win the race, got confirm=$confirmResult cancel=$cancelResult",
                confirmResult != cancelResult,
            )
            assertEquals(
                "bridge must observe exactly one total outcome, never both",
                1,
                bridge.confirmedCount.get() + bridge.cancelledCount.get(),
            )
            assertTrue(
                "confirmed and cancelled must never both fire",
                bridge.confirmedCount.get() == 0 || bridge.cancelledCount.get() == 0,
            )
        }
    }

    // FRI-555 R4-1: a re-arm racing a resolve of the PRIOR pending confirmation must not interleave
    // a half-cleared state -- the persisted record is always either the new one or cleanly absent,
    // and the old event's bridge outcome fires at most once (never corrupted, never double-fired).
    @Test
    fun rearmVsResolve_concurrentRace_noHalfState() {
        repeat(200) {
            val store = FakeEventStateStore()
            val bridge = ThreadSafeRecordingBridge()
            val c = center(store = store, bridge = bridge)
            c.arm(confirmationEvent(id = "eOld", correlationId = "c1", sourceId = "src-1"))

            val barrier = CyclicBarrier(2)
            val threadA = Thread {
                barrier.await()
                c.arm(confirmationEvent(id = "eNew", correlationId = "c2", sourceId = "src-1"))
            }
            val threadB = Thread {
                barrier.await()
                c.confirm("eOld")
            }
            threadA.start()
            threadB.start()
            threadA.join()
            threadB.join()

            // The old event resolves at most once (either confirm won before the re-arm, or the
            // re-arm won first and confirm("eOld") became a stale no-op against the new record).
            assertTrue(
                "old event outcome must fire at most once",
                bridge.confirmedCount.get() <= 1,
            )
            assertEquals("cancel must never fire from this race", 0, bridge.cancelledCount.get())

            // Persisted/in-memory state is never half-cleared or corrupt: the re-arm always wins the
            // slot in the end (it runs after confirm in both possible interleavings), so pending is
            // deterministically the new record -- never null, never the stale/old one.
            val current = c.pending.value
            assertNotNull("pending must not be left half-cleared", current)
            assertEquals("eNew", current!!.eventId)

            val recreated = center(store = store, bridge = ThreadSafeRecordingBridge())
            val restored = recreated.pending.value
            assertNotNull("persisted store must deserialize cleanly, never corrupt", restored)
            assertEquals("eNew", restored!!.eventId)
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

    // FRI-555 R5-1: claimCancel WON -- pending matches both keys, so this call is the sole
    // teardown: bridge sees exactly onCancelled, and the slot clears.
    @Test
    fun claimCancel_pendingMatchesBothKeys_returnsWon_callsBridgeOnCancelledOnce() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))

        assertEquals(CancelClaim.WON, c.claimCancel("src-1", "c-1"))
        assertEquals(1, bridge.cancelledCount)
        assertEquals(0, bridge.confirmedCount)
        assertNull(c.pending.value)
    }

    // FRI-555 R5-1: after a prior confirm() resolves an identity, a later claimCancel for the SAME
    // identity must not re-fire the bridge -- it observes ALREADY_RESOLVED (the identity was
    // recorded by resolve()'s recordResolved on the winning confirm), never WON or a second callback.
    @Test
    fun claimCancel_afterPriorConfirm_returnsAlreadyResolved_neverCallsBridgeAgain() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))
        assertTrue(c.confirm("e1"))
        assertEquals(1, bridge.confirmedCount)

        assertEquals(CancelClaim.ALREADY_RESOLVED, c.claimCancel("src-1", "c-1"))
        assertEquals(1, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }

    // FRI-555 R5-1: same as above but the prior resolution came from cancel() rather than confirm()
    // -- claimCancel must still see ALREADY_RESOLVED and not double-fire onCancelled.
    @Test
    fun claimCancel_afterPriorCancel_returnsAlreadyResolved_neverCallsBridgeAgain() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))
        assertTrue(c.cancel("e1"))
        assertEquals(1, bridge.cancelledCount)

        assertEquals(CancelClaim.ALREADY_RESOLVED, c.claimCancel("src-1", "c-1"))
        assertEquals(1, bridge.cancelledCount)
        assertEquals(0, bridge.confirmedCount)
    }

    // FRI-555 R7-1: noteState bumps the identity's generation, invalidating a prior resolution's
    // generation-stamped suppression -- a superseding delivery for the identity means a subsequent
    // claimCancel must fall through to NONE (plain-task teardown path), not the stale ALREADY_RESOLVED
    // skip from the earlier confirm() (whose stamp is now an older generation).
    @Test
    fun noteState_afterPriorConfirm_makesClaimCancelReturnNone() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))
        assertTrue(c.confirm("e1"))
        assertEquals(1, bridge.confirmedCount)

        c.noteState("src-1", "c-1")

        assertEquals(CancelClaim.NONE, c.claimCancel("src-1", "c-1"))
        assertEquals(1, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }

    // FRI-555 R7-1: noteState for an identity that was never armed/resolved is a safe no-op (it only
    // bumps a generation counter; claimCancel still returns NONE with no recorded resolution).
    @Test
    fun noteState_neverArmedIdentity_isSafeNoOp() {
        val c = center()
        c.noteState("src-1", "c-1")
        assertEquals(CancelClaim.NONE, c.claimCancel("src-1", "c-1"))
    }

    // FRI-555 R8-1: the round-7 two-LRU desync bug. arm+confirm identity X stamps its resolution; then
    // churning noteState() across MORE distinct identities than the cap (32) evicts X's entry entirely.
    // Because current + resolved now live in ONE entry, they evict TOGETHER -- a later noteState(X)
    // recreates a FRESH entry with resolved == null, so claimCancel(X) returns NONE (tears down the new
    // state), never the stale ALREADY_RESOLVED that would swallow the new generation's teardown. The
    // non-evicted control below proves ordinary exactly-once (ALREADY_RESOLVED) is unbroken.
    @Test
    fun evictionMustNotReviveOldResolutionStamp() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)

        // Arm + confirm identity X -> entry {current=1, resolved=1}.
        c.arm(confirmationEvent(id = "eX", correlationId = "cX", sourceId = "srcX"))
        assertTrue(c.confirm("eX"))

        // Churn 40 distinct OTHER identities through noteState -> exceeds cap 32 -> LRU evicts X.
        repeat(40) { i ->
            c.noteState("src-$i", "corr-$i")
        }

        // A superseding delivery for X recreates a fresh entry {current=1, resolved=null}.
        c.noteState("srcX", "cX")

        assertEquals(
            "an evicted-then-recreated identity must not revive its old resolution stamp",
            CancelClaim.NONE,
            c.claimCancel("srcX", "cX"),
        )

        // Control: a normal non-evicted confirm -> claimCancel still returns ALREADY_RESOLVED
        // (exactly-once suppression is unbroken by the unified entry).
        c.arm(confirmationEvent(id = "eY", correlationId = "cY", sourceId = "srcY"))
        assertTrue(c.confirm("eY"))
        assertEquals(CancelClaim.ALREADY_RESOLVED, c.claimCancel("srcY", "cY"))
    }

    // FRI-555 R5-1: an identity that was never armed and never resolved -- a plain task's CANCEL --
    // must return NONE (not ALREADY_RESOLVED) so the caller still unconditionally cancels the OS
    // notification, and the bridge must never be touched.
    @Test
    fun claimCancel_neverArmedOrResolved_returnsNone_neverCallsBridge() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)

        assertEquals(CancelClaim.NONE, c.claimCancel("src-1", "c-1"))
        assertEquals(0, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }

    // FRI-555 R5-1: a claimCancel for a DIFFERENT identity than the one currently pending must not
    // resolve the pending record and must return NONE (no prior resolution recorded for it either).
    @Test
    fun claimCancel_mismatchedIdentity_returnsNone_leavesPendingIntact() {
        val bridge = RecordingBridge()
        val c = center(bridge = bridge)
        c.arm(confirmationEvent(id = "e1", correlationId = "c-1", sourceId = "src-1"))

        assertEquals(CancelClaim.NONE, c.claimCancel("src-1", "c-2"))
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

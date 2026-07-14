package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.live.BrainCorrelation
import com.dark.tool_neuron.repo.gateway.live.LiveBrainGateway
import com.dark.tool_neuron.repo.gateway.live.LiveCloudBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Fake LiveBrainGateway captures the BrainCorrelation received at the seam, proving turn/continue/cancel/confirm target the right turn.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveCloudBridgeTest {

    // Captures exactly what identity + history arrives at the seam so tests assert the right turn is targeted.
    private class FakeBrainGateway : LiveBrainGateway {
        data class TurnCall(val correlation: BrainCorrelation, val history: List<GatewayTurn>)

        val turns = mutableListOf<TurnCall>()
        val continues = mutableListOf<TurnCall>()
        val cancels = mutableListOf<BrainCorrelation>()
        val confirms = mutableListOf<BrainCorrelation>()
        var awaiting = false
        var confirmResult = true

        override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
            turns += TurnCall(correlation, history)
            return flow { emit(GatewayEvent.Delta("hi")); emit(GatewayEvent.Done("hi")) }
        }

        override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
            continues += TurnCall(correlation, history)
            return flow { emit(GatewayEvent.Done("more")) }
        }

        override fun brainCancel(correlation: BrainCorrelation) { cancels += correlation }
        override fun brainConfirm(correlation: BrainCorrelation): Boolean {
            confirms += correlation; awaiting = false; return confirmResult
        }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    @Test
    fun brainTurn_carriesSessionCorrelationAndTranscriptToSeam() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        val history = listOf(GatewayTurn("user", "earlier"), GatewayTurn("assistant", "reply"))

        val events = bridge.brainTurn(history, transcript = "what's the weather").toList()

        assertEquals(1, fake.turns.size)
        val call = fake.turns.first()
        // The correlation actually reached the seam, tagged to this session.
        assertEquals("sess-1", call.correlation.sessionId)
        assertTrue(call.correlation.turnId.isNotBlank())
        assertEquals(call.correlation, bridge.currentCorrelation)
        // Existing history preserved, transcript appended as the new user turn — audio never crosses.
        assertEquals(history + GatewayTurn("user", "what's the weather"), call.history)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainContinue_carriesFreshTurnIdSameSession_noNewUserTurn() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        val history = listOf(GatewayTurn("user", "again"))

        bridge.brainContinue(history).toList()

        assertEquals(1, fake.continues.size)
        assertEquals("sess-1", fake.continues.first().correlation.sessionId)
        assertEquals(history, fake.continues.first().history)
    }

    @Test
    fun brainCancel_forwardsTheActiveTurnCorrelation_andClearsIt() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        bridge.brainTurn(emptyList(), "hello").toList()
        val active = bridge.currentCorrelation!!

        bridge.brainCancel()

        assertEquals("cancel forwards the active turn's correlation", listOf(active), fake.cancels)
        assertNull("cancel clears the in-flight correlation", bridge.currentCorrelation)
    }

    @Test
    fun brainConfirm_forwardsActiveCorrelation_andReturnsGateResult() = runTest {
        val fake = FakeBrainGateway().apply { confirmResult = true }
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        bridge.brainTurn(emptyList(), "do it").toList()
        val active = bridge.currentCorrelation!!

        assertTrue(bridge.brainConfirm())
        assertEquals(listOf(active), fake.confirms)
    }

    @Test
    fun brainConfirm_returnsFalseWhenNoTurnActive() {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        assertFalse(bridge.brainConfirm())
        assertTrue("no active turn -> nothing forwarded", fake.confirms.isEmpty())
    }

    @Test
    fun awaitingConfirmation_passesThroughSeam() {
        val fake = FakeBrainGateway().apply { awaiting = true }
        assertTrue(LiveCloudBridge(fake, "sess-1").brainAwaitingConfirmation())
    }

    @Test
    fun eachTurnGetsFreshCorrelationUnderStableSession() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-42")

        bridge.brainTurn(emptyList(), "one").toList()
        val first = bridge.currentCorrelation!!
        bridge.brainContinue(emptyList()).toList()
        val second = bridge.currentCorrelation!!

        assertEquals("sess-42", first.sessionId)
        assertEquals("sess-42", second.sessionId)
        assertNotEquals("each turn gets a fresh turnId", first.turnId, second.turnId)
        // Both identities were actually delivered to the seam.
        assertEquals(first, fake.turns.first().correlation)
        assertEquals(second, fake.continues.first().correlation)
    }

    // Stale-turn guard: turn A opens, turn B supersedes it; a late cancel for A must NOT cancel B.
    @Test
    fun staleCancel_forSupersededTurn_neverCancelsTheNewerTurn() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")

        bridge.brainTurn(emptyList(), "turn A").toList()
        val turnA = bridge.currentCorrelation!!
        bridge.brainContinue(emptyList()).toList()
        val turnB = bridge.currentCorrelation!!
        assertNotEquals(turnA, turnB)

        // A late cancel arriving for the already-superseded turn A: must be dropped, not forwarded.
        bridge.cancelTurn(turnA)
        assertTrue("stale cancel for turn A is not forwarded", fake.cancels.isEmpty())
        assertEquals("newer turn B stays active", turnB, bridge.currentCorrelation)

        // Cancelling the actual active turn B forwards exactly B.
        bridge.cancelTurn(turnB)
        assertEquals(listOf(turnB), fake.cancels)
        assertNull(bridge.currentCorrelation)
    }

    // Stale-turn guard for confirm: a confirm from a superseded turn must not resolve the newer turn's gate.
    @Test
    fun staleConfirm_forSupersededTurn_isDropped() = runTest {
        val fake = FakeBrainGateway().apply { confirmResult = true }
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")

        bridge.brainTurn(emptyList(), "turn A").toList()
        val turnA = bridge.currentCorrelation!!
        bridge.brainTurn(emptyList(), "turn B").toList()
        val turnB = bridge.currentCorrelation!!

        assertFalse("stale confirm for turn A returns false", bridge.confirmTurn(turnA))
        assertTrue("stale confirm never reaches the seam", fake.confirms.isEmpty())

        assertTrue("confirming the active turn B works", bridge.confirmTurn(turnB))
        assertEquals(listOf(turnB), fake.confirms)
    }

    // A cross-session correlation (different sessionId) must never match the active turn.
    @Test
    fun cancel_withForeignSessionCorrelation_isIgnored() = runTest {
        val fake = FakeBrainGateway()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        bridge.brainTurn(emptyList(), "hi").toList()

        bridge.cancelTurn(BrainCorrelation(sessionId = "other-session", turnId = "whatever"))

        assertTrue("a foreign-session cancel never forwards", fake.cancels.isEmpty())
        assertTrue("active turn is untouched", bridge.currentCorrelation != null)
    }

    // Blocks inside the seam's confirm so a racing new-turn open can be pinned mid-critical-section.
    private class BlockingBrainGateway : LiveBrainGateway {
        val turns = mutableListOf<BrainCorrelation>()
        val confirms = mutableListOf<BrainCorrelation>()
        val confirmEntered = CountDownLatch(1)
        val confirmProceed = CountDownLatch(1)
        override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
            synchronized(turns) { turns += correlation }
            return flow { emit(GatewayEvent.Done("ok")) }
        }
        override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> =
            brainTurn(correlation, history)
        override fun brainCancel(correlation: BrainCorrelation) {}
        override fun brainConfirm(correlation: BrainCorrelation): Boolean {
            synchronized(confirms) { confirms += correlation }
            confirmEntered.countDown()
            confirmProceed.await(5, TimeUnit.SECONDS)
            return true
        }
        override fun brainAwaitingConfirmation(): Boolean = false
    }

    // Serialized end-to-end: while confirm(A) is inside the seam, a new turn open must wait — it can never interleave and let A's confirm land on B's gate.
    @Test
    fun confirmTurn_vsNewTurnOpen_isSerializedNotInterleaved() {
        val seam = BlockingBrainGateway()
        val bridge = LiveCloudBridge(seam, sessionId = "sess-1")
        bridge.brainTurn(emptyList(), "turn A")
        val turnA = bridge.currentCorrelation!!

        var confirmResult = false
        val confirmer = thread { confirmResult = bridge.confirmTurn(turnA) }
        assertTrue("confirm reached the seam", seam.confirmEntered.await(5, TimeUnit.SECONDS))

        val opener = thread { bridge.brainTurn(emptyList(), "turn B") }
        Thread.sleep(200)
        assertEquals("turn B open waits behind the in-flight confirm", 1, synchronized(seam.turns) { seam.turns.size })

        seam.confirmProceed.countDown()
        confirmer.join(5000)
        opener.join(5000)

        assertTrue("confirm targeted A while A was still the active turn", confirmResult)
        assertEquals(listOf(turnA), seam.confirms)
        assertNotEquals("B superseded A after the confirm completed", turnA, bridge.currentCorrelation)
        assertFalse("a late confirm for A is now stale", bridge.confirmTurn(turnA))
        assertEquals("the stale confirm never reached the seam", listOf(turnA), seam.confirms)
    }
}

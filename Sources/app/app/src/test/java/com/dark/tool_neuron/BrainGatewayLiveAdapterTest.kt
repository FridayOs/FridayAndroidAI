package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.live.BrainCorrelation
import com.dark.tool_neuron.repo.gateway.live.BrainGatewayLiveAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Production-adapter proof: cancel/confirm forward only for the exact active correlation (cross-session isolation).
class BrainGatewayLiveAdapterTest {

    private class FakeBrain : BrainBridge {
        var turnCount = 0
        var continueCount = 0
        var cancelCount = 0
        var confirmCount = 0
        var confirmResult = true
        var awaiting = false
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> { turnCount++; return emptyFlow() }
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> { continueCount++; return emptyFlow() }
        override fun brainCancel() { cancelCount++ }
        override fun brainConfirm(): Boolean { confirmCount++; return confirmResult }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    @Test
    fun turnAndContinue_forwardToBrain() {
        val brain = FakeBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        adapter.brainTurn(BrainCorrelation("s1", "t1"), listOf(GatewayTurn("user", "hi")))
        adapter.brainContinue(BrainCorrelation("s1", "t2"), emptyList())
        assertEquals(1, brain.turnCount)
        assertEquals(1, brain.continueCount)
    }

    @Test
    fun cancel_forwardsForActiveTurn_onlyOnce() {
        val brain = FakeBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        adapter.brainTurn(a, emptyList())
        adapter.brainCancel(a)
        assertEquals("active cancel forwards", 1, brain.cancelCount)
        // active cleared — a repeat cancel for the same (now inactive) turn is a no-op.
        adapter.brainCancel(a)
        assertEquals(1, brain.cancelCount)
    }

    @Test
    fun staleCancel_forSupersededTurn_isDropped() {
        val brain = FakeBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        val b = BrainCorrelation("s1", "tB")
        adapter.brainTurn(a, emptyList())
        adapter.brainTurn(b, emptyList()) // b supersedes a
        adapter.brainCancel(a)
        assertEquals("stale cancel for A is dropped", 0, brain.cancelCount)
        adapter.brainCancel(b)
        assertEquals("cancel for the active turn B forwards", 1, brain.cancelCount)
    }

    @Test
    fun crossSession_sameTurnIdDifferentSession_isDropped() {
        val brain = FakeBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        adapter.brainTurn(BrainCorrelation("s1", "t1"), emptyList())
        // Same turnId string but a foreign session — full-correlation match means this must NOT cancel s1's turn.
        adapter.brainCancel(BrainCorrelation("s2", "t1"))
        assertEquals("foreign-session cancel is dropped", 0, brain.cancelCount)
        adapter.brainConfirm(BrainCorrelation("s2", "t1"))
        assertEquals("foreign-session confirm never reaches the brain", 0, brain.confirmCount)
    }

    @Test
    fun confirm_forwardsForActiveTurn_staleReturnsFalse() {
        val brain = FakeBrain().apply { confirmResult = true }
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        val b = BrainCorrelation("s1", "tB")
        adapter.brainTurn(a, emptyList())
        assertTrue(adapter.brainConfirm(a))
        assertEquals(1, brain.confirmCount)
        adapter.brainTurn(b, emptyList()) // supersedes a
        assertFalse("stale confirm for A returns false", adapter.brainConfirm(a))
        assertEquals("stale confirm never reaches the brain", 1, brain.confirmCount)
        assertTrue(adapter.brainConfirm(b))
        assertEquals(2, brain.confirmCount)
    }

    @Test
    fun awaitingConfirmation_passesThrough() {
        val brain = FakeBrain().apply { awaiting = true }
        assertTrue(BrainGatewayLiveAdapter(brain).brainAwaitingConfirmation())
    }

    // Blocks inside the identity-less brain calls so tests can pin a racing thread mid-critical-section deterministically.
    private class BlockingBrain : BrainBridge {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val confirmEntered = CountDownLatch(1)
        val confirmProceed = CountDownLatch(1)
        val cancelEntered = CountDownLatch(1)
        val cancelProceed = CountDownLatch(1)
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> { calls += "turn"; return emptyFlow() }
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> { calls += "continue"; return emptyFlow() }
        override fun brainCancel() { calls += "cancel"; cancelEntered.countDown(); cancelProceed.await(5, TimeUnit.SECONDS) }
        override fun brainConfirm(): Boolean { calls += "confirm"; confirmEntered.countDown(); confirmProceed.await(5, TimeUnit.SECONDS); return true }
        override fun brainAwaitingConfirmation(): Boolean = false
    }

    // The race the review flagged: confirm(A) check passes, then a new turn B opens before the identity-less brainConfirm() fires — B's gate must never be the one confirmed.
    @Test
    fun concurrentTurnOpen_cannotInterleaveInsideConfirm() {
        val brain = BlockingBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        val b = BrainCorrelation("s1", "tB")
        adapter.brainTurn(a, emptyList())

        var confirmResult = false
        val confirmer = thread { confirmResult = adapter.brainConfirm(a) }
        assertTrue("confirm reached the brain seam", brain.confirmEntered.await(5, TimeUnit.SECONDS))

        val opener = thread { adapter.brainTurn(b, emptyList()) }
        Thread.sleep(200)
        assertEquals("turn open for B must wait for confirm(A) to finish", 1, brain.calls.count { it == "turn" })

        brain.confirmProceed.countDown()
        confirmer.join(5000)
        opener.join(5000)

        assertTrue("confirm resolved A's gate while A was still active", confirmResult)
        assertEquals(listOf("turn", "confirm", "turn"), brain.calls)
        assertFalse("A is superseded by B after the race settles", adapter.brainConfirm(a))
    }

    @Test
    fun concurrentCancelAndConfirm_onlyCancelReachesBrain() {
        val brain = BlockingBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        adapter.brainTurn(a, emptyList())

        val canceller = thread { adapter.brainCancel(a) }
        assertTrue(brain.cancelEntered.await(5, TimeUnit.SECONDS))

        var confirmResult = true
        val confirmer = thread { confirmResult = adapter.brainConfirm(a) }
        Thread.sleep(200)
        assertEquals("confirm must wait behind the in-flight cancel", 0, brain.calls.count { it == "confirm" })

        brain.cancelProceed.countDown()
        canceller.join(5000)
        confirmer.join(5000)

        assertFalse("confirm after cancel sees no active turn", confirmResult)
        assertEquals("confirm never reached the brain", 0, brain.calls.count { it == "confirm" })
        assertEquals(1, brain.calls.count { it == "cancel" })
    }

    @Test
    fun foreignSessionConfirm_duringActiveConfirm_neverReachesBrain() {
        val brain = BlockingBrain()
        val adapter = BrainGatewayLiveAdapter(brain)
        val a = BrainCorrelation("s1", "tA")
        adapter.brainTurn(a, emptyList())

        var ownResult = false
        val owner = thread { ownResult = adapter.brainConfirm(a) }
        assertTrue(brain.confirmEntered.await(5, TimeUnit.SECONDS))

        var foreignResult = true
        val foreign = thread { foreignResult = adapter.brainConfirm(BrainCorrelation("s2", "tA")) }
        Thread.sleep(200)
        assertEquals("foreign-session confirm is serialized behind the active one", 1, brain.calls.count { it == "confirm" })

        brain.confirmProceed.countDown()
        owner.join(5000)
        foreign.join(5000)

        assertTrue(ownResult)
        assertFalse("foreign-session confirm is dropped", foreignResult)
        assertEquals("only the owning session's confirm reached the brain", 1, brain.calls.count { it == "confirm" })
    }
}

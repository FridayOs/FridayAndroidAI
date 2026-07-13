package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
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

// Proves the FRI-530 CloudBridge seam reaches the Brain Gateway through all four brain_* ops with a fake brain,
// carries the live transcript as text (never audio), and keeps every request correlated to the session id.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveCloudBridgeTest {

    private class FakeBrain : BrainBridge {
        val turns = mutableListOf<List<GatewayTurn>>()
        val continues = mutableListOf<List<GatewayTurn>>()
        var cancelled = false
        var confirmed = false
        var awaiting = false
        var confirmResult = true

        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> {
            turns += history
            return flow { emit(GatewayEvent.Delta("hi")); emit(GatewayEvent.Done("hi")) }
        }

        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
            continues += history
            return flow { emit(GatewayEvent.Done("more")) }
        }

        override fun brainCancel() { cancelled = true }
        override fun brainConfirm(): Boolean { confirmed = true; awaiting = false; return confirmResult }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    @Test
    fun brainTurn_appendsTranscriptAsUserTurn_andReachesBrain() = runTest {
        val fake = FakeBrain()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        val history = listOf(GatewayTurn("user", "earlier"), GatewayTurn("assistant", "reply"))

        val events = bridge.brainTurn(history, transcript = "what's the weather").toList()

        assertEquals(1, fake.turns.size)
        val forwarded = fake.turns.first()
        // Existing history preserved, transcript appended as the new user turn — audio never crosses the seam.
        assertEquals(history + GatewayTurn("user", "what's the weather"), forwarded)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainContinue_reRunsHistory_withoutInjectingUserTurn() = runTest {
        val fake = FakeBrain()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        val history = listOf(GatewayTurn("user", "again"))

        val events = bridge.brainContinue(history).toList()

        assertEquals(listOf(history), fake.continues)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainCancel_reachesBrain_andClearsCorrelation() = runTest {
        val fake = FakeBrain()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        bridge.brainTurn(emptyList(), "hello").toList()
        assertNotNullCorrelation(bridge.currentCorrelationId)

        bridge.brainCancel()

        assertTrue("brain_cancel must reach the brain", fake.cancelled)
        assertNull("cancel clears the in-flight correlation", bridge.currentCorrelationId)
    }

    @Test
    fun brainConfirm_reachesBrain_andReturnsGateResult() {
        val fake = FakeBrain().apply { confirmResult = true }
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        assertTrue(bridge.brainConfirm())
        assertTrue(fake.confirmed)
    }

    @Test
    fun brainConfirm_returnsFalseWhenNothingArmed() {
        val fake = FakeBrain().apply { confirmResult = false }
        val bridge = LiveCloudBridge(fake, sessionId = "sess-1")
        assertFalse(bridge.brainConfirm())
    }

    @Test
    fun awaitingConfirmation_passesThroughBrain() {
        val fake = FakeBrain().apply { awaiting = true }
        assertTrue(LiveCloudBridge(fake, "sess-1").brainAwaitingConfirmation())
    }

    // Correlation contract: every brain request is tagged to the session id and each turn gets a distinct id.
    @Test
    fun everyRequestCorrelationIsTaggedToSession() = runTest {
        val fake = FakeBrain()
        val bridge = LiveCloudBridge(fake, sessionId = "sess-42")

        bridge.brainTurn(emptyList(), "one").toList()
        val first = bridge.currentCorrelationId
        bridge.brainContinue(emptyList()).toList()
        val second = bridge.currentCorrelationId

        assertTrue("correlation carries the session id", first!!.startsWith("sess-42:"))
        assertTrue("correlation carries the session id", second!!.startsWith("sess-42:"))
        assertNotEquals("each turn gets a fresh correlation id", first, second)
    }

    private fun assertNotNullCorrelation(id: String?) {
        assertTrue("a turn must set a correlation id", id != null && id.isNotBlank())
    }
}

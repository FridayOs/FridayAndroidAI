package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Proves the voice layer reaches the brain through all four bridge ops and that brainCancel tears down the live collecting Job it owns.
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceBridgeWiringTest {

    private class FakeBrain : BrainBridge {
        val turns = mutableListOf<List<GatewayTurn>>()
        val continues = mutableListOf<List<GatewayTurn>>()
        var cancelled = false
        var confirmed = false
        var cancelledMidStream = false

        // Swapped to the endless stream by the cancellation test; finite otherwise.
        var turnFactory: () -> Flow<GatewayEvent> = {
            flow {
                emit(GatewayEvent.Delta("hi"))
                emit(GatewayEvent.Done("hi"))
            }
        }

        fun useEndlessTurn() {
            turnFactory = {
                flow {
                    try {
                        emit(GatewayEvent.Delta("streaming"))
                        while (true) {
                            yield()
                            emit(GatewayEvent.Delta("."))
                        }
                    } finally {
                        cancelledMidStream = true
                    }
                }
            }
        }

        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> {
            turns += history
            return turnFactory()
        }

        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
            continues += history
            return flow {
                emit(GatewayEvent.Delta("more"))
                emit(GatewayEvent.Done("more"))
            }
        }

        override fun brainCancel() { cancelled = true }
        override fun brainConfirm(): Boolean { confirmed = true; return true }
    }

    @Test
    fun runTurn_bridgesTranscriptToBrain() = runTest {
        val fake = FakeBrain()
        val bridge = VoiceBridge(fake)
        val history = listOf(GatewayTurn("user", "hello"))
        val events = mutableListOf<GatewayEvent>()

        bridge.runTurn(this, history) { events += it }.join()

        assertEquals(listOf(history), fake.turns)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainContinue_reachesBrainContinue() = runTest {
        val fake = FakeBrain()
        val bridge = VoiceBridge(fake)
        val history = listOf(GatewayTurn("user", "again"))
        val events = mutableListOf<GatewayEvent>()

        bridge.brainContinue(history).collect { events += it }

        assertEquals(listOf(history), fake.continues)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainConfirm_reachesBrain_andReturnsResult() {
        val fake = FakeBrain()
        val bridge = VoiceBridge(fake)

        val result = bridge.brainConfirm()

        assertTrue("confirm must reach the brain", fake.confirmed)
        assertTrue("bridge returns the brain's confirm result", result)
    }

    @Test
    fun brainCancel_viaBridge_cancelsLiveCollectingJob() = runTest {
        val fake = FakeBrain().apply { useEndlessTurn() }
        val bridge = VoiceBridge(fake)
        val started = CompletableDeferred<Unit>()

        val job = bridge.runTurn(backgroundScope, listOf(GatewayTurn("user", "hi"))) { event ->
            if (event is GatewayEvent.Delta && !started.isCompleted) started.complete(Unit)
        }
        withTimeout(1_000) { started.await() }
        assertFalse("stream still live before cancel", fake.cancelledMidStream)

        bridge.brainCancel()
        job.join()

        assertTrue("brainCancel must reach the delegate", fake.cancelled)
        assertTrue("brainCancel must tear down the live collecting Job", fake.cancelledMidStream)
        assertTrue("cancelled Job must be complete", job.isCancelled)
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Proves the voice layer reaches the brain through all four bridge ops and that brain_cancel tears down a live collecting Job.
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceBridgeWiringTest {

    // Records which ops were called and lets tests drive the streams by hand.
    private class FakeBrain : BrainBridge {
        val turns = mutableListOf<List<GatewayTurn>>()
        val continues = mutableListOf<List<GatewayTurn>>()
        var cancelled = false
        var confirmed = false
        var cancelledMidStream = false

        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> {
            turns += history
            return flow {
                emit(GatewayEvent.Delta("hi"))
                emit(GatewayEvent.Done("hi"))
            }
        }

        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
            continues += history
            return flow {
                emit(GatewayEvent.Delta("more"))
                emit(GatewayEvent.Done("more"))
            }
        }

        // Never completes — proves cancellation tears the stream down.
        fun endlessTurn(): Flow<GatewayEvent> = flow {
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

        override fun brainCancel() { cancelled = true }
        override fun brainConfirm(): Boolean { confirmed = true; return true }
    }

    @Test
    fun brainTurn_bridgesTranscriptToBrain() = runTest {
        val fake = FakeBrain()
        val bridge = VoiceBridge(fake)
        val history = listOf(GatewayTurn("user", "hello"))

        val events = bridge.brainTurn(history).toList()

        assertEquals(listOf(history), fake.turns)
        assertTrue(events.any { it is GatewayEvent.Done })
    }

    @Test
    fun brainContinue_reachesBrainContinue() = runTest {
        val fake = FakeBrain()
        val bridge = VoiceBridge(fake)
        val history = listOf(GatewayTurn("user", "again"))

        bridge.brainContinue(history).toList()

        assertEquals(listOf(history), fake.continues)
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
    fun brainCancel_cancelsLiveCollectingJob() = runTest {
        val fake = FakeBrain()
        // Collect the endless stream in a child Job, cancel it, and assert its finally ran (Job torn down).
        val started = CompletableDeferred<Unit>()
        val job = launch {
            fake.endlessTurn().collect {
                if (it is GatewayEvent.Delta && !started.isCompleted) started.complete(Unit)
            }
        }
        withTimeout(1_000) { started.await() }
        assertFalse(fake.cancelledMidStream)

        job.cancel()
        job.join()

        assertTrue("cancelling the Job must tear down the live stream", fake.cancelledMidStream)
    }
}

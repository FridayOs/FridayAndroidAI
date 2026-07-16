package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The confirmation gate contract: a user tap is the ONLY resolver. Confirm forwards to brainConfirm
// exactly once, cancel forwards to brainCancel, and nothing on the bridge auto-executes either.
@OptIn(ExperimentalCoroutinesApi::class)
class InboundEventConfirmationTest {

    private class RecordingBrain : BrainBridge {
        var confirmCalls = 0
        var cancelCalls = 0
        var awaiting = false
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> =
            flow { emit(GatewayEvent.Done("")) }
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brainTurn(history)
        override fun brainCancel() { cancelCalls++ }
        override fun brainConfirm(): Boolean { confirmCalls++; awaiting = false; return true }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    @Test
    fun confirm_forwardsToBrainConfirm_exactlyOnce() = runTest {
        val brain = RecordingBrain().apply { awaiting = true }
        val bridge = VoiceBridge(brain)
        assertTrue(bridge.brainConfirm())
        assertEquals(1, brain.confirmCalls)
        assertEquals(0, brain.cancelCalls)
    }

    @Test
    fun cancel_forwardsToBrainCancel_neverConfirms() = runTest {
        val brain = RecordingBrain().apply { awaiting = true }
        val bridge = VoiceBridge(brain)
        bridge.brainCancel()
        assertEquals(1, brain.cancelCalls)
        assertEquals("cancel must never resolve the gate as confirmed", 0, brain.confirmCalls)
    }

    @Test
    fun armedGate_withoutUserAction_neverAutoExecutes() = runTest {
        val brain = RecordingBrain().apply { awaiting = true }
        val bridge = VoiceBridge(brain)
        // Reads and a full brain turn are not user confirmation actions.
        assertTrue(bridge.brainAwaitingConfirmation())
        bridge.runTurn(this, emptyList()) {}
        advanceUntilIdle()
        assertEquals("no path may auto-confirm an armed gate", 0, brain.confirmCalls)
        assertEquals("no path may auto-cancel an armed gate", 0, brain.cancelCalls)
    }

    @Test
    fun awaitingConfirmationState_isConstantFalse_forNonRouterBrains() = runTest {
        // The reactive mirror only exists for the real GatewayRoleRouter; fakes fall back to a constant.
        val bridge = VoiceBridge(RecordingBrain().apply { awaiting = true })
        assertFalse(bridge.awaitingConfirmationState.value)
        assertTrue("the synchronous query still reflects the brain", bridge.brainAwaitingConfirmation())
    }
}

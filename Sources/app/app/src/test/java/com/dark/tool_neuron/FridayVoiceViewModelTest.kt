package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import com.dark.tool_neuron.repo.gateway.VoiceRoutePort
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import com.dark.tool_neuron.viewmodel.FridayVoiceViewModel
import com.dark.tool_neuron.voice.VoiceIo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Public FridayVoiceViewModel.confirm()/awaitingConfirmation() reach the brain's gate (VoiceBridge-only tests don't cover the VM call site).
@OptIn(ExperimentalCoroutinesApi::class)
class FridayVoiceViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeBrain : BrainBridge {
        var confirmed = false
        var awaiting = false
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = flow { emit(GatewayEvent.Done("")) }
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brainTurn(history)
        override fun brainCancel() {}
        override fun brainConfirm(): Boolean { confirmed = true; awaiting = false; return true }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    private class FakeConvoStore : FridayConvoStore {
        override val conversations: StateFlow<List<FridayConversation>> = MutableStateFlow(emptyList())
        override fun refresh() {}
        override fun createConversation(gatewayId: String) = FridayConversation("c", "t", gatewayId, 0, 0)
        override fun getConversation(id: String): FridayConversation? = null
        override fun getTurns(conversationId: String): List<FridayTurn> = emptyList()
        override fun addTurn(turn: FridayTurn) {}
        override fun updateTurn(turn: FridayTurn) {}
        override fun deleteConversation(id: String) {}
    }

    private class FakeVoiceIo : VoiceIo {
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override val recordingAmplitude: StateFlow<Float> = MutableStateFlow(0f)
        override fun startRecording(): Boolean = false
        override suspend fun stopRecordingAndRecognize(): String? = null
        override fun cancelRecording() {}
        override fun stopSpeaking() {}
        override suspend fun speak(messageId: String, text: String): Boolean = true
    }

    private class FakeRoute : VoiceRoutePort {
        override fun route(): VoiceRouter.Route = VoiceRouter.Route.NoVoice
    }

    private class FakeContextEngine : ContextHistorySource {
        override suspend fun buildHistory(conversationId: String?, pendingTurns: List<FridayTurn>?): List<GatewayTurn> = emptyList()
        override fun onUserTurnPersisted(turn: FridayTurn) {}
        override fun onTurnCompleted(conversationId: String) {}
    }

    private fun vm(brain: FakeBrain): FridayVoiceViewModel =
        FridayVoiceViewModel(FakeRoute(), VoiceBridge(brain), FakeConvoStore(), FakeVoiceIo(), FakeContextEngine())

    @Test
    fun confirm_reachesBrainConfirmationGate() = runTest {
        val brain = FakeBrain()
        vm(brain).confirm()
        assertTrue("VM confirm() must reach the brain's confirmation gate", brain.confirmed)
    }

    @Test
    fun awaitingConfirmation_reflectsBrainGateState() = runTest {
        val brain = FakeBrain()
        val model = vm(brain)
        assertFalse("no gate armed -> not awaiting", model.awaitingConfirmation())
        brain.awaiting = true
        assertTrue("armed gate -> awaiting through the VM call site", model.awaitingConfirmation())
    }
}

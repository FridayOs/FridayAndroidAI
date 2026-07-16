package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceAdapter
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceHandle
import com.dark.tool_neuron.repo.gateway.live.SpeakOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Drives a full turn through the LiveVoiceAdapter/LiveVoiceHandle seam using only interface types, proving
// the shape is provider-agnostic (no Gemini-specific import here) and ready for a future OpenAI transport.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveVoiceAdapterContractTest {

    private class SeamHandle : LiveVoiceHandle {
        val events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 8)
        var endUserTurnCalls = 0
        var cancelCalls = 0
        val spoken = mutableListOf<String>()
        override fun run(): Flow<LiveEvent> = events
        override fun endUserTurn() { endUserTurnCalls++ }
        override fun onAudioFocusLost() {}
        override fun onAudioRouteChanged() {}
        override fun onBackground() {}
        override fun onPermissionRevoked() {}
        override fun cancel() { cancelCalls++ }
        override fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> =
            flow { emit(GatewayEvent.Delta("part")); emit(GatewayEvent.Done("part answer")) }
        override fun brainCancel() {}
        override fun brainConfirm(): Boolean = true
        override fun resumeUserTurn(): Boolean = true
        override suspend fun speak(text: String): SpeakOutcome { spoken += text; return SpeakOutcome.Completed }
    }

    private class SeamAdapter : LiveVoiceAdapter {
        val handle = SeamHandle()
        var openedWith: GatewayConfig? = null
        override fun supports(config: GatewayConfig): Boolean = true
        override fun open(config: GatewayConfig, voice: String, locale: String, bargeIn: Boolean): LiveVoiceHandle {
            openedWith = config
            return handle
        }
    }

    private fun voiceGateway() = GatewayConfig(
        id = "seam-1", provider = GatewayProvider.GEMINI, label = "seam", baseUrl = "", apiKey = "key",
        model = "seam-model", createdAt = 0, updatedAt = 0,
    )

    @Test
    fun fullTurn_flowsThroughAdapterSeamUsingOnlyInterfaceTypes() = runTest {
        val seamAdapter = SeamAdapter()
        val adapter: LiveVoiceAdapter = seamAdapter
        val config = voiceGateway()
        assertTrue(adapter.supports(config))

        val handle: LiveVoiceHandle = adapter.open(config, "Aoede", "en-US", bargeIn = true)

        val collected = mutableListOf<LiveEvent>()
        val job = launch(Dispatchers.Unconfined) {
            handle.run().collect { collected += it }
        }
        seamAdapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        seamAdapter.handle.events.tryEmit(LiveEvent.InputTranscript("seam transcript"))
        seamAdapter.handle.events.tryEmit(LiveEvent.TurnComplete)
        job.cancel()

        assertEquals(
            listOf(
                LiveEvent.State(LiveSessionState.CONFIGURED),
                LiveEvent.InputTranscript("seam transcript"),
                LiveEvent.TurnComplete,
            ),
            collected,
        )

        handle.endUserTurn()
        val brainEvents = handle.brainTurn(emptyList(), "seam transcript").toList()
        assertEquals(listOf(GatewayEvent.Delta("part"), GatewayEvent.Done("part answer")), brainEvents)

        // B1: the Brain answer is vocalized through the same seam (Voice Gateway TTS), never Gemini's own audio.
        assertEquals(SpeakOutcome.Completed, handle.speak("brain final answer"))
        assertEquals(listOf("brain final answer"), seamAdapter.handle.spoken)

        handle.cancel()
        assertEquals(1, seamAdapter.handle.endUserTurnCalls)
        assertEquals(1, seamAdapter.handle.cancelCalls)
    }
}

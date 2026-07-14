package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.live.BrainCorrelation
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSink
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSource
import com.dark.tool_neuron.repo.gateway.live.LiveBrainGateway
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceSessionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Proves create() atomically supersedes the prior session through the production factory seam (singleton mic/player).
class LiveVoiceSessionFactoryTest {

    private class FakeSource : LiveAudioSource {
        var stopCount = 0
        override fun hasPermission(): Boolean = true
        override fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit) = true
        override fun stop() { stopCount++ }
    }

    private class FakeSink : LiveAudioSink {
        var stopCount = 0
        override fun start(onError: (Throwable) -> Unit) {}
        override fun currentGeneration(): Long = 0
        override fun enqueue(pcm: ByteArray, gen: Long) {}
        override fun flush() {}
        override fun stop() { stopCount++ }
    }

    private class FakeBrain : LiveBrainGateway {
        override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> = emptyFlow()
        override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> = emptyFlow()
        override fun brainCancel(correlation: BrainCorrelation) {}
        override fun brainConfirm(correlation: BrainCorrelation): Boolean = false
        override fun brainAwaitingConfirmation(): Boolean = false
    }

    private fun geminiConfig() = GatewayConfig(
        id = "g1", provider = GatewayProvider.GEMINI, label = "Gemini",
        baseUrl = "", apiKey = "sk-live", model = "", createdAt = 0, updatedAt = 0,
    )

    @Test
    fun creatingASecondSession_supersedesTheFirst() {
        val src = FakeSource(); val sink = FakeSink()
        val factory = LiveVoiceSessionFactory(src, sink, FakeBrain())

        val a = factory.create(geminiConfig())
        val b = factory.create(geminiConfig())

        // The first session was atomically torn down when the second was created.
        assertEquals("superseded session ends CLOSED", LiveSessionState.CLOSED, a.state.value)
        assertEquals("the new session is fresh", LiveSessionState.IDLE, b.state.value)
        assertTrue("superseding released the shared mic + player", src.stopCount >= 1 && sink.stopCount >= 1)
    }
}

package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// A Gemini Live session paired with its Brain Gateway bridge; both share one sessionId so audio and reasoning stay correlated.
class LiveVoiceSession internal constructor(
    private val engine: LiveSessionEngine,
    private val config: GeminiLiveConfig,
    val sessionId: String,
    val cloudBridge: LiveCloudBridge,
    private val synthesizer: LiveVoiceSynthesizer,
    private val sink: LiveAudioSink,
) {
    val state: StateFlow<LiveSessionState> get() = engine.state

    fun run(): Flow<LiveEvent> = engine.run(config)
    fun endUserTurn() = engine.endUserTurn()
    fun resumeUserTurn(): Boolean = engine.resumeUserTurn()

    // B1: vocalize the Brain Gateway's answer. Synthesize the text (direct to the user's Gemini host) and play
    // the PCM to completion in the SAME sink the engine uses — in brain-owns-answer mode Gemini's own audio is
    // discarded, so only this TTS PCM is ever played. Capturing the generation BEFORE synthesis means a
    // barge-in flush during the network call bumps the token and playToCompletion returns Superseded. A
    // cancelled speak coroutine (barge-in/reset) flushes the sink and rethrows.
    suspend fun speak(text: String): SpeakOutcome {
        val gen = sink.currentGeneration()
        try {
            return when (val result = synthesizer.synthesize(config, text)) {
                is SynthResult.Empty -> SpeakOutcome.Empty
                is SynthResult.Failed -> SpeakOutcome.Failed(result.message)
                is SynthResult.Audio -> when (val r = sink.playToCompletion(result.pcm, gen)) {
                    PlaybackResult.Completed -> SpeakOutcome.Completed
                    PlaybackResult.Superseded -> SpeakOutcome.Superseded
                    is PlaybackResult.Failed -> SpeakOutcome.Failed(r.reason)
                }
            }
        } catch (ce: CancellationException) {
            sink.flush()
            throw ce
        }
    }

    // Device/app lifecycle passthroughs the host (FRI-562) wires to audio-focus/route/foreground/permission callbacks.
    fun onAudioFocusLost() = engine.onAudioFocusLost()
    fun onAudioRouteChanged() = engine.onAudioRouteChanged()
    fun onBackground() = engine.onBackground()
    fun onPermissionRevoked() {
        cloudBridge.brainCancel()
        engine.onPermissionRevoked()
    }

    // Tearing the session down also cancels any in-flight brain turn and drops pending TTS — everything stops together.
    fun cancel() {
        cloudBridge.brainCancel()
        sink.flush()
        engine.cancel()
    }
}

// Mic/player/brain are app singletons, so create() atomically supersedes any prior session — only ONE is live at a time.
@Singleton
class LiveVoiceSessionFactory @Inject constructor(
    private val capture: LiveAudioSource,
    private val player: LiveAudioSink,
    private val brain: LiveBrainGateway,
    private val synthesizer: LiveVoiceSynthesizer,
) {
    private val active = AtomicReference<LiveVoiceSession?>(null)

    // Only Gemini's wire format is a Live-capable cloud voice transport in this task's scope.
    fun supports(config: GatewayConfig): Boolean =
        config.provider.wireFormat == GatewayWireFormat.GEMINI

    fun create(
        config: GatewayConfig,
        voice: String = GeminiLiveConfig.DEFAULT_VOICE,
        locale: String = "en-US",
        bargeIn: Boolean = true,
    ): LiveVoiceSession {
        val sessionId = UUID.randomUUID().toString()
        val session = LiveVoiceSession(
            engine = LiveSessionEngine(
                transportFactory = { LiveWebSocketTransport() },
                capture = capture,
                player = player,
            ),
            config = GeminiLiveConfig.from(config, voice, locale, bargeIn),
            sessionId = sessionId,
            cloudBridge = LiveCloudBridge(brain, sessionId),
            synthesizer = synthesizer,
            sink = player,
        )
        active.getAndSet(session)?.cancel()
        return session
    }
}

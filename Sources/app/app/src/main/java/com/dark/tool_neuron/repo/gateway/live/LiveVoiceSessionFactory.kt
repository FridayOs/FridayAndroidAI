package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
import com.dark.tool_neuron.repo.gateway.BrainBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// A configured Gemini Live session paired with its Brain Gateway bridge. The session owns realtime audio;
// cloudBridge routes the transcript to the brain via the four brain_* ops. Both share one sessionId so a
// turn's audio and its reasoning request stay correlated.
class LiveVoiceSession internal constructor(
    private val engine: LiveSessionEngine,
    private val config: GeminiLiveConfig,
    val sessionId: String,
    val cloudBridge: LiveCloudBridge,
) {
    val state: StateFlow<LiveSessionState> get() = engine.state

    fun run(): Flow<LiveEvent> = engine.run(config)
    fun endUserTurn() = engine.endUserTurn()

    // Device/app lifecycle passthroughs the host (FRI-562) wires to audio-focus/route/foreground/permission callbacks.
    fun onAudioFocusLost() = engine.onAudioFocusLost()
    fun onAudioRouteChanged() = engine.onAudioRouteChanged()
    fun onBackground() = engine.onBackground()
    fun onPermissionRevoked() {
        cloudBridge.brainCancel()
        engine.onPermissionRevoked()
    }

    // Tearing the session down also cancels any in-flight brain turn — audio and reasoning stop together.
    fun cancel() {
        cloudBridge.brainCancel()
        engine.cancel()
    }
}

// Builds a fresh LiveVoiceSession per voice turn from a user-owned GatewayConfig. Injectable so FRI-562's
// Voice Home can open a Gemini Live session and bridge its transcript to brain_turn — this factory owns
// transport/session + the brain-bridge wiring, never the brain reasoning or the Android action layer itself.
@Singleton
class LiveVoiceSessionFactory @Inject constructor(
    private val capture: LiveAudioCapture,
    private val player: LiveAudioPlayer,
    private val brain: BrainBridge,
) {
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
        return LiveVoiceSession(
            engine = LiveSessionEngine(
                transportFactory = { LiveWebSocketTransport() },
                capture = capture,
                player = player,
            ),
            config = GeminiLiveConfig.from(config, voice, locale, bargeIn),
            sessionId = sessionId,
            cloudBridge = LiveCloudBridge(brain, sessionId),
        )
    }
}

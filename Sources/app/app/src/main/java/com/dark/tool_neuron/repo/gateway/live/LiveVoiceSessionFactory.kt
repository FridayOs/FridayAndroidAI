package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
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

// Mic/player/brain are app singletons, so create() atomically supersedes any prior session — only ONE is live at a time.
@Singleton
class LiveVoiceSessionFactory @Inject constructor(
    private val capture: LiveAudioSource,
    private val player: LiveAudioSink,
    private val brain: LiveBrainGateway,
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
        )
        active.getAndSet(session)?.cancel()
        return session
    }
}

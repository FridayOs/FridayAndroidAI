package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

// A configured Gemini Live session: pairs the engine with its resolved wire config so the caller just run()s.
class LiveVoiceSession internal constructor(
    private val engine: LiveSessionEngine,
    private val config: GeminiLiveConfig,
) {
    val state: StateFlow<LiveSessionState> get() = engine.state

    fun run(): Flow<LiveEvent> = engine.run(config)
    fun endUserTurn() = engine.endUserTurn()
    fun cancel() = engine.cancel()
}

// Builds a fresh LiveVoiceSession per voice turn from a user-owned GatewayConfig. Injectable so FRI-562's
// CloudBridge (the router seam from FRI-553) can open a Gemini Live session and bridge its transcript to
// brain_turn — this factory owns transport/session only, never the brain reasoning or the Android action layer.
@Singleton
class LiveVoiceSessionFactory @Inject constructor(
    private val capture: LiveAudioCapture,
    private val player: LiveAudioPlayer,
) {
    // Only Gemini's wire format is a Live-capable cloud voice transport in this task's scope.
    fun supports(config: GatewayConfig): Boolean =
        config.provider.wireFormat == GatewayWireFormat.GEMINI

    fun create(
        config: GatewayConfig,
        voice: String = GeminiLiveConfig.DEFAULT_VOICE,
        locale: String = "en-US",
        bargeIn: Boolean = true,
    ): LiveVoiceSession = LiveVoiceSession(
        engine = LiveSessionEngine(
            transportFactory = { LiveWebSocketTransport() },
            capture = capture,
            player = player,
        ),
        config = GeminiLiveConfig.from(config, voice, locale, bargeIn),
    )
}

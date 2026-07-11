package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.GatewayConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

// Voice layer router. The voice VM asks `route()` for every turn so a mid-turn
// switch (user picks Gemini voice while a local-voice turn is in flight) only
// applies to the next voice capture. The current turn keeps its resolved route
// until completion or cancel.
//
// Cloud voice (Gemini Live / OpenAI Realtime) needs a bidirectional audio
// socket that is not part of M2-02. Until that socket lands, selecting a CLOUD
// voice gateway surfaces CloudVoiceCapability.Unavailable — the screen is
// honest about it instead of silently using local STT/TTS under a Gemini label.
// Local voice still works because the brain bridge + local STT/TTS paths are
// already wired.
@Singleton
class VoiceRouter @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
) {

    sealed interface Route {
        // Use on-device STT to produce a transcript, then brain_turn runs the
        // reply against the active Brain Gateway, then on-device TTS speaks it.
        data class LocalBridge(val voice: GatewayConfig, val brain: GatewayConfig?) : Route
        // Cloud realtime-audio voice is not implemented in M2-02 — the
        // capability hook returns Unavailable so callers don't fall through to
        // local STT under a Gemini/OpenAI label.
        data class CloudUnavailable(val voice: GatewayConfig, val capability: CloudVoiceCapability) : Route
        data object NoVoice : Route
        data object NoBrainForCloud : Route
    }

    fun route(): Route = decide(gatewayRepo.voiceGateway(), gatewayRepo.brainGateway())

    companion object {
        // Pure routing decision, split out so it is unit-testable off-device with
        // plain GatewayConfig values (no HXS / Hilt). route() just feeds it the
        // live repo pointers.
        fun decide(voice: GatewayConfig?, brain: GatewayConfig?): Route {
            if (voice == null) return Route.NoVoice
            return when (voice.voiceRoute) {
                VoiceRoute.LOCAL -> Route.LocalBridge(voice, brain)
                VoiceRoute.CLOUD ->
                    if (brain == null) Route.NoBrainForCloud
                    else Route.CloudUnavailable(voice, CloudVoiceCapability.Unavailable)
            }
        }
    }
}
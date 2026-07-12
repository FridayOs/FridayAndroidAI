package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.GatewayConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRouter @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
) : VoiceRoutePort {

    sealed interface Route {
        data class LocalBridge(val voice: GatewayConfig, val brain: GatewayConfig) : Route
        data class CloudBridge(val voice: GatewayConfig, val brain: GatewayConfig) : Route
        data object NoVoice : Route
        data object NoBrain : Route
    }

    override fun route(): Route = decide(gatewayRepo.voiceGateway(), gatewayRepo.brainGateway())

    companion object {
        // Both routes bridge reasoning to the brain, so a brain is required before either opens the mic.
        fun decide(voice: GatewayConfig?, brain: GatewayConfig?): Route {
            if (voice == null) return Route.NoVoice
            if (brain == null) return Route.NoBrain
            return when (voice.voiceRoute) {
                VoiceRoute.LOCAL -> Route.LocalBridge(voice, brain)
                VoiceRoute.CLOUD -> Route.CloudBridge(voice, brain)
            }
        }
    }
}

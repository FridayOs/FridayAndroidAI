package com.dark.tool_neuron.repo.gateway

// Voice routing seam so FridayVoiceViewModel resolves its route against a fake off-device.
interface VoiceRoutePort {
    fun route(): VoiceRouter.Route
}

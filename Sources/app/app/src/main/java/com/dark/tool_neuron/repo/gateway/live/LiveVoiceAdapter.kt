package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow

// Provider-agnostic live-voice seam: Gemini implements it today, OpenAI Realtime is a future
// transport behind this same shape (see LiveVoiceAdapterContractTest).
interface LiveVoiceAdapter {
    fun supports(config: GatewayConfig): Boolean
    fun open(config: GatewayConfig, voice: String, locale: String, bargeIn: Boolean): LiveVoiceHandle
}

// One live-voice session's lifecycle + brain delegation, mirrored 1:1 from LiveVoiceSession so the
// VM never imports a provider-specific type.
interface LiveVoiceHandle {
    fun run(): Flow<LiveEvent>
    fun endUserTurn()
    fun onAudioFocusLost()
    fun onAudioRouteChanged()
    fun onBackground()
    fun onPermissionRevoked()
    fun cancel()
    fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent>
    fun brainCancel()
    fun brainConfirm(): Boolean
    // B1: vocalize the Brain Gateway's final answer through the Voice Gateway (suspends until the PCM is
    // synthesized + enqueued; single-chunk speak-on-Done). Text only ever crosses here — never audio.
    suspend fun speak(text: String)
}

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
    // Cloud barge-in: re-open the mic for a fresh user turn on the SAME live session (endUserTurn stopped
    // capture). Returns false when the session can't resume (closed/cancelled) so the VM opens a fresh one.
    fun resumeUserTurn(): Boolean
    // B1: vocalize the Brain Gateway's final answer through the Voice Gateway. Suspends until the PCM has
    // actually finished playing (so the terminal SpeakComplete fires after playback, not on enqueue).
    // Returns a typed outcome so the VM surfaces a synthesis failure honestly. Text only ever crosses here.
    suspend fun speak(text: String): SpeakOutcome
}

// Completed = played to the end. Superseded = a barge-in flushed it mid-play. Empty = nothing to speak.
// Failed carries a sanitized, key-free message.
sealed interface SpeakOutcome {
    data object Completed : SpeakOutcome
    data object Superseded : SpeakOutcome
    data object Empty : SpeakOutcome
    data class Failed(val message: String) : SpeakOutcome
}

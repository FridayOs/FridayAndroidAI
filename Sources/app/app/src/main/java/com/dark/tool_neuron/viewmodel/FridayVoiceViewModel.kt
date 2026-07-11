package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.gateway.CloudVoiceCapability
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayRoleRouter
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import com.dark.tool_neuron.voice.VoiceModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class VoiceMode { IDLE, LISTENING, THINKING, SPEAKING, DONE }

// The voice VM resolves the active Voice Router route per turn. The router
// decides whether audio is handled locally (VoiceModelManager STT/TTS + brain
// bridge) or by a cloud realtime gateway (not implemented in M2-02 — surfaces
// a clear error instead of pretending). Reasoning still always goes through
// the active Brain Gateway — never through the FRIDAY API.
@HiltViewModel
class FridayVoiceViewModel @Inject constructor(
    private val router: GatewayRoleRouter,
    private val voiceRouter: VoiceRouter,
    private val convoRepo: FridayConversationRepository,
    private val voiceManager: VoiceModelManager,
) : ViewModel() {

    private val _mode = MutableStateFlow(VoiceMode.IDLE)
    val mode: StateFlow<VoiceMode> = _mode.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val amplitude: StateFlow<Float> = voiceManager.recordingAmplitude

    private var conversationId: String? = null
    private var flowJob: Job? = null

    fun clearError() { _error.value = null }

    // Push-to-talk press: begin capturing mic audio. Requires the RECORD_AUDIO
    // grant (requested by the screen) plus an installed STT model when the
    // active Voice Gateway resolves to a local bridge. CLOUD voice gateways
    // surface their Unavailable status before the mic is asked for.
    fun startListening() {
        if (_mode.value == VoiceMode.THINKING || _mode.value == VoiceMode.LISTENING) return
        when (val route = voiceRouter.route()) {
            is VoiceRouter.Route.CloudUnavailable -> {
                _error.value = "Cloud voice (${route.voice.label}) requires realtime audio — coming soon. Add or pick a Local voice gateway."
                return
            }
            is VoiceRouter.Route.NoVoice -> {
                _error.value = "Add a Voice Gateway in the menu to talk to Friday."
                return
            }
            is VoiceRouter.Route.NoBrainForCloud -> {
                _error.value = "Voice Gateway picked, but no Brain Gateway is selected — pick a brain first."
                return
            }
            is VoiceRouter.Route.LocalBridge -> Unit
        }
        voiceManager.stopSpeaking()
        flowJob?.cancel()
        _error.value = null
        _question.value = ""
        _answer.value = ""
        val started = voiceManager.startRecording()
        if (!started) {
            _error.value = voiceManager.error.value ?: "Could not start recording"
            _mode.value = VoiceMode.IDLE
            return
        }
        _mode.value = VoiceMode.LISTENING
    }

    // Push-to-talk release: transcribe locally, then stream a brain reply and
    // speak it. Audio is always handled here on-device (the only Voice Gateway
    // route supported in M2-02); reasoning bridges to the active Brain Gateway.
    fun stopListening() {
        if (_mode.value != VoiceMode.LISTENING) return
        _mode.value = VoiceMode.THINKING
        flowJob = viewModelScope.launch {
            val transcript = voiceManager.stopRecordingAndRecognize()
            if (transcript.isNullOrBlank()) {
                _error.value = voiceManager.error.value ?: "No speech detected"
                _mode.value = VoiceMode.IDLE
                return@launch
            }
            _question.value = transcript
            respond(transcript)
        }
    }

    fun cancel() {
        flowJob?.cancel()
        router.brainCancel()
        voiceManager.cancelRecording()
        voiceManager.stopSpeaking()
        _mode.value = VoiceMode.IDLE
        _question.value = ""
        _answer.value = ""
        _error.value = null
    }

    private suspend fun respond(transcript: String) {
        val brain = router.brainGateway()
        if (brain == null) {
            _error.value = router.brainUnavailableMessage()
            _mode.value = VoiceMode.IDLE
            return
        }
        val convoId = conversationId ?: convoRepo.createConversation(brain.id).id.also { conversationId = it }
        val now = System.currentTimeMillis()
        convoRepo.addTurn(
            FridayTurn(
                id = UUID.randomUUID().toString(),
                conversationId = convoId,
                role = "user",
                content = transcript,
                timestamp = now,
                viaVoice = true,
            )
        )
        val history = convoRepo.getTurns(convoId).map { GatewayTurn(it.role, it.content) }
        router.brainTurn(history).collect { event ->
            when (event) {
                is GatewayEvent.Delta -> {
                    if (_mode.value != VoiceMode.SPEAKING) _mode.value = VoiceMode.SPEAKING
                    _answer.value += event.text
                }
                is GatewayEvent.Done -> {
                    val finalText = event.fullText
                    _answer.value = finalText
                    _mode.value = VoiceMode.SPEAKING
                    if (finalText.isNotBlank()) {
                        convoRepo.addTurn(
                            FridayTurn(
                                id = UUID.randomUUID().toString(),
                                conversationId = convoId,
                                role = "assistant",
                                content = finalText,
                                timestamp = System.currentTimeMillis(),
                                viaVoice = true,
                            )
                        )
                        val spoken = voiceManager.speak(UUID.randomUUID().toString(), finalText)
                        if (!spoken) _error.value = voiceManager.error.value
                    }
                    _mode.value = VoiceMode.DONE
                }
                is GatewayEvent.Error -> {
                    _error.value = event.message
                    _mode.value = VoiceMode.IDLE
                }
            }
        }
    }

    override fun onCleared() {
        voiceManager.stopSpeaking()
        flowJob?.cancel()
    }
}
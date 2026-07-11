package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
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

sealed interface VoiceSelectorRequest {
    data object NeedVoiceGateway : VoiceSelectorRequest
    data object NeedBrainGateway : VoiceSelectorRequest
}

@HiltViewModel
class FridayVoiceViewModel @Inject constructor(
    private val voiceRouter: VoiceRouter,
    private val bridge: VoiceBridge,
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

    private val _selectorRequest = MutableStateFlow<VoiceSelectorRequest?>(null)
    val selectorRequest: StateFlow<VoiceSelectorRequest?> = _selectorRequest.asStateFlow()

    val amplitude: StateFlow<Float> = voiceManager.recordingAmplitude

    private var conversationId: String? = null
    private var brainForTurn: GatewayConfig? = null
    private var flowJob: Job? = null

    fun clearError() { _error.value = null }
    fun clearSelectorRequest() { _selectorRequest.value = null }

    // Gate mic before recording so a missing gateway opens the selector, never records-then-fails.
    fun startListening() {
        if (_mode.value == VoiceMode.THINKING || _mode.value == VoiceMode.LISTENING) return
        val brain = when (val route = voiceRouter.route()) {
            is VoiceRouter.Route.LocalBridge -> route.brain
            is VoiceRouter.Route.CloudBridge -> route.brain
            VoiceRouter.Route.NoVoice -> {
                _selectorRequest.value = VoiceSelectorRequest.NeedVoiceGateway
                return
            }
            VoiceRouter.Route.NoBrain -> {
                _selectorRequest.value = VoiceSelectorRequest.NeedBrainGateway
                return
            }
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
        brainForTurn = brain
        _mode.value = VoiceMode.LISTENING
    }

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
        bridge.brainCancel()
        voiceManager.cancelRecording()
        voiceManager.stopSpeaking()
        _mode.value = VoiceMode.IDLE
        _question.value = ""
        _answer.value = ""
        _error.value = null
    }

    private suspend fun respond(transcript: String) {
        val brain = brainForTurn ?: run {
            _selectorRequest.value = VoiceSelectorRequest.NeedBrainGateway
            _mode.value = VoiceMode.IDLE
            return
        }
        val convoId = conversationId ?: convoRepo.createConversation(brain.id).id.also { conversationId = it }
        convoRepo.addTurn(
            FridayTurn(
                id = UUID.randomUUID().toString(),
                conversationId = convoId,
                role = "user",
                content = transcript,
                timestamp = System.currentTimeMillis(),
                viaVoice = true,
            )
        )
        val history = convoRepo.getTurns(convoId).map { GatewayTurn(it.role, it.content) }
        bridge.brainTurn(history).collect { event ->
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

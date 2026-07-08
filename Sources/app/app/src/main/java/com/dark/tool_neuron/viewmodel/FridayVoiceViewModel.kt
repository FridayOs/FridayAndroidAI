package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.repo.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceMode { IDLE, LISTENING, THINKING, SPEAKING, DONE }

@HiltViewModel
class FridayVoiceViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _mode = MutableStateFlow(VoiceMode.IDLE)
    val mode: StateFlow<VoiceMode> = _mode.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer.asStateFlow()

    private var sampleIdx = 0
    private var flowJob: Job? = null

    fun tapMic() {
        when (_mode.value) {
            VoiceMode.IDLE, VoiceMode.DONE -> startListening()
            VoiceMode.LISTENING -> stopListening()
            VoiceMode.SPEAKING -> endSpeaking()
            VoiceMode.THINKING -> Unit
        }
    }

    fun reset() {
        flowJob?.cancel()
        _mode.value = VoiceMode.IDLE
        _question.value = ""
        _answer.value = ""
    }

    private fun startListening() {
        flowJob?.cancel()
        _answer.value = ""
        _question.value = ""
        _mode.value = VoiceMode.LISTENING
        val full = SAMPLES[sampleIdx]
        flowJob = viewModelScope.launch {
            for (i in 1..full.length) {
                _question.value = full.take(i)
                delay(42)
            }
            delay(600)
            if (_mode.value == VoiceMode.LISTENING) stopListening()
        }
    }

    private fun stopListening() {
        flowJob?.cancel()
        _question.value = SAMPLES[sampleIdx] + "?"
        _mode.value = VoiceMode.THINKING
        flowJob = viewModelScope.launch {
            delay(1400)
            speak()
        }
    }

    private fun speak() {
        val full = ANSWERS[sampleIdx]
        _mode.value = VoiceMode.SPEAKING
        _answer.value = ""
        flowJob = viewModelScope.launch {
            for (i in 1..full.length) {
                _answer.value = full.take(i)
                delay(24)
            }
            endSpeaking()
        }
    }

    private fun endSpeaking() {
        flowJob?.cancel()
        _answer.value = ANSWERS[sampleIdx]
        _mode.value = VoiceMode.DONE
        sampleIdx = (sampleIdx + 1) % SAMPLES.size
    }

    companion object {
        private val SAMPLES = listOf(
            "What are the advantages of online education",
            "Give me three quick ideas for a healthy weeknight dinner",
            "Explain how voice recognition works in simple terms",
        )
        private val ANSWERS = listOf(
            "Online learning gives you flexibility to study anywhere and at your own pace, often at a lower cost than a traditional classroom.",
            "Try a sheet-pan salmon with broccoli and lemon, a chickpea and spinach curry over rice, or a quick veg stir-fry — each ready in about 25 minutes.",
            "Your phone turns your voice into a wave, slices it into tiny pieces, and matches the patterns to words it has learned from millions of examples.",
        )
    }
}

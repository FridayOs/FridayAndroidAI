package com.dark.tool_neuron.voice

import kotlinx.coroutines.flow.StateFlow

// Voice I/O seam so FridayVoiceViewModel drives record/recognize/speak against a fake off-device.
interface VoiceIo {
    val error: StateFlow<String?>
    val recordingAmplitude: StateFlow<Float>
    fun startRecording(): Boolean
    suspend fun stopRecordingAndRecognize(): String?
    fun cancelRecording()
    fun stopSpeaking()
    suspend fun speak(messageId: String, text: String): Boolean
}

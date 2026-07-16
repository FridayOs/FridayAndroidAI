package com.dark.tool_neuron.service.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.dark.tool_neuron.voice.VoiceModelManager
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Minimal, honest single-utterance recognizer that satisfies the assistant-picker XML contract
// (interaction_service.xml requires a recognitionService). It owns its OWN AudioRecord — never the
// singleton SttRecorder, which a live voice session may be holding — records until the client stops
// (or a hard cap), then recognizes through the installed sherpa STT model. No partial results, no
// hotword, no always-on mic; the mic opens only inside an explicit system recognition request.
@AndroidEntryPoint
class FridaySpeechRecognitionService : RecognitionService() {

    @Inject lateinit var voice: Lazy<VoiceModelManager>

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var recordJob: Job? = null
    @Volatile private var capJob: Job? = null
    @Volatile private var recording = false
    private val buffer = ArrayList<Float>(SAMPLE_RATE * 15)

    override fun onStartListening(recognizerIntent: Intent, callback: Callback) {
        // Confused-deputy guard: the calling client must itself hold RECORD_AUDIO or we'd record
        // on its behalf with our own permission.
        val callerUid = try { callback.callingUid } catch (_: Throwable) { -1 }
        if (callerUid <= 0 ||
            checkPermission(Manifest.permission.RECORD_AUDIO, -1, callerUid) != PackageManager.PERMISSION_GRANTED
        ) {
            safeError(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            safeError(callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }
        if (!voice.get().hasStt()) {
            safeError(callback, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
            return
        }
        if (!startCapture()) {
            safeError(callback, SpeechRecognizer.ERROR_AUDIO)
            return
        }
        try { callback.readyForSpeech(Bundle()) } catch (_: Throwable) {}
        try { callback.beginningOfSpeech() } catch (_: Throwable) {}
        // Hard cap so a client that never calls stopListening can't hold the mic open forever.
        capJob = scope.launch {
            kotlinx.coroutines.delay(MAX_UTTERANCE_MS)
            finish(callback)
        }
    }

    override fun onStopListening(callback: Callback) = finish(callback)

    override fun onCancel(callback: Callback) {
        // Same synchronized discipline as finish(): cap-job expiry / stopListening / cancel race
        // on the shared recorder state.
        synchronized(this) {
            capJob?.cancel(); capJob = null
            stopCapture()
            synchronized(buffer) { buffer.clear() }
        }
    }

    override fun onDestroy() {
        capJob?.cancel()
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    // Cap-job expiry and client stopListening can race; only one may drain and emit a result.
    @Synchronized
    private fun finish(callback: Callback) {
        if (!recording && recorder == null) return
        capJob?.cancel(); capJob = null
        val samples = drainCapture()
        try { callback.endOfSpeech() } catch (_: Throwable) {}
        scope.launch {
            val text = try { voice.get().recognizeSamples(samples) } catch (_: Throwable) { null }
            if (text.isNullOrBlank()) {
                safeError(callback, SpeechRecognizer.ERROR_NO_MATCH)
            } else {
                val results = Bundle().apply {
                    putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                }
                try { callback.results(results) } catch (_: Throwable) {}
            }
        }
    }

    private fun startCapture(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
        ).coerceAtLeast(4096)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, minBuf * 2,
            )
        } catch (_: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            try { rec.release() } catch (_: Throwable) {}
            return false
        }
        recorder = rec
        synchronized(buffer) { buffer.clear() }
        recording = true
        recordJob = scope.launch {
            val chunk = FloatArray(CHUNK_SAMPLES)
            try {
                rec.startRecording()
                while (recording) {
                    val n = rec.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    synchronized(buffer) { for (i in 0 until n) buffer.add(chunk[i]) }
                }
            } catch (_: Throwable) {
            }
        }
        return true
    }

    private fun stopCapture() {
        recording = false
        recordJob?.cancel(); recordJob = null
        val rec = recorder
        recorder = null
        if (rec != null) {
            try { rec.stop() } catch (_: Throwable) {}
            try { rec.release() } catch (_: Throwable) {}
        }
    }

    private fun drainCapture(): FloatArray {
        stopCapture()
        return synchronized(buffer) {
            val arr = FloatArray(buffer.size)
            for (i in buffer.indices) arr[i] = buffer[i]
            buffer.clear()
            arr
        }
    }

    private fun safeError(callback: Callback, code: Int) {
        try { callback.error(code) } catch (_: Throwable) {}
    }

    private companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SAMPLES = 1024
        const val MAX_UTTERANCE_MS = 15_000L
    }
}

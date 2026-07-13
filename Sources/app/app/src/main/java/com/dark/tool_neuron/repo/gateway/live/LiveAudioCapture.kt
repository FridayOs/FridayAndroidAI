package com.dark.tool_neuron.repo.gateway.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Streams 16kHz mono PCM16 mic frames to Gemini Live. Capture stops the moment the session tears down —
// the recorder is released in stop(), never left holding the mic after a turn ends.
@Singleton
class LiveAudioCapture @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val recording = AtomicBoolean(false)
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var job: Job? = null

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    // Emits PCM16 little-endian chunks via onChunk on an IO coroutine; returns false if the mic can't open.
    fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit): Boolean {
        if (recording.get()) return true
        if (!hasPermission()) return false
        val minBuf = AudioRecord.getMinBufferSize(
            LiveProtocol.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_BYTES)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                LiveProtocol.INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
        } catch (_: SecurityException) {
            return false
        } catch (_: Throwable) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }
        recorder = rec
        recording.set(true)
        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(CHUNK_BYTES)
            try {
                rec.startRecording()
                while (recording.get()) {
                    val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) continue
                    onChunk(buf.copyOf(n))
                }
            } catch (_: Throwable) {
                // Surfaced by the session engine as an AUDIO error via the state stream.
            }
        }
        return true
    }

    fun stop() {
        if (!recording.compareAndSet(true, false)) {
            releaseRecorder()
            return
        }
        job?.cancel()
        job = null
        releaseRecorder()
    }

    private fun releaseRecorder() {
        val rec = recorder ?: return
        recorder = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
    }

    companion object {
        // 100ms of 16kHz PCM16 mono = 1600 samples * 2 bytes = 3200 bytes; keeps upload latency low.
        private const val CHUNK_BYTES = 3200
    }
}

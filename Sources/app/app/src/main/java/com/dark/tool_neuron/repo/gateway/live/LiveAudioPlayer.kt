package com.dark.tool_neuron.repo.gateway.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Plays Gemini Live's 24kHz PCM16 in arrival order on a dedicated consumer coroutine; flush() bumps a generation token so a barge-in drops the current turn's audio instantly.
@Singleton
class LiveAudioPlayer @Inject constructor() : LiveAudioSink {

    @Volatile private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)

    // A per-turn generation token: bumped on flush() so queued/writing chunks from a superseded turn are dropped.
    @Volatile private var generation = 0L

    private var scope: CoroutineScope? = null
    private var queue: Channel<Chunk>? = null
    private var consumer: Job? = null

    private data class Chunk(val pcm: ByteArray, val gen: Long)

    // ~64 chunks of 24kHz PCM16 is a few seconds of buffered audio — enough headroom, bounded RAM.
    private companion object { const val QUEUE_CAPACITY = 64 }

    @Volatile private var errored = false

    @Synchronized
    override fun start(onError: (Throwable) -> Unit) {
        if (playing.get()) return
        val t = try {
            buildTrack().also { it.play() }
        } catch (t: Throwable) {
            // A build/play failure is a terminal AUDIO fault, not a transport error — surface it once.
            if (!errored) { errored = true; onError(t) }
            return
        }
        track = t
        playing.set(true)

        // Bounded + DROP_OLDEST so a slow AudioTrack can't grow the queue without limit; newest audio wins.
        val ch = Channel<Chunk>(QUEUE_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        queue = ch
        scope = sc
        consumer = sc.launch {
            for (chunk in ch) drain(chunk.pcm, chunk.gen, onError)
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            LiveProtocol.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(LiveProtocol.OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    override fun currentGeneration(): Long = generation

    // Enqueue a chunk for ordered playback. Non-blocking on the caller — the consumer coroutine does the write.
    override fun enqueue(pcm: ByteArray, gen: Long) {
        if (gen != generation) return
        queue?.trySend(Chunk(pcm, gen))
    }

    // Blocking write on the consumer coroutine; abandons the chunk if a flush bumped the generation mid-write.
    private fun drain(pcm: ByteArray, gen: Long, onError: (Throwable) -> Unit) {
        val t = track ?: return
        if (gen != generation) return
        var offset = 0
        while (offset < pcm.size && gen == generation && playing.get()) {
            val written = t.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            // A negative return is an AudioTrack error (dead object / invalid op) — surface AUDIO, don't stall silently.
            if (written < 0) { if (!errored) { errored = true; onError(IOException("AudioTrack.write failed ($written)")) }; return }
            if (written == 0) break
            offset += written
        }
    }

    // Barge-in / interrupt: bump generation, drain the superseded turn's queued chunks, pause+flush the track.
    @Synchronized
    override fun flush() {
        generation++
        // Drain stale queued chunks so the superseded turn's PCM can't delay the next turn.
        queue?.let { q ->
            while (q.tryReceive().isSuccess) Unit
        }
        val t = track ?: return
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.play() }
    }

    @Synchronized
    override fun stop() {
        generation++
        errored = false
        playing.set(false)
        queue?.close()
        queue = null
        consumer?.cancel()
        consumer = null
        scope?.cancel()
        scope = null
        val t = track ?: return
        track = null
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.stop() }
        runCatching { t.release() }
    }
}

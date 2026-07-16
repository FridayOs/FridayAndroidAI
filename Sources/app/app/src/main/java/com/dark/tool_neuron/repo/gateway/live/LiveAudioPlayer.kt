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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private companion object {
        // ~64 chunks of 24kHz PCM16 is a few seconds of buffered audio — enough headroom, bounded RAM.
        const val QUEUE_CAPACITY = 64
        // PCM16 mono = 2 bytes per frame; used to convert the written byte offset into a frame count.
        const val BYTES_PER_FRAME = 2
        // Poll cadence for the playback-head drain wait; small enough to feel instant, cheap enough on CPU.
        const val DRAIN_POLL_MS = 20L
    }

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

    // B1 speak-on-Done: write one chunk and SUSPEND until it has actually drained through the AudioTrack,
    // so the caller fires SpeakComplete after playback (not on enqueue). Honors the generation token so a
    // barge-in flush() returns false immediately. Only the cloud brain-owns path calls this, and it never
    // uses enqueue() concurrently, so there's no consumer-coroutine contention on the track.
    override suspend fun playToCompletion(pcm: ByteArray, gen: Long): Boolean =
        withContext(Dispatchers.IO) {
            val t = track ?: return@withContext false
            if (gen != generation) return@withContext false
            var offset = 0
            while (offset < pcm.size && gen == generation && playing.get()) {
                ensureActive()
                val written = t.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) return@withContext false
                if (written == 0) break
                offset += written
            }
            if (gen != generation) return@withContext false
            // MODE_STREAM buffers ahead of the DAC; poll the playback head until every written frame has played.
            val targetFrame = t.playbackHeadPosition + (offset / BYTES_PER_FRAME)
            while (gen == generation && playing.get() && t.playbackHeadPosition < targetFrame) {
                ensureActive()
                delay(DRAIN_POLL_MS)
            }
            gen == generation
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

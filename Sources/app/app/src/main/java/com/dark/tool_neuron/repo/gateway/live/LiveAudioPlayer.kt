package com.dark.tool_neuron.repo.gateway.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// Plays Gemini Live's 24kHz PCM16 audio in arrival order on a dedicated consumer coroutine, so a barge-in
// flush() never waits behind an in-flight AudioTrack.WRITE_BLOCKING call on the receive thread. flush() bumps
// a generation token and drops the current turn's queued audio instantly so a stale chunk never plays after
// the user cuts in.
@Singleton
class LiveAudioPlayer @Inject constructor() {

    @Volatile private var track: AudioTrack? = null
    private val playing = AtomicBoolean(false)

    // A per-turn generation token: bumped on flush() so queued/writing chunks from a superseded turn are dropped.
    @Volatile private var generation = 0L

    private var scope: CoroutineScope? = null
    private var queue: Channel<Chunk>? = null
    private var consumer: Job? = null

    private data class Chunk(val pcm: ByteArray, val gen: Long)

    @Synchronized
    fun start() {
        if (playing.get()) return
        val minBuf = AudioTrack.getMinBufferSize(
            LiveProtocol.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val t = AudioTrack.Builder()
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
        track = t
        t.play()
        playing.set(true)

        val ch = Channel<Chunk>(Channel.UNLIMITED)
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        queue = ch
        scope = sc
        consumer = sc.launch {
            for (chunk in ch) drain(chunk.pcm, chunk.gen)
        }
    }

    fun currentGeneration(): Long = generation

    // Enqueue a chunk for ordered playback. Non-blocking on the caller — the consumer coroutine does the write.
    fun enqueue(pcm: ByteArray, gen: Long) {
        if (gen != generation) return
        queue?.trySend(Chunk(pcm, gen))
    }

    // Blocking write on the consumer coroutine; abandons the chunk if a flush bumped the generation mid-write.
    private fun drain(pcm: ByteArray, gen: Long) {
        val t = track ?: return
        if (gen != generation) return
        var offset = 0
        while (offset < pcm.size && gen == generation && playing.get()) {
            val written = t.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
    }

    // Barge-in / interrupt: bump generation, pause+flush the track so queued PCM stops immediately.
    @Synchronized
    fun flush() {
        generation++
        val t = track ?: return
        runCatching { t.pause() }
        runCatching { t.flush() }
        runCatching { t.play() }
    }

    @Synchronized
    fun stop() {
        generation++
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

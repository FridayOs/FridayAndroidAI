package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

// Minimal seam so the drain math is provable off-device (no Android AudioTrack in unit tests).
internal interface PcmOutput {
    fun write(pcm: ByteArray, offset: Int, size: Int): Int   // WRITE_BLOCKING: >0 bytes, 0 accepted-none, <0 fault
    fun playbackHeadFrames(): Int                            // monotonic frames played (Int-wraps like AudioTrack)
    fun isPlaying(): Boolean
}

internal object LiveAudioDrain {
    const val BYTES_PER_FRAME = 2
    const val DRAIN_POLL_MS = 20L
    const val DRAIN_MARGIN_MS = 1500L   // cap the drain wait at audio duration + margin so a stalled track can't hang speak()

    suspend fun drainToCompletion(
        out: PcmOutput,
        pcm: ByteArray,
        gen: Long,
        liveGen: () -> Long,
        sampleRate: Int,
    ): PlaybackResult {
        if (gen != liveGen()) return PlaybackResult.Superseded
        if (!out.isPlaying()) return PlaybackResult.Failed("audio track not playing")
        val startHead = out.playbackHeadFrames()   // BEFORE any write — capturing after overshoots
        var offset = 0
        while (offset < pcm.size) {
            coroutineContext.ensureActive()
            if (gen != liveGen()) return PlaybackResult.Superseded
            if (!out.isPlaying()) return PlaybackResult.Superseded   // stop() bumps gen too
            val written = out.write(pcm, offset, pcm.size - offset)
            if (written < 0) return PlaybackResult.Failed("AudioTrack.write failed ($written)")
            if (written == 0) return PlaybackResult.Failed("AudioTrack.write stalled (0 bytes)")
            offset += written
        }
        val submittedFrames = offset / BYTES_PER_FRAME
        if (submittedFrames <= 0) return PlaybackResult.Completed
        val durationMs = submittedFrames.toLong() * 1000L / sampleRate
        val maxPolls = ((durationMs + DRAIN_MARGIN_MS) / DRAIN_POLL_MS).coerceAtLeast(1)
        var polls = 0L
        while (true) {
            coroutineContext.ensureActive()
            if (gen != liveGen()) return PlaybackResult.Superseded
            if (!out.isPlaying()) return PlaybackResult.Superseded
            val played = out.playbackHeadFrames() - startHead   // Int delta is wrap-safe across one 2^31 wrap
            if (played >= submittedFrames) return PlaybackResult.Completed
            if (++polls > maxPolls) return PlaybackResult.Failed("playback drain timeout")
            delay(DRAIN_POLL_MS)
        }
    }
}

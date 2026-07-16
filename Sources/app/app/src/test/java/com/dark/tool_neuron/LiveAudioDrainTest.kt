package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveAudioDrain
import com.dark.tool_neuron.repo.gateway.live.LiveProtocol
import com.dark.tool_neuron.repo.gateway.live.PcmOutput
import com.dark.tool_neuron.repo.gateway.live.PlaybackResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// D1 proof: LiveAudioDrain's math is provable off-device — no overshoot/hang, real faults surface as Failed
// (never mislabeled Superseded), and a mid-drain generation bump supersedes cleanly.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveAudioDrainTest {

    // Scriptable PcmOutput: write() accumulates submittedFrames + optionally nudges head; playbackHeadFrames()
    // nudges head toward submittedFrames by headStepFrames each poll (0 = never advances on its own).
    private class FakePcmOutput(
        private val writeChunkBytes: Int = Int.MAX_VALUE,
        private val headStepFrames: Int = Int.MAX_VALUE,
        private val advanceHeadPerWriteFrames: Int = 0,
        private val negative: Boolean = false,
        private val zero: Boolean = false,
        private val playing: Boolean = true,
        private val onPollAfterFirstCall: (() -> Unit)? = null,
    ) : PcmOutput {
        var submittedFrames = 0
            private set
        var head = 0
            private set
        private var pollCalls = 0

        override fun write(pcm: ByteArray, offset: Int, size: Int): Int {
            if (negative) return -3
            if (zero) return 0
            val n = minOf(size, writeChunkBytes)
            submittedFrames += n / LiveAudioDrain.BYTES_PER_FRAME
            head += advanceHeadPerWriteFrames
            return n
        }

        override fun playbackHeadFrames(): Int {
            pollCalls++
            if (pollCalls > 1) onPollAfterFirstCall?.invoke()
            if (head < submittedFrames) head = minOf(submittedFrames, head + headStepFrames)
            return head
        }

        override fun isPlaying(): Boolean = playing
    }

    private fun sampleRate() = LiveProtocol.OUTPUT_SAMPLE_RATE

    @Test
    fun largeChunk_drainsToCompletion_noOvershootNoHang() = runTest {
        // Partial writes (small writeChunkBytes) force multiple write() calls; advanceHeadPerWriteFrames>0
        // during the write loop reproduces the old-bug overshoot trigger — startHead capture BEFORE writes fixes it.
        val out = FakePcmOutput(writeChunkBytes = 20, advanceHeadPerWriteFrames = 5, headStepFrames = 15)
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 0L, liveGen = { 0L }, sampleRate = sampleRate())

        assertEquals(PlaybackResult.Completed, result)
    }

    @Test
    fun negativeWrite_returnsFailed_notSuperseded() = runTest {
        val out = FakePcmOutput(negative = true)
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 0L, liveGen = { 0L }, sampleRate = sampleRate())

        assertTrue("dead-track fault must surface as Failed", result is PlaybackResult.Failed)
        assertNotEquals("a real fault must never be conflated with a barge-in supersede", PlaybackResult.Superseded, result)
    }

    @Test
    fun zeroWrite_returnsFailed() = runTest {
        val out = FakePcmOutput(zero = true)
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 0L, liveGen = { 0L }, sampleRate = sampleRate())

        assertTrue(result is PlaybackResult.Failed)
    }

    @Test
    fun supersedeDuringDrain_returnsSuperseded() = runTest {
        // headStepFrames=0 so the fake's head never advances on its own — the drain wait loop stays parked
        // until the generation bump (mid-loop) supersedes it.
        val liveGen = AtomicLong(0)
        var bumped = false
        val out = FakePcmOutput(
            headStepFrames = 0,
            onPollAfterFirstCall = { if (!bumped) { bumped = true; liveGen.incrementAndGet() } },
        )
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 0L, liveGen = { liveGen.get() }, sampleRate = sampleRate())

        assertEquals(PlaybackResult.Superseded, result)
    }

    @Test
    fun stalledTrack_timesOut_returnsFailed() = runTest {
        val out = FakePcmOutput(headStepFrames = 0, advanceHeadPerWriteFrames = 0)
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 0L, liveGen = { 0L }, sampleRate = sampleRate())

        assertTrue(result is PlaybackResult.Failed)
        assertTrue((result as PlaybackResult.Failed).reason.contains("timeout"))
    }

    @Test
    fun supersedeBeforeStart_returnsSuperseded() = runTest {
        val out = FakePcmOutput()
        val pcm = ByteArray(200)

        val result = LiveAudioDrain.drainToCompletion(out, pcm, gen = 5L, liveGen = { 0L }, sampleRate = sampleRate())

        assertEquals(PlaybackResult.Superseded, result)
        assertEquals("no write should happen when already superseded", 0, out.submittedFrames)
    }
}

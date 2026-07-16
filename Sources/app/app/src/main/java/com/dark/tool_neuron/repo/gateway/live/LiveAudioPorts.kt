package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.CoroutineScope

// Seams over the Android audio classes so the engine's mic/playback/teardown flow is unit-testable off-device against fakes.
interface LiveAudioSource {
    fun hasPermission(): Boolean
    // false when the mic can't open; onError fires once on a failure AFTER a successful open so the engine ends the session as AUDIO.
    fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean
    fun stop()
}

interface LiveAudioSink {
    // onError fires once if the track can't build/play or a write fails, so the engine surfaces a terminal AUDIO error.
    fun start(onError: (Throwable) -> Unit)
    fun currentGeneration(): Long
    fun enqueue(pcm: ByteArray, gen: Long)
    // B1 speak-on-Done: play one chunk and SUSPEND until the audio has actually drained, so the caller can
    // fire the terminal SpeakComplete only after playback finished (not the moment it was queued). Returns
    // false when a flush bumped the generation mid-playback (superseded turn). Used only by the cloud
    // brain-owns-answer path, where enqueue() is never called — so no consumer-coroutine contention.
    suspend fun playToCompletion(pcm: ByteArray, gen: Long): Boolean
    // Barge-in / interrupt: drop the current turn's queued audio instantly.
    fun flush()
    fun stop()
}

package com.dark.tool_neuron.repo.gateway.live

import kotlinx.coroutines.CoroutineScope

// Seams over the Android audio classes so the session engine's mic/playback/teardown flow is unit-testable
// off-device against fakes (AudioRecord/AudioTrack can't run in a JVM test). LiveAudioCapture/LiveAudioPlayer
// are the real Android implementations.
interface LiveAudioSource {
    fun hasPermission(): Boolean
    // Streams PCM16 chunks via onChunk on an IO coroutine; false when the mic can't open (denied/unavailable).
    // onError fires once if the recorder fails AFTER a successful open (mic yanked, read error) so the engine
    // surfaces a terminal AUDIO error instead of silently keeping the socket alive.
    fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean
    fun stop()
}

interface LiveAudioSink {
    fun start()
    fun currentGeneration(): Long
    fun enqueue(pcm: ByteArray, gen: Long)
    // Barge-in / interrupt: drop the current turn's queued audio instantly.
    fun flush()
    fun stop()
}

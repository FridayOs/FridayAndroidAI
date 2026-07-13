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
    fun start()
    fun currentGeneration(): Long
    fun enqueue(pcm: ByteArray, gen: Long)
    // Barge-in / interrupt: drop the current turn's queued audio instantly.
    fun flush()
    fun stop()
}

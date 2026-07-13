package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSink
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSource
import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionEngine
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.repo.gateway.live.LiveTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Lifecycle-contract proof (FRI-548 owns transport/session teardown): audio focus loss, route change, background,
// permission revoke, network recovery and rapid start/stop each drive the right capture/playback/socket response
// with no leaked mic or socket. Runs off-device against fake transport + audio seams.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionEngineLifecycleTest {

    private class FakeSource : LiveAudioSource {
        var startCount = 0
        var stopCount = 0
        var permission = true
        var openable = true
        override fun hasPermission(): Boolean = permission
        override fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit): Boolean {
            startCount++
            return openable
        }
        override fun stop() { stopCount++ }
    }

    private class FakeSink : LiveAudioSink {
        var startCount = 0
        var flushCount = 0
        var stopCount = 0
        private var gen = 0L
        override fun start() { startCount++ }
        override fun currentGeneration(): Long = gen
        override fun enqueue(pcm: ByteArray, gen: Long) {}
        override fun flush() { flushCount++; gen++ }
        override fun stop() { stopCount++ }
    }

    // A transport whose inbound frames the test scripts; records close() so teardown is observable.
    private class FakeTransport(
        private val frames: List<LiveTransport.Incoming> = emptyList(),
        private val throwOnOpen: Throwable? = null,
        private val keepOpen: Boolean = false,
    ) : LiveTransport {
        var closeCount = 0
        var sent = mutableListOf<String>()
        override fun open(host: String, port: Int, path: String, onReady: () -> Unit): Flow<LiveTransport.Incoming> = callbackFlow {
            throwOnOpen?.let { throw it }
            onReady()
            frames.forEach { trySend(it) }
            if (!keepOpen) close()
            awaitClose { }
        }
        override fun sendText(text: String) { sent += text }
        override fun close() { closeCount++ }
    }

    private fun config() = GeminiLiveConfig(
        apiKey = "sk-test", model = "gemini-2.0-flash-live-001", voice = "Aoede",
        locale = "en-US", bargeIn = true, baseHost = GeminiLiveConfig.DEFAULT_HOST,
    )

    private fun engine(
        source: FakeSource = FakeSource(),
        sink: FakeSink = FakeSink(),
        transportFactory: () -> LiveTransport = { FakeTransport() },
    ) = LiveSessionEngine(transportFactory, source, sink)

    @Test
    fun audioFocusLost_stopsCaptureFlushesPlaybackAndTearsDown() {
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink)
        eng.onAudioFocusLost()
        assertTrue("capture must stop on focus loss", src.stopCount >= 1)
        assertTrue("playback must flush on focus loss", sink.flushCount >= 1)
        assertTrue("session tears down", sink.stopCount >= 1)
        assertEquals(LiveSessionState.CLOSED, eng.state.value)
    }

    @Test
    fun background_isTerminalTeardown_neverKeepsMicAlive() {
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink)
        eng.onBackground()
        assertTrue("mic must not survive backgrounding", src.stopCount >= 1)
        assertEquals(LiveSessionState.CLOSED, eng.state.value)
    }

    @Test
    fun audioRouteChanged_flushesPlaybackButKeepsSessionAlive() {
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink)
        eng.onAudioRouteChanged()
        assertTrue("route change flushes queued audio", sink.flushCount >= 1)
        assertEquals("route change is not a teardown", 0, sink.stopCount)
        assertEquals("route change does not stop capture", 0, src.stopCount)
    }

    @Test
    fun permissionRevoked_endsSessionAsError() {
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink)
        eng.onPermissionRevoked()
        assertTrue("capture stops when permission is revoked", src.stopCount >= 1)
        // ERROR is terminal and must not be masked by CLOSED on teardown.
        assertEquals(LiveSessionState.ERROR, eng.state.value)
    }

    @Test
    fun rapidStartStop_isIdempotent_noLeak() {
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink)
        // Hammer cancel/lifecycle without a running flow — must not crash and must always release audio.
        repeat(20) {
            eng.cancel()
            eng.onAudioRouteChanged()
            eng.onAudioFocusLost()
        }
        assertTrue(src.stopCount >= 1)
        assertTrue(sink.stopCount >= 1)
        assertEquals(LiveSessionState.CLOSED, eng.state.value)
    }

    @Test
    fun networkDropThenRecovery_reconnectsAndReachesConfigured() = runTest {
        // First attempt drops (IOException = NETWORK, transient); second attempt reaches setupComplete.
        val attempts = AtomicInteger(0)
        val src = FakeSource(); val sink = FakeSink()
        val eng = engine(src, sink) {
            if (attempts.getAndIncrement() == 0) FakeTransport(throwOnOpen = IOException("network down"))
            else FakeTransport(frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")))
        }
        val states = mutableListOf<LiveSessionState>()
        // Collect on a child job; cancel the moment recovery reaches CONFIGURED so the healthy-reconnect loop ends.
        val job = launch {
            eng.run(config()).collect {
                if (it is LiveEvent.State) {
                    states += it.state
                    if (it.state == LiveSessionState.CONFIGURED) eng.cancel()
                }
            }
        }
        advanceUntilIdle()
        job.join()

        assertTrue("a transient network drop retries", states.contains(LiveSessionState.RECONNECTING))
        assertTrue("recovery reaches CONFIGURED on reconnect", states.contains(LiveSessionState.CONFIGURED))
        assertTrue("second attempt was made", attempts.get() >= 2)
    }

    @Test
    fun authFailureDoesNotRetry_surfacesErrorImmediately() = runTest {
        val eng = engine {
            FakeTransport(throwOnOpen = LiveTransport.HandshakeException(401, "unauthorized"))
        }
        val events = eng.run(config()).toList()
        val error = events.filterIsInstance<LiveEvent.Error>().firstOrNull()
        assertTrue("auth failure surfaces an error", error != null)
        assertEquals(LiveErrorKind.AUTH, error!!.kind)
        // No RECONNECTING — auth is non-retryable.
        assertTrue(events.none { it is LiveEvent.State && it.state == LiveSessionState.RECONNECTING })
    }

    @Test
    fun cleanCollectorCancel_tearsDownTransportAndAudio() = runTest {
        val src = FakeSource(); val sink = FakeSink()
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, sink) { transport }
        val collected = mutableListOf<LiveEvent>()
        val job = launch { eng.run(config()).collect { collected += it } }
        advanceUntilIdle()
        job.cancel()
        job.join()
        eng.cancel()
        assertTrue("transport closed on teardown", transport.closeCount >= 1)
        assertTrue("capture released on teardown", src.stopCount >= 1)
        assertTrue("playback released on teardown", sink.stopCount >= 1)
    }

    @Test
    fun micDeniedMidSession_surfacesPermission_notTimeoutLoop() = runTest {
        val src = FakeSource().apply { openable = false } // mic can't open after setupComplete
        val sink = FakeSink()
        val eng = engine(src, sink) {
            FakeTransport(frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")))
        }
        val events = eng.run(config()).toList()
        val error = events.filterIsInstance<LiveEvent.Error>().firstOrNull()
        assertEquals(LiveErrorKind.PERMISSION, error?.kind)
        // PERMISSION is terminal — the socket must not idle into a TIMEOUT retry loop.
        assertTrue(events.none { it is LiveEvent.State && it.state == LiveSessionState.RECONNECTING })
    }
}

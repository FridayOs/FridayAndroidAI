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

// Lifecycle-contract proof: each device/app event drives the right capture/playback/socket response, no leaks.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionEngineLifecycleTest {

    private class FakeSource : LiveAudioSource {
        var startCount = 0
        var stopCount = 0
        var permission = true
        var openable = true
        var failAfterStart = false // simulate a runtime mic failure after a successful open
        override fun hasPermission(): Boolean = permission
        override fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean {
            startCount++
            if (!openable) return false
            if (failAfterStart) onError(IOException("mic died mid-session"))
            return true
        }
        override fun stop() { stopCount++ }
    }

    private class FakeSink : LiveAudioSink {
        var startCount = 0
        var flushCount = 0
        var stopCount = 0
        var failOnStart = false // simulate an AudioTrack build/play failure
        private var gen = 0L
        override fun start(onError: (Throwable) -> Unit) { startCount++; if (failOnStart) onError(IOException("audio track dead")) }
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
    fun rapidLifecycleCallsOnIdleEngine_areSafe() {
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

    // A real running session started and torn down repeatedly: every cycle releases mic+player+transport, no leak.
    @Test
    fun rapidRunningSessionStartStop_noLeak() = runTest {
        repeat(5) {
            val src = FakeSource(); val sink = FakeSink()
            val transport = FakeTransport(
                frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
                keepOpen = true,
            )
            val eng = engine(src, sink) { transport }
            val job = launch { eng.run(config()).collect {} }
            advanceUntilIdle()
            assertTrue("running session opened the mic", src.startCount >= 1)
            job.cancel(); job.join()
            eng.cancel()
            assertTrue("transport closed on stop", transport.closeCount >= 1)
            assertTrue("capture released on stop", src.stopCount >= 1)
            assertTrue("playback released on stop", sink.stopCount >= 1)
            assertEquals(LiveSessionState.CLOSED, eng.state.value)
        }
    }

    // Mic fails AFTER a successful open: engine surfaces terminal AUDIO, never a silent socket or retry loop.
    @Test
    fun micFailsAfterOpen_surfacesAudioError_notRetryLoop() = runTest {
        val src = FakeSource().apply { failAfterStart = true }
        val sink = FakeSink()
        val eng = engine(src, sink) {
            FakeTransport(frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")))
        }
        val events = eng.run(config()).toList()
        val error = events.filterIsInstance<LiveEvent.Error>().firstOrNull()
        assertEquals(LiveErrorKind.AUDIO, error?.kind)
        assertTrue(
            "AUDIO is terminal — no reconnect loop",
            events.none { it is LiveEvent.State && it.state == LiveSessionState.RECONNECTING },
        )
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

    // A peer that configures then abnormally drops in a loop must NOT reconnect forever — bounded, then terminal.
    @Test
    fun repeatedConfigureThenDrop_terminatesWithBoundedReconnects() = runTest {
        val eng = engine {
            FakeTransport(
                frames = listOf(
                    LiveTransport.Incoming.Text("""{"setupComplete":{}}"""),
                    LiveTransport.Incoming.Closed(1011, ""),
                ),
            )
        }
        val events = eng.run(config()).toList()
        assertTrue(
            "a configure/drop loop terminates with ERROR",
            events.any { it is LiveEvent.State && it.state == LiveSessionState.ERROR },
        )
        val reconnects = events.count { it is LiveEvent.State && it.state == LiveSessionState.RECONNECTING }
        assertTrue("reconnects are bounded, not infinite", reconnects in 1..11)
    }

    // A playback build/play failure ends the session as terminal AUDIO, not a retry loop.
    @Test
    fun playbackStartFailure_surfacesAudio() = runTest {
        val sink = FakeSink().apply { failOnStart = true }
        val eng = engine(FakeSource(), sink) {
            FakeTransport(frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")))
        }
        val events = eng.run(config()).toList()
        assertEquals(LiveErrorKind.AUDIO, events.filterIsInstance<LiveEvent.Error>().firstOrNull()?.kind)
        assertTrue(events.none { it is LiveEvent.State && it.state == LiveSessionState.RECONNECTING })
    }

    // A client-side send failure surfaces a classified error (NETWORK), not a silent EOF/REMOTE_CLOSE.
    @Test
    fun transportSendFailure_isClassifiedNotSilent() = runTest {
        val eng = engine {
            FakeTransport(
                frames = listOf(
                    LiveTransport.Incoming.Text("""{"setupComplete":{}}"""),
                    LiveTransport.Incoming.TransportError(IOException("broken pipe")),
                ),
            )
        }
        val events = eng.run(config()).toList()
        assertTrue(
            "the send failure is surfaced as a classified NETWORK error",
            events.filterIsInstance<LiveEvent.Error>().any { it.kind == LiveErrorKind.NETWORK },
        )
    }

    // A provider error echoing the api key must be redacted before it reaches the UI.
    @Test
    fun serverErrorEchoingKey_isSanitized() = runTest {
        val eng = engine {
            FakeTransport(frames = listOf(LiveTransport.Incoming.Text("""{"error":{"code":401,"message":"bad key sk-test in request"}}""")))
        }
        val events = eng.run(config()).toList()
        val msg = events.filterIsInstance<LiveEvent.Error>().firstOrNull()?.message
        assertTrue("error message must not leak the api key", msg != null && !msg.contains("sk-test"))
    }
}

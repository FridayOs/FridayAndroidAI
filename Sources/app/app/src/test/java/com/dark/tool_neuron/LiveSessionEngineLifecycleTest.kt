package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSink
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSource
import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionEngine
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.repo.gateway.live.LiveTransport
import com.dark.tool_neuron.repo.gateway.live.PlaybackResult
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
        var lastOnChunk: ((ByteArray) -> Unit)? = null
        override fun hasPermission(): Boolean = permission
        override fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean {
            startCount++
            if (!openable) return false
            lastOnChunk = onChunk
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
        override suspend fun playToCompletion(pcm: ByteArray, gen: Long): PlaybackResult = PlaybackResult.Completed
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

    // D2 production-path proof: a cloud barge-in resume must reopen the mic and keep streaming on the SAME
    // socket (no reconnect), proving resumeUserTurn()/startMicStreaming() wiring end-to-end against the real engine.
    @Test
    fun cloudBargeIn_resumeUserTurn_restartsCaptureAndSendsChunkOnSameTransport() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, FakeSink()) { transport }
        val job = launch { eng.run(config()).collect {} }
        advanceUntilIdle()

        assertEquals("initial turn opens the mic once", 1, src.startCount)

        src.lastOnChunk!!.invoke(ByteArray(320))
        assertTrue(
            "captured chunk is sent as a realtimeInput audio frame",
            transport.sent.any { it.contains("realtimeInput") && it.contains("audio") },
        )

        eng.endUserTurn()
        assertTrue("ending the turn stops capture", src.stopCount >= 1)
        assertTrue("ending the turn sends audioStreamEnd", transport.sent.any { it.contains("audioStreamEnd") })

        assertTrue("resumeUserTurn succeeds on a live session", eng.resumeUserTurn())
        assertEquals("resume reopens the mic", 2, src.startCount)

        val before = transport.sent.size
        src.lastOnChunk!!.invoke(ByteArray(320))
        assertTrue("resumed capture sends a new frame", transport.sent.size > before)
        val newFrame = transport.sent.last()
        assertTrue(
            "the new frame is a realtimeInput audio frame",
            newFrame.contains("realtimeInput") && newFrame.contains("audio"),
        )
        assertEquals("resume reuses the same socket, no reconnect", 0, transport.closeCount)

        job.cancel(); job.join(); eng.cancel()
    }

    @Test
    fun resumeUserTurn_afterCancel_returnsFalse() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, FakeSink()) { transport }
        val job = launch { eng.run(config()).collect {} }
        advanceUntilIdle()
        job.cancel(); job.join()
        eng.cancel()

        assertEquals(false, eng.resumeUserTurn())
    }

    @Test
    fun resumeUserTurn_onIdleEngine_returnsFalse() {
        val eng = engine()
        assertEquals(false, eng.resumeUserTurn())
    }

    // E1 race: a clean server close runs connectOnce's finally (liveGate cleared, socket closed) before the
    // outer loop even attempts a reconnect. resumeUserTurn() must see the cleared gate and refuse to restart capture.
    @Test
    fun resumeUserTurn_afterCleanClose_returnsFalse_noCaptureRestart() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(
                LiveTransport.Incoming.Text("""{"setupComplete":{}}"""),
                LiveTransport.Incoming.Closed(1000, ""),
            ),
        )
        val eng = engine(src, FakeSink()) { transport }
        eng.run(config()).toList()

        val starts = src.startCount
        assertEquals(false, eng.resumeUserTurn())
        assertEquals("resume must not restart capture on a closed socket", starts, src.startCount)
    }

    // E1 race: the collector cancel drives connectOnce's finally (liveGate cleared) which happens-before
    // teardown() runs — so resumeUserTurn() must return false even in this pre-teardown window, not just after
    // teardown has fully run.
    @Test
    fun resumeUserTurn_afterCollectorCancelTeardownRace_returnsFalse() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, FakeSink()) { transport }
        val job = launch { eng.run(config()).collect {} }
        advanceUntilIdle()
        assertEquals(1, src.startCount)

        job.cancel(); job.join()

        val starts = src.startCount
        assertEquals(false, eng.resumeUserTurn())
        assertEquals(starts, src.startCount)
    }

    // E2 race: an early close (peer Closed frame handled mid-collect) invalidates the gate via closeLive()
    // immediately, well BEFORE connectOnce's finally runs. keepOpen=true keeps the callbackFlow suspended in
    // awaitClose after both frames are delivered, so finally never runs here -- isolating the pre-finally window.
    // resumeLock also serializes the concurrent read-before-clear that a single-threaded test can't interleave.
    @Test
    fun resumeUserTurn_afterEarlyServerClose_beforeFinally_returnsFalse() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(
                LiveTransport.Incoming.Text("""{"setupComplete":{}}"""),
                LiveTransport.Incoming.Closed(1011, ""),
            ),
            keepOpen = true,
        )
        val eng = engine(src, FakeSink()) { transport }
        val events = mutableListOf<LiveEvent>()
        val job = launch { eng.run(config()).collect { events += it } }
        advanceUntilIdle()

        val listeningBefore = events.count { it is LiveEvent.State && it.state == LiveSessionState.LISTENING }
        val starts = src.startCount
        assertEquals("resume in the close-before-finally window must return false", false, eng.resumeUserTurn())
        assertEquals("no capture restart in the window", starts, src.startCount)
        assertEquals(
            "no LISTENING emitted on the closing socket",
            listeningBefore,
            events.count { it is LiveEvent.State && it.state == LiveSessionState.LISTENING },
        )

        job.cancel(); job.join(); eng.cancel()
    }

    // F1: a synchronous playback-start failure (player.start -> onError -> failAudio -> closeLive) must never
    // let the following (now-removed) re-arm line resurrect the lease on a dead socket.
    @Test
    fun resumeUserTurn_afterSyncPlaybackStartFailure_returnsFalse_notReArmed() = runTest {
        val src = FakeSource()
        val sink = FakeSink().apply { failOnStart = true }   // player.start → synchronous onError → failAudio → closeLive
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, sink) { transport }
        val events = mutableListOf<LiveEvent>()
        val job = launch { eng.run(config()).collect { events += it } }
        advanceUntilIdle()
        // Playback-start failed synchronously → closeLive revoked the lease. It must NOT have been re-armed.
        val startsAtFailure = src.startCount
        val listeningAtFailure = events.count { it is LiveEvent.State && it.state == LiveSessionState.LISTENING }
        assertEquals(false, eng.resumeUserTurn())
        assertEquals("resume must not restart capture on a failed-startup socket", startsAtFailure, src.startCount)
        assertEquals(
            "resume must not emit a new LISTENING on a failed-startup socket",
            listeningAtFailure,
            events.count { it is LiveEvent.State && it.state == LiveSessionState.LISTENING },
        )
        job.cancel(); job.join(); eng.cancel()
    }

    // F2: after resume, a lease revocation must stop the ALREADY-CAPTURED mic onChunk callback from sending —
    // proving the gate is re-checked per-chunk, not just at startMicStreaming() call time.
    @Test
    fun resumeThenRevoke_captureCallbackSendsNothingAfterInvalidation() = runTest {
        val src = FakeSource()
        val transport = FakeTransport(
            frames = listOf(LiveTransport.Incoming.Text("""{"setupComplete":{}}""")),
            keepOpen = true,
        )
        val eng = engine(src, FakeSink()) { transport }
        val job = launch { eng.run(config()).collect {} }
        advanceUntilIdle()
        assertEquals(1, src.startCount)
        eng.endUserTurn()
        assertTrue(eng.resumeUserTurn())
        assertEquals(2, src.startCount)
        val resumedChunk = src.lastOnChunk!!
        // Lease still active: the captured callback DOES send.
        val beforeActive = transport.sent.size
        resumedChunk.invoke(ByteArray(320))
        assertTrue("active lease must send mic frames", transport.sent.size > beforeActive)
        // Close wins after resume → finally revokes the lease. The SAME captured callback must now send nothing.
        job.cancel(); job.join()
        val afterRevoke = transport.sent.size
        resumedChunk.invoke(ByteArray(320))
        assertEquals("no mic frame may be sent after the lease is revoked", afterRevoke, transport.sent.size)
        eng.cancel()
    }
}

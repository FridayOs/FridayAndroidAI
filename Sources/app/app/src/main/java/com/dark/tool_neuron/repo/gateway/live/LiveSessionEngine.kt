package com.dark.tool_neuron.repo.gateway.live

import android.util.Base64
import com.dark.tool_neuron.repo.gateway.GatewayErrorSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

// Owns one Gemini Live session: connect → send setup → configure → stream mic → receive+play audio, with bounded retry.
// Audio never touches the brain; the transcript it yields is bridged to brain_turn by the caller (FRI-553 seam).
// Every failure surfaces as a displayable, secret-free LiveEvent.Error — connected/ready is never faked.
internal class LiveSessionEngine(
    private val transportFactory: () -> LiveTransport,
    private val capture: LiveAudioSource,
    private val player: LiveAudioSink,
) {
    private val _state = MutableStateFlow(LiveSessionState.IDLE)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val cancelled = AtomicBoolean(false)
    private val audioSeq = AtomicLong(0)
    @Volatile private var bargeIn = true
    // Reset to true once an attempt reaches CONFIGURED so a healthy session that blips later keeps its full retry budget.
    @Volatile private var reachedConfigured = false
    private val transport = AtomicReference<LiveTransport?>(null)
    @Volatile private var sessionScope: CoroutineScope? = null

    // Runs the full session as a cold flow. Collecting starts it; cancelling the collector tears everything down.
    fun run(config: GeminiLiveConfig): Flow<LiveEvent> = callbackFlow {
        cancelled.set(false)
        bargeIn = config.bargeIn
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessionScope = scope

        var attempt = 0
        try {
            while (!cancelled.get()) {
                reachedConfigured = false
                val outcome = connectOnce(config, scope) { trySend(it) }
                if (cancelled.get() || outcome == null) break
                if (!LiveRetryPolicy.retryable(outcome)) {
                    emitState(LiveSessionState.ERROR) { trySend(it) }
                    break
                }
                // A connection that got as far as CONFIGURED was healthy — reset the backoff budget on its drop.
                if (reachedConfigured) attempt = 0
                val backoff = LiveRetryPolicy.backoffMillis(attempt++)
                if (backoff == null) {
                    emitState(LiveSessionState.ERROR) { trySend(it) }
                    break
                }
                emitState(LiveSessionState.RECONNECTING) { trySend(it) }
                delay(backoff)
            }
        } catch (_: CancellationException) {
            // Collector left — fall through to teardown.
        }

        // The retry loop ended (terminal error / clean close / cancel), so close the producer — the flow completes
        // for the collector instead of hanging in awaitClose. A collector that cancels mid-session while the loop
        // is still streaming never reaches here; its cancellation fires awaitClose's teardown directly.
        close()
        awaitClose { teardown() }
    }

    // One connect attempt. Returns null on clean close/cancel, or the error kind to drive retry decisions.
    private suspend fun connectOnce(
        config: GeminiLiveConfig,
        scope: CoroutineScope,
        emit: (LiveEvent) -> Unit,
    ): LiveErrorKind? {
        val ws = transportFactory()
        transport.set(ws)
        emitState(LiveSessionState.CONNECTING, emit)
        // Captured out of the collect lambda since a non-local return from collect is illegal.
        val serverFailure = AtomicReference<LiveErrorKind?>(null)
        var peerClose: PeerClose? = null
        val micDenied = AtomicBoolean(false)
        try {
            // Setup is the mandatory first frame — sent in onReady, before any server message is expected.
            val onReady = { ws.sendText(LiveProtocol.setupFrame(config)) }
            ws.open(config.baseHost, PORT, endpointPath(config), onReady).collect { incoming ->
                when (incoming) {
                    is LiveTransport.Incoming.Text -> {
                        val kind = handleServerText(config, incoming.text, ws, scope, emit, micDenied)
                        if (kind != null) {
                            serverFailure.set(kind)
                            ws.close()
                        }
                    }
                    is LiveTransport.Incoming.Binary -> {} // Gemini Live is text-framed JSON only.
                    is LiveTransport.Incoming.Closed -> {
                        peerClose = PeerClose(incoming.code)
                        ws.close()
                    }
                }
            }
            serverFailure.get()?.let { return it }
            if (cancelled.get()) return null
            // Local mic permission denial is terminal, not a transient network drop — never loop it into TIMEOUT.
            if (micDenied.get()) return LiveErrorKind.PERMISSION
            // A normal WebSocket close (1000/1005) is a deliberate end, not a fault — don't reconnect it.
            peerClose?.let { return if (it.isClean) null else LiveErrorKind.REMOTE_CLOSE }
            // Read loop ended without a close frame (EOF) — treat as an unexpected drop and retry.
            return LiveErrorKind.REMOTE_CLOSE
        } catch (_: CancellationException) {
            return null
        } catch (t: Throwable) {
            serverFailure.get()?.let { return it }
            if (micDenied.get()) return LiveErrorKind.PERMISSION
            val kind = classifyTransport(t)
            emit(LiveEvent.Error(kind, GatewayErrorSanitizer.sanitize(t.message, config.apiKey)))
            return kind
        } finally {
            capture.stop()
            runCatching { ws.close() }
        }
    }

    private data class PeerClose(val code: Int) {
        // 1000 (normal) and 1005 (no status) are deliberate ends; anything else is an abnormal drop worth retrying.
        val isClean: Boolean get() = code == 1000 || code == 1005
    }

    // Returns a non-null LiveErrorKind only on a server-reported failure (ends this attempt); null keeps streaming.
    private fun handleServerText(
        config: GeminiLiveConfig,
        text: String,
        ws: LiveTransport,
        scope: CoroutineScope,
        emit: (LiveEvent) -> Unit,
        micDenied: AtomicBoolean,
    ): LiveErrorKind? {
        for (frame in LiveProtocol.parseServerFrame(text)) {
            when (frame) {
                is LiveProtocol.ServerFrame.SetupComplete -> {
                    reachedConfigured = true
                    emitState(LiveSessionState.CONFIGURED, emit)
                    // Mic denial is terminal — close so connectOnce returns PERMISSION instead of idling into a timeout retry.
                    if (!startMicStreaming(config, ws, scope, emit)) {
                        micDenied.set(true)
                        ws.close()
                        return null
                    }
                }
                is LiveProtocol.ServerFrame.Audio -> {
                    val pcm = runCatching { Base64.decode(frame.base64Pcm, Base64.DEFAULT) }.getOrNull()
                    if (pcm != null && pcm.isNotEmpty()) {
                        if (_state.value != LiveSessionState.STREAMING) emitState(LiveSessionState.STREAMING, emit)
                        player.start()
                        val gen = player.currentGeneration()
                        emit(LiveEvent.AudioDelta(pcm, audioSeq.getAndIncrement()))
                        player.enqueue(pcm, gen)
                    }
                }
                is LiveProtocol.ServerFrame.OutputText -> emit(LiveEvent.OutputTranscript(frame.text))
                is LiveProtocol.ServerFrame.InputText -> emit(LiveEvent.InputTranscript(frame.text))
                is LiveProtocol.ServerFrame.Interrupted -> {
                    player.flush()
                    emit(LiveEvent.Interrupted)
                }
                is LiveProtocol.ServerFrame.TurnComplete -> emit(LiveEvent.TurnComplete)
                is LiveProtocol.ServerFrame.GoAway -> return LiveErrorKind.REMOTE_CLOSE
                is LiveProtocol.ServerFrame.Failure -> {
                    emit(LiveEvent.Error(frame.kind, GatewayErrorSanitizer.sanitize(frame.message)))
                    return frame.kind
                }
                is LiveProtocol.ServerFrame.Unknown -> {}
            }
        }
        return null
    }

    // Returns false when the mic can't open (permission/hardware) so connectOnce ends the attempt as PERMISSION.
    private fun startMicStreaming(
        config: GeminiLiveConfig,
        ws: LiveTransport,
        scope: CoroutineScope,
        emit: (LiveEvent) -> Unit,
    ): Boolean {
        emitState(LiveSessionState.LISTENING, emit)
        if (!config.bargeIn) ws.sendText(LiveProtocol.activityStartFrame())
        val started = capture.start(scope) { chunk ->
            if (cancelled.get()) return@start
            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
            ws.sendText(LiveProtocol.audioFrame(b64))
        }
        if (!started) {
            emit(LiveEvent.Error(LiveErrorKind.PERMISSION, "Microphone unavailable or permission denied"))
        }
        return started
    }

    // User cancel: stop retries + capture + playback + socket. Nothing survives teardown.
    fun cancel() {
        cancelled.set(true)
        teardown()
    }

    // Ends the current user audio turn; the model then produces its reply turn.
    fun endUserTurn() {
        val ws = transport.get() ?: return
        capture.stop()
        if (!bargeIn) ws.sendText(LiveProtocol.activityEndFrame())
        ws.sendText(LiveProtocol.audioStreamEndFrame())
    }

    // --- Lifecycle contract (FRI-548 owns transport/session teardown; FRI-562 owns the UI that calls these) ---
    // Each maps a device/app event to the right transport+audio response so mic/socket never outlive the reason
    // to hold them. The host (Voice Home) wires audio-focus/route/foreground callbacks to these; the engine keeps
    // the teardown discipline in one place instead of scattering it across the UI.

    // Audio focus lost (a call, another media app) or the app went to background: stop capturing + speaking and
    // end the user's turn. Not a fault — a full teardown, since we must not hold the mic while backgrounded.
    fun onAudioFocusLost() {
        capture.stop()
        player.flush()
        cancel()
    }

    // Headset/Bluetooth/speaker route change mid-turn: flush the queued playback so a half-buffered chunk doesn't
    // replay on the new device, then keep streaming. The AudioTrack re-binds to the new route on the next enqueue.
    fun onAudioRouteChanged() {
        player.flush()
    }

    // App moved to background: identical policy to focus loss — never keep the mic/socket alive off-screen.
    fun onBackground() = onAudioFocusLost()

    // Microphone permission revoked while live: capture can no longer read, so end the session as PERMISSION
    // (terminal, non-retryable) rather than idling the socket into a timeout retry.
    fun onPermissionRevoked() {
        capture.stop()
        _state.value = LiveSessionState.ERROR
        cancel()
    }

    private fun teardown() {
        capture.stop()
        player.stop()
        runCatching { transport.getAndSet(null)?.close() }
        sessionScope?.cancel()
        sessionScope = null
        // Don't mask a terminal ERROR with CLOSED — a UI observing only state must still see the failure.
        if (_state.value != LiveSessionState.ERROR) _state.value = LiveSessionState.CLOSED
    }

    private fun emitState(next: LiveSessionState, emit: (LiveEvent) -> Unit) {
        _state.value = next
        emit(LiveEvent.State(next))
    }

    companion object {
        private const val PORT = 443

        fun endpointPath(config: GeminiLiveConfig): String =
            "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${config.apiKey}"

        internal fun classifyTransport(t: Throwable): LiveErrorKind = when (t) {
            is LiveTransport.HandshakeException -> when (t.httpStatus) {
                401, 403 -> LiveErrorKind.AUTH
                404 -> LiveErrorKind.INVALID_MODEL
                429 -> LiveErrorKind.QUOTA
                in 500..599 -> LiveErrorKind.REMOTE_CLOSE
                else -> LiveErrorKind.PROTOCOL
            }
            is java.net.SocketTimeoutException -> LiveErrorKind.TIMEOUT
            is java.net.UnknownHostException -> LiveErrorKind.NETWORK
            is javax.net.ssl.SSLException -> LiveErrorKind.NETWORK
            is java.io.IOException -> LiveErrorKind.NETWORK
            else -> LiveErrorKind.PROTOCOL
        }
    }
}

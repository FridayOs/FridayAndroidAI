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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

// Owns one Gemini Live session; audio never touches the brain (transcript bridges to brain_turn via FRI-553), and connected/ready is never faked.
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
    // Retained from the active attempt so a cloud barge-in can re-open the mic on the SAME socket without
    // reconnecting. Null outside a live attempt (before connect / after teardown).
    @Volatile private var activeConfig: GeminiLiveConfig? = null
    @Volatile private var emitter: ((LiveEvent) -> Unit)? = null
    private val captureErrorRef = AtomicReference<LiveErrorKind?>(null)
    // The currently live+configured socket; only this is resumable. Cleared in connectOnce's finally BEFORE the
    // socket closes (and again in teardown, belt-and-suspenders) so resumeUserTurn() can't race a closing/reconnecting
    // session into sending frames on a dead socket.
    private val liveGate = AtomicReference<LiveTransport?>(null)

    // Runs the full session as a cold flow. Collecting starts it; cancelling the collector tears everything down.
    fun run(config: GeminiLiveConfig): Flow<LiveEvent> = callbackFlow {
        cancelled.set(false)
        bargeIn = config.bargeIn
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        sessionScope = scope

        var attempt = 0
        var totalReconnects = 0
        try {
            while (!cancelled.get()) {
                reachedConfigured = false
                val outcome = connectOnce(config, scope) { trySend(it) }
                if (cancelled.get() || outcome == null) break
                if (!LiveRetryPolicy.retryable(outcome)) {
                    emitState(LiveSessionState.ERROR) { trySend(it) }
                    break
                }
                // Hard session-wide reconnect cap so a configure-then-drop peer can't reset the backoff budget forever.
                if (++totalReconnects > MAX_TOTAL_RECONNECTS) {
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

        // Retry loop ended — close the producer so the collector completes instead of hanging in awaitClose.
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
        // Retain for resumeUserTurn (cloud barge-in re-opens the mic on this same socket).
        activeConfig = config
        emitter = emit
        captureErrorRef.set(null)
        val captureError = captureErrorRef
        emitState(LiveSessionState.CONNECTING, emit)
        // Captured out of the collect lambda since a non-local return from collect is illegal.
        val serverFailure = AtomicReference<LiveErrorKind?>(null)
        // A client-side send failure surfaced by the transport — classified from its cause, not masked as EOF.
        val sendFailure = AtomicReference<LiveErrorKind?>(null)
        var peerClose: PeerClose? = null
        val micDenied = AtomicBoolean(false)
        try {
            // Setup is the mandatory first frame — sent in onReady, before any server message is expected.
            val onReady = { ws.sendText(LiveProtocol.setupFrame(config)) }
            ws.open(config.baseHost, config.basePort, endpointPath(config), onReady).collect { incoming ->
                when (incoming) {
                    is LiveTransport.Incoming.Text -> {
                        val kind = handleServerText(config, incoming.text, ws, scope, emit, micDenied, captureError)
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
                    is LiveTransport.Incoming.TransportError -> {
                        val kind = classifyTransport(incoming.cause)
                        sendFailure.set(kind)
                        emit(LiveEvent.Error(kind, GatewayErrorSanitizer.sanitize(incoming.cause.message, config.apiKey)))
                        ws.close()
                    }
                }
            }
            serverFailure.get()?.let { return it }
            // A client send that failed is a real transport fault (its classified cause), not a bare EOF/REMOTE_CLOSE.
            sendFailure.get()?.let { return it }
            // A mic that died mid-session is terminal AUDIO, not a network drop — never loop it.
            captureError.get()?.let { return it }
            if (cancelled.get()) return null
            // Local mic permission denial is terminal, not a transient network drop — never loop it into TIMEOUT.
            if (micDenied.get()) return LiveErrorKind.PERMISSION
            // Only a normal close (1000) is a deliberate end; anything else (incl. 1005 no-status) is an abnormal drop worth retrying.
            peerClose?.let { return if (it.isClean) null else LiveErrorKind.REMOTE_CLOSE }
            // Read loop ended without a close frame (EOF) — treat as an unexpected drop and retry.
            return LiveErrorKind.REMOTE_CLOSE
        } catch (_: CancellationException) {
            return null
        } catch (t: Throwable) {
            serverFailure.get()?.let { return it }
            sendFailure.get()?.let { return it }
            captureError.get()?.let { return it }
            if (micDenied.get()) return LiveErrorKind.PERMISSION
            val kind = classifyTransport(t)
            emit(LiveEvent.Error(kind, GatewayErrorSanitizer.sanitize(t.message, config.apiKey)))
            return kind
        } finally {
            liveGate.set(null) // invalidate resume BEFORE the socket closes (close/teardown race)
            capture.stop()
            runCatching { ws.close() }
        }
    }

    private data class PeerClose(val code: Int) {
        // Only 1000 is a clean end; 1005 (no-status sentinel) and any other close is an abnormal drop worth retrying (RFC 6455 §7.4).
        val isClean: Boolean get() = code == 1000
    }

    // Returns a non-null LiveErrorKind only on a server-reported failure (ends this attempt); null keeps streaming.
    private fun handleServerText(
        config: GeminiLiveConfig,
        text: String,
        ws: LiveTransport,
        scope: CoroutineScope,
        emit: (LiveEvent) -> Unit,
        micDenied: AtomicBoolean,
        captureError: AtomicReference<LiveErrorKind?>,
    ): LiveErrorKind? {
        for (frame in LiveProtocol.parseServerFrame(text)) {
            when (frame) {
                is LiveProtocol.ServerFrame.SetupComplete -> {
                    reachedConfigured = true
                    emitState(LiveSessionState.CONFIGURED, emit)
                    // Mic denial is terminal — close so connectOnce returns PERMISSION instead of idling into a timeout retry.
                    if (!startMicStreaming(config, ws, scope, emit, captureError)) {
                        micDenied.set(true)
                        ws.close()
                        return null
                    }
                    // Open playback now so a broken speaker ends the session as terminal AUDIO instead of failing silent.
                    player.start(onError = { t -> failAudio(config, ws, emit, captureError, t) })
                    // Mic + player are fully up and the session is live: only now is this socket resumable.
                    liveGate.set(ws)
                }
                is LiveProtocol.ServerFrame.Audio -> {
                    // B1 brain-owns-answer: Gemini's own generated audio is discarded — never enqueued, never
                    // surfaced. The spoken answer is the Brain Gateway's text vocalized via TTS into the same sink.
                    if (!config.brainOwnsAnswer) {
                        val pcm = runCatching { Base64.decode(frame.base64Pcm, Base64.DEFAULT) }.getOrNull()
                        if (pcm != null && pcm.isNotEmpty()) {
                            if (_state.value != LiveSessionState.STREAMING) emitState(LiveSessionState.STREAMING, emit)
                            val gen = player.currentGeneration()
                            emit(LiveEvent.AudioDelta(pcm, audioSeq.getAndIncrement()))
                            player.enqueue(pcm, gen)
                        }
                    }
                }
                // Discard Gemini's own answer text in brain-owns-answer mode; the Brain Gateway owns the answer.
                is LiveProtocol.ServerFrame.OutputText ->
                    if (!config.brainOwnsAnswer) emit(LiveEvent.OutputTranscript(frame.text))
                is LiveProtocol.ServerFrame.InputText -> emit(LiveEvent.InputTranscript(frame.text))
                is LiveProtocol.ServerFrame.Interrupted -> {
                    player.flush()
                    emit(LiveEvent.Interrupted)
                }
                is LiveProtocol.ServerFrame.TurnComplete -> emit(LiveEvent.TurnComplete)
                is LiveProtocol.ServerFrame.GoAway -> return LiveErrorKind.REMOTE_CLOSE
                is LiveProtocol.ServerFrame.Failure -> {
                    // Pass the active key so a provider error echoing it is redacted before it reaches the UI.
                    emit(LiveEvent.Error(frame.kind, GatewayErrorSanitizer.sanitize(frame.message, config.apiKey)))
                    return frame.kind
                }
                is LiveProtocol.ServerFrame.Unknown -> {}
            }
        }
        return null
    }

    // Records a terminal AUDIO fault once (mic or speaker) and closes the socket so connectOnce ends the attempt.
    private fun failAudio(
        config: GeminiLiveConfig,
        ws: LiveTransport,
        emit: (LiveEvent) -> Unit,
        captureError: AtomicReference<LiveErrorKind?>,
        cause: Throwable,
    ) {
        if (captureError.compareAndSet(null, LiveErrorKind.AUDIO)) {
            emit(LiveEvent.Error(LiveErrorKind.AUDIO, GatewayErrorSanitizer.sanitize(cause.message, config.apiKey)))
            ws.close()
        }
    }

    // false = mic can't open (→ PERMISSION); a failure AFTER open arrives via onError → records AUDIO + closes the socket (no silent dead mic).
    private fun startMicStreaming(
        config: GeminiLiveConfig,
        ws: LiveTransport,
        scope: CoroutineScope,
        emit: (LiveEvent) -> Unit,
        captureError: AtomicReference<LiveErrorKind?>,
    ): Boolean {
        emitState(LiveSessionState.LISTENING, emit)
        if (!config.bargeIn) ws.sendText(LiveProtocol.activityStartFrame())
        val started = capture.start(
            scope,
            onChunk = onChunk@{ chunk ->
                if (cancelled.get()) return@onChunk
                // java.util encoder is JVM-testable + matches android NO_WRAP; android.util.Base64 stays for decode
                val b64 = java.util.Base64.getEncoder().encodeToString(chunk)
                ws.sendText(LiveProtocol.audioFrame(b64))
            },
            onError = { t -> failAudio(config, ws, emit, captureError, t) },
        )
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

    // Ends the user's audio turn: manual VAD (barge-in off) marks it with activityEnd, automatic VAD with audioStreamEnd — never both.
    fun endUserTurn() {
        val ws = transport.get() ?: return
        capture.stop()
        if (!bargeIn) ws.sendText(LiveProtocol.activityEndFrame()) else ws.sendText(LiveProtocol.audioStreamEndFrame())
    }

    // Cloud barge-in: re-open the mic for a fresh user turn on the SAME live socket (endUserTurn stopped
    // capture). Flushes any queued Gemini audio first, then restarts mic streaming. Returns false when the
    // session isn't live (cancelled / no transport / torn down) so the VM opens a fresh session instead.
    fun resumeUserTurn(): Boolean {
        if (cancelled.get()) return false
        val ws = liveGate.get() ?: return false // only a live+configured socket is resumable
        val scope = sessionScope ?: return false
        val config = activeConfig ?: return false
        val emit = emitter ?: return false
        player.flush()
        return startMicStreaming(config, ws, scope, emit, captureErrorRef)
    }

    // Lifecycle contract (FRI-548 owns teardown; FRI-562's UI wires the OS callbacks) — mic/socket never outlive the reason to hold them.

    // Focus loss / background: full teardown, since we must not hold the mic while backgrounded.
    fun onAudioFocusLost() {
        capture.stop()
        player.flush()
        cancel()
    }

    // Route change (headset/BT/speaker): flush queued playback so a half-buffered chunk doesn't replay on the new device; session stays alive.
    fun onAudioRouteChanged() {
        player.flush()
    }

    // App moved to background: identical policy to focus loss — never keep the mic/socket alive off-screen.
    fun onBackground() = onAudioFocusLost()

    // Mic permission revoked while live: end as terminal ERROR rather than idling the socket into a timeout retry.
    fun onPermissionRevoked() {
        capture.stop()
        _state.value = LiveSessionState.ERROR
        cancel()
    }

    private fun teardown() {
        liveGate.set(null) // belt-and-suspenders; connectOnce's finally already clears it before close
        capture.stop()
        player.stop()
        runCatching { transport.getAndSet(null)?.close() }
        sessionScope?.cancel()
        sessionScope = null
        activeConfig = null
        emitter = null
        // Don't mask a terminal ERROR with CLOSED — a UI observing only state must still see the failure.
        if (_state.value != LiveSessionState.ERROR) _state.value = LiveSessionState.CLOSED
    }

    private fun emitState(next: LiveSessionState, emit: (LiveEvent) -> Unit) {
        _state.value = next
        emit(LiveEvent.State(next))
    }

    companion object {
        // Session-wide reconnect ceiling regardless of CONFIGURED resets — bounds radio use on a configure/drop peer.
        private const val MAX_TOTAL_RECONNECTS = 10

        // The Gemini BidiGenerateContent gRPC-web path, prefixed by any base path the user's endpoint carries.
        fun endpointPath(config: GeminiLiveConfig): String =
            "${config.basePath}/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${config.apiKey}"

        internal fun classifyTransport(t: Throwable): LiveErrorKind = when (t) {
            is LiveTransport.HandshakeException -> when (t.httpStatus) {
                401, 403 -> LiveErrorKind.AUTH
                404 -> LiveErrorKind.INVALID_MODEL
                429 -> LiveErrorKind.QUOTA
                in 500..599 -> LiveErrorKind.REMOTE_CLOSE
                else -> LiveErrorKind.PROTOCOL
            }
            // A malformed server frame (masked / RSV / bad control) is a protocol fault, not a network blip.
            is WebSocketFrame.ProtocolException -> LiveErrorKind.PROTOCOL
            is SSLPeerUnverifiedException -> LiveErrorKind.AUTH
            is SocketTimeoutException -> LiveErrorKind.TIMEOUT
            is UnknownHostException -> LiveErrorKind.NETWORK
            is SSLException -> LiveErrorKind.NETWORK
            is IOException -> LiveErrorKind.NETWORK
            else -> LiveErrorKind.PROTOCOL
        }
    }
}

package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.PendingAssistInvocation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import com.dark.tool_neuron.repo.gateway.VoiceRoutePort
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceAdapter
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceHandle
import com.dark.tool_neuron.repo.gateway.live.SpeakOutcome
import com.dark.tool_neuron.service.voice.VoiceSessionForegroundService
import com.dark.tool_neuron.ui.screens.friday.components.VoiceAnimation
import com.dark.tool_neuron.voice.AttachedSession
import com.dark.tool_neuron.voice.VoiceIo
import com.dark.tool_neuron.voice.VoiceSessionLifecycleHost
import com.dark.tool_neuron.voice.VoiceSessionServiceGate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface VoiceSelectorRequest {
    data object NeedVoiceGateway : VoiceSelectorRequest
    data object NeedBrainGateway : VoiceSelectorRequest
}

// AppPreferences is HXS-native-backed (unconstructable off-device); this narrow seam is the only
// part the VM reads, so JVM unit tests can fake it without touching AppPreferences itself.
internal interface VoicePrefsPort {
    val fridayVoiceBargeIn: Boolean
    val foregroundContinue: Boolean
    fun language(): String
    fun voiceAnim(): String
}

private class AppPreferencesVoicePrefsPort(private val prefs: AppPreferences) : VoicePrefsPort {
    override val fridayVoiceBargeIn: Boolean get() = prefs.fridayVoiceBargeIn
    override val foregroundContinue: Boolean get() = prefs.fridayVoiceForegroundContinue
    override fun language(): String = prefs.getString(FridayVoiceViewModel.KEY_FRIDAY_LANGUAGE)
    override fun voiceAnim(): String = prefs.fridayVoiceAnim
}

// Narrow seam over the Context-only bits (start/stop the opt-in mic-FGS) so the VM stays
// constructable in JVM tests without an Android Context or a mocking library.
internal interface VoiceForegroundServicePort {
    fun start()
    fun stop()
}

private class AndroidVoiceForegroundServicePort(
    private val context: Context,
) : VoiceForegroundServicePort {
    override fun start() {
        ContextCompat.startForegroundService(context, VoiceSessionForegroundService.intent(context))
    }
    override fun stop() {
        context.stopService(VoiceSessionForegroundService.intent(context))
    }
}

@HiltViewModel
class FridayVoiceViewModel internal constructor(
    private val voiceRouter: VoiceRoutePort,
    private val bridge: VoiceBridge,
    private val convoRepo: FridayConvoStore,
    private val voiceManager: VoiceIo,
    private val contextEngine: ContextHistorySource,
    private val adapter: LiveVoiceAdapter,
    private val prefs: VoicePrefsPort,
    private val gatewayState: VoiceGatewayStatePort,
    private val lifecycleHost: VoiceSessionLifecycleHost,
    private val serviceGate: VoiceSessionServiceGate,
    private val foregroundService: VoiceForegroundServicePort,
    private val pendingAssist: PendingAssistInvocation = PendingAssistInvocation(),
    // Trailing default keeps pre-existing positional test call sites compiling unmodified;
    // Hilt's generated factory still supplies the real bound singleton explicitly.
    private val activeConversationStore: ActiveConversationStore = DefaultActiveConversationStore(),
) : ViewModel() {

    @Inject constructor(
        voiceRouter: VoiceRoutePort,
        bridge: VoiceBridge,
        convoRepo: FridayConvoStore,
        voiceManager: VoiceIo,
        contextEngine: ContextHistorySource,
        adapter: LiveVoiceAdapter,
        prefs: AppPreferences,
        gatewayState: VoiceGatewayStatePort,
        lifecycleHost: VoiceSessionLifecycleHost,
        serviceGate: VoiceSessionServiceGate,
        pendingAssist: PendingAssistInvocation,
        @ApplicationContext context: Context,
    ) : this(
        voiceRouter, bridge, convoRepo, voiceManager, contextEngine, adapter,
        AppPreferencesVoicePrefsPort(prefs), gatewayState,
        lifecycleHost, serviceGate, AndroidVoiceForegroundServicePort(context), pendingAssist,
    )

    // Confirm/cancel card renders while the brain has a gate armed; user tap is the ONLY resolver.
    val awaitingConfirmationState: StateFlow<Boolean> = bridge.awaitingConfirmationState

    private var conversationId: String? = null
    private var brainForTurn: GatewayConfig? = null
    private var cloudVoiceConfig: GatewayConfig? = null
    private var handle: LiveVoiceHandle? = null
    private var sessionJob: Job? = null
    private var brainTurnJob: Job? = null
    private var speakJob: Job? = null
    // The socket collector outlives a single turn (one live session spans barge-ins), so it tags events
    // with the CURRENT turn epoch read here — not a per-open captured value that would go stale after a
    // barge-in bumped the epoch and cause the resumed mic's transcript to be dropped by the epoch guard.
    @Volatile private var sessionEpoch = 0

    init {
        // FRI-574 phase 7 (B3): seed the active conversation id (if any) so Voice resumes
        // the conversation Chat just opened. The store is in-process and synchronous, so a
        // direct read here is safe and avoids an idle flash of an empty conversation.
        conversationId = activeConversationStore.activeConversationId.value
        viewModelScope.launch {
            serviceGate.stopRequests.collect { reset() }
        }
        // Idle-adopt: while no session/turn is in flight, follow any new active id so
        // switching back to a Chat-originated conversation (or the sidebar's history list)
        // re-hydrates the same context without clobbering a live brain turn.
        viewModelScope.launch {
            activeConversationStore.activeConversationId.collect { id ->
                if (id != null && sessionJob == null && brainTurnJob == null && conversationId != id) {
                    conversationId = id
                }
            }
        }
    }

    private val state = MutableStateFlow(VoiceTurnState())
    val uiState: StateFlow<VoiceUiState> =
        state.map { it.ui }.stateIn(viewModelScope, SharingStarted.Eagerly, VoiceUiState.Idle)

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer.asStateFlow()

    private val _selectorRequest = MutableStateFlow<VoiceSelectorRequest?>(null)
    val selectorRequest: StateFlow<VoiceSelectorRequest?> = _selectorRequest.asStateFlow()

    private val _permissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequest: SharedFlow<Unit> = _permissionRequest.asSharedFlow()

    val amplitude: StateFlow<Float> = voiceManager.recordingAmplitude

    // Pref read once at VM init; FRI-583 will add live change notification (screen resume is enough now).
    val voiceAnimation: StateFlow<VoiceAnimation> =
        MutableStateFlow(VoiceAnimation.fromKey(prefs.voiceAnim())).asStateFlow()
    val voiceGateway: StateFlow<GatewayConfig?> = gatewayState.voiceGateway
    val brainConfigured: StateFlow<Boolean> = gatewayState.brainConfigured

    fun clearSelectorRequest() { _selectorRequest.value = null }

    // Set by MainActivity when the OS delivers an assist invocation (long-press home / assist gesture);
    // the Voice screen observes it and, once, calls onAssistantInvocation() after the lock chain resolves.
    val pendingAssistInvocation: StateFlow<Boolean> = pendingAssist.pending
    fun consumeAssistInvocation(): Boolean = pendingAssist.consume()

    // Assist invocation opens the exact same live-voice turn a mic tap does — no separate audio path.
    fun onAssistantInvocation() = onMicTap()

    fun onMicTap() {
        val ui = state.value.ui
        if (ui == VoiceUiState.Idle || ui == VoiceUiState.Done || ui is VoiceUiState.Error) {
            val route = resolveRoute() ?: return
            dispatchClearingTextOnNewEpoch(VoiceTurnEvent.MicTap(route, prefs.fridayVoiceBargeIn))
        } else {
            dispatchClearingTextOnNewEpoch(VoiceTurnEvent.MicTap(state.value.route, state.value.bargeIn))
        }
    }

    fun onMicNeedsPermission() {
        val route = resolveRoute() ?: return
        dispatch(VoiceTurnEvent.PermissionNeeded(route, prefs.fridayVoiceBargeIn))
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) dispatch(VoiceTurnEvent.PermissionGranted)
        else dispatch(VoiceTurnEvent.PermissionDenied("Microphone permission is required for voice."))
    }

    fun reset() = dispatch(VoiceTurnEvent.ResetTap)

    // brain_confirm goes to the live handle when a cloud session owns the turn, else the local bridge.
    fun confirm() { handle?.brainConfirm() ?: bridge.brainConfirm() }

    fun awaitingConfirmation(): Boolean = bridge.brainAwaitingConfirmation()

    // Gate mic on a resolvable route so a missing gateway opens the selector, never records-then-fails.
    private fun resolveRoute(): VoiceTurnRoute? = when (val route = voiceRouter.route()) {
        VoiceRouter.Route.NoVoice -> {
            _selectorRequest.value = VoiceSelectorRequest.NeedVoiceGateway
            null
        }
        VoiceRouter.Route.NoBrain -> {
            _selectorRequest.value = VoiceSelectorRequest.NeedBrainGateway
            null
        }
        is VoiceRouter.Route.LocalBridge -> {
            brainForTurn = route.brain
            cloudVoiceConfig = null
            VoiceTurnRoute.LOCAL
        }
        is VoiceRouter.Route.CloudBridge -> {
            brainForTurn = route.brain
            cloudVoiceConfig = route.voice
            VoiceTurnRoute.CLOUD
        }
    }

    private fun epochCurrent(epoch: Int): Boolean = state.value.epoch == epoch

    // Epoch bump = new turn (fresh start or barge-in); stale question/answer must not leak into it.
    private fun dispatchClearingTextOnNewEpoch(event: VoiceTurnEvent) {
        val before = state.value.epoch
        dispatch(event)
        if (state.value.epoch != before) {
            _question.value = ""
            _answer.value = ""
        }
    }

    private fun dispatch(event: VoiceTurnEvent) {
        val effects: List<VoiceTurnEffect>
        synchronized(this) {
            val result = FridayVoiceTurnMachine.reduce(state.value, event)
            state.value = result.state
            effects = result.effects
        }
        effects.forEach(::execute)
    }

    private fun execute(effect: VoiceTurnEffect) {
        when (effect) {
            VoiceTurnEffect.RequestPermission -> _permissionRequest.tryEmit(Unit)
            is VoiceTurnEffect.OpenCloudSession -> openCloudSession(effect.epoch, effect.bargeIn)
            is VoiceTurnEffect.EndCloudUserTurn -> handle?.endUserTurn()
            is VoiceTurnEffect.StartLocalRecording -> startLocalRecording(effect.epoch)
            is VoiceTurnEffect.StopLocalRecordingAndRecognize -> stopLocalRecording(effect.epoch)
            VoiceTurnEffect.CancelBrainTurn -> {
                // Also cancel the collect + speak jobs so a cancelled/barged-in cloud turn stops streaming,
                // persisting, and vocalizing (a cancelled speak flushes the sink so stale TTS PCM stops).
                brainTurnJob?.cancel()
                brainTurnJob = null
                speakJob?.cancel()
                speakJob = null
                handle?.brainCancel() ?: bridge.brainCancel()
            }
            is VoiceTurnEffect.ResumeCloudUserTurn -> resumeCloudUserTurn(effect.epoch, effect.bargeIn)
            is VoiceTurnEffect.RunBrainTurn -> runBrainTurn(effect.epoch, effect.transcript)
            is VoiceTurnEffect.SpeakLocal -> speakLocal(effect.epoch, effect.text)
            is VoiceTurnEffect.SpeakCloud -> speakCloud(effect.epoch, effect.text)
            VoiceTurnEffect.StopLocalSpeaking -> voiceManager.stopSpeaking()
            VoiceTurnEffect.TeardownAll -> teardownAll()
            VoiceTurnEffect.ReleaseSession -> releaseSession()
        }
    }

    private fun openCloudSession(epoch: Int, bargeIn: Boolean) {
        val voiceCfg = cloudVoiceConfig
        if (voiceCfg == null || !adapter.supports(voiceCfg)) {
            dispatch(VoiceTurnEvent.BrainError(epoch, "Live voice isn't supported for this provider yet."))
            return
        }
        sessionEpoch = epoch
        val h = adapter.open(voiceCfg, GeminiLiveConfig.DEFAULT_VOICE, locale(), bargeIn)
        handle = h
        if (!lifecycleHost.attach(cloudAttachedSession(h))) {
            // Audio focus denied: tear down the just-opened session before any mic stream / FGS starts.
            h.cancel()
            handle = null
            dispatch(VoiceTurnEvent.BrainError(epoch, FOCUS_DENIED_MESSAGE))
            return
        }
        lifecycleHost.setContinuationActive(prefs.foregroundContinue)
        serviceGate.setSessionActive(true)
        if (prefs.foregroundContinue) foregroundService.start()
        sessionJob = viewModelScope.launch {
            h.run().collect { ev ->
                // Read the live epoch (not the captured one) so events after a barge-in tag the new turn.
                val e = sessionEpoch
                if (ev is LiveEvent.InputTranscript && epochCurrent(e)) _question.value = ev.text
                if (ev is LiveEvent.OutputTranscript && epochCurrent(e)) _answer.value += ev.text
                dispatch(VoiceTurnEvent.SessionEvent(e, ev))
            }
        }
    }

    // Cloud barge-in: re-open the mic on the existing live socket. Advance sessionEpoch first so the still-
    // running collector tags the resumed turn's events with the new epoch; fall back to a fresh session if the
    // handle can't resume (closed/cancelled).
    private fun resumeCloudUserTurn(epoch: Int, bargeIn: Boolean) {
        sessionEpoch = epoch
        if (handle?.resumeUserTurn() != true) openCloudSession(epoch, bargeIn)
    }

    private fun startLocalRecording(epoch: Int) {
        if (!voiceManager.startRecording()) {
            dispatch(VoiceTurnEvent.BrainError(epoch, voiceManager.error.value ?: "Could not start recording"))
            return
        }
        if (!lifecycleHost.attach(localAttachedSession())) {
            // Audio focus denied: don't leave the recorder running.
            voiceManager.cancelRecording()
            dispatch(VoiceTurnEvent.BrainError(epoch, FOCUS_DENIED_MESSAGE))
        }
    }

    // Cloud route: forward focus/route/background straight to the live handle (FRI-548 contract).
    private fun cloudAttachedSession(h: LiveVoiceHandle): AttachedSession = object : AttachedSession {
        override fun onAudioFocusLost() = h.onAudioFocusLost()
        override fun onAudioRouteChanged() = h.onAudioRouteChanged()
        override fun onBackground() = h.onBackground()
    }

    // Local route: no live-handle passthroughs exist, so focus loss/background cancel the turn
    // via the same reset() path; route change never affects local playback routing.
    private fun localAttachedSession(): AttachedSession = object : AttachedSession {
        override fun onAudioFocusLost() { reset() }
        override fun onAudioRouteChanged() {}
        override fun onBackground() { reset() }
    }

    private fun stopLocalRecording(epoch: Int) {
        viewModelScope.launch {
            val transcript = voiceManager.stopRecordingAndRecognize()
            if (epochCurrent(epoch) && !transcript.isNullOrBlank()) _question.value = transcript
            dispatch(VoiceTurnEvent.LocalTranscript(epoch, transcript))
        }
    }

    private fun runBrainTurn(epoch: Int, transcript: String) {
        val brain = brainForTurn ?: run {
            dispatch(VoiceTurnEvent.BrainError(epoch, "No brain gateway selected."))
            return
        }
        brainTurnJob = viewModelScope.launch {
            val convoId = conversationId
                ?: convoRepo.createConversation(brain.id).id.also {
                    conversationId = it
                    activeConversationStore.set(it)
                }
            val userTurn = FridayTurn(
                id = UUID.randomUUID().toString(),
                conversationId = convoId,
                role = "user",
                content = transcript,
                timestamp = System.currentTimeMillis(),
                viaVoice = true,
            )
            convoRepo.addTurn(userTurn)
            contextEngine.onUserTurnPersisted(userTurn)
            val history = contextEngine.buildHistory(convoId)
            val onEvent: suspend (GatewayEvent) -> Unit = { event ->
                when (event) {
                    is GatewayEvent.Delta -> {
                        if (epochCurrent(epoch)) _answer.value += event.text
                        dispatch(VoiceTurnEvent.BrainDelta(epoch, event.text))
                    }
                    is GatewayEvent.Done -> {
                        // A cancelled/superseded turn must not persist a stale assistant reply.
                        if (epochCurrent(epoch) && event.fullText.isNotBlank()) {
                            convoRepo.addTurn(
                                FridayTurn(
                                    id = UUID.randomUUID().toString(),
                                    conversationId = convoId,
                                    role = "assistant",
                                    content = event.fullText,
                                    timestamp = System.currentTimeMillis(),
                                    viaVoice = true,
                                )
                            )
                            contextEngine.onTurnCompleted(convoId)
                        }
                        if (epochCurrent(epoch)) _answer.value = event.fullText
                        dispatch(VoiceTurnEvent.BrainDone(epoch, event.fullText))
                    }
                    is GatewayEvent.Error -> dispatch(VoiceTurnEvent.BrainError(epoch, event.message))
                }
            }
            val h = handle
            if (h != null) {
                // LiveCloudBridge.brainTurn appends the transcript as the new user turn itself;
                // drop the trailing duplicate the context engine already included.
                val cloudHistory =
                    if (history.lastOrNull() == GatewayTurn("user", transcript)) history.dropLast(1) else history
                h.brainTurn(cloudHistory, transcript).collect { onEvent(it) }
            } else {
                bridge.runTurn(viewModelScope, history) { onEvent(it) }
            }
        }
    }

    private fun speakLocal(epoch: Int, text: String) {
        viewModelScope.launch {
            val spoken = voiceManager.speak(UUID.randomUUID().toString(), text)
            // Honest surfacing: a failed TTS after Done flips the UI to Error deliberately.
            if (!spoken && epochCurrent(epoch)) {
                dispatch(VoiceTurnEvent.BrainError(epoch, voiceManager.error.value ?: "Speech failed"))
            }
        }
    }

    // B1: vocalize the Brain answer through the Voice Gateway (Gemini TTS) — audio never touches the brain,
    // only the final text does. speak() suspends until the PCM is synthesized + enqueued (single-chunk
    // speak-on-Done); then SpeakComplete drives the terminal Done + ReleaseSession. A stale epoch (barge-in
    // superseded the turn) is dropped so it can't complete a turn that no longer exists.
    private fun speakCloud(epoch: Int, text: String) {
        val h = handle ?: run {
            dispatch(VoiceTurnEvent.SpeakComplete(epoch))
            return
        }
        speakJob = viewModelScope.launch {
            // speak() suspends until the PCM has actually finished playing, so SpeakComplete (→ terminal Done
            // + ReleaseSession) only fires after playback — not the moment audio is queued. A synthesis
            // failure surfaces as Error; a barge-in that superseded the turn drops silently.
            when (val outcome = h.speak(text)) {
                is SpeakOutcome.Failed ->
                    if (epochCurrent(epoch)) dispatch(VoiceTurnEvent.BrainError(epoch, outcome.message))
                SpeakOutcome.Completed, SpeakOutcome.Empty ->
                    if (epochCurrent(epoch)) dispatch(VoiceTurnEvent.SpeakComplete(epoch))
                SpeakOutcome.Superseded -> Unit
            }
        }
    }

    // Terminal-state resource release: mic/session/FGS go away, but question/answer text and the
    // Done/Error surface stay for the user to read. TeardownAll (mid-turn cancel) also resets UI.
    // Deliberately does NOT stopSpeaking — local Done keeps TTS playing.
    private fun releaseSession() {
        brainTurnJob?.cancel()
        brainTurnJob = null
        speakJob?.cancel()
        speakJob = null
        sessionJob?.cancel()
        sessionJob = null
        handle?.cancel()
        handle = null
        bridge.brainCancel()
        voiceManager.cancelRecording()
        lifecycleHost.detach()
        serviceGate.setSessionActive(false)
        foregroundService.stop()
    }

    private fun teardownAll() {
        releaseSession()
        voiceManager.stopSpeaking()
        _question.value = ""
        _answer.value = ""
        dispatch(VoiceTurnEvent.TeardownComplete(state.value.epoch))
    }

    private fun locale(): String =
        if (prefs.language() == "vi") "vi-VN" else "en-US"

    override fun onCleared() {
        brainTurnJob?.cancel()
        speakJob?.cancel()
        sessionJob?.cancel()
        handle?.cancel()
        voiceManager.cancelRecording()
        voiceManager.stopSpeaking()
        lifecycleHost.detach()
        serviceGate.setSessionActive(false)
        foregroundService.stop()
    }

    internal companion object {
        const val KEY_FRIDAY_LANGUAGE = "friday_language"
        // Stable, provider-free copy shown when audio focus can't be acquired (another app holds it).
        const val FOCUS_DENIED_MESSAGE = "Couldn't get audio focus. Close other audio apps and try again."
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.data.PendingAssistInvocation
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.VoiceBridge
import com.dark.tool_neuron.repo.gateway.VoiceRoutePort
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceAdapter
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceHandle
import com.dark.tool_neuron.repo.gateway.live.SpeakOutcome
import android.media.AudioDeviceCallback
import android.media.AudioManager
import androidx.lifecycle.LifecycleEventObserver
import com.dark.tool_neuron.viewmodel.FridayVoiceViewModel
import com.dark.tool_neuron.viewmodel.VoiceForegroundServicePort
import com.dark.tool_neuron.viewmodel.VoiceGatewayStatePort
import com.dark.tool_neuron.viewmodel.VoicePrefsPort
import com.dark.tool_neuron.viewmodel.VoiceSelectorRequest
import com.dark.tool_neuron.viewmodel.VoiceUiState
import com.dark.tool_neuron.voice.SystemHooks
import com.dark.tool_neuron.voice.VoiceIo
import com.dark.tool_neuron.voice.VoiceSessionLifecycleHost
import com.dark.tool_neuron.voice.VoiceSessionServiceGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Public FridayVoiceViewModel.confirm()/awaitingConfirmation() reach the brain's gate, plus full local/cloud
// turn orchestration through FridayVoiceTurnMachine (VoiceBridge-only tests don't cover the VM call sites).
@OptIn(ExperimentalCoroutinesApi::class)
class FridayVoiceViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeBrain : BrainBridge {
        var confirmed = false
        var awaiting = false
        var turnFactory: () -> Flow<GatewayEvent> = { flow { emit(GatewayEvent.Done("")) } }
        var lastHistory: List<GatewayTurn>? = null
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> {
            lastHistory = history
            return turnFactory()
        }
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brainTurn(history)
        override fun brainCancel() {}
        override fun brainConfirm(): Boolean { confirmed = true; awaiting = false; return true }
        override fun brainAwaitingConfirmation(): Boolean = awaiting
    }

    private class FakeConvoStore : FridayConvoStore {
        val addedTurns = mutableListOf<FridayTurn>()
        override val conversations: StateFlow<List<FridayConversation>> = MutableStateFlow(emptyList())
        override fun refresh() {}
        override fun createConversation(gatewayId: String) = FridayConversation("c1", "t", gatewayId, 0, 0)
        override fun getConversation(id: String): FridayConversation? = null
        override fun getTurns(conversationId: String): List<FridayTurn> = emptyList()
        override fun addTurn(turn: FridayTurn) { addedTurns += turn }
        override fun updateTurn(turn: FridayTurn) {}
        override fun deleteConversation(id: String) {}
    }

    private class FakeVoiceIo : VoiceIo {
        var startRecordingResult = true
        var recognizedTranscript: String? = "hello"
        var speakResult = true
        var startRecordingCalls = 0
        var cancelRecordingCalls = 0
        var speakCalls = mutableListOf<String>()
        override val error: StateFlow<String?> = MutableStateFlow(null)
        override val recordingAmplitude: StateFlow<Float> = MutableStateFlow(0f)
        override fun startRecording(): Boolean { startRecordingCalls++; return startRecordingResult }
        override suspend fun stopRecordingAndRecognize(): String? = recognizedTranscript
        override fun cancelRecording() { cancelRecordingCalls++ }
        override fun stopSpeaking() {}
        override suspend fun speak(messageId: String, text: String): Boolean { speakCalls += text; return speakResult }
    }

    private class FakeRoute(private var route: VoiceRouter.Route = VoiceRouter.Route.NoVoice) : VoiceRoutePort {
        fun set(newRoute: VoiceRouter.Route) { route = newRoute }
        override fun route(): VoiceRouter.Route = route
    }

    private class FakeContextEngine : ContextHistorySource {
        var history: List<GatewayTurn> = emptyList()
        val persistedUserTurns = mutableListOf<FridayTurn>()
        val completedConversationIds = mutableListOf<String>()
        override suspend fun buildHistory(conversationId: String?, pendingTurns: List<FridayTurn>?): List<GatewayTurn> = history
        override fun onUserTurnPersisted(turn: FridayTurn) { persistedUserTurns += turn }
        override fun onTurnCompleted(conversationId: String) { completedConversationIds += conversationId }
    }

    private class FakeLiveVoiceHandle : LiveVoiceHandle {
        val events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 8)
        var endUserTurnCalls = 0
        var cancelCalls = 0
        var resumeUserTurnCalls = 0
        var resumeUserTurnResult = true
        var brainConfirmResult = true
        var brainTurnFactory: (List<GatewayTurn>, String) -> Flow<GatewayEvent> =
            { _, _ -> flow { emit(GatewayEvent.Done("")) } }
        var lastBrainTurnHistory: List<GatewayTurn>? = null
        var lastBrainTurnTranscript: String? = null
        val spokenTexts = mutableListOf<String>()
        override fun run(): Flow<LiveEvent> = events
        override fun endUserTurn() { endUserTurnCalls++ }
        override fun onAudioFocusLost() {}
        override fun onAudioRouteChanged() {}
        override fun onBackground() {}
        override fun onPermissionRevoked() {}
        override fun cancel() { cancelCalls++ }
        override fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> {
            lastBrainTurnHistory = history
            lastBrainTurnTranscript = transcript
            return brainTurnFactory(history, transcript)
        }
        override fun brainCancel() {}
        override fun brainConfirm(): Boolean = brainConfirmResult
        override fun resumeUserTurn(): Boolean { resumeUserTurnCalls++; return resumeUserTurnResult }
        override suspend fun speak(text: String): SpeakOutcome { spokenTexts += text; return SpeakOutcome.Completed }
    }

    private class FakeLiveVoiceAdapter(private var supportsResult: Boolean = true) : LiveVoiceAdapter {
        var openCalls = 0
        var lastBargeIn: Boolean? = null
        var lastLocale: String? = null
        val handle = FakeLiveVoiceHandle()
        fun setSupports(value: Boolean) { supportsResult = value }
        override fun supports(config: GatewayConfig): Boolean = supportsResult
        override fun open(config: GatewayConfig, voice: String, locale: String, bargeIn: Boolean): LiveVoiceHandle {
            openCalls++
            lastBargeIn = bargeIn
            lastLocale = locale
            return handle
        }
    }

    private class FakeVoicePrefsPort(
        override val fridayVoiceBargeIn: Boolean = true,
        override val foregroundContinue: Boolean = false,
        private val lang: String = "en",
    ) : VoicePrefsPort {
        override fun language(): String = lang
        override fun voiceAnim(): String = "orb"
    }

    private class FakeSystemHooks(private val focusGranted: Boolean = true) : SystemHooks {
        override fun registerFocus(listener: AudioManager.OnAudioFocusChangeListener): Boolean = focusGranted
        override fun abandonFocus() {}
        override fun registerRouteCallback(callback: AudioDeviceCallback) {}
        override fun unregisterRouteCallback(callback: AudioDeviceCallback) {}
        override fun addLifecycleObserver(observer: LifecycleEventObserver) {}
        override fun removeLifecycleObserver(observer: LifecycleEventObserver) {}
    }

    private class FakeForegroundServicePort : VoiceForegroundServicePort {
        var startCalls = 0
        var stopCalls = 0
        override fun start() { startCalls++ }
        override fun stop() { stopCalls++ }
    }

    private class FakeVoiceGatewayStatePort(
        override val voiceGateway: StateFlow<GatewayConfig?> = MutableStateFlow(null),
        override val brainConfigured: StateFlow<Boolean> = MutableStateFlow(false),
    ) : VoiceGatewayStatePort

    private fun localGateway() = GatewayConfig(
        id = "local-1", provider = GatewayProvider.LOCAL, label = "local", baseUrl = "", apiKey = "",
        model = "", createdAt = 0, updatedAt = 0,
    )

    private fun cloudGateway() = GatewayConfig(
        id = "cloud-1", provider = GatewayProvider.GEMINI, label = "gemini", baseUrl = "", apiKey = "key",
        model = "gemini-live", createdAt = 0, updatedAt = 0,
    )

    private fun vm(
        brain: FakeBrain = FakeBrain(),
        route: FakeRoute = FakeRoute(),
        convoStore: FakeConvoStore = FakeConvoStore(),
        voiceIo: FakeVoiceIo = FakeVoiceIo(),
        contextEngine: FakeContextEngine = FakeContextEngine(),
        adapter: FakeLiveVoiceAdapter = FakeLiveVoiceAdapter(),
        prefs: FakeVoicePrefsPort = FakeVoicePrefsPort(),
        gatewayState: FakeVoiceGatewayStatePort = FakeVoiceGatewayStatePort(),
        foregroundService: FakeForegroundServicePort = FakeForegroundServicePort(),
        pendingAssist: PendingAssistInvocation = PendingAssistInvocation(),
        lifecycleHost: VoiceSessionLifecycleHost = VoiceSessionLifecycleHost(FakeSystemHooks()),
    ): FridayVoiceViewModel =
        FridayVoiceViewModel(
            route, VoiceBridge(brain), convoStore, voiceIo, contextEngine, adapter, prefs, gatewayState,
            lifecycleHost, VoiceSessionServiceGate(), foregroundService,
            pendingAssist,
        )

    @Test
    fun confirm_reachesBrainConfirmationGate() = runTest {
        val brain = FakeBrain()
        vm(brain = brain).confirm()
        assertTrue("VM confirm() must reach the brain's confirmation gate", brain.confirmed)
    }

    @Test
    fun awaitingConfirmation_reflectsBrainGateState() = runTest {
        val brain = FakeBrain()
        val model = vm(brain = brain)
        assertFalse("no gate armed -> not awaiting", model.awaitingConfirmation())
        brain.awaiting = true
        assertTrue("armed gate -> awaiting through the VM call site", model.awaitingConfirmation())
    }

    @Test
    fun noVoiceRoute_micTap_opensVoiceSelectorAndNeverRecords() = runTest {
        val voiceIo = FakeVoiceIo()
        val route = FakeRoute(VoiceRouter.Route.NoVoice)
        val model = vm(route = route, voiceIo = voiceIo)
        model.onMicTap()
        assertEquals(VoiceSelectorRequest.NeedVoiceGateway, model.selectorRequest.value)
        assertEquals(0, voiceIo.startRecordingCalls)
    }

    @Test
    fun noBrainRoute_micTap_opensBrainSelectorAndNeverRecords() = runTest {
        val voiceIo = FakeVoiceIo()
        val route = FakeRoute(VoiceRouter.Route.NoBrain)
        val model = vm(route = route, voiceIo = voiceIo)
        model.onMicTap()
        assertEquals(VoiceSelectorRequest.NeedBrainGateway, model.selectorRequest.value)
        assertEquals(0, voiceIo.startRecordingCalls)
    }

    @Test
    fun localRoute_fullTurn_persistsTurnsAndSpeaksFinalAnswer() = runTest {
        val brain = FakeBrain().apply {
            turnFactory = { flow { emit(GatewayEvent.Delta("Hel")); emit(GatewayEvent.Done("Hello there")) } }
        }
        val voiceIo = FakeVoiceIo().apply { recognizedTranscript = "hello" }
        val convoStore = FakeConvoStore()
        val contextEngine = FakeContextEngine()
        val route = FakeRoute(VoiceRouter.Route.LocalBridge(localGateway(), localGateway()))
        val model = vm(brain = brain, route = route, convoStore = convoStore, voiceIo = voiceIo, contextEngine = contextEngine)

        model.onMicTap()
        assertEquals(1, voiceIo.startRecordingCalls)

        model.onMicTap()
        advanceUntilIdle()

        assertEquals(2, convoStore.addedTurns.size)
        val userTurn = convoStore.addedTurns[0]
        assertEquals("user", userTurn.role)
        assertTrue("user turn must be marked viaVoice", userTurn.viaVoice)
        val assistantTurn = convoStore.addedTurns[1]
        assertEquals("assistant", assistantTurn.role)
        assertTrue("assistant turn must be marked viaVoice", assistantTurn.viaVoice)
        assertEquals(1, contextEngine.persistedUserTurns.size)
        assertEquals(listOf("c1"), contextEngine.completedConversationIds)
        assertEquals(listOf("Hello there"), voiceIo.speakCalls)
        assertEquals(VoiceUiState.Done, model.uiState.value)
        assertEquals("Hello there", model.answer.value)
    }

    @Test
    fun cloudRoute_fullTurn_opensSessionAndRunsBrainThroughHandle() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val contextEngine = FakeContextEngine()
        val prefs = FakeVoicePrefsPort(fridayVoiceBargeIn = false)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, contextEngine = contextEngine, prefs = prefs)

        model.onMicTap()
        assertEquals(1, adapter.openCalls)
        assertEquals(false, adapter.lastBargeIn)

        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        advanceUntilIdle()
        assertEquals(VoiceUiState.Listening, model.uiState.value)

        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()
        assertEquals("hi", model.question.value)

        adapter.handle.brainTurnFactory = { _, _ -> flow { emit(GatewayEvent.Done("hi back")) } }
        model.onMicTap()
        advanceUntilIdle()

        assertEquals(1, adapter.handle.endUserTurnCalls)
        assertEquals("hi", adapter.handle.lastBrainTurnTranscript)
        // B1: the Brain answer is vocalized via the Voice Gateway (handle.speak), NOT Gemini's own audio; the
        // terminal Done fires only after SpeakComplete once TTS playback is enqueued.
        assertEquals(listOf("hi back"), adapter.handle.spokenTexts)
        assertEquals(VoiceUiState.Done, model.uiState.value)
    }

    // B3: audio focus denied on a cloud turn tears the just-opened session down before any mic stream /
    // FGS starts, cancels the handle, and surfaces a stable Error.
    @Test
    fun cloudRoute_focusDenied_cancelsSession_surfacesError_andNeverStartsFgs() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val prefs = FakeVoicePrefsPort(foregroundContinue = true)
        val fgs = FakeForegroundServicePort()
        val deniedHost = VoiceSessionLifecycleHost(FakeSystemHooks(focusGranted = false))
        val model = vm(route = route, adapter = adapter, prefs = prefs, foregroundService = fgs, lifecycleHost = deniedHost)

        model.onMicTap()
        advanceUntilIdle()

        assertEquals("opened session must be cancelled on focus denial", 1, adapter.handle.cancelCalls)
        assertEquals("mic-FGS must not start on a denied session", 0, fgs.startCalls)
        val ui = model.uiState.value
        assertTrue("focus denial surfaces an Error, got $ui", ui is VoiceUiState.Error)
        assertEquals(FridayVoiceViewModel.FOCUS_DENIED_MESSAGE, (ui as VoiceUiState.Error).message)
    }

    // B3: audio focus denied on a local turn stops the recorder and surfaces the same stable Error.
    @Test
    fun localRoute_focusDenied_stopsRecording_surfacesError() = runTest {
        val voiceIo = FakeVoiceIo()
        val route = FakeRoute(VoiceRouter.Route.LocalBridge(localGateway(), localGateway()))
        val deniedHost = VoiceSessionLifecycleHost(FakeSystemHooks(focusGranted = false))
        val model = vm(route = route, voiceIo = voiceIo, lifecycleHost = deniedHost)

        model.onMicTap()
        advanceUntilIdle()

        assertEquals(1, voiceIo.startRecordingCalls)
        assertTrue("recording must be cancelled on focus denial", voiceIo.cancelRecordingCalls >= 1)
        val ui = model.uiState.value
        assertTrue("focus denial surfaces an Error, got $ui", ui is VoiceUiState.Error)
        assertEquals(FridayVoiceViewModel.FOCUS_DENIED_MESSAGE, (ui as VoiceUiState.Error).message)
    }

    // B1: Gemini's own TurnComplete (its discarded-answer boundary) must NOT end our turn — only the Brain
    // answer, vocalized via TTS, does. A TurnComplete mid-Thinking is benign: the session stays alive.
    @Test
    fun cloudRoute_geminiTurnComplete_beforeBrainDone_doesNotReleaseSession() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter)

        model.onMicTap()
        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()

        // Brain hangs, so we sit in Thinking; Gemini's TurnComplete arrives and must be ignored.
        adapter.handle.brainTurnFactory = { _, _ -> flow { awaitCancellation() } }
        model.onMicTap()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Thinking, model.uiState.value)

        adapter.handle.events.tryEmit(LiveEvent.TurnComplete)
        advanceUntilIdle()

        assertEquals("Gemini TurnComplete must not end the turn", VoiceUiState.Thinking, model.uiState.value)
        assertEquals("session must stay alive", 0, adapter.handle.cancelCalls)
        assertTrue("nothing vocalized yet", adapter.handle.spokenTexts.isEmpty())
    }

    @Test
    fun cloudRoute_unsupported_surfacesErrorAndNeverOpensSession() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = false)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter)

        model.onMicTap()
        advanceUntilIdle()

        assertEquals(0, adapter.openCalls)
        val ui = model.uiState.value
        assertTrue("expected an Error state, got $ui", ui is VoiceUiState.Error)
        val message = (ui as VoiceUiState.Error).message
        assertTrue("message should explain lack of support: $message", message.contains("isn't supported"))
    }

    @Test
    fun staleEventsAfterReset_doNotMutateClearedQuestionOrAnswer() = runTest {
        // Hangs after the transcript is set so reset() lands mid-Thinking, not after the turn already finished.
        val brain = FakeBrain().apply { turnFactory = { flow { awaitCancellation() } } }
        val voiceIo = FakeVoiceIo().apply { recognizedTranscript = "will be reset" }
        val route = FakeRoute(VoiceRouter.Route.LocalBridge(localGateway(), localGateway()))
        val model = vm(brain = brain, route = route, voiceIo = voiceIo)

        model.onMicTap()
        model.onMicTap()
        assertEquals(VoiceUiState.Thinking, model.uiState.value)
        assertEquals("will be reset", model.question.value)

        model.reset()
        advanceUntilIdle()

        assertEquals("", model.question.value)
        assertEquals("", model.answer.value)
        assertEquals(VoiceUiState.Idle, model.uiState.value)
    }

    @Test
    fun bargeInFalse_duringSpeaking_finishesWithoutRestartingRecording() = runTest {
        // Delta lands (ui -> Speaking) then hangs before Done, so the barge-in tap lands mid-stream.
        val brain = FakeBrain().apply { turnFactory = { flow { emit(GatewayEvent.Delta("ans")); awaitCancellation() } } }
        val voiceIo = FakeVoiceIo().apply { recognizedTranscript = "question" }
        val prefs = FakeVoicePrefsPort(fridayVoiceBargeIn = false)
        val route = FakeRoute(VoiceRouter.Route.LocalBridge(localGateway(), localGateway()))
        val model = vm(brain = brain, route = route, voiceIo = voiceIo, prefs = prefs)

        model.onMicTap()
        model.onMicTap()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Speaking, model.uiState.value)
        val recordingCallsAfterTurn = voiceIo.startRecordingCalls

        model.onMicTap()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Done, model.uiState.value)
        assertEquals(recordingCallsAfterTurn, voiceIo.startRecordingCalls)
    }

    @Test
    fun assistantInvocation_opensSameTurnAsMicTap() = runTest {
        // Assist invocation with no voice gateway must open the selector (never records), exactly like onMicTap.
        val voiceIo = FakeVoiceIo()
        val route = FakeRoute(VoiceRouter.Route.NoVoice)
        val model = vm(route = route, voiceIo = voiceIo)
        model.onAssistantInvocation()
        assertEquals(VoiceSelectorRequest.NeedVoiceGateway, model.selectorRequest.value)
        assertEquals(0, voiceIo.startRecordingCalls)
    }

    @Test
    fun pendingAssistInvocation_isOneShotThroughViewModel() = runTest {
        val pending = PendingAssistInvocation()
        val model = vm(pendingAssist = pending)
        assertFalse(model.pendingAssistInvocation.value)
        pending.set()
        assertTrue(model.pendingAssistInvocation.value)
        assertTrue(model.consumeAssistInvocation())
        assertFalse("consume must clear the flag", model.pendingAssistInvocation.value)
        assertFalse(model.consumeAssistInvocation())
    }

    @Test
    fun rapidDoubleTap_duringConnecting_opensSessionOnlyOnce() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter)

        model.onMicTap()
        model.onMicTap()

        assertEquals(1, adapter.openCalls)
    }

    @Test
    fun resetFromError_releasesSessionResources() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val prefs = FakeVoicePrefsPort(foregroundContinue = true)
        val foregroundService = FakeForegroundServicePort()
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, prefs = prefs, foregroundService = foregroundService)

        model.onMicTap()
        assertEquals(1, foregroundService.startCalls)

        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()
        assertEquals("hi", model.question.value)

        adapter.handle.events.tryEmit(LiveEvent.Error(LiveErrorKind.NETWORK, "net down"))
        advanceUntilIdle()

        // Terminal Error releases FGS + handle immediately; the transcript stays readable.
        assertTrue(model.uiState.value is VoiceUiState.Error)
        assertTrue("FGS must stop on terminal error", foregroundService.stopCalls >= 1)
        assertTrue("handle must be cancelled on terminal error", adapter.handle.cancelCalls >= 1)
        assertEquals("hi", model.question.value)

        model.reset()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Idle, model.uiState.value)
    }

    @Test
    fun cancelledCloudTurn_doesNotPersistAssistantTurn() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val convoStore = FakeConvoStore()
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, convoStore = convoStore)

        model.onMicTap()
        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()

        // Done("late") lands only after virtual time advances, so reset() supersedes the turn first.
        adapter.handle.brainTurnFactory = { _, _ ->
            flow {
                delay(10)
                emit(GatewayEvent.Done("late"))
            }
        }
        model.onMicTap()
        model.reset()
        advanceUntilIdle()

        assertTrue("user turn must persist", convoStore.addedTurns.any { it.role == "user" && it.content == "hi" })
        assertTrue(
            "stale assistant reply must not persist",
            convoStore.addedTurns.none { it.role == "assistant" && it.content == "late" },
        )
    }

    @Test
    fun cloudBrainTurn_receivesHistoryWithoutDuplicateUserTurn() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val contextEngine = FakeContextEngine().apply {
            history = listOf(GatewayTurn("system", "ctx"), GatewayTurn("user", "hi"))
        }
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, contextEngine = contextEngine)

        model.onMicTap()
        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()

        model.onMicTap()
        advanceUntilIdle()

        // LiveCloudBridge appends the transcript itself; the VM drops the trailing duplicate.
        assertEquals(listOf(GatewayTurn("system", "ctx")), adapter.handle.lastBrainTurnHistory)
        assertEquals("hi", adapter.handle.lastBrainTurnTranscript)
    }

    // C2: a cloud barge-in re-opens the mic on the SAME socket (endUserTurn stopped capture), and the
    // resumed-turn transcript must pass the epoch guard (the socket collector reads the live sessionEpoch).
    @Test
    fun cloudBargeIn_resumesMicOnSameSocket_andResumedTranscriptPassesEpochGuard() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true)
        val prefs = FakeVoicePrefsPort(fridayVoiceBargeIn = true)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, prefs = prefs)

        model.onMicTap()
        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()
        assertEquals("hi", model.question.value)

        // Brain answers with a delta then hangs, so we sit in Speaking for the barge-in.
        adapter.handle.brainTurnFactory = { _, _ -> flow { emit(GatewayEvent.Delta("part")); awaitCancellation() } }
        model.onMicTap()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Speaking, model.uiState.value)

        // Barge-in tap: re-open the mic on the SAME socket (no new open), back to Listening.
        model.onMicTap()
        advanceUntilIdle()
        assertEquals("no new session on barge-in", 1, adapter.openCalls)
        assertEquals("mic re-opened on the same socket", 1, adapter.handle.resumeUserTurnCalls)
        assertEquals(VoiceUiState.Listening, model.uiState.value)

        // The resumed-turn transcript must land (epoch guard sees the live sessionEpoch, not a stale one).
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("again"))
        advanceUntilIdle()
        assertEquals("again", model.question.value)
    }

    // C2: if the socket can't resume (handle returns false), the VM falls back to a fresh session.
    @Test
    fun cloudBargeIn_whenResumeFails_opensFreshSession() = runTest {
        val adapter = FakeLiveVoiceAdapter(supportsResult = true).apply { handle.resumeUserTurnResult = false }
        val prefs = FakeVoicePrefsPort(fridayVoiceBargeIn = true)
        val route = FakeRoute(VoiceRouter.Route.CloudBridge(cloudGateway(), cloudGateway()))
        val model = vm(route = route, adapter = adapter, prefs = prefs)

        model.onMicTap()
        adapter.handle.events.tryEmit(LiveEvent.State(LiveSessionState.CONFIGURED))
        adapter.handle.events.tryEmit(LiveEvent.InputTranscript("hi"))
        advanceUntilIdle()

        adapter.handle.brainTurnFactory = { _, _ -> flow { emit(GatewayEvent.Delta("part")); awaitCancellation() } }
        model.onMicTap()
        advanceUntilIdle()
        assertEquals(VoiceUiState.Speaking, model.uiState.value)

        model.onMicTap()
        advanceUntilIdle()
        assertEquals("failed resume falls back to a fresh open", 2, adapter.openCalls)
        assertEquals(1, adapter.handle.resumeUserTurnCalls)
    }
}

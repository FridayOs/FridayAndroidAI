package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState
import com.dark.tool_neuron.viewmodel.FridayVoiceTurnMachine
import com.dark.tool_neuron.viewmodel.VoiceTurnEffect
import com.dark.tool_neuron.viewmodel.VoiceTurnEvent
import com.dark.tool_neuron.viewmodel.VoiceTurnRoute
import com.dark.tool_neuron.viewmodel.VoiceTurnState
import com.dark.tool_neuron.viewmodel.VoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure reducer: no coroutines, no Android, exercises every (state, event) transition in FridayVoiceTurnMachine.
class FridayVoiceTurnMachineTest {

    private fun reduce(state: VoiceTurnState, event: VoiceTurnEvent) = FridayVoiceTurnMachine.reduce(state, event)

    @Test
    fun micTap_fromIdle_local_startsListeningAndRecording() {
        val result = reduce(VoiceTurnState(), VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = true))
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals(1, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.StartLocalRecording(1)), result.effects)
    }

    @Test
    fun micTap_fromIdle_cloud_startsConnectingAndOpensSession() {
        val result = reduce(VoiceTurnState(), VoiceTurnEvent.MicTap(VoiceTurnRoute.CLOUD, bargeIn = true))
        assertEquals(VoiceUiState.Connecting, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.OpenCloudSession(1, true)), result.effects)
    }

    @Test
    fun micTap_fromDone_startsNewTurnWithBumpedEpoch() {
        val done = VoiceTurnState(ui = VoiceUiState.Done, epoch = 3)
        val result = reduce(done, VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = true))
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals(4, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.StartLocalRecording(4)), result.effects)
    }

    @Test
    fun micTap_fromError_startsNewTurnWithBumpedEpoch() {
        val error = VoiceTurnState(ui = VoiceUiState.Error("boom"), epoch = 5)
        val result = reduce(error, VoiceTurnEvent.MicTap(VoiceTurnRoute.CLOUD, bargeIn = false))
        assertEquals(VoiceUiState.Connecting, result.state.ui)
        assertEquals(6, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.OpenCloudSession(6, false)), result.effects)
    }

    @Test
    fun micTap_duringConnectingThinkingCancellingRequestingPermission_isIgnored() {
        listOf(
            VoiceUiState.Connecting,
            VoiceUiState.Thinking,
            VoiceUiState.Cancelling,
            VoiceUiState.RequestingPermission,
        ).forEach { ui ->
            val state = VoiceTurnState(ui = ui, epoch = 2, route = VoiceTurnRoute.LOCAL)
            val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = true))
            assertEquals("rapid tap during $ui must be a no-op", state, result.state)
            assertTrue("rapid tap during $ui must emit no effects", result.effects.isEmpty())
        }
    }

    @Test
    fun micTap_duringListening_local_stopsRecordingAndRecognizes() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1, route = VoiceTurnRoute.LOCAL)
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = true))
        assertEquals(VoiceUiState.Thinking, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.StopLocalRecordingAndRecognize(1)), result.effects)
    }

    @Test
    fun micTap_duringListening_cloud_endsUserTurnAndRunsBrain() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1, route = VoiceTurnRoute.CLOUD, transcript = "hi there")
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.CLOUD, bargeIn = true))
        assertEquals(VoiceUiState.Thinking, result.state.ui)
        assertEquals(
            listOf(VoiceTurnEffect.EndCloudUserTurn(1), VoiceTurnEffect.RunBrainTurn(1, "hi there")),
            result.effects,
        )
    }

    @Test
    fun micTap_duringSpeaking_bargeInTrue_local_cancelsAndRestartsRecording() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 2, route = VoiceTurnRoute.LOCAL, bargeIn = true, transcript = "old")
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = true))
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals(3, result.state.epoch)
        assertEquals("", result.state.transcript)
        assertEquals(
            listOf(VoiceTurnEffect.CancelBrainTurn, VoiceTurnEffect.StopLocalSpeaking, VoiceTurnEffect.StartLocalRecording(3)),
            result.effects,
        )
    }

    @Test
    fun micTap_duringSpeaking_bargeInTrue_cloud_onlyCancelsBrainTurn() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 2, route = VoiceTurnRoute.CLOUD, bargeIn = true)
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.CLOUD, bargeIn = true))
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals(3, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.CancelBrainTurn), result.effects)
    }

    @Test
    fun micTap_duringSpeaking_bargeInFalse_local_stopsSpeakingAndFinishes() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 2, route = VoiceTurnRoute.LOCAL, bargeIn = false)
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.LOCAL, bargeIn = false))
        assertEquals(VoiceUiState.Done, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.StopLocalSpeaking), result.effects)
    }

    @Test
    fun micTap_duringSpeaking_bargeInFalse_cloud_finishesAndReleasesSession() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 2, route = VoiceTurnRoute.CLOUD, bargeIn = false)
        val result = reduce(state, VoiceTurnEvent.MicTap(VoiceTurnRoute.CLOUD, bargeIn = false))
        assertEquals(VoiceUiState.Done, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
    }

    @Test
    fun permissionNeeded_fromIdle_requestsPermission() {
        val result = reduce(VoiceTurnState(), VoiceTurnEvent.PermissionNeeded(VoiceTurnRoute.LOCAL, bargeIn = true))
        assertEquals(VoiceUiState.RequestingPermission, result.state.ui)
        assertEquals(1, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.RequestPermission), result.effects)
    }

    @Test
    fun permissionGranted_local_startsListening() {
        val state = VoiceTurnState(ui = VoiceUiState.RequestingPermission, epoch = 1, route = VoiceTurnRoute.LOCAL)
        val result = reduce(state, VoiceTurnEvent.PermissionGranted)
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.StartLocalRecording(1)), result.effects)
    }

    @Test
    fun permissionGranted_cloud_startsConnecting() {
        val state = VoiceTurnState(ui = VoiceUiState.RequestingPermission, epoch = 1, route = VoiceTurnRoute.CLOUD, bargeIn = false)
        val result = reduce(state, VoiceTurnEvent.PermissionGranted)
        assertEquals(VoiceUiState.Connecting, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.OpenCloudSession(1, false)), result.effects)
    }

    @Test
    fun permissionDenied_movesToError() {
        val state = VoiceTurnState(ui = VoiceUiState.RequestingPermission, epoch = 1, route = VoiceTurnRoute.LOCAL)
        val result = reduce(state, VoiceTurnEvent.PermissionDenied("nope"))
        assertEquals(VoiceUiState.Error("nope"), result.state.ui)
        assertEquals(VoiceTurnRoute.NONE, result.state.route)
    }

    @Test
    fun resetTap_midThinking_teardownsWithBumpedEpoch() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.ResetTap)
        assertEquals(VoiceUiState.Cancelling, result.state.ui)
        assertEquals(2, result.state.epoch)
        assertEquals(listOf(VoiceTurnEffect.TeardownAll), result.effects)
    }

    @Test
    fun resetTap_fromIdle_goesIdleWithNoEffects() {
        val result = reduce(VoiceTurnState(ui = VoiceUiState.Idle, epoch = 1), VoiceTurnEvent.ResetTap)
        assertEquals(VoiceUiState.Idle, result.state.ui)
        assertEquals(1, result.state.epoch)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun resetTap_fromDoneOrError_goesIdleAndReleasesSession() {
        listOf(
            VoiceTurnState(ui = VoiceUiState.Done, epoch = 1),
            VoiceTurnState(ui = VoiceUiState.Error("x"), epoch = 1),
        ).forEach { state ->
            val result = reduce(state, VoiceTurnEvent.ResetTap)
            assertEquals(VoiceUiState.Idle, result.state.ui)
            assertEquals(1, result.state.epoch)
            assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
        }
    }

    @Test
    fun resetTap_duringCancelling_isUnchanged() {
        val state = VoiceTurnState(ui = VoiceUiState.Cancelling, epoch = 4)
        val result = reduce(state, VoiceTurnEvent.ResetTap)
        assertEquals(state, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun teardownComplete_currentEpoch_movesToIdle() {
        val state = VoiceTurnState(ui = VoiceUiState.Cancelling, epoch = 2)
        val result = reduce(state, VoiceTurnEvent.TeardownComplete(2))
        assertEquals(VoiceTurnState(ui = VoiceUiState.Idle, epoch = 2), result.state)
    }

    @Test
    fun staleEpochEvents_areDroppedVerbatim() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 5, transcript = "keep")
        val staleEpoch = 4
        val events = listOf(
            VoiceTurnEvent.SessionEvent(staleEpoch, LiveEvent.TurnComplete),
            VoiceTurnEvent.LocalTranscript(staleEpoch, "ignored"),
            VoiceTurnEvent.BrainDelta(staleEpoch, "ignored"),
            VoiceTurnEvent.BrainDone(staleEpoch, "ignored"),
            VoiceTurnEvent.BrainError(staleEpoch, "ignored"),
            VoiceTurnEvent.TeardownComplete(staleEpoch),
        )
        events.forEach { event ->
            val result = reduce(state, event)
            assertEquals("stale event $event must not mutate state", state, result.state)
            assertTrue("stale event $event must emit no effects", result.effects.isEmpty())
        }
    }

    @Test
    fun sessionEvent_configuredWhileConnecting_movesToListening() {
        val state = VoiceTurnState(ui = VoiceUiState.Connecting, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.State(LiveSessionState.CONFIGURED)))
        assertEquals(VoiceUiState.Listening, result.state.ui)
    }

    @Test
    fun sessionEvent_configuredWhileNotConnecting_isNoOp() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.State(LiveSessionState.CONFIGURED)))
        assertEquals(state, result.state)
    }

    @Test
    fun sessionEvent_reconnecting_movesToConnecting() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.State(LiveSessionState.RECONNECTING)))
        assertEquals(VoiceUiState.Connecting, result.state.ui)
    }

    @Test
    fun sessionEvent_closed_movesToError() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.State(LiveSessionState.CLOSED)))
        assertEquals(VoiceUiState.Error("Connection closed."), result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
    }

    @Test
    fun sessionEvent_inputTranscript_updatesTranscript() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.InputTranscript("hello")))
        assertEquals("hello", result.state.transcript)
    }

    @Test
    fun sessionEvent_outputTranscriptAndAudioDelta_moveToSpeaking() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1)
        assertEquals(VoiceUiState.Speaking, reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.OutputTranscript("hi"))).state.ui)
        assertEquals(VoiceUiState.Speaking, reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.AudioDelta(ByteArray(0), 0))).state.ui)
    }

    // B1: Gemini's TurnComplete is only ITS own discarded-turn boundary — the Brain Gateway owns the answer,
    // so it's benign: no move to Done, no session release. The turn ends on SpeakComplete after TTS.
    @Test
    fun sessionEvent_turnComplete_isBenignNonTerminal() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.TurnComplete))
        assertEquals(state, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun sessionEvent_interrupted_movesToListeningAndClearsTranscript() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 1, transcript = "stale")
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.Interrupted))
        assertEquals(VoiceUiState.Listening, result.state.ui)
        assertEquals("", result.state.transcript)
    }

    @Test
    fun sessionEvent_error_movesToError() {
        val state = VoiceTurnState(ui = VoiceUiState.Listening, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.SessionEvent(1, LiveEvent.Error(LiveErrorKind.NETWORK, "net down")))
        assertEquals(VoiceUiState.Error("net down"), result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
    }

    @Test
    fun localTranscript_blankOrNull_movesToDone() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1)
        assertEquals(VoiceUiState.Done, reduce(state, VoiceTurnEvent.LocalTranscript(1, null)).state.ui)
        assertEquals(VoiceUiState.Done, reduce(state, VoiceTurnEvent.LocalTranscript(1, "   ")).state.ui)
    }

    @Test
    fun localTranscript_nonBlank_runsBrainTurn() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.LocalTranscript(1, "what time is it"))
        assertEquals(listOf(VoiceTurnEffect.RunBrainTurn(1, "what time is it")), result.effects)
    }

    @Test
    fun brainDone_local_movesToDoneAndSpeaksLocal() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1, route = VoiceTurnRoute.LOCAL)
        val result = reduce(state, VoiceTurnEvent.BrainDone(1, "the answer"))
        assertEquals(VoiceUiState.Done, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.SpeakLocal(1, "the answer")), result.effects)
    }

    // B1: cloud BrainDone is NOT terminal — the Brain answer must still be vocalized via the Voice Gateway.
    // Move to Speaking + SpeakCloud; the terminal Done + ReleaseSession fires on SpeakComplete after playback.
    @Test
    fun brainDone_cloud_movesToSpeakingAndSpeaksCloud() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1, route = VoiceTurnRoute.CLOUD)
        val result = reduce(state, VoiceTurnEvent.BrainDone(1, "the answer"))
        assertEquals(VoiceUiState.Speaking, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.SpeakCloud(1, "the answer")), result.effects)
    }

    @Test
    fun speakComplete_movesToDoneAndReleasesSession() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 1, route = VoiceTurnRoute.CLOUD)
        val result = reduce(state, VoiceTurnEvent.SpeakComplete(1))
        assertEquals(VoiceUiState.Done, result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
    }

    @Test
    fun speakComplete_staleEpoch_isDroppedVerbatim() {
        val state = VoiceTurnState(ui = VoiceUiState.Speaking, epoch = 5, route = VoiceTurnRoute.CLOUD)
        val result = reduce(state, VoiceTurnEvent.SpeakComplete(4))
        assertEquals(state, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun brainError_movesToErrorAndReleasesSession() {
        val state = VoiceTurnState(ui = VoiceUiState.Thinking, epoch = 1)
        val result = reduce(state, VoiceTurnEvent.BrainError(1, "boom"))
        assertEquals(VoiceUiState.Error("boom"), result.state.ui)
        assertEquals(listOf(VoiceTurnEffect.ReleaseSession), result.effects)
    }
}

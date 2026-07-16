package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionState

// 9-state orchestrator UI surface (M2-04). Error carries a sanitized, displayable message only.
sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object RequestingPermission : VoiceUiState
    data object Connecting : VoiceUiState
    data object Listening : VoiceUiState
    data object Thinking : VoiceUiState
    data object Speaking : VoiceUiState
    data object Done : VoiceUiState
    data object Cancelling : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

enum class VoiceTurnRoute { NONE, CLOUD, LOCAL }

data class VoiceTurnState(
    val ui: VoiceUiState = VoiceUiState.Idle,
    val epoch: Int = 0,
    val route: VoiceTurnRoute = VoiceTurnRoute.NONE,
    val transcript: String = "",
    val bargeIn: Boolean = true,
)

// Every async-arriving event carries the epoch it was launched under so stale turns are dropped.
sealed interface VoiceTurnEvent {
    data class MicTap(val route: VoiceTurnRoute, val bargeIn: Boolean) : VoiceTurnEvent
    data class PermissionNeeded(val route: VoiceTurnRoute, val bargeIn: Boolean) : VoiceTurnEvent
    data object PermissionGranted : VoiceTurnEvent
    data class PermissionDenied(val message: String) : VoiceTurnEvent
    data object ResetTap : VoiceTurnEvent
    data class SessionEvent(val epoch: Int, val event: LiveEvent) : VoiceTurnEvent
    data class LocalTranscript(val epoch: Int, val text: String?) : VoiceTurnEvent
    data class BrainDelta(val epoch: Int, val text: String) : VoiceTurnEvent
    data class BrainDone(val epoch: Int, val text: String) : VoiceTurnEvent
    data class BrainError(val epoch: Int, val message: String) : VoiceTurnEvent
    // Cloud route: fired after the Brain answer has been vocalized via TTS (SpeakCloud) — the terminal signal.
    data class SpeakComplete(val epoch: Int) : VoiceTurnEvent
    data class TeardownComplete(val epoch: Int) : VoiceTurnEvent
}

sealed interface VoiceTurnEffect {
    data object RequestPermission : VoiceTurnEffect
    data class OpenCloudSession(val epoch: Int, val bargeIn: Boolean) : VoiceTurnEffect
    data class EndCloudUserTurn(val epoch: Int) : VoiceTurnEffect
    data class StartLocalRecording(val epoch: Int) : VoiceTurnEffect
    data class StopLocalRecordingAndRecognize(val epoch: Int) : VoiceTurnEffect
    data object CancelBrainTurn : VoiceTurnEffect
    // Cloud barge-in: re-open the mic for a fresh user turn on the SAME live session (the prior turn's
    // endUserTurn stopped capture). The VM falls back to a fresh session if the handle can't resume.
    data class ResumeCloudUserTurn(val epoch: Int, val bargeIn: Boolean) : VoiceTurnEffect
    data class RunBrainTurn(val epoch: Int, val transcript: String) : VoiceTurnEffect
    data class SpeakLocal(val epoch: Int, val text: String) : VoiceTurnEffect
    // Cloud route: vocalize the Brain answer through the Voice Gateway (Gemini TTS), then dispatch SpeakComplete.
    data class SpeakCloud(val epoch: Int, val text: String) : VoiceTurnEffect
    data object StopLocalSpeaking : VoiceTurnEffect
    data object TeardownAll : VoiceTurnEffect
    // Releases live-session resources (cloud handle, session job, FGS, lifecycle host) on terminal
    // states WITHOUT clearing question/answer text or resetting UI — the user keeps reading the
    // result while the mic indicator goes off. TeardownAll remains the mid-turn cancel path.
    data object ReleaseSession : VoiceTurnEffect
}

data class VoiceTurnResult(val state: VoiceTurnState, val effects: List<VoiceTurnEffect> = emptyList())

// Pure (State, Event) -> State+effects reducer. Epoch guard: any epoch-carrying event whose epoch
// doesn't match state.epoch is dropped verbatim (stale turn, no UI mutation, no effects).
object FridayVoiceTurnMachine {

    fun reduce(state: VoiceTurnState, event: VoiceTurnEvent): VoiceTurnResult {
        val guardedEpoch = epochOf(event)
        if (guardedEpoch != null && guardedEpoch != state.epoch) return VoiceTurnResult(state)

        return when (event) {
            is VoiceTurnEvent.MicTap -> onMicTap(state, event)
            is VoiceTurnEvent.PermissionNeeded -> onPermissionNeeded(state, event)
            VoiceTurnEvent.PermissionGranted -> onPermissionGranted(state)
            is VoiceTurnEvent.PermissionDenied -> VoiceTurnResult(
                state.copy(ui = VoiceUiState.Error(event.message), route = VoiceTurnRoute.NONE),
            )
            VoiceTurnEvent.ResetTap -> onResetTap(state)
            is VoiceTurnEvent.SessionEvent -> onSessionEvent(state, event.event)
            is VoiceTurnEvent.LocalTranscript -> onLocalTranscript(state, event)
            is VoiceTurnEvent.BrainDelta -> VoiceTurnResult(state.copy(ui = VoiceUiState.Speaking))
            is VoiceTurnEvent.BrainDone -> onBrainDone(state, event)
            is VoiceTurnEvent.BrainError -> VoiceTurnResult(
                state.copy(ui = VoiceUiState.Error(event.message)),
                listOf(VoiceTurnEffect.ReleaseSession),
            )
            // Cloud terminal: the Brain answer finished playing through the Voice Gateway.
            is VoiceTurnEvent.SpeakComplete -> VoiceTurnResult(
                state.copy(ui = VoiceUiState.Done),
                listOf(VoiceTurnEffect.ReleaseSession),
            )
            is VoiceTurnEvent.TeardownComplete -> VoiceTurnResult(
                VoiceTurnState(ui = VoiceUiState.Idle, epoch = state.epoch),
            )
        }
    }

    private fun epochOf(event: VoiceTurnEvent): Int? = when (event) {
        is VoiceTurnEvent.SessionEvent -> event.epoch
        is VoiceTurnEvent.LocalTranscript -> event.epoch
        is VoiceTurnEvent.BrainDelta -> event.epoch
        is VoiceTurnEvent.BrainDone -> event.epoch
        is VoiceTurnEvent.BrainError -> event.epoch
        is VoiceTurnEvent.SpeakComplete -> event.epoch
        is VoiceTurnEvent.TeardownComplete -> event.epoch
        else -> null
    }

    private fun onMicTap(state: VoiceTurnState, event: VoiceTurnEvent.MicTap): VoiceTurnResult =
        when (state.ui) {
            VoiceUiState.Idle, VoiceUiState.Done, is VoiceUiState.Error -> {
                val epoch = state.epoch + 1
                if (event.route == VoiceTurnRoute.LOCAL) {
                    VoiceTurnResult(
                        VoiceTurnState(VoiceUiState.Listening, epoch, event.route, "", event.bargeIn),
                        listOf(VoiceTurnEffect.StartLocalRecording(epoch)),
                    )
                } else {
                    VoiceTurnResult(
                        VoiceTurnState(VoiceUiState.Connecting, epoch, event.route, "", event.bargeIn),
                        listOf(VoiceTurnEffect.OpenCloudSession(epoch, event.bargeIn)),
                    )
                }
            }
            VoiceUiState.Listening -> if (state.route == VoiceTurnRoute.LOCAL) {
                VoiceTurnResult(
                    state.copy(ui = VoiceUiState.Thinking),
                    listOf(VoiceTurnEffect.StopLocalRecordingAndRecognize(state.epoch)),
                )
            } else {
                VoiceTurnResult(
                    state.copy(ui = VoiceUiState.Thinking),
                    listOf(
                        VoiceTurnEffect.EndCloudUserTurn(state.epoch),
                        VoiceTurnEffect.RunBrainTurn(state.epoch, state.transcript),
                    ),
                )
            }
            // Barge-in: tap during SPEAKING. bargeIn=true -> cancel current output, start a new listen turn
            // under a fresh epoch (kills stale deltas from the interrupted turn). endUserTurn stopped the
            // cloud mic when this turn began, so the cloud route needs an explicit ResumeCloudUserTurn to
            // re-open capture on the same socket; the local route re-records via StartLocalRecording.
            VoiceUiState.Speaking -> if (event.bargeIn) {
                val epoch = state.epoch + 1
                val effects = buildList {
                    add(VoiceTurnEffect.CancelBrainTurn)
                    if (state.route == VoiceTurnRoute.LOCAL) {
                        add(VoiceTurnEffect.StopLocalSpeaking)
                        add(VoiceTurnEffect.StartLocalRecording(epoch))
                    } else {
                        add(VoiceTurnEffect.ResumeCloudUserTurn(epoch, state.bargeIn))
                    }
                }
                VoiceTurnResult(state.copy(ui = VoiceUiState.Listening, epoch = epoch, transcript = ""), effects)
            } else {
                VoiceTurnResult(
                    state.copy(ui = VoiceUiState.Done),
                    if (state.route == VoiceTurnRoute.LOCAL) listOf(VoiceTurnEffect.StopLocalSpeaking)
                    else listOf(VoiceTurnEffect.ReleaseSession),
                )
            }
            else -> VoiceTurnResult(state) // Connecting/Thinking/Cancelling/RequestingPermission: ignore rapid taps
        }

    private fun onPermissionNeeded(state: VoiceTurnState, event: VoiceTurnEvent.PermissionNeeded): VoiceTurnResult =
        if (state.ui == VoiceUiState.Idle || state.ui == VoiceUiState.Done || state.ui is VoiceUiState.Error) {
            val epoch = state.epoch + 1
            VoiceTurnResult(
                VoiceTurnState(VoiceUiState.RequestingPermission, epoch, event.route, "", event.bargeIn),
                listOf(VoiceTurnEffect.RequestPermission),
            )
        } else VoiceTurnResult(state)

    private fun onPermissionGranted(state: VoiceTurnState): VoiceTurnResult =
        if (state.ui == VoiceUiState.RequestingPermission) {
            if (state.route == VoiceTurnRoute.LOCAL) {
                VoiceTurnResult(
                    state.copy(ui = VoiceUiState.Listening),
                    listOf(VoiceTurnEffect.StartLocalRecording(state.epoch)),
                )
            } else {
                VoiceTurnResult(
                    state.copy(ui = VoiceUiState.Connecting),
                    listOf(VoiceTurnEffect.OpenCloudSession(state.epoch, state.bargeIn)),
                )
            }
        } else VoiceTurnResult(state)

    private fun onResetTap(state: VoiceTurnState): VoiceTurnResult = when (state.ui) {
        VoiceUiState.Idle -> VoiceTurnResult(VoiceTurnState(ui = VoiceUiState.Idle, epoch = state.epoch))
        // Idempotent belt-and-braces: terminal entry already released, but a reset from Done/Error
        // re-releases so no path can leave the FGS/mic indicator alive.
        VoiceUiState.Done, is VoiceUiState.Error -> VoiceTurnResult(
            VoiceTurnState(ui = VoiceUiState.Idle, epoch = state.epoch),
            listOf(VoiceTurnEffect.ReleaseSession),
        )
        VoiceUiState.Cancelling -> VoiceTurnResult(state)
        else -> {
            val epoch = state.epoch + 1
            VoiceTurnResult(state.copy(ui = VoiceUiState.Cancelling, epoch = epoch), listOf(VoiceTurnEffect.TeardownAll))
        }
    }

    private fun onSessionEvent(state: VoiceTurnState, event: LiveEvent): VoiceTurnResult = when (event) {
        is LiveEvent.State -> when (event.state) {
            LiveSessionState.CONFIGURED -> if (state.ui == VoiceUiState.Connecting)
                VoiceTurnResult(state.copy(ui = VoiceUiState.Listening)) else VoiceTurnResult(state)
            LiveSessionState.RECONNECTING -> VoiceTurnResult(state.copy(ui = VoiceUiState.Connecting))
            LiveSessionState.CLOSED -> VoiceTurnResult(
                state.copy(ui = VoiceUiState.Error("Connection closed.")),
                listOf(VoiceTurnEffect.ReleaseSession),
            )
            else -> VoiceTurnResult(state)
        }
        is LiveEvent.InputTranscript -> VoiceTurnResult(state.copy(transcript = event.text))
        is LiveEvent.OutputTranscript -> VoiceTurnResult(state.copy(ui = VoiceUiState.Speaking))
        is LiveEvent.AudioDelta -> VoiceTurnResult(state.copy(ui = VoiceUiState.Speaking))
        // B1: Gemini's TurnComplete is only ITS discarded own-turn boundary — NOT the end of our answer.
        // The Brain Gateway owns the answer, so this is benign: don't move to Done, don't release the session.
        // The turn ends on SpeakComplete after the Brain answer is vocalized via the Voice Gateway.
        LiveEvent.TurnComplete -> VoiceTurnResult(state)
        LiveEvent.Interrupted -> VoiceTurnResult(state.copy(ui = VoiceUiState.Listening, transcript = ""))
        is LiveEvent.Error -> VoiceTurnResult(
            state.copy(ui = VoiceUiState.Error(event.message)),
            listOf(VoiceTurnEffect.ReleaseSession),
        )
    }

    private fun onLocalTranscript(state: VoiceTurnState, event: VoiceTurnEvent.LocalTranscript): VoiceTurnResult =
        if (event.text.isNullOrBlank()) {
            VoiceTurnResult(state.copy(ui = VoiceUiState.Done))
        } else {
            VoiceTurnResult(state, listOf(VoiceTurnEffect.RunBrainTurn(state.epoch, event.text)))
        }

    // LOCAL: Done + SpeakLocal (on-device TTS; still needs the lifecycle host attached).
    // CLOUD: NOT terminal here — the Brain answer must still be vocalized via the Voice Gateway. Move to
    // Speaking + SpeakCloud; the terminal Done + ReleaseSession fires on SpeakComplete after playback.
    private fun onBrainDone(state: VoiceTurnState, event: VoiceTurnEvent.BrainDone): VoiceTurnResult =
        if (state.route == VoiceTurnRoute.LOCAL) {
            VoiceTurnResult(
                state.copy(ui = VoiceUiState.Done),
                listOf(VoiceTurnEffect.SpeakLocal(state.epoch, event.text)),
            )
        } else {
            VoiceTurnResult(
                state.copy(ui = VoiceUiState.Speaking),
                listOf(VoiceTurnEffect.SpeakCloud(state.epoch, event.text)),
            )
        }
}

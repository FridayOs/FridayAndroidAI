package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// The FRI-530 CloudBridge seam: Gemini Live owns realtime audio, the Brain Gateway owns reasoning/tools/actions.
// The live transport never routes a prompt to a model — it produces a transcript, and this bridge hands that
// transcript to the Brain Gateway through the four brain_* ops (never a FRIDAY proxy; the brain never sees audio).
//
// Correlation: every voice interaction carries a stable sessionId, and each brain request a fresh correlationId
// tagged to it. brain_cancel/brain_confirm resolve against the turn opened by the latest brain_turn/brain_continue,
// so a barge-in cancels the right turn and a confirmation resolves the right gate.
class LiveCloudBridge(
    private val brain: BrainBridge,
    val sessionId: String = UUID.randomUUID().toString(),
) {
    // The correlation id of the most recent brain request; brain_cancel/confirm act on this turn.
    @Volatile
    var currentCorrelationId: String? = null
        private set

    private fun nextCorrelation(): String = "$sessionId:${UUID.randomUUID()}".also { currentCorrelationId = it }

    // brain_turn — a fresh transcript-driven turn. Appends the live transcript as the new user turn, then
    // streams the brain's reply. Audio never crosses this seam; only text history does.
    fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> {
        nextCorrelation()
        val turn = history + GatewayTurn("user", transcript)
        return brain.brainTurn(turn)
    }

    // brain_continue — re-run over the existing history without injecting a new user message (e.g. the model
    // was interrupted and the same turn continues). Keeps the same session, new correlation.
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
        nextCorrelation()
        return brain.brainContinue(history)
    }

    // brain_cancel — stop the in-flight brain turn (barge-in / user cut-in) and fail any parked confirmation.
    fun brainCancel() {
        currentCorrelationId = null
        brain.brainCancel()
    }

    // brain_confirm — resolve a confirmation gate the brain armed during the current turn. False if none armed.
    fun brainConfirm(): Boolean = brain.brainConfirm()

    fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

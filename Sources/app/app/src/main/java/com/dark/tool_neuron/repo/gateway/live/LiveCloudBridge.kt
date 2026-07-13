package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// FRI-530 CloudBridge: hands the Live transcript (text, never audio) to the brain via brain_* ops; fresh correlation per turn, stale cancel/confirm dropped.
class LiveCloudBridge(
    private val brain: LiveBrainGateway,
    val sessionId: String = UUID.randomUUID().toString(),
) {
    // In-flight turn's correlation; null when idle. cancel/confirm target this.
    @Volatile
    var currentCorrelation: BrainCorrelation? = null
        private set

    val currentCorrelationId: String? get() = currentCorrelation?.turnId

    private fun open(): BrainCorrelation =
        BrainCorrelation(sessionId, UUID.randomUUID().toString()).also { currentCorrelation = it }

    // brain_turn: appends the transcript as the new user turn, mints a fresh correlation, streams the reply.
    fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> {
        val correlation = open()
        return brain.brainTurn(correlation, history + GatewayTurn("user", transcript))
    }

    // brain_continue: re-run over existing history (no new user turn), fresh turnId under the same session.
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
        val correlation = open()
        return brain.brainContinue(correlation, history)
    }

    // brain_cancel — stop the in-flight brain turn (barge-in / user cut-in). Forwards the current correlation.
    fun brainCancel() {
        val correlation = currentCorrelation ?: return
        currentCorrelation = null
        brain.brainCancel(correlation)
    }

    // Cancel a SPECIFIC turn; a stale correlation (not the active turn) is ignored.
    fun cancelTurn(correlation: BrainCorrelation) {
        if (correlation != currentCorrelation) return
        currentCorrelation = null
        brain.brainCancel(correlation)
    }

    // brain_confirm — resolve a confirmation gate the brain armed during the current turn. False if none active.
    fun brainConfirm(): Boolean {
        val correlation = currentCorrelation ?: return false
        return brain.brainConfirm(correlation)
    }

    // Confirm a SPECIFIC turn; a stale correlation is ignored so it can't resolve a newer turn's gate.
    fun confirmTurn(correlation: BrainCorrelation): Boolean {
        if (correlation != currentCorrelation) return false
        return brain.brainConfirm(correlation)
    }

    fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

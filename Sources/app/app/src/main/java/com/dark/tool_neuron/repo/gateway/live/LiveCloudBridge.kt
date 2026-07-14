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
    // One lock serializes open/cancel/confirm end-to-end: the ownership check and the seam call happen as one unit, so a new turn can't interleave between them.
    private val lock = Any()
    private var current: BrainCorrelation? = null

    val currentCorrelation: BrainCorrelation? get() = synchronized(lock) { current }
    val currentCorrelationId: String? get() = currentCorrelation?.turnId

    // brain_turn: appends the transcript as the new user turn, mints a fresh correlation, streams the reply.
    fun brainTurn(history: List<GatewayTurn>, transcript: String): Flow<GatewayEvent> =
        synchronized(lock) {
            val correlation = BrainCorrelation(sessionId, UUID.randomUUID().toString())
            current = correlation
            brain.brainTurn(correlation, history + GatewayTurn("user", transcript))
        }

    // brain_continue: re-run over existing history (no new user turn), fresh turnId under the same session.
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> =
        synchronized(lock) {
            val correlation = BrainCorrelation(sessionId, UUID.randomUUID().toString())
            current = correlation
            brain.brainContinue(correlation, history)
        }

    // brain_cancel — take the in-flight correlation and forward it (barge-in / user cut-in) as one atomic unit.
    fun brainCancel() {
        synchronized(lock) {
            val correlation = current ?: return
            current = null
            brain.brainCancel(correlation)
        }
    }

    // Cancel a SPECIFIC turn; a stale correlation (not the active turn) is dropped inside the same lock.
    fun cancelTurn(correlation: BrainCorrelation) {
        synchronized(lock) {
            if (current != correlation) return
            current = null
            brain.brainCancel(correlation)
        }
    }

    // brain_confirm — resolve a confirmation gate the brain armed during the current turn. False if none active.
    fun brainConfirm(): Boolean =
        synchronized(lock) {
            val correlation = current ?: return false
            brain.brainConfirm(correlation)
        }

    // Confirm a SPECIFIC turn; check + seam call share the lock so a superseding turn can't slip between them.
    fun confirmTurn(correlation: BrainCorrelation): Boolean =
        synchronized(lock) {
            if (current != correlation) return false
            brain.brainConfirm(correlation)
        }

    fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

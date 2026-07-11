package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceBridge @Inject constructor(
    private val brain: BrainBridge,
) {
    private var turnJob: Job? = null

    // Owns the collecting Job so brainCancel() tears down the live turn, not just the delegate.
    fun runTurn(
        scope: CoroutineScope,
        history: List<GatewayTurn>,
        onEvent: suspend (GatewayEvent) -> Unit,
    ): Job {
        turnJob?.cancel()
        val job = brain.brainTurn(history).onEach(onEvent).launchIn(scope)
        turnJob = job
        return job
    }

    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brain.brainContinue(history)

    // Cancel the live collecting Job first, then tell the delegate to stop the underlying stream.
    fun brainCancel() {
        turnJob?.cancel()
        turnJob = null
        brain.brainCancel()
    }

    fun brainConfirm(): Boolean = brain.brainConfirm()

    // Used by the UI to render a "waiting for confirmation" affordance when the brain arms a gate.
    fun brainAwaitingConfirmation(): Boolean =
        (brain as? GatewayRoleRouter)?.awaitingConfirmation?.value == true
}

package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceBridge @Inject constructor(
    private val brain: BrainBridge,
) {
    // Both voice routes cross this seam: audio yields a transcript, brain answers it.
    fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = brain.brainTurn(history)

    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brain.brainContinue(history)

    fun brainCancel() = brain.brainCancel()

    fun brainConfirm(): Boolean = brain.brainConfirm()
}

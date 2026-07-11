package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceBridge @Inject constructor(
    private val router: GatewayRoleRouter,
) {
    // Both voice routes cross this seam: audio yields a transcript, brain answers it.
    fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = router.brainTurn(history)

    fun cancel() = router.brainCancel()
}

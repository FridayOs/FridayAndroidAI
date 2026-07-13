package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Production LiveBrainGateway over the real BrainBridge; tracks the active turnId so a stale cancel/confirm is dropped.
@Singleton
class BrainGatewayLiveAdapter @Inject constructor(
    private val brain: BrainBridge,
) : LiveBrainGateway {

    @Volatile
    private var activeTurnId: String? = null

    override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        activeTurnId = correlation.turnId
        return brain.brainTurn(history)
    }

    override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        activeTurnId = correlation.turnId
        return brain.brainContinue(history)
    }

    override fun brainCancel(correlation: BrainCorrelation) {
        if (correlation.turnId != activeTurnId) return
        activeTurnId = null
        brain.brainCancel()
    }

    override fun brainConfirm(correlation: BrainCorrelation): Boolean {
        if (correlation.turnId != activeTurnId) return false
        return brain.brainConfirm()
    }

    override fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

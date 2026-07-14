package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Adapter owns correlation (real BrainBridge takes no id): one lock spans the ownership check AND the identity-less brain call, so a turn open can never interleave between confirm/cancel's check and its forward.
@Singleton
class BrainGatewayLiveAdapter @Inject constructor(
    private val brain: BrainBridge,
) : LiveBrainGateway {

    private val lock = Any()
    private var active: BrainCorrelation? = null

    override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> =
        synchronized(lock) {
            active = correlation
            brain.brainTurn(history)
        }

    override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> =
        synchronized(lock) {
            active = correlation
            brain.brainContinue(history)
        }

    override fun brainCancel(correlation: BrainCorrelation) {
        synchronized(lock) {
            if (active != correlation) return
            active = null
            brain.brainCancel()
        }
    }

    override fun brainConfirm(correlation: BrainCorrelation): Boolean =
        synchronized(lock) {
            if (active == correlation) brain.brainConfirm() else false
        }

    override fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

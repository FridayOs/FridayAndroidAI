package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Production LiveBrainGateway over the real BrainBridge. This adapter OWNS correlation: the underlying BrainBridge
// (GatewayRoleRouter) manages a single in-flight turn and takes no id, so identity is enforced here. It tracks the
// full active BrainCorrelation (sessionId + turnId), not just the turnId, so a cancel/confirm from a superseded
// turn OR a different session is dropped before it can touch the live turn (cross-session isolation). Concurrent
// Live sessions aren't a product scenario (one mic), but the guard makes a stale/foreign correlation a no-op.
@Singleton
class BrainGatewayLiveAdapter @Inject constructor(
    private val brain: BrainBridge,
) : LiveBrainGateway {

    @Volatile
    private var active: BrainCorrelation? = null

    override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        active = correlation
        return brain.brainTurn(history)
    }

    override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        active = correlation
        return brain.brainContinue(history)
    }

    override fun brainCancel(correlation: BrainCorrelation) {
        if (correlation != active) return
        active = null
        brain.brainCancel()
    }

    override fun brainConfirm(correlation: BrainCorrelation): Boolean {
        if (correlation != active) return false
        return brain.brainConfirm()
    }

    override fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

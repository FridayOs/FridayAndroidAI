package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.BrainBridge
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// Adapter owns correlation (real BrainBridge takes no id): holds the full active BrainCorrelation in an AtomicReference so a cancel/confirm from a superseded turn or foreign session is an atomic no-op.
@Singleton
class BrainGatewayLiveAdapter @Inject constructor(
    private val brain: BrainBridge,
) : LiveBrainGateway {

    private val active = AtomicReference<BrainCorrelation?>(null)

    override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        active.set(correlation)
        return brain.brainTurn(history)
    }

    override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> {
        active.set(correlation)
        return brain.brainContinue(history)
    }

    override fun brainCancel(correlation: BrainCorrelation) {
        if (active.compareAndSet(correlation, null)) brain.brainCancel()
    }

    override fun brainConfirm(correlation: BrainCorrelation): Boolean =
        if (active.get() == correlation) brain.brainConfirm() else false

    override fun brainAwaitingConfirmation(): Boolean = brain.brainAwaitingConfirmation()
}

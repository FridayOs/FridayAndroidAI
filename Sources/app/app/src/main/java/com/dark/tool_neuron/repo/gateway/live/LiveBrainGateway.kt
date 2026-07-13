package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.flow.Flow

// sessionId stable per session, turnId fresh per turn, so cancel/confirm hit exactly their turn.
data class BrainCorrelation(val sessionId: String, val turnId: String)

// Brain seam LiveCloudBridge routes through; every op carries the correlation to the impl. Text only, never audio.
interface LiveBrainGateway {
    fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainCancel(correlation: BrainCorrelation)
    fun brainConfirm(correlation: BrainCorrelation): Boolean
    fun brainAwaitingConfirmation(): Boolean
}

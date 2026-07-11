package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.flow.Flow

// Four-op seam the voice layer uses to reach the brain (transcript only, never audio); an interface so callers test against a fake.
interface BrainBridge {
    fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainCancel()
    fun brainConfirm(): Boolean
}

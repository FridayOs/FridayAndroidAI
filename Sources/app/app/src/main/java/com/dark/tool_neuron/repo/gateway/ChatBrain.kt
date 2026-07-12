package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import kotlinx.coroutines.flow.Flow

// Chat's view of the brain router, so FridayChatViewModel streams/regenerates against a fake off-device.
interface ChatBrain {
    fun brainGateway(): GatewayConfig?
    fun brainUnavailableMessage(): String
    fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent>
    fun brainCancel()
}

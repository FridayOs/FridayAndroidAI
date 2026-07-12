package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import kotlinx.coroutines.flow.StateFlow

// Read seam over the gateway vault so routers/VMs resolve brain/voice against a fake off-device.
interface GatewayDirectory {
    val gateways: StateFlow<List<GatewayConfig>>
    fun brainGateway(): GatewayConfig?
    fun voiceGateway(): GatewayConfig?
}

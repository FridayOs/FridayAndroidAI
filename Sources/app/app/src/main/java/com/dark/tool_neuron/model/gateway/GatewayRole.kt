package com.dark.tool_neuron.model.gateway

// A gateway instance is selected into one of two independent roles. The Brain
// Gateway answers chat/reasoning/tools/actions; the Voice Gateway handles live
// audio and always bridges reasoning back to the active Brain Gateway.
enum class GatewayRole { BRAIN, VOICE }

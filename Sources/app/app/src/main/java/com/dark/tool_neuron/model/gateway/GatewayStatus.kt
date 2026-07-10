package com.dark.tool_neuron.model.gateway

// Result of a direct connection probe against a gateway. NOT_TESTED is the
// default; a real probe flips it to READY or FAILED. The design never fakes a
// READY state — see FailedInfo for the sanitized (secret-free) error summary.
enum class GatewayStatus { NOT_TESTED, READY, FAILED }

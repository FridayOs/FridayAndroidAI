package com.dark.tool_neuron.model.gateway

// A user-owned gateway endpoint. apiKey, baseUrl and model are sensitive per
// the M1 data boundary — they live HXS-only and never reach Firebase / FRIDAY API.
data class GatewayConfig(
    val id: String,
    val provider: GatewayProvider,
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val displayModel: String get() = model.ifBlank { provider.defaultModel }
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
}

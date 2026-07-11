package com.dark.tool_neuron.model.gateway

// apiKey/baseUrl/model are HXS-only — never reach Firebase / FRIDAY API. statusError is sanitized, never a raw key.
data class GatewayConfig(
    val id: String,
    val provider: GatewayProvider,
    val label: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: GatewayStatus = GatewayStatus.NOT_TESTED,
    val lastTestedAt: Long = 0L,
    val statusError: String = "",
    val voiceRoute: VoiceRoute = if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD,
) {
    val displayModel: String get() = model.ifBlank { provider.defaultModel }
    val effectiveBaseUrl: String get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')

    fun supportsRole(role: GatewayRole): Boolean = provider.supportsRole(role)
}

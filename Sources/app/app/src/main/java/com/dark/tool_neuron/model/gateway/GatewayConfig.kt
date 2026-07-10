package com.dark.tool_neuron.model.gateway

// A user-owned gateway endpoint. apiKey, baseUrl and model are sensitive per
// the M1 data boundary — they live HXS-only and never reach Firebase / FRIDAY API.
//
// status/lastTestedAt/statusError capture the result of a direct connection
// probe (see DirectGatewayClient.testConnection). statusError is a sanitized,
// secret-free summary only — never a raw Authorization header or key.
//
// voiceRoute only matters when the config is used as a Voice Gateway: CLOUD
// streams audio to the provider, LOCAL uses on-device STT/TTS. Either way the
// reasoning is bridged to the active Brain Gateway.
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

package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.friday.ai.R

/*
 * Pure gatewayId -> display-label/status resolver (FRI-574 phase 1). Joins a
 * conversation's persisted gatewayId against the live GatewayConfigRepository
 * list. A deleted/unknown gateway yields `label = null` + `labelRes` pointing at
 * the localized "Unknown provider" string, so callers resolve it via
 * stringResource() — mirrors OnboardingProvidersLogic's R.string-id pattern,
 * keeping this object free of Context/Hilt and directly JVM-unit-testable.
 */
data class GatewayLabel(
    val label: String?,
    val status: GatewayStatus?,
    val labelRes: Int? = null,
)

object GatewayLabelResolver {

    fun resolve(gatewayId: String, gateways: List<GatewayConfig>): GatewayLabel {
        val config = gateways.firstOrNull { it.id == gatewayId }
            ?: return GatewayLabel(label = null, status = null, labelRes = R.string.friday_unknown_provider)
        val label = config.label.ifBlank { config.provider.displayName }
        return GatewayLabel(label = label, status = config.status)
    }
}

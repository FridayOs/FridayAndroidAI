package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.friday.ai.R

/*
 * Pure logic for the Providers screen (FRI-582 P7, design-spec §8): status-chip
 * string-resource mapping and available-catalog ordering. Kept free of
 * Hilt/Context/Android framework types so it is directly JVM-unit-testable
 * (see OnboardingProvidersLogicTest).
 */
object OnboardingProvidersLogic {

    // Selected (brain) takes priority over the raw test status in the chip label.
    fun statusLabelRes(config: GatewayConfig, activeId: String?): Int =
        if (activeId != null && config.id == activeId) {
            R.string.friday_providers_status_selected
        } else {
            when (config.status) {
                GatewayStatus.READY -> R.string.friday_providers_status_ready
                GatewayStatus.NOT_TESTED -> R.string.friday_providers_status_not_tested
                GatewayStatus.FAILED -> R.string.friday_providers_status_failed
            }
        }

    fun isSelected(config: GatewayConfig, activeId: String?): Boolean =
        activeId != null && config.id == activeId

    // Exactly the 7 configurable non-local providers in enum declaration order,
    // then Friday (disabled placeholder) appended last — 8 entries total, LOCAL excluded.
    // Friday itself is enabled = false, so it must be appended explicitly rather than
    // relying on the `enabled` filter that selects the other 7.
    fun availableCatalog(): List<GatewayProvider> =
        GatewayProvider.entries.filter { it.enabled && !it.isLocal } + GatewayProvider.FRIDAY
}

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

    // Per-provider available-row description (design-spec §4.2 availableItems, HTML:2045-2048).
    // Returns a pure value the Composable layer maps to a string resource — keeps this
    // JVM-unit-testable with no Context dependency. Mirrors P0 exactly:
    //   - Friday (disabled placeholder) -> fridayDesc "Managed FRIDAY provider"
    //   - urlRequired catalog rows (OpenClaw/Hermes/Custom) -> "OpenAI-compatible · custom base URL"
    //   - other catalog rows -> "Default: <defaultModel>"
    fun availableDescription(provider: GatewayProvider): ProviderAvailableDesc = when {
        !provider.enabled -> ProviderAvailableDesc.ManagedFriday
        provider.urlRequired -> ProviderAvailableDesc.OpenAiCompatible
        else -> ProviderAvailableDesc.DefaultModel(provider.defaultModel)
    }
}

// Pure description kind for an available-catalog row (see availableDescription above).
// The Composable layer resolves each kind to the matching string resource.
sealed interface ProviderAvailableDesc {
    data class DefaultModel(val model: String) : ProviderAvailableDesc
    data object OpenAiCompatible : ProviderAvailableDesc
    data object ManagedFriday : ProviderAvailableDesc
}

package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.GatewayConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/*
 * Providers screen VM (FRI-582 P7, design-spec §8). Reuses the existing
 * GatewayConfigRepository/GatewayDirectory — no new storage collection is
 * created here. Status-label and catalog-ordering logic is delegated to the
 * pure OnboardingProvidersLogic object so it stays unit-testable without Hilt.
 */
@HiltViewModel
class OnboardingProvidersViewModel @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
) : ViewModel() {

    val gateways: StateFlow<List<GatewayConfig>> = gatewayRepo.gateways
    val activeId: StateFlow<String> = gatewayRepo.brainId

    val availableCatalog: List<GatewayProvider> = OnboardingProvidersLogic.availableCatalog()

    fun statusLabelRes(config: GatewayConfig): Int =
        OnboardingProvidersLogic.statusLabelRes(config, activeId.value)

    fun isSelected(config: GatewayConfig): Boolean =
        OnboardingProvidersLogic.isSelected(config, activeId.value)

    fun select(id: String) = gatewayRepo.selectBrain(id)

    fun delete(id: String) = gatewayRepo.delete(id)
}

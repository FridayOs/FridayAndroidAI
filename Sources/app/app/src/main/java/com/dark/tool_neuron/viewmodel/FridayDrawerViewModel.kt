package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.GatewayConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FridayDrawerViewModel @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
    private val themeController: ThemeController,
) : ViewModel() {

    // User-owned gateways, HXS-only. The drawer lists these instead of local
    // GGUF models — Friday chat/voice route to whichever is selected, direct
    // from Android. The FRIDAY managed provider stays a disabled placeholder.
    val gateways: StateFlow<List<GatewayConfig>> = gatewayRepo.gateways
    val selectedId: StateFlow<String> = gatewayRepo.selectedId

    val themeMode: StateFlow<ThemeController.Mode> = themeController.mode

    val providers: List<GatewayProvider> = GatewayProvider.configurable

    fun selectGateway(id: String) = gatewayRepo.select(id)

    fun addGateway(provider: GatewayProvider, label: String, baseUrl: String, apiKey: String, model: String) {
        gatewayRepo.create(provider, label, baseUrl, apiKey, model)
    }

    fun deleteGateway(id: String) = gatewayRepo.delete(id)

    fun setThemeMode(mode: ThemeController.Mode) {
        themeController.setMode(mode)
    }
}

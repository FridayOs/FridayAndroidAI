package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.model.gateway.GatewayValidation
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.gateway.DirectGatewayClient
import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FridayDrawerViewModel @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
    private val client: DirectGatewayClient,
    private val themeController: ThemeController,
) : ViewModel() {

    // Brain and Voice selection are independent (M2-02); HXS-only, no FRIDAY proxy.
    val gateways: StateFlow<List<GatewayConfig>> = gatewayRepo.gateways
    val selectedId: StateFlow<String> = gatewayRepo.brainId
    val brainId: StateFlow<String> = gatewayRepo.brainId
    val voiceId: StateFlow<String> = gatewayRepo.voiceId

    val themeMode: StateFlow<ThemeController.Mode> = themeController.mode

    val providers: List<GatewayProvider> = GatewayProvider.configurable
    fun providersForRole(role: GatewayRole): List<GatewayProvider> = GatewayProvider.forRole(role)

    // In-flight connection test id, so the UI can show a spinner per row.
    private val _testingId = MutableStateFlow<String?>(null)
    val testingId: StateFlow<String?> = _testingId.asStateFlow()
    private var testJob: Job? = null

    fun selectGateway(id: String) = gatewayRepo.selectBrain(id)
    fun selectBrain(id: String) = gatewayRepo.selectBrain(id)
    fun selectVoice(id: String) = gatewayRepo.selectVoice(id)

    fun validate(provider: GatewayProvider, apiKey: String, model: String, baseUrl: String): GatewayValidation.Result =
        GatewayValidation.validate(provider, apiKey, model, baseUrl)

    // Only creates when valid; returns the result so the caller can surface the reason.
    fun addGateway(
        provider: GatewayProvider,
        label: String,
        baseUrl: String,
        apiKey: String,
        model: String,
    ): GatewayValidation.Result {
        val result = GatewayValidation.validate(provider, apiKey, model, baseUrl)
        if (result is GatewayValidation.Result.Valid) {
            gatewayRepo.create(provider, label, baseUrl, apiKey, model)
        }
        return result
    }

    fun deleteGateway(id: String) = gatewayRepo.delete(id)

    // Never fakes ready — status comes straight from the provider's response.
    fun testGateway(id: String, onResult: (GatewayTestResult) -> Unit = {}) {
        val config = gatewayRepo.getById(id) ?: return
        testJob?.cancel()
        _testingId.value = id
        testJob = viewModelScope.launch {
            val result = client.testConnection(config)
            when (result) {
                GatewayTestResult.Ready -> {
                    gatewayRepo.recordStatus(id, GatewayStatus.READY, "")
                    _testingId.value = null
                }
                is GatewayTestResult.Failure -> {
                    gatewayRepo.recordStatus(id, GatewayStatus.FAILED, result.message)
                    _testingId.value = null
                }
            }
            onResult(result)
        }
    }

    fun setThemeMode(mode: ThemeController.Mode) {
        themeController.setMode(mode)
    }
}

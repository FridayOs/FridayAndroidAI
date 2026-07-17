package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.gateway.DirectGatewayClient
import com.friday.ai.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/*
 * Add Provider screen VM (FRI-582 P8, design-spec §8). Real, cancellable
 * direct-connection test via DirectGatewayClient.testConnection — no delay()
 * or regex fake-validation. Field/priority validation and status/label mapping
 * are delegated to the pure OnboardingAddProviderLogic object so they stay
 * unit-testable without Hilt/Context/network (see OnboardingAddProviderLogicTest).
 */
@HiltViewModel
class OnboardingAddProviderViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gatewayRepo: GatewayConfigRepository,
    private val directClient: DirectGatewayClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val provider: GatewayProvider =
        GatewayProvider.fromId(savedStateHandle.get<String>(NavScreens.OnboardingAddProvider.ARG_CATALOG_ID).orEmpty())

    private val _label = MutableStateFlow("")
    val label: StateFlow<String> = _label.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow(provider.defaultModel)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _baseUrl = MutableStateFlow(provider.defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _advancedOpen = MutableStateFlow(false)
    val advancedOpen: StateFlow<Boolean> = _advancedOpen.asStateFlow()

    private val _keyVisible = MutableStateFlow(false)
    val keyVisible: StateFlow<Boolean> = _keyVisible.asStateFlow()

    private val _status = MutableStateFlow(OnboardingAddProviderStatus.NOT_TESTED)
    val status: StateFlow<OnboardingAddProviderStatus> = _status.asStateFlow()

    private val _errorRes = MutableStateFlow<Int?>(null)
    val errorRes: StateFlow<Int?> = _errorRes.asStateFlow()

    private val _failureMessage = MutableStateFlow<String?>(null)
    val failureMessage: StateFlow<String?> = _failureMessage.asStateFlow()

    // Generation-guarded probe orchestration: publication of a test result is gated on the launch
    // generation still being current, so a stale probe can never publish (see AddProviderTestRunner).
    private val testRunner = AddProviderTestRunner(viewModelScope)

    fun onLabelChange(value: String) {
        _label.value = value
        _errorRes.value = null
    }

    // Editing a connection-relevant field invalidates any prior test result: cancel the in-flight
    // probe and reset to NOT_TESTED so a stale READY can't be saved/selected against changed
    // credentials. Mirrors the design prototype (setAp resets status to 'idle' on key/url/model).
    fun onApiKeyChange(value: String) { _apiKey.value = value; invalidateTestResult() }
    fun onModelChange(value: String) { _model.value = value; invalidateTestResult() }
    fun onBaseUrlChange(value: String) { _baseUrl.value = value; invalidateTestResult() }
    fun toggleAdvanced() { _advancedOpen.value = !_advancedOpen.value }
    fun toggleKeyVisible() { _keyVisible.value = !_keyVisible.value }

    private fun invalidateTestResult() {
        testRunner.invalidate()
        _status.value = OnboardingAddProviderStatus.NOT_TESTED
        _failureMessage.value = null
        _errorRes.value = null
    }

    // Cancellable: leaving/navigating away or editing a field drops the in-flight probe and blocks
    // any stale result from publishing (no orphaned request, no stale READY).
    fun test() {
        val error = OnboardingAddProviderLogic.validate(provider, _apiKey.value, _model.value, _baseUrl.value)
        if (error != null) {
            _errorRes.value = error
            return
        }
        _errorRes.value = null
        _failureMessage.value = null
        val config = transientConfig()
        testRunner.run(
            probe = { directClient.testConnection(config) },
            onStart = { _status.value = OnboardingAddProviderStatus.TESTING },
            onResult = { result ->
                _status.value = OnboardingAddProviderLogic.statusFor(result)
                _failureMessage.value = OnboardingAddProviderLogic.failureMessage(result)
            },
            // Defensive: an unexpected non-cancellation error still leaves a usable FAILED state
            // (never stuck in TESTING). The real client already maps Throwable→Failure; this guards
            // the runner contract regardless.
            onFailure = {
                _status.value = OnboardingAddProviderStatus.FAILED
                _failureMessage.value = context.getString(R.string.friday_add_provider_test_fail_unknown)
            },
        )
    }

    // Returns the persisted config on success, or null (with errorRes set) on validation failure.
    fun save(use: Boolean): GatewayConfig? {
        val error = OnboardingAddProviderLogic.validate(provider, _apiKey.value, _model.value, _baseUrl.value)
        if (error != null) {
            _errorRes.value = error
            return null
        }
        val placeholderTemplate = context.getString(R.string.friday_add_provider_label_placeholder)
        val resolvedLabel = OnboardingAddProviderLogic.resolveLabel(_label.value, placeholderTemplate, provider.displayName)
        val config = gatewayRepo.create(
            provider = provider,
            label = resolvedLabel,
            baseUrl = _baseUrl.value,
            apiKey = _apiKey.value,
            model = _model.value,
            voiceRoute = if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD,
        )
        val recordedStatus = OnboardingAddProviderLogic.recordedStatusFor(_status.value)
        gatewayRepo.recordStatus(config.id, recordedStatus, _failureMessage.value.orEmpty())
        if (use) gatewayRepo.selectBrain(config.id)
        return config
    }

    private fun transientConfig(): GatewayConfig = GatewayConfig(
        id = "",
        provider = provider,
        label = _label.value,
        baseUrl = _baseUrl.value,
        apiKey = _apiKey.value,
        model = _model.value,
        createdAt = 0L,
        updatedAt = 0L,
    )

    override fun onCleared() {
        testRunner.cancel()
        super.onCleared()
    }
}

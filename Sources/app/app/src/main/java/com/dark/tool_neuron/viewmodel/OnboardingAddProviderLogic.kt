package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.model.gateway.GatewayValidation
import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import com.friday.ai.R

/*
 * Pure logic for the Add Provider screen (FRI-582 P8, design-spec §8): field
 * validation priority, test-status mapping, and default-label resolution. Kept
 * free of Hilt/Context/network so it is directly JVM-unit-testable (see
 * OnboardingAddProviderLogicTest) — no delay()/regex fake validation lives here,
 * only the real GatewayValidation.isWellFormedHttpUrl parse.
 */
enum class OnboardingAddProviderStatus { NOT_TESTED, TESTING, READY, FAILED }

object OnboardingAddProviderLogic {

    // Priority: missing key > missing model > required-but-bad URL > optional-but-bad URL.
    fun validate(provider: GatewayProvider, apiKey: String, model: String, baseUrl: String): Int? {
        val key = apiKey.trim()
        val effectiveModel = model.trim()
        val url = baseUrl.trim()

        if (provider.needsKey && key.isBlank()) return R.string.friday_add_provider_err_key
        if (effectiveModel.isBlank()) return R.string.friday_add_provider_err_model
        if (provider.urlRequired && !GatewayValidation.isWellFormedHttpUrl(url)) {
            return R.string.friday_add_provider_err_url
        }
        if (url.isNotBlank() && !GatewayValidation.isWellFormedHttpUrl(url)) {
            return R.string.friday_add_provider_err_url
        }
        return null
    }

    fun statusFor(result: GatewayTestResult): OnboardingAddProviderStatus = when (result) {
        is GatewayTestResult.Ready -> OnboardingAddProviderStatus.READY
        is GatewayTestResult.Failure -> OnboardingAddProviderStatus.FAILED
    }

    // Sanitized message surfaced verbatim from the typed Failure — never a raw statusError.
    fun failureMessage(result: GatewayTestResult): String? =
        (result as? GatewayTestResult.Failure)?.message

    // Persisted status must reflect the CURRENT UI status only. NOT_TESTED/TESTING never persist as
    // READY — so a provider whose credentials were edited after a passing test (UI reset to
    // NOT_TESTED, see OnboardingAddProviderViewModel.invalidateTestResult) is saved untested, never
    // as a stale ready/active brain.
    fun recordedStatusFor(status: OnboardingAddProviderStatus): GatewayStatus = when (status) {
        OnboardingAddProviderStatus.READY -> GatewayStatus.READY
        OnboardingAddProviderStatus.FAILED -> GatewayStatus.FAILED
        OnboardingAddProviderStatus.NOT_TESTED, OnboardingAddProviderStatus.TESTING -> GatewayStatus.NOT_TESTED
    }

    // Blank label falls back to the localized "Personal <Provider>" placeholder template.
    fun resolveLabel(rawLabel: String, labelPlaceholderTemplate: String, providerDisplayName: String): String {
        val trimmed = rawLabel.trim()
        if (trimmed.isNotEmpty()) return trimmed
        return String.format(labelPlaceholderTemplate, providerDisplayName)
    }
}

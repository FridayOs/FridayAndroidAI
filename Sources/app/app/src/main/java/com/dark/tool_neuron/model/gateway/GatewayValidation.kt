package com.dark.tool_neuron.model.gateway

// Pure, Android-free validation for a gateway draft. Mirrors the design's
// _validateAp: API key required when the provider needs one, model required,
// base URL must be http/https and is mandatory for OpenClaw/Hermes/Custom.
// Kept as a pure object so it can be unit-tested off-device.
object GatewayValidation {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason { MISSING_KEY, MISSING_MODEL, MISSING_URL, BAD_URL }

    private val URL_REGEX = Regex("^https?://.+", RegexOption.IGNORE_CASE)

    fun validate(
        provider: GatewayProvider,
        apiKey: String,
        model: String,
        baseUrl: String,
    ): Result {
        val key = apiKey.trim()
        val url = baseUrl.trim()
        val effectiveModel = model.trim().ifBlank { provider.defaultModel }

        if (provider.needsKey && key.isEmpty()) return Result.Invalid(Reason.MISSING_KEY)
        // Local gateways pick an installed on-device model separately, so a model
        // string isn't required here. Cloud providers still need one.
        if (!provider.isLocal && effectiveModel.isBlank()) return Result.Invalid(Reason.MISSING_MODEL)
        if (provider.urlRequired && url.isEmpty()) return Result.Invalid(Reason.MISSING_URL)
        if (url.isNotEmpty() && !URL_REGEX.matches(url)) return Result.Invalid(Reason.BAD_URL)
        return Result.Valid
    }

    fun isValid(provider: GatewayProvider, apiKey: String, model: String, baseUrl: String): Boolean =
        validate(provider, apiKey, model, baseUrl) is Result.Valid
}

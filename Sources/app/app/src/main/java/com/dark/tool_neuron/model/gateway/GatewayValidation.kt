package com.dark.tool_neuron.model.gateway

// Pure so it is unit-testable off-device.
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
        // Local picks an on-device model separately, so no model string required here.
        if (!provider.isLocal && effectiveModel.isBlank()) return Result.Invalid(Reason.MISSING_MODEL)
        if (provider.urlRequired && url.isEmpty()) return Result.Invalid(Reason.MISSING_URL)
        if (url.isNotEmpty() && !URL_REGEX.matches(url)) return Result.Invalid(Reason.BAD_URL)
        return Result.Valid
    }

    fun isValid(provider: GatewayProvider, apiKey: String, model: String, baseUrl: String): Boolean =
        validate(provider, apiKey, model, baseUrl) is Result.Valid
}

package com.dark.tool_neuron.model.gateway

import java.net.URI

// Pure so it is unit-testable off-device.
object GatewayValidation {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason { MISSING_KEY, MISSING_MODEL, MISSING_URL, BAD_URL }

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
        if (url.isNotEmpty() && !isWellFormedHttpUrl(url)) return Result.Invalid(Reason.BAD_URL)
        return Result.Valid
    }

    // java.net.URI rejects fragments without scheme; require explicit http(s) + non-empty host so
    // the regex "^https?://.+" can no longer accept malformed strings like "http://" or "https:// ".
    fun isWellFormedHttpUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (host.isBlank()) return false
        // Host must contain at least one dot OR be a literal IPv4/IPv6 — bare single-label hosts
        // ("http://localhost") are still valid since some providers run on them.
        return host.any { it == '.' || it == ':' } || host.equals("localhost", ignoreCase = true)
    }

    fun isValid(provider: GatewayProvider, apiKey: String, model: String, baseUrl: String): Boolean =
        validate(provider, apiKey, model, baseUrl) is Result.Valid
}

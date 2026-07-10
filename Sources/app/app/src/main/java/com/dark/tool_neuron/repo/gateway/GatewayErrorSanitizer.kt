package com.dark.tool_neuron.repo.gateway

// Turns a raw failure into a short, human-readable, SECRET-FREE summary suitable
// for persisting on a GatewayConfig and showing in the UI. It must never leak the
// API key, a raw Authorization header, or the full endpoint with a query-string
// key (Gemini carries ?key=... in the URL). Pure + Android-free so it is unit
// testable off-device.
object GatewayErrorSanitizer {

    private const val CAP = 200

    // Redact anything that looks like a secret before it reaches storage/logs:
    // bearer tokens, x-api-key values, and Gemini's ?key= query param.
    private val REDACTIONS = listOf(
        Regex("(?i)(authorization\\s*:?\\s*bearer\\s+)\\S+") to "$1***",
        Regex("(?i)(x-api-key\\s*:?\\s*)\\S+") to "$1***",
        Regex("(?i)([?&]key=)[^&\\s\"]+") to "$1***",
        Regex("(?i)(\"?api[_-]?key\"?\\s*[:=]\\s*\"?)[^\",\\s}]+") to "$1***",
        Regex("(?i)\\b(sk|xai|gsk|hf)[-_][A-Za-z0-9_-]{6,}") to "***",
    )

    fun sanitize(raw: String?, key: String = ""): String {
        var out = raw?.trim().orEmpty()
        if (out.isEmpty()) return "Connection failed"
        if (key.isNotBlank()) out = out.replace(key, "***")
        REDACTIONS.forEach { (re, repl) -> out = out.replace(re, repl) }
        out = out.replace(Regex("\\s+"), " ").trim()
        return if (out.length > CAP) out.take(CAP).trimEnd() + "…" else out
    }

    // Map an HTTP status to a stable category so callers get consistent copy
    // regardless of the provider's own wording. The provider body (already
    // redacted) is appended for context.
    fun forHttpStatus(status: Int, body: String?, key: String = ""): String {
        val label = when (status) {
            401, 403 -> "Auth rejected (HTTP $status)"
            404 -> "Model or endpoint not found (HTTP 404)"
            429 -> "Rate limited (HTTP 429)"
            in 500..599 -> "Provider error (HTTP $status)"
            else -> "HTTP $status"
        }
        val detail = sanitize(body, key)
        return if (detail.isBlank() || detail == "Connection failed") label else "$label: $detail"
    }
}

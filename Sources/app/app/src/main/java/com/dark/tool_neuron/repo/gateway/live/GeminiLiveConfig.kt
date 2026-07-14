package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig
import java.net.URI

// Live session config resolved from a user-owned GatewayConfig — key/host stay on-device, never a FRIDAY proxy.
data class GeminiLiveConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
    val locale: String,
    val bargeIn: Boolean,
    val baseHost: String,
    val basePort: Int = DEFAULT_PORT,
    val basePath: String = "",
) {
    // model must be "models/<id>" on the wire.
    val wireModel: String get() = if (model.startsWith("models/")) model else "models/$model"

    companion object {
        const val DEFAULT_HOST = "generativelanguage.googleapis.com"
        // Current documented Gemini Live model on generativelanguage BidiGenerateContent (ai.google.dev docs).
        const val DEFAULT_MODEL = "gemini-3.1-flash-live-preview"
        const val DEFAULT_VOICE = "Aoede"
        const val DEFAULT_PORT = 443

        fun from(config: GatewayConfig, voice: String = DEFAULT_VOICE, locale: String = "en-US", bargeIn: Boolean = true): GeminiLiveConfig {
            val endpoint = parseEndpoint(config.baseUrl)
            return GeminiLiveConfig(
                apiKey = config.apiKey.trim(),
                model = config.model.ifBlank { DEFAULT_MODEL },
                voice = voice.ifBlank { DEFAULT_VOICE },
                locale = locale,
                bargeIn = bargeIn,
                baseHost = endpoint.host,
                basePort = endpoint.port,
                basePath = endpoint.path,
            )
        }

        private data class Endpoint(val host: String, val port: Int, val path: String)

        // Blank/invalid/non-TLS endpoint falls back to the secure Gemini default — key + audio must ride TLS.
        private fun parseEndpoint(baseUrl: String): Endpoint {
            val raw = baseUrl.trim()
            if (raw.isBlank()) return Endpoint(DEFAULT_HOST, DEFAULT_PORT, "")
            val withScheme = if (raw.contains("://")) raw else "https://$raw"
            val uri = runCatching { URI(withScheme) }.getOrNull()
            val scheme = uri?.scheme?.lowercase()
            if (uri?.host.isNullOrBlank() || (scheme != "https" && scheme != "wss")) {
                return Endpoint(DEFAULT_HOST, DEFAULT_PORT, "")
            }
            val port = if (uri.port > 0) uri.port else DEFAULT_PORT
            val path = uri.path?.trimEnd('/').orEmpty()
            return Endpoint(uri.host, port, path)
        }
    }
}

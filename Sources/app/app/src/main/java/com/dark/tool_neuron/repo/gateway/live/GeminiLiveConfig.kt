package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.model.gateway.GatewayConfig

// Live session config resolved from a user-owned GatewayConfig — key/host stay on-device, never a FRIDAY proxy.
data class GeminiLiveConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
    val locale: String,
    val bargeIn: Boolean,
    val baseHost: String,
) {
    // model must be "models/<id>" on the wire.
    val wireModel: String get() = if (model.startsWith("models/")) model else "models/$model"

    companion object {
        const val DEFAULT_HOST = "generativelanguage.googleapis.com"
        const val DEFAULT_MODEL = "gemini-2.0-flash-live-001"
        const val DEFAULT_VOICE = "Aoede"

        fun from(config: GatewayConfig, voice: String = DEFAULT_VOICE, locale: String = "en-US", bargeIn: Boolean = true): GeminiLiveConfig {
            val host = config.baseUrl
                .ifBlank { "https://$DEFAULT_HOST" }
                .substringAfter("://")
                .substringBefore('/')
                .ifBlank { DEFAULT_HOST }
            return GeminiLiveConfig(
                apiKey = config.apiKey.trim(),
                model = config.model.ifBlank { DEFAULT_MODEL },
                voice = voice.ifBlank { DEFAULT_VOICE },
                locale = locale,
                bargeIn = bargeIn,
                baseHost = host,
            )
        }
    }
}

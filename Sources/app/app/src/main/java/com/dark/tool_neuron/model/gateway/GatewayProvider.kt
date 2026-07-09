package com.dark.tool_neuron.model.gateway

enum class GatewayWireFormat { OPENAI, ANTHROPIC, GEMINI }

// User-owned AI gateways. Each connects DIRECTLY from Android — there is no
// FRIDAY API proxy. FRIDAY is a disabled placeholder for a later phase; it
// carries no wire format and cannot be configured or called.
enum class GatewayProvider(
    val displayName: String,
    val wireFormat: GatewayWireFormat,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val enabled: Boolean,
) {
    GEMINI("Gemini", GatewayWireFormat.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.0-flash", true),
    OPENAI("OpenAI", GatewayWireFormat.OPENAI, "https://api.openai.com", "gpt-4o-mini", true),
    ANTHROPIC("Anthropic", GatewayWireFormat.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-5", true),
    DEEPSEEK("DeepSeek", GatewayWireFormat.OPENAI, "https://api.deepseek.com", "deepseek-chat", true),
    OPENCLAW("OpenClaw", GatewayWireFormat.OPENAI, "", "", true),
    HERMES("Hermes", GatewayWireFormat.OPENAI, "", "", true),
    CUSTOM("Custom", GatewayWireFormat.OPENAI, "", "", true),
    FRIDAY("Friday", GatewayWireFormat.OPENAI, "", "", false);

    companion object {
        fun fromId(id: String): GatewayProvider =
            entries.firstOrNull { it.name == id } ?: CUSTOM

        val configurable: List<GatewayProvider> = entries.filter { it.enabled }
    }
}

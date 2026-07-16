package com.dark.tool_neuron.model.gateway

enum class GatewayWireFormat { OPENAI, ANTHROPIC, GEMINI }

// User-owned gateways connect directly from Android; FRIDAY is a disabled, roleless placeholder.
enum class GatewayProvider(
    val displayName: String,
    val wireFormat: GatewayWireFormat,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val enabled: Boolean,
    val needsKey: Boolean,
    val urlRequired: Boolean,
    val isLocal: Boolean,
    val roles: Set<GatewayRole>,
) {
    GEMINI("Gemini", GatewayWireFormat.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.0-flash", true, needsKey = true, urlRequired = false, isLocal = false, roles = setOf(GatewayRole.BRAIN, GatewayRole.VOICE)),
    // OpenAI Realtime (Live voice) is deferred; LiveVoiceAdapter seam/contract is kept, so OpenAI is BRAIN-only until a production Realtime voice adapter ships.
    OPENAI("OpenAI", GatewayWireFormat.OPENAI, "https://api.openai.com", "gpt-4o-mini", true, needsKey = true, urlRequired = false, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    ANTHROPIC("Anthropic", GatewayWireFormat.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-5", true, needsKey = true, urlRequired = false, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    DEEPSEEK("DeepSeek", GatewayWireFormat.OPENAI, "https://api.deepseek.com", "deepseek-chat", true, needsKey = true, urlRequired = false, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    OPENCLAW("OpenClaw", GatewayWireFormat.OPENAI, "", "", true, needsKey = true, urlRequired = true, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    HERMES("Hermes", GatewayWireFormat.OPENAI, "", "", true, needsKey = true, urlRequired = true, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    CUSTOM("Custom", GatewayWireFormat.OPENAI, "", "", true, needsKey = true, urlRequired = true, isLocal = false, roles = setOf(GatewayRole.BRAIN)),
    LOCAL("Local", GatewayWireFormat.OPENAI, "", "", true, needsKey = false, urlRequired = false, isLocal = true, roles = setOf(GatewayRole.BRAIN, GatewayRole.VOICE)),
    FRIDAY("Friday", GatewayWireFormat.OPENAI, "", "", false, needsKey = false, urlRequired = false, isLocal = false, roles = emptySet());

    fun supportsRole(role: GatewayRole): Boolean = role in roles

    companion object {
        fun fromId(id: String): GatewayProvider =
            entries.firstOrNull { it.name == id } ?: CUSTOM

        val configurable: List<GatewayProvider> = entries.filter { it.enabled }

        fun forRole(role: GatewayRole): List<GatewayProvider> =
            entries.filter { it.enabled && it.supportsRole(role) }
    }
}

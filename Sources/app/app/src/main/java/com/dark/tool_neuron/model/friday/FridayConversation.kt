package com.dark.tool_neuron.model.friday

// A Friday gateway conversation and its turns. Persisted HXS-only; never
// uploaded to Firebase / FRIDAY API. gatewayId points at the GatewayConfig
// that produced the replies (kept for continuity; the key itself stays in the
// gateway vault).
data class FridayConversation(
    val id: String,
    val title: String,
    val gatewayId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
)

data class FridayTurn(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val viaVoice: Boolean = false,
)

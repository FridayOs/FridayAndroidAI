package com.dark.tool_neuron.model.context

data class ConversationSummary(
    val conversationId: String,
    val upToTurnId: String,
    val content: String,
    val tokenEstimate: Int,
    val createdAt: Long,
)

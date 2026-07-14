package com.dark.tool_neuron.model.context

// Rolling summary of a conversation's older turns, used to keep Brain Gateway
// packages small. One active summary per conversationId — replace-on-write.
data class ConversationSummary(
    val conversationId: String,
    val upToTurnId: String,
    val content: String,
    val tokenEstimate: Int,
    val createdAt: Long,
)

package com.dark.tool_neuron.model.context

data class RetrievedContext(
    val memories: List<MemoryRecord>,
    val snippets: List<SummarySnippet>,
)

data class SummarySnippet(
    val conversationId: String,
    val chunkIndex: Int,
    val text: String,
    val rank: Double,
)

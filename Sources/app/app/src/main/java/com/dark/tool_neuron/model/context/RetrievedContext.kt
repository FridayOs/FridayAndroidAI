package com.dark.tool_neuron.model.context

// Value objects returned by ContextRetriever. Consumed by the package
// builder (phase 3) — never mutated, never cross-conversation by
// construction (retriever scopes every query to a single chatId: "global"
// for memories, the exact conversationId for summary snippets).
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

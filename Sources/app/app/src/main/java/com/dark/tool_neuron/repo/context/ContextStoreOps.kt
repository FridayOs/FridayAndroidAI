package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.context.MemoryRecord

interface ContextStoreOps {
    fun getMemory(id: String): MemoryRecord?
    fun queryBm25(query: String, chatId: String, topK: Int): List<ContextBm25Hit>
}

data class ContextBm25Hit(
    val docId: String,
    val chatId: String,
    val sourceId: String,
    val chunkIndex: Int,
    val text: String,
    val rank: Double,
)

package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.context.MemoryRecord

// Minimal read seam ContextRetriever depends on so unit tests exercise a
// hand-written fake instead of the real encrypted-vault-backed ContextStore.
// Phase 1 shipped without this interface; phase 2 owns it. Phase 4 wires the
// production ContextStore to implement it (adds a queryBm25 method backed by
// the vault's context_bm25 collection).
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

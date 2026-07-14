package com.dark.tool_neuron.model.context

// Explicit user memory ("remember that I prefer VI voice") or a
// deterministically-extracted fact. Lives in context_store_v1, never in
// friday_store_v1 — memories survive "Clear conversations".
data class MemoryRecord(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val source: MemorySource,
    val locale: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class MemoryCategory {
    FACT,
    PREFERENCE,
    INSTRUCTION,
}

enum class MemorySource {
    EXPLICIT_USER,
    EXTRACTED,
}

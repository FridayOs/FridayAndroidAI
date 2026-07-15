package com.dark.tool_neuron.model.context

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

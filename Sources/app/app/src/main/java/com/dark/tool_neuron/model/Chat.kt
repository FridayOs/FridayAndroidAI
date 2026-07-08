package com.dark.tool_neuron.model

data class Chat(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
    val forkedFromChatId: String? = null,
)

enum class MessageKind {
    Text,
    Image,
    ToolResult,
    // Wide chat-summary card emitted by GGMLEngine.compact. Acts as the
    // single in-context anchor for everything before it; pre-summary
    // messages get [ChatMessage.archivedByCompactId] set to this card's id
    // and are hidden from the model on subsequent generates while still
    // visible (muted) in the UI.
    CompactSummary;

    companion object {
        fun from(id: Int): MessageKind = entries.getOrNull(id) ?: Text
    }
}

data class TextMetrics(
    val tokensPerSecond: Double = 0.0,
    val timeToFirstTokenMs: Long = 0L,
    val totalTimeMs: Long = 0L,
    val promptTokens: Int = 0,
    val generatedTokens: Int = 0,
) {
    companion object
}

data class MemoryMetrics(
    val modelSizeMB: Double = 0.0,
    val contextSizeMB: Double = 0.0,
    val peakMemoryMB: Double = 0.0,
    val currentMemoryMB: Double = 0.0,
    val usagePercent: Double = 0.0,
) {
    companion object
}

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val thinkingContent: String = "",
    val imageUris: List<String> = emptyList(),
    val kind: MessageKind = MessageKind.Text,
    val modelName: String = "",
    val textMetrics: TextMetrics? = null,
    val memoryMetrics: MemoryMetrics? = null,
    val citations: List<Citation> = emptyList(),
    val webSearchRunId: String? = null,
    val webSearchState: String = "",
    // Non-null = this message has been folded into a CompactSummary card
    // and must NOT be included in the model's context window. Kept in
    // storage + visible (muted) in the UI as conversation history.
    val archivedByCompactId: String? = null,
)

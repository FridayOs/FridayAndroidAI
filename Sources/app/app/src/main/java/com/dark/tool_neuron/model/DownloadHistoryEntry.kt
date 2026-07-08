package com.dark.tool_neuron.model

data class DownloadHistoryEntry(
    val id: String,
    val displayName: String,
    val type: String,
    val status: DownloadHistoryStatus,
    val totalBytes: Long,
    val completedAt: Long,
    val error: String? = null,
)

enum class DownloadHistoryStatus { COMPLETED, FAILED, CANCELLED }

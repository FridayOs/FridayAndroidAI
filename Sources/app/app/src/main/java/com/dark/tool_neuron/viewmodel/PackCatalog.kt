package com.dark.tool_neuron.viewmodel

/**
 * Pure, JVM-testable catalog of Model Setup local "pack" definitions (design
 * `pathDefs`/`PACKS` arrays, FRI-582 §7, HTML lines 1206-1210/1495-1536).
 * Extracted out of [ModelStoreViewModel] so pack-id validation is testable
 * without Android instrumentation.
 */
object PackCatalog {
    const val PACK_CHAT_ONLY = "pack_chat_only"
    const val PACK_CHAT_VOICE = "pack_chat_voice"
    const val PACK_LARGE_CHAT_VOICE = "pack_large_chat_voice"

    enum class PackEntryKind { Chat, Voice }
    data class PackEntry(val id: String, val kind: PackEntryKind)

    private val PACK_CONTENTS: Map<String, List<PackEntry>> = mapOf(
        PACK_CHAT_ONLY to listOf(
            PackEntry("lfm25-350m", PackEntryKind.Chat),
        ),
        PACK_CHAT_VOICE to listOf(
            PackEntry("lfm25-350m", PackEntryKind.Chat),
            PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
            PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
        ),
        PACK_LARGE_CHAT_VOICE to listOf(
            PackEntry("qwen3-0.6b", PackEntryKind.Chat),
            PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
            PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
        ),
    )

    /** True when [packId] matches one of the known pack constants. */
    fun isKnownPack(packId: String): Boolean = PACK_CONTENTS.containsKey(packId)

    /** Entries for [packId], or null when the pack is unknown. */
    fun entriesFor(packId: String): List<PackEntry>? = PACK_CONTENTS[packId]
}

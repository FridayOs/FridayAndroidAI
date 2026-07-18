package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.HuggingFaceModel

/**
 * Pure, JVM-testable resolution seam extracted out of
 * [ModelStoreViewModel.downloadPack] (FRI-582 Codex-QA round-2 B5). Takes a
 * model pool snapshot and a pack id and decides the fail-closed
 * [DownloadPackOutcome] WITHOUT enqueuing anything or touching ViewModel
 * state — [ModelStoreViewModel.downloadPack] still owns awaiting the pool
 * and calling [ModelStoreViewModel.downloadModel] for [Resolution.toEnqueue]
 * on [DownloadPackOutcome.Success]. Behavior is unchanged from the original
 * inline logic; only the pure part moved out.
 */
object PackResolver {
    val QUICK_START_QUANT_PRIORITY = listOf("Q4_K_M", "Q4_K_S", "Q4_0", "Q5_K_M", "Q5_K_S", "Q8_0")

    /** [outcome] mirrors what callers observe; [toEnqueue] is the resolved model list for [DownloadPackOutcome.Success], empty otherwise. */
    data class Resolution(val outcome: DownloadPackOutcome, val toEnqueue: List<HuggingFaceModel>)

    fun resolve(pool: List<HuggingFaceModel>, packId: String): Resolution {
        val entries = PackCatalog.entriesFor(packId)
            ?: return Resolution(DownloadPackOutcome.UnknownPack, emptyList())

        val resolutions = entries.map { entry ->
            entry to when (entry.kind) {
                PackCatalog.PackEntryKind.Chat -> resolveChatCandidate(pool, entry.id)
                PackCatalog.PackEntryKind.Voice -> resolveVoiceCandidate(pool, entry.id)
            }
        }
        val missing = resolutions.filter { it.second == null }.map { it.first.id }
        if (missing.isNotEmpty()) return Resolution(DownloadPackOutcome.Missing(missing), emptyList())

        val toEnqueue = resolutions.mapNotNull { it.second }
        return Resolution(DownloadPackOutcome.Success(toEnqueue.map { it.id }), toEnqueue)
    }

    fun resolveChatCandidate(pool: List<HuggingFaceModel>, modelId: String): HuggingFaceModel? {
        val candidates = pool.filter { it.repoId == modelId || it.id.startsWith(modelId) }
        return QUICK_START_QUANT_PRIORITY.firstNotNullOfOrNull { q ->
            candidates.firstOrNull { it.quantization.equals(q, ignoreCase = true) }
        } ?: candidates.filter { it.sizeBytes > 0 }.minByOrNull { it.sizeBytes }
            ?: candidates.firstOrNull()
    }

    fun resolveVoiceCandidate(pool: List<HuggingFaceModel>, modelId: String): HuggingFaceModel? =
        pool.firstOrNull { it.id == modelId }
}

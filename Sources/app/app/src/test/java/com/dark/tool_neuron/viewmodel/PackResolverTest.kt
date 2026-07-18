package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.HuggingFaceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FRI-582 Codex-QA round-2 B5#1: [PackResolver.resolve] is the pure seam
 * extracted out of [ModelStoreViewModel.downloadPack] (unchanged runtime
 * behavior — see that function's delegation). Proves the fail-closed
 * contract on plain JVM without needing a real model pool StateFlow:
 * unknown pack ids, partial/empty pools (no silent Success), and exact
 * enqueued ids for all 3 real catalog packs when the pool has everything.
 */
class PackResolverTest {

    private fun chatModel(repoId: String, quant: String, sizeBytes: Long) = HuggingFaceModel(
        id = "$repoId-$quant",
        name = repoId,
        fileName = "$repoId-$quant.gguf",
        fileUri = "file:///$repoId-$quant.gguf",
        approximateSize = "$sizeBytes",
        sizeBytes = sizeBytes,
        repoId = repoId,
        quantization = quant,
    )

    private fun voiceModel(id: String) = HuggingFaceModel(
        id = id,
        name = id,
        fileName = "$id.onnx",
        fileUri = "file:///$id.onnx",
        approximateSize = "1000000",
        sizeBytes = 1_000_000,
    )

    private val fullPool = listOf(
        chatModel("lfm25-350m", "Q4_K_M", 200_000_000),
        chatModel("qwen3-0.6b", "Q4_K_M", 400_000_000),
        voiceModel("sherpa-onnx-whisper-tiny-en"),
        voiceModel("vits-piper-en_US-amy-low"),
    )

    @Test
    fun unknown_pack_id_returns_UnknownPack_regardless_of_pool() {
        val resolution = PackResolver.resolve(fullPool, "not_a_real_pack")
        assertEquals(DownloadPackOutcome.UnknownPack, resolution.outcome)
        assertTrue(resolution.toEnqueue.isEmpty())
    }

    @Test
    fun empty_pool_yields_Missing_not_Success_for_every_known_pack() {
        listOf(
            PackCatalog.PACK_CHAT_ONLY,
            PackCatalog.PACK_CHAT_VOICE,
            PackCatalog.PACK_LARGE_CHAT_VOICE,
        ).forEach { packId ->
            val resolution = PackResolver.resolve(emptyList(), packId)
            assertTrue(
                "expected Missing for $packId on empty pool, got ${resolution.outcome}",
                resolution.outcome is DownloadPackOutcome.Missing,
            )
            assertTrue(resolution.toEnqueue.isEmpty())
        }
    }

    @Test
    fun partial_pool_missing_voice_entries_yields_Missing_with_exact_missing_ids() {
        // Chat-only pool: PACK_CHAT_VOICE also needs the 2 voice entries.
        val chatOnlyPool = listOf(chatModel("lfm25-350m", "Q4_K_M", 200_000_000))

        val resolution = PackResolver.resolve(chatOnlyPool, PackCatalog.PACK_CHAT_VOICE)

        val missing = resolution.outcome as DownloadPackOutcome.Missing
        assertEquals(
            listOf("sherpa-onnx-whisper-tiny-en", "vits-piper-en_US-amy-low"),
            missing.missingIds,
        )
        assertTrue(resolution.toEnqueue.isEmpty())
    }

    @Test
    fun full_pool_resolves_PACK_CHAT_ONLY_with_exact_enqueued_id() {
        val resolution = PackResolver.resolve(fullPool, PackCatalog.PACK_CHAT_ONLY)

        val success = resolution.outcome as DownloadPackOutcome.Success
        assertEquals(listOf("lfm25-350m-Q4_K_M"), success.enqueued)
        assertEquals(listOf("lfm25-350m-Q4_K_M"), resolution.toEnqueue.map { it.id })
    }

    @Test
    fun full_pool_resolves_PACK_CHAT_VOICE_with_exact_enqueued_ids() {
        val resolution = PackResolver.resolve(fullPool, PackCatalog.PACK_CHAT_VOICE)

        val success = resolution.outcome as DownloadPackOutcome.Success
        assertEquals(
            listOf("lfm25-350m-Q4_K_M", "sherpa-onnx-whisper-tiny-en", "vits-piper-en_US-amy-low"),
            success.enqueued,
        )
        assertEquals(success.enqueued, resolution.toEnqueue.map { it.id })
    }

    @Test
    fun full_pool_resolves_PACK_LARGE_CHAT_VOICE_with_exact_enqueued_ids() {
        val resolution = PackResolver.resolve(fullPool, PackCatalog.PACK_LARGE_CHAT_VOICE)

        val success = resolution.outcome as DownloadPackOutcome.Success
        assertEquals(
            listOf("qwen3-0.6b-Q4_K_M", "sherpa-onnx-whisper-tiny-en", "vits-piper-en_US-amy-low"),
            success.enqueued,
        )
        assertEquals(success.enqueued, resolution.toEnqueue.map { it.id })
    }

    @Test
    fun quant_priority_prefers_Q4_K_M_over_lower_priority_quant_when_both_present() {
        val pool = listOf(
            chatModel("lfm25-350m", "Q8_0", 500_000_000),
            chatModel("lfm25-350m", "Q4_K_M", 200_000_000),
        )

        val resolution = PackResolver.resolve(pool, PackCatalog.PACK_CHAT_ONLY)

        val success = resolution.outcome as DownloadPackOutcome.Success
        assertEquals(listOf("lfm25-350m-Q4_K_M"), success.enqueued)
    }
}

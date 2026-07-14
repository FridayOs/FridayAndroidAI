package com.dark.tool_neuron.repo.context

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.repo.RagChunker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// context_store_v1: signer-bound HXS vault for explicit/extracted memories +
// per-conversation summaries + their BM25 index. Independent of
// friday_store_v1 so "Clear context & memory" never touches transcripts and
// "Clear conversations" never touches memories.
@Singleton
class ContextStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) : ContextEngineStore {
    private val storage = HexStorage()

    private val _memories = MutableStateFlow<List<MemoryRecord>>(emptyList())
    override val memories: StateFlow<List<MemoryRecord>> = _memories.asStateFlow()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted context_store vault")

        storage.ensureCollection(COL_MEMORIES)
        storage.ensureCollection(COL_SUMMARIES)
        storage.ensureCollection(COL_BM25)
        storage.addIndex(COL_MEMORIES, ContextTags.MEM_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_SUMMARIES, ContextTags.SUM_CONVO_ID, HexStorage.WIRE_BYTES)
        refresh()
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    private fun refresh() {
        _memories.value = storage.getAll(COL_MEMORIES)
            .map { it.toMemory() }
            .sortedByDescending { it.updatedAt }
    }

    // ── Memory CRUD ──

    override fun createMemory(
        content: String,
        category: MemoryCategory,
        source: MemorySource,
        locale: String,
    ): MemoryRecord {
        val now = System.currentTimeMillis()
        val memory = MemoryRecord(
            id = UUID.randomUUID().toString(),
            content = content,
            category = category,
            source = source,
            locale = locale,
            createdAt = now,
            updatedAt = now,
        )
        storage.put(COL_MEMORIES, memory.toRecord())
        storage.flush(COL_MEMORIES)
        ingestMemoryBm25(memory)
        refresh()
        return memory
    }

    override fun getMemory(id: String): MemoryRecord? =
        storage.queryString(COL_MEMORIES, ContextTags.MEM_ID, id).firstOrNull()?.toMemory()

    fun getAllMemories(): List<MemoryRecord> = _memories.value

    override fun updateMemory(memory: MemoryRecord) {
        val updated = memory.copy(updatedAt = System.currentTimeMillis())
        storage.queryString(COL_MEMORIES, ContextTags.MEM_ID, updated.id).forEach { storage.delete(COL_MEMORIES, it.id) }
        storage.put(COL_MEMORIES, updated.toRecord())
        storage.flush(COL_MEMORIES)
        ingestMemoryBm25(updated)
        refresh()
    }

    override fun deleteMemory(id: String) {
        storage.queryString(COL_MEMORIES, ContextTags.MEM_ID, id).forEach { storage.delete(COL_MEMORIES, it.id) }
        storage.flush(COL_MEMORIES)
        storage.ragRemoveDocument(COL_BM25, bm25MemoryDocId(id))
        storage.flush(COL_BM25)
        refresh()
    }

    private fun ingestMemoryBm25(memory: MemoryRecord) {
        val docId = bm25MemoryDocId(memory.id)
        storage.ragRemoveDocument(COL_BM25, docId)
        if (memory.content.isBlank()) return
        storage.ragIngest(COL_BM25, docId, BM25_GLOBAL_CHAT_ID, "mem", 0, memory.content)
        storage.flush(COL_BM25)
    }

    // ── BM25 read seam (ContextStoreOps) ──

    override fun queryBm25(query: String, chatId: String, topK: Int): List<ContextBm25Hit> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val records = storage.ragQuery(COL_BM25, query, chatId, topK)
        return records.map { rec ->
            ContextBm25Hit(
                docId = rec.getString(TAG_DOC_ID),
                chatId = rec.getString(TAG_CHAT_ID),
                sourceId = rec.getString(TAG_SOURCE_ID),
                chunkIndex = rec.getInt(TAG_CHUNK_INDEX, 0).toInt(),
                text = rec.getString(TAG_TEXT),
                rank = rec.getDouble(TAG_SCORE, 0.0),
            )
        }
    }

    // ── Summary (replace-on-write, one active per conversation) ──

    override fun getSummary(conversationId: String): ConversationSummary? =
        storage.queryString(COL_SUMMARIES, ContextTags.SUM_CONVO_ID, conversationId).firstOrNull()?.toSummary()

    override fun putSummary(summary: ConversationSummary) {
        storage.queryString(COL_SUMMARIES, ContextTags.SUM_CONVO_ID, summary.conversationId)
            .forEach { storage.delete(COL_SUMMARIES, it.id) }
        storage.put(COL_SUMMARIES, summary.toRecord())
        storage.flush(COL_SUMMARIES)

        val docId = bm25SummaryDocId(summary.conversationId)
        storage.ragRemoveDocument(COL_BM25, docId)
        RagChunker.chunk(summary.content).forEachIndexed { idx, chunk ->
            storage.ragIngest(COL_BM25, docId, summary.conversationId, "sum", idx, chunk)
        }
        storage.flush(COL_BM25)
    }

    // ── Clear operations (independent from friday_store_v1 / gateway_store_v1) ──

    override fun clearContextAndMemory() {
        storage.getAll(COL_MEMORIES).forEach { storage.delete(COL_MEMORIES, it.id) }
        storage.getAll(COL_SUMMARIES).forEach { storage.delete(COL_SUMMARIES, it.id) }
        storage.ragClear(COL_BM25)
        storage.flushAll()
        refresh()
    }

    override fun clearForConversation(conversationId: String) {
        storage.queryString(COL_SUMMARIES, ContextTags.SUM_CONVO_ID, conversationId)
            .forEach { storage.delete(COL_SUMMARIES, it.id) }
        storage.flush(COL_SUMMARIES)
        storage.ragRemoveDocument(COL_BM25, bm25SummaryDocId(conversationId))
        storage.flush(COL_BM25)
    }

    companion object {
        private const val SECURE_DIR = "context_store_v1"
        private const val USER_KEY_INFO = "tn.context.user_key.v2"

        private const val COL_MEMORIES = "context_memories"
        private const val COL_SUMMARIES = "context_summaries"
        private const val COL_BM25 = "context_bm25"

        private const val BM25_GLOBAL_CHAT_ID = "global"

        private const val TAG_DOC_ID = 1
        private const val TAG_CHAT_ID = 2
        private const val TAG_SOURCE_ID = 3
        private const val TAG_CHUNK_INDEX = 4
        private const val TAG_TEXT = 5
        private const val TAG_SCORE = 6

        fun bm25MemoryDocId(memoryId: String) = "mem:$memoryId"
        fun bm25SummaryDocId(conversationId: String) = "sum:$conversationId"
    }
}

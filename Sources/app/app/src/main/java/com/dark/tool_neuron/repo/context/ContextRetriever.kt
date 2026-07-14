package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.context.RetrievedContext
import com.dark.tool_neuron.model.context.SummarySnippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Scoped BM25 lookup: memories are global (chatId="global"), summary
// snippets are scoped to the exact conversationId being asked about. Never
// queries a foreign chatId — that structural scoping is what prevents
// cross-conversation leakage into a context package.
class ContextRetriever(
    private val store: ContextStoreOps,
) {
    suspend fun retrieve(
        query: String,
        conversationId: String,
        maxMemories: Int = DEFAULT_MAX_MEMORIES,
        maxSnippets: Int = DEFAULT_MAX_SNIPPETS,
    ): RetrievedContext = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext RetrievedContext(emptyList(), emptyList())

        val memoryHits = store.queryBm25(query, GLOBAL_CHAT_ID, CANDIDATE_POOL).sortedWith(HIT_ORDER)
        // Blank chatId matches ALL rows in the native BM25 index (rag_keyword
        // treats empty chat_id as no filter) — querying with it would pull
        // every conversation's summary snippets. Skip snippets entirely when
        // there is no concrete conversationId.
        val snippetHits = if (conversationId.isBlank()) emptyList()
        else store.queryBm25(query, conversationId, CANDIDATE_POOL).sortedWith(HIT_ORDER)

        val memories = memoryHits.asSequence()
            .filter { it.docId.startsWith(MEM_DOC_PREFIX) }
            .mapNotNull { hit -> store.getMemory(hit.docId.removePrefix(MEM_DOC_PREFIX)) }
            .distinctBy { it.id }
            .take(maxMemories)
            .toList()

        val snippets = snippetHits.asSequence()
            .map { hit ->
                SummarySnippet(
                    conversationId = hit.chatId,
                    chunkIndex = hit.chunkIndex,
                    text = hit.text,
                    rank = hit.rank,
                )
            }
            .take(maxSnippets)
            .toList()

        RetrievedContext(memories = memories, snippets = snippets)
    }

    companion object {
        const val DEFAULT_MAX_MEMORIES = 4
        const val DEFAULT_MAX_SNIPPETS = 3

        private const val GLOBAL_CHAT_ID = "global"
        private const val MEM_DOC_PREFIX = "mem:"
        private const val CANDIDATE_POOL = 20

        private val HIT_ORDER = compareByDescending<ContextBm25Hit> { it.rank }.thenBy { it.docId }
    }
}

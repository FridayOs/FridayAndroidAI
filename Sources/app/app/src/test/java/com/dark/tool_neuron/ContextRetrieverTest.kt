package com.dark.tool_neuron

import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.repo.context.ContextBm25Hit
import com.dark.tool_neuron.repo.context.ContextRetriever
import com.dark.tool_neuron.repo.context.ContextStoreOps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeContextStoreOps(
    private val hitsByChat: Map<String, List<ContextBm25Hit>>,
    private val memories: Map<String, MemoryRecord> = emptyMap(),
) : ContextStoreOps {
    override fun getMemory(id: String): MemoryRecord? = memories[id]
    override fun queryBm25(query: String, chatId: String, topK: Int): List<ContextBm25Hit> =
        hitsByChat[chatId].orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContextRetrieverTest {

    private fun memory(id: String, content: String) = MemoryRecord(
        id = id,
        content = content,
        category = MemoryCategory.FACT,
        source = MemorySource.EXPLICIT_USER,
        locale = "en",
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun `convo B rows never leak into convo A retrieval even with identical query terms`() = runTest {
        val store = FakeContextStoreOps(
            hitsByChat = mapOf(
                "convo-a" to listOf(
                    ContextBm25Hit("sum:a1", "convo-a", "a1", 0, "convo A snippet", 1.0),
                ),
                "convo-b" to listOf(
                    ContextBm25Hit("sum:b1", "convo-b", "b1", 0, "convo B snippet", 1.0),
                ),
            ),
        )
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "same query", conversationId = "convo-a")

        assertEquals(1, result.snippets.size)
        assertEquals("convo A snippet", result.snippets.single().text)
        assertTrue(result.snippets.none { it.text == "convo B snippet" })
    }

    @Test
    fun `ties in rank are broken deterministically by docId ascending`() = runTest {
        val store = FakeContextStoreOps(
            hitsByChat = mapOf(
                "global" to listOf(
                    ContextBm25Hit("mem:z", "global", "z", 0, "z", rank = 1.0),
                    ContextBm25Hit("mem:a", "global", "a", 0, "a", rank = 1.0),
                    ContextBm25Hit("mem:m", "global", "m", 0, "m", rank = 1.0),
                ),
            ),
            memories = mapOf(
                "z" to memory("z", "Z fact"),
                "a" to memory("a", "A fact"),
                "m" to memory("m", "M fact"),
            ),
        )
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "q", conversationId = "c1")

        assertEquals(listOf("a", "m", "z"), result.memories.map { it.id })
    }

    @Test
    fun `empty index returns empty result never errors`() = runTest {
        val store = FakeContextStoreOps(hitsByChat = emptyMap())
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "anything", conversationId = "c1")

        assertTrue(result.memories.isEmpty())
        assertTrue(result.snippets.isEmpty())
    }

    @Test
    fun `blank query returns empty result without querying the store`() = runTest {
        val store = FakeContextStoreOps(
            hitsByChat = mapOf(
                "global" to listOf(ContextBm25Hit("mem:x", "global", "x", 0, "x", 1.0)),
            ),
        )
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "   ", conversationId = "c1")

        assertTrue(result.memories.isEmpty())
        assertTrue(result.snippets.isEmpty())
    }

    @Test
    fun `results are capped at maxMemories and maxSnippets`() = runTest {
        val memHits = (1..10).map { i ->
            ContextBm25Hit("mem:m$i", "global", "m$i", 0, "m$i", rank = (10 - i).toDouble())
        }
        val snippetHits = (1..10).map { i ->
            ContextBm25Hit("sum:s$i", "c1", "s$i", i, "snippet-$i", rank = (10 - i).toDouble())
        }
        val store = FakeContextStoreOps(
            hitsByChat = mapOf("global" to memHits, "c1" to snippetHits),
            memories = (1..10).associate { "m$it" to memory("m$it", "fact $it") },
        )
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "q", conversationId = "c1", maxMemories = 4, maxSnippets = 3)

        assertEquals(4, result.memories.size)
        assertEquals(3, result.snippets.size)
    }

    @Test
    fun `stale index hits pointing at deleted memories are dropped`() = runTest {
        val store = FakeContextStoreOps(
            hitsByChat = mapOf(
                "global" to listOf(
                    ContextBm25Hit("mem:missing", "global", "missing", 0, "missing", 1.0),
                    ContextBm25Hit("mem:kept", "global", "kept", 0, "kept", 1.0),
                ),
            ),
            memories = mapOf("kept" to memory("kept", "kept fact")),
        )
        val retriever = ContextRetriever(store)

        val result = retriever.retrieve(query = "q", conversationId = "c1")

        assertEquals(listOf("kept"), result.memories.map { it.id })
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextBm25Hit
import com.dark.tool_neuron.repo.context.ContextEngine
import com.dark.tool_neuron.repo.context.ContextEngineStore
import com.dark.tool_neuron.repo.context.ConversationSummarizer
import com.dark.tool_neuron.repo.context.LocalSummaryModel
import com.dark.tool_neuron.repo.context.LocaleSource
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class FakeContextEngineStore(
    private val bm25Hits: Map<String, List<ContextBm25Hit>> = emptyMap(),
    initialMemories: Map<String, MemoryRecord> = emptyMap(),
    private val getSummaryError: (() -> Throwable)? = null,
) : ContextEngineStore {
    private val memoryMap = LinkedHashMap(initialMemories)
    private val _memories = MutableStateFlow(memoryMap.values.toList())
    override val memories: StateFlow<List<MemoryRecord>> get() = _memories

    val summaries = mutableMapOf<String, ConversationSummary>()
    val clearForConversationCalls = mutableListOf<String>()
    var clearContextAndMemoryCalled = false
    var getSummaryThreadName: String? = null

    override fun getMemory(id: String): MemoryRecord? = memoryMap[id]
    override fun queryBm25(query: String, chatId: String, topK: Int): List<ContextBm25Hit> =
        bm25Hits[chatId].orEmpty()

    override fun createMemory(content: String, category: MemoryCategory, source: MemorySource, locale: String): MemoryRecord {
        val record = MemoryRecord(
            id = "m${memoryMap.size + 1}",
            content = content,
            category = category,
            source = source,
            locale = locale,
            createdAt = 1L,
            updatedAt = 1L,
        )
        memoryMap[record.id] = record
        _memories.value = memoryMap.values.toList()
        return record
    }

    override fun updateMemory(memory: MemoryRecord) {
        memoryMap[memory.id] = memory
        _memories.value = memoryMap.values.toList()
    }

    override fun deleteMemory(id: String) {
        memoryMap.remove(id)
        _memories.value = memoryMap.values.toList()
    }

    override fun getSummary(conversationId: String): ConversationSummary? {
        getSummaryThreadName = Thread.currentThread().name
        getSummaryError?.let { throw it() }
        return summaries[conversationId]
    }

    override fun putSummary(summary: ConversationSummary) {
        summaries[summary.conversationId] = summary
    }

    override fun clearContextAndMemory() {
        clearContextAndMemoryCalled = true
        memoryMap.clear()
        _memories.value = emptyList()
        summaries.clear()
    }

    override fun clearForConversation(conversationId: String) {
        clearForConversationCalls += conversationId
        summaries.remove(conversationId)
    }
}

private class FakeFridayConvoStore(
    initialConversations: List<FridayConversation> = emptyList(),
    initialTurns: Map<String, List<FridayTurn>> = emptyMap(),
) : FridayConvoStore {
    private val _conversations = MutableStateFlow(initialConversations)
    override val conversations: StateFlow<List<FridayConversation>> get() = _conversations
    private val turnMap = initialTurns.toMutableMap()
    val deletedIds = mutableListOf<String>()

    override fun refresh() = Unit
    override fun createConversation(gatewayId: String): FridayConversation = throw NotImplementedError("unused by ContextEngine")
    override fun getConversation(id: String): FridayConversation? = _conversations.value.firstOrNull { it.id == id }
    override fun getTurns(conversationId: String): List<FridayTurn> = turnMap[conversationId].orEmpty()
    override fun addTurn(turn: FridayTurn) {
        turnMap[turn.conversationId] = turnMap[turn.conversationId].orEmpty() + turn
    }
    override fun updateTurn(turn: FridayTurn) = Unit
    override fun deleteConversation(id: String) {
        deletedIds += id
        _conversations.value = _conversations.value.filterNot { it.id == id }
        turnMap.remove(id)
    }
}

private class FakeLocaleSource(initial: AppLanguage = AppLanguage.EN) : LocaleSource {
    override val selected: StateFlow<AppLanguage> = MutableStateFlow(initial)
}

private class FakeEngineSummaryModel(
    private val loaded: Boolean,
    private val gate: CompletableDeferred<Unit>? = null,
    private val started: CompletableDeferred<Unit>? = null,
) : LocalSummaryModel {
    val callCount = AtomicInteger(0)
    override fun isLoaded(): Boolean = loaded
    override suspend fun compact(messagesJson: String, maxTokens: Int): String? {
        callCount.incrementAndGet()
        started?.complete(Unit)
        gate?.await()
        return "model summary"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ContextEngineTest {

    private fun turn(id: String, convoId: String, role: String, content: String, ts: Long) = FridayTurn(
        id = id, conversationId = convoId, role = role, content = content, timestamp = ts,
    )

    private fun conversation(id: String) = FridayConversation(
        id = id, title = "title-$id", gatewayId = "g-$id", createdAt = 1L, updatedAt = 1L,
    )

    private fun memoryRecord(id: String, content: String) = MemoryRecord(
        id = id, content = content, category = MemoryCategory.FACT, source = MemorySource.EXPLICIT_USER,
        locale = "en", createdAt = 1L, updatedAt = 1L,
    )

    private fun newEngine(
        store: FakeContextEngineStore,
        convoRepo: FakeFridayConvoStore,
        locale: AppLanguage = AppLanguage.EN,
        summarizerModel: FakeEngineSummaryModel = FakeEngineSummaryModel(loaded = false),
    ): ContextEngine = ContextEngine(
        store = store,
        convoRepo = convoRepo,
        summarizer = ConversationSummarizer(summarizerModel),
        languageController = FakeLocaleSource(locale),
    )

    @Test
    fun `fresh chat with no conversationId still surfaces global memories`() = runTest {
        val store = FakeContextEngineStore(
            bm25Hits = mapOf("global" to listOf(ContextBm25Hit("mem:pref1", "global", "pref1", 0, "likes tea", 5.0))),
            initialMemories = mapOf("pref1" to memoryRecord("pref1", "User likes tea")),
        )
        val engine = newEngine(store, FakeFridayConvoStore())

        val history = engine.buildHistory(
            conversationId = null,
            pendingTurns = listOf(turn("t1", "", "user", "what do I like?", 1L)),
        )

        val systemTurn = history.firstOrNull { it.role == "system" }
        assertNotNull(systemTurn)
        assertTrue(systemTurn!!.content.contains("User likes tea"))
        assertTrue(history.any { it.role == "user" && it.content == "what do I like?" })
    }

    @Test
    fun `store failure during buildHistory degrades without leaking partial retrieval`() = runTest {
        val store = FakeContextEngineStore(
            bm25Hits = mapOf("global" to listOf(ContextBm25Hit("mem:sec1", "global", "sec1", 0, "secret", 9.0))),
            initialMemories = mapOf("sec1" to memoryRecord("sec1", "SECRET_MEMORY_VALUE")),
            getSummaryError = { RuntimeException("vault I/O failure") },
        )
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to listOf(turn("t1", "c1", "user", "hello there", 1L))),
        )
        val engine = newEngine(store, convoRepo)

        val history = engine.buildHistory(conversationId = "c1")

        assertTrue(history.none { it.content.contains("SECRET_MEMORY_VALUE") })
        assertTrue(history.any { it.role == "user" && it.content == "hello there" })
    }

    @Test
    fun `onTurnCompleted single-flights concurrent completions for the same conversation`() {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val model = FakeEngineSummaryModel(loaded = true, gate = gate, started = started)
        val store = FakeContextEngineStore()
        val turns = (1..12).map { i -> turn("t$i", "c1", if (i % 2 == 0) "assistant" else "user", "turn $i", i.toLong()) }
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to turns),
        )
        val engine = newEngine(store, convoRepo, summarizerModel = model)

        runBlocking {
            withTimeout(5000) {
                engine.onTurnCompleted("c1")
                started.await()
                engine.onTurnCompleted("c1") // in-flight -> single-flight skip
                gate.complete(Unit)
                while (store.summaries.isEmpty()) delay(10)
            }
        }

        assertEquals(1, model.callCount.get())
        assertEquals(1, store.summaries.size)
    }

    @Test
    fun `onTurnCompleted never fabricates a summary for a too-short conversation`() {
        val model = FakeEngineSummaryModel(loaded = true)
        val store = FakeContextEngineStore()
        val shortTurns = (1..3).map { i -> turn("t$i", "c1", "user", "turn $i", i.toLong()) }
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to shortTurns),
        )
        val engine = newEngine(store, convoRepo, summarizerModel = model)

        runBlocking {
            engine.onTurnCompleted("c1")
            withTimeout(2000) { delay(200) } // let the fire-and-forget job finish
        }

        assertTrue(store.summaries.isEmpty())
        assertEquals(0, model.callCount.get())
    }

    @Test
    fun `cancelling buildHistory job before it starts leaves no partial state`() = runTest {
        val engine = newEngine(FakeContextEngineStore(), FakeFridayConvoStore())
        var completedResult: List<GatewayTurn>? = null

        val job = launch {
            completedResult = engine.buildHistory(
                conversationId = null,
                pendingTurns = listOf(turn("t1", "", "user", "hi", 1L)),
            )
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertNull(completedResult)
    }

    @Test
    fun `buildHistory result stays within the token budget for a very long conversation`() = runTest {
        val longTurns = (1..80).map { i ->
            turn("t$i", "c1", if (i % 2 == 0) "assistant" else "user", "x".repeat(500), i.toLong())
        }
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to longTurns),
        )
        val engine = newEngine(FakeContextEngineStore(), convoRepo)

        val history = engine.buildHistory(conversationId = "c1")

        val totalChars = history.sumOf { it.content.length }
        val rawTotalChars = longTurns.sumOf { it.content.length }
        assertTrue("expected truncation of an oversized history", totalChars < rawTotalChars)
        assertTrue("history exceeded the token budget bound", totalChars <= ContextEngine.DEFAULT_BUDGET * 4)
    }

    @Test
    fun `buildHistory runs on Dispatchers Default not the caller thread`() = runTest {
        val store = FakeContextEngineStore()
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to listOf(turn("t1", "c1", "user", "hi", 1L))),
        )
        val engine = newEngine(store, convoRepo)
        val callingThread = Thread.currentThread().name

        engine.buildHistory(conversationId = "c1")

        assertNotNull(store.getSummaryThreadName)
        assertTrue(store.getSummaryThreadName!!.contains("DefaultDispatcher"))
        assertTrue(store.getSummaryThreadName != callingThread)
    }

    @Test
    fun `clearAllConversations deletes every conversation but keeps memories intact`() {
        val store = FakeContextEngineStore(initialMemories = mapOf("m1" to memoryRecord("m1", "kept fact")))
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1"), conversation("c2")),
            initialTurns = mapOf("c1" to emptyList(), "c2" to emptyList()),
        )
        val engine = newEngine(store, convoRepo)

        engine.clearAllConversations()

        assertEquals(setOf("c1", "c2"), convoRepo.deletedIds.toSet())
        assertEquals(setOf("c1", "c2"), store.clearForConversationCalls.toSet())
        assertFalse(store.clearContextAndMemoryCalled)
        assertEquals(listOf("kept fact"), store.memories.value.map { it.content })
    }

    @Test
    fun `clearContextAndMemory wipes memories but leaves conversations untouched`() {
        val store = FakeContextEngineStore(initialMemories = mapOf("m1" to memoryRecord("m1", "fact")))
        val convoRepo = FakeFridayConvoStore(initialConversations = listOf(conversation("c1")))
        val engine = newEngine(store, convoRepo)

        engine.clearContextAndMemory()

        assertTrue(store.clearContextAndMemoryCalled)
        assertTrue(store.memories.value.isEmpty())
        assertEquals(listOf("c1"), convoRepo.conversations.value.map { it.id })
        assertTrue(convoRepo.deletedIds.isEmpty())
    }

    @Test
    fun `memory CRUD passes through to the store unchanged`() {
        val engine = newEngine(FakeContextEngineStore(), FakeFridayConvoStore())

        val created = engine.createMemory("likes tea", MemoryCategory.PREFERENCE, MemorySource.EXPLICIT_USER, "en")
        assertEquals(created, engine.getMemory(created.id))

        engine.updateMemory(created.copy(content = "likes coffee"))
        assertEquals("likes coffee", engine.getMemory(created.id)?.content)

        engine.deleteMemory(created.id)
        assertNull(engine.getMemory(created.id))
    }

    @Test
    fun `onUserTurnPersisted creates a memory only when an explicit marker is present`() {
        val store = FakeContextEngineStore()
        val engine = newEngine(store, FakeFridayConvoStore())

        engine.onUserTurnPersisted(turn("t1", "c1", "user", "hello there", 1L))
        assertTrue(store.memories.value.isEmpty())

        engine.onUserTurnPersisted(turn("t2", "c1", "user", "remember that I like tea", 2L))
        assertEquals(1, store.memories.value.size)
        val created = store.memories.value.single()
        assertEquals("I like tea", created.content)
        assertEquals(MemoryCategory.FACT, created.category)
        assertEquals(MemorySource.EXTRACTED, created.source)
    }

    @Test
    fun `buildHistory is deterministic for identical fake inputs`() = runTest {
        val store = FakeContextEngineStore(
            bm25Hits = mapOf("global" to listOf(ContextBm25Hit("mem:m1", "global", "m1", 0, "m1", 1.0))),
            initialMemories = mapOf("m1" to memoryRecord("m1", "stable fact")),
        )
        val convoRepo = FakeFridayConvoStore(
            initialConversations = listOf(conversation("c1")),
            initialTurns = mapOf("c1" to listOf(turn("t1", "c1", "user", "hi", 1L))),
        )
        val engine = newEngine(store, convoRepo)

        val first = engine.buildHistory(conversationId = "c1")
        val second = engine.buildHistory(conversationId = "c1")

        assertEquals(first, second)
    }
}

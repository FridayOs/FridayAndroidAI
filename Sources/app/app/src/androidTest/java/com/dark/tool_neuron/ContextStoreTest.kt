package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.context.ContextStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContextStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    @Before
    fun setup() {
        File(context.filesDir, "context_store_v1").deleteRecursively()
        File(context.filesDir, "friday_store_v1").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        encryptor = HxsEncryptor()
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "context_store_v1").deleteRecursively()
        File(context.filesDir, "friday_store_v1").deleteRecursively()
    }

    private fun newContextStore(): ContextStore =
        ContextStore(context, AppKeyStore(context), encryptor)

    private fun newConvoRepo(): FridayConversationRepository =
        FridayConversationRepository(context, AppKeyStore(context), encryptor)

    @Test
    fun memoryCrudRoundtrip() {
        val store = newContextStore()
        val created = store.createMemory("Prefers dark mode", MemoryCategory.PREFERENCE, MemorySource.EXPLICIT_USER, "en")
        assertEquals(1, store.memories.value.size)
        assertEquals(created.id, store.getMemory(created.id)!!.id)

        val updated = store.getMemory(created.id)!!.copy(content = "Prefers light mode")
        store.updateMemory(updated)
        assertEquals("Prefers light mode", store.getMemory(created.id)!!.content)
        assertEquals(1, store.getAllMemories().size)

        store.deleteMemory(created.id)
        assertNull(store.getMemory(created.id))
        assertTrue(store.memories.value.isEmpty())
    }

    @Test
    fun restartPersistsMemoriesAndSummaries() {
        val storeA = newContextStore()
        val memory = storeA.createMemory("Nhớ rằng tôi thích trà", MemoryCategory.FACT, MemorySource.EXTRACTED, "vi")
        storeA.putSummary(ConversationSummary("convo-1", "turn-9", "Discussed weekend plans.", 42, System.currentTimeMillis()))

        HexStorage().close()

        val storeB = newContextStore()
        assertEquals("memory must persist across restart", 1, storeB.memories.value.size)
        assertEquals(memory.content, storeB.getMemory(memory.id)!!.content)
        assertEquals("summary must persist across restart", "Discussed weekend plans.", storeB.getSummary("convo-1")!!.content)
    }

    @Test
    fun vietnameseContentRoundtripsByteExact() {
        val store = newContextStore()
        val vi = "Tôi thích cà phê sữa đá ☕"
        val created = store.createMemory(vi, MemoryCategory.PREFERENCE, MemorySource.EXPLICIT_USER, "vi")
        assertEquals(vi, store.getMemory(created.id)!!.content)

        HexStorage().close()
        val reopened = newContextStore()
        assertEquals("VI content must survive restart byte-exact", vi, reopened.getMemory(created.id)!!.content)
    }

    @Test
    fun clearContextAndMemoryDoesNotTouchConversationVault() {
        val convoRepo = newConvoRepo()
        convoRepo.createConversation("gw-1")
        assertEquals(1, convoRepo.conversations.value.size)

        val contextStore = newContextStore()
        contextStore.createMemory("Some fact", MemoryCategory.FACT, MemorySource.EXPLICIT_USER, "en")
        contextStore.putSummary(ConversationSummary("convo-1", "turn-1", "Summary text", 10, System.currentTimeMillis()))

        contextStore.clearContextAndMemory()

        assertTrue("context vault must be empty after clear", contextStore.memories.value.isEmpty())
        assertNull(contextStore.getSummary("convo-1"))
        assertEquals(
            "clearContextAndMemory must never touch friday_store_v1",
            1,
            convoRepo.conversations.value.size,
        )
    }

    @Test
    fun clearForConversationRemovesSummaryOnlyKeepsMemories() {
        val store = newContextStore()
        val memory = store.createMemory("Keep this", MemoryCategory.FACT, MemorySource.EXPLICIT_USER, "en")
        store.putSummary(ConversationSummary("convo-2", "turn-4", "Old summary", 5, System.currentTimeMillis()))

        store.clearForConversation("convo-2")

        assertNull(store.getSummary("convo-2"))
        assertEquals("memories must survive clearForConversation", 1, store.memories.value.size)
        assertEquals(memory.id, store.getMemory(memory.id)!!.id)
    }

    // Native rag_keyword tags are fixed by hxs/src/main/cpp/rag_keyword.h: TAG_DOC_ID = 1.
    @Test
    fun bm25GlobalQueryNeverReturnsSummaryRows() {
        val store = newContextStore()
        store.createMemory("cà phê sữa đá là món yêu thích", MemoryCategory.PREFERENCE, MemorySource.EXPLICIT_USER, "vi")
        store.putSummary(ConversationSummary("convo-3", "turn-2", "cà phê sữa đá conversation summary", 8, System.currentTimeMillis()))

        val hits = HexStorage().ragQuery("context_bm25", "cà phê", "global", 10)
        assertTrue("global scope must return at least the memory hit", hits.isNotEmpty())
        assertTrue(
            "global scope must never leak sum: rows",
            hits.none { it.getString(1).startsWith("sum:") },
        )
    }
}

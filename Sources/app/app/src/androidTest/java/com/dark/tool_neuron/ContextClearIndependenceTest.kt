package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.context.ContextEngine
import com.dark.tool_neuron.repo.context.ContextStore
import com.dark.tool_neuron.repo.context.ConversationSummarizer
import com.dark.tool_neuron.repo.context.LocalSummaryModel
import com.dark.tool_neuron.repo.context.LocaleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContextClearIndependenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    private class FakeLocalSummaryModel : LocalSummaryModel {
        override fun isLoaded(): Boolean = false
        override suspend fun compact(messagesJson: String, maxTokens: Int): String? = null
    }

    private class FakeLocaleSource(lang: AppLanguage) : LocaleSource {
        private val flow = MutableStateFlow(lang)
        override val selected: StateFlow<AppLanguage> = flow.asStateFlow()
    }

    @Before
    fun setup() {
        File(context.filesDir, "context_store_v1").deleteRecursively()
        File(context.filesDir, "friday_store_v1").deleteRecursively()
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
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
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
    }

    private fun newConvoRepo(): FridayConversationRepository =
        FridayConversationRepository(context, AppKeyStore(context), encryptor)

    private fun newGatewayRepo(): GatewayConfigRepository =
        GatewayConfigRepository(context, AppKeyStore(context), encryptor)

    private fun newEngine(convoRepo: FridayConversationRepository): ContextEngine =
        ContextEngine(
            store = ContextStore(context, AppKeyStore(context), encryptor),
            convoRepo = convoRepo,
            summarizer = ConversationSummarizer(FakeLocalSummaryModel()),
            languageController = FakeLocaleSource(AppLanguage.EN),
        )

    @Test
    fun clearContextAndMemoryKeepsConversationsAndGateway() = runBlocking {
        val gatewayRepo = newGatewayRepo()
        gatewayRepo.create(GatewayProvider.OPENAI, "Work", "", "sk-secret", "gpt-4o-mini")

        val convoRepo = newConvoRepo()
        val convo = convoRepo.createConversation("gw-1")

        val engine = newEngine(convoRepo)
        engine.createMemory("Prefers dark mode", MemoryCategory.PREFERENCE, MemorySource.EXPLICIT_USER, "en")

        val store2 = ContextStore(context, AppKeyStore(context), encryptor)
        store2.putSummary(ConversationSummary(convo.id, "turn-1", "Summary text", 10, System.currentTimeMillis()))

        engine.clearContextAndMemory()

        assertTrue("memories must be gone after clearContextAndMemory", engine.memories.value.isEmpty())
        assertNull("summaries must be gone after clearContextAndMemory", store2.getSummary(convo.id))
        assertEquals(
            "clearContextAndMemory must never touch friday_store_v1",
            1,
            convoRepo.conversations.value.size,
        )
        assertEquals(
            "clearContextAndMemory must never touch gateway_store_v1",
            1,
            gatewayRepo.gateways.value.size,
        )
    }

    @Test
    fun clearAllConversationsKeepsMemoriesAndGateway() = runBlocking {
        val gatewayRepo = newGatewayRepo()
        gatewayRepo.create(GatewayProvider.OPENAI, "Personal", "", "sk-a", "gpt-4o-mini")

        val convoRepo = newConvoRepo()
        val convo = convoRepo.createConversation("gw-2")

        val engine = newEngine(convoRepo)
        val memory = engine.createMemory("Keep this fact", MemoryCategory.FACT, MemorySource.EXPLICIT_USER, "en")

        val store2 = ContextStore(context, AppKeyStore(context), encryptor)
        store2.putSummary(ConversationSummary(convo.id, "turn-1", "Old summary", 10, System.currentTimeMillis()))

        engine.clearAllConversations()

        assertTrue("conversations must be gone after clearAllConversations", convoRepo.conversations.value.isEmpty())
        assertNull(
            "per-conversation summary must be gone after clearAllConversations",
            store2.getSummary(convo.id),
        )
        assertEquals(
            "global memories must survive clearAllConversations",
            1,
            engine.memories.value.size,
        )
        assertEquals(memory.id, engine.getMemory(memory.id)!!.id)
        assertEquals(
            "clearAllConversations must never touch gateway_store_v1",
            1,
            gatewayRepo.gateways.value.size,
        )
    }
}

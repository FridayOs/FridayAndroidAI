package com.dark.tool_neuron

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConversationRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

// FRI-557 phase 6: full-stack VI memory E2E through the real ContextEngine -
// real ContextStore + real FridayConversationRepository + real
// ContextPackageBuilder, only the local inference model is faked ("fake
// brain": ConversationSummarizer never needs a loaded model for this flow).
// Proves the explicit VI marker "nhớ rằng ..." survives extraction,
// encrypted persistence, a vault restart, and BM25 retrieval back into the
// packaged system turn with diacritics byte-exact.
@RunWith(AndroidJUnit4::class)
class ContextEngineIntegrationTest {

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

    private fun newConvoRepo(): FridayConversationRepository =
        FridayConversationRepository(context, AppKeyStore(context), encryptor)

    private fun newEngine(convoRepo: FridayConversationRepository): ContextEngine =
        ContextEngine(
            store = ContextStore(context, AppKeyStore(context), encryptor),
            convoRepo = convoRepo,
            summarizer = ConversationSummarizer(FakeLocalSummaryModel()),
            languageController = FakeLocaleSource(AppLanguage.VI),
        )

    @Test
    fun vietnameseMarkerMemorySurvivesRestartAndRetrievesIntoPackage() = runBlocking {
        val viMessage = "nhớ rằng tôi tên là Hùng"

        // ── session A: extract + persist ──
        val convoRepoA = newConvoRepo()
        val convo = convoRepoA.createConversation("gw-vi")
        val turn = FridayTurn(
            id = UUID.randomUUID().toString(),
            conversationId = convo.id,
            role = "user",
            content = viMessage,
            timestamp = System.currentTimeMillis(),
        )
        convoRepoA.addTurn(turn)

        val engineA = newEngine(convoRepoA)
        engineA.onUserTurnPersisted(turn)

        assertEquals("marker extraction must fire exactly once", 1, engineA.memories.value.size)
        assertEquals("tôi tên là Hùng", engineA.memories.value.first().content)

        // ── restart: close encrypted vaults, reopen fresh instances ──
        HexStorage().close()

        val convoRepoB = newConvoRepo()
        val engineB = newEngine(convoRepoB)

        assertEquals("memory must persist across restart", 1, engineB.memories.value.size)
        assertEquals(
            "VI diacritics must survive restart byte-exact",
            "tôi tên là Hùng",
            engineB.memories.value.first().content,
        )

        // ── retrieval: build the minimal package sent to the gateway ──
        val history = engineB.buildHistory(convo.id)

        assertTrue("package must include a system preamble turn", history.isNotEmpty())
        val systemTurn = history.first()
        assertEquals("system", systemTurn.role)
        assertTrue(
            "retrieved memory must appear in the system turn with diacritics intact, was: ${systemTurn.content}",
            systemTurn.content.contains("tôi tên là Hùng"),
        )
    }
}

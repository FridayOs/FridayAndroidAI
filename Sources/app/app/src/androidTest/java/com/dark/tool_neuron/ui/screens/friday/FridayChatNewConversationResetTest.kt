package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.ChatBrain
import com.dark.tool_neuron.repo.gateway.GatewayDirectory
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import com.friday.ai.R
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 B2 (round-3 re-review): the shell/UI regression Codex QA required. The VM-level
 * composerResetSignal counter test (FridayChatViewModelTest) only proves the seam bumps; it
 * does NOT prove the production screen wiring clears the composer draft, messages, error,
 * and in-flight flags when a "New conversation" fires from either entry point.
 *
 * This hosts the REAL FridayChatScreen with a REAL FridayChatViewModel constructed from
 * androidTest-local fakes (no Hilt / no @HiltAndroidTest infra in this repo), wrapped in a
 * plain MaterialTheme so the production composables' MaterialTheme.colorScheme/typography
 * resolve. LocalFridayAccent falls back to its staticCompositionLocal default (SUN). The
 * screen's hiltViewModel() default is bypassed by passing the fake-built VM explicitly, so
 * no ViewModelStoreOwner / Hilt wiring is needed.
 *
 * Both production reset paths are driven and asserted:
 *  - Sidebar: ActiveConversationStore.requestNewChat() — the EXACT command AppScaffold's
 *    onNewConversation CTA runs (AppScaffold.kt:205) — which the VM's init collector turns
 *    into newChat(), which bumps composerResetSignal, which the screen's LaunchedEffect
 *    observes to clear the local draft. Proves the sidebar action goes through the real
 *    production command, not a navigation side-effect.
 *  - Header: the header's new-chat affordance (friday_cd_new_chat) -> viewModel::newChat,
 *    the same command the sidebar routes into.
 *
 * Composer-content is asserted via the placeholder Text (FridayChatComposer shows
 * friday_chat_placeholder only when value.isEmpty()), not by reading BasicTextField text
 * directly — placeholder presence/absence is a rock-solid signal that the draft was entered
 * and then cleared, without depending on BasicTextField text-semantics behavior.
 *
 * Device-run note: compiled against the androidTest source set; execution needs an
 * emulator/device (no adb in the build env) — same limitation noted in prior handoffs.
 */
@RunWith(AndroidJUnit4::class)
class FridayChatNewConversationResetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val store = FakeConvoStore()
    private val contextEngine = FakeContextEngine()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context().getString(id)

    private fun readyBrain() = GatewayConfig(
        id = "g1",
        provider = GatewayProvider.OPENAI,
        label = "Work",
        baseUrl = "",
        apiKey = "sk-x",
        model = "gpt-4o-mini",
        createdAt = 0L,
        updatedAt = 0L,
        status = GatewayStatus.READY,
    )

    // androidTest-local fakes mirroring FridayChatViewModelTest's JVM fakes. The concrete
    // repos (GatewayConfigRepository / FridayConversationRepository) pull in HXS + AppKeyStore
    // construction in init, so they are not instantiable here — the interface seams let the
    // real VM run against in-memory fakes instead.
    private class FakeConvoStore : FridayConvoStore {
        val convo = FridayConversation("c1", "t", "g1", 0L, 0L, 0)
        val turnsMap = mutableMapOf<String, MutableList<FridayTurn>>()
        override val conversations: StateFlow<List<FridayConversation>> =
            MutableStateFlow(listOf(convo)).asStateFlow()
        override fun refresh() {}
        override fun createConversation(gatewayId: String) = convo
        override fun getConversation(id: String) = convo
        override fun getTurns(conversationId: String): List<FridayTurn> =
            turnsMap[conversationId].orEmpty().toList()
        override fun addTurn(turn: FridayTurn) {
            turnsMap.getOrPut(turn.conversationId) { mutableListOf() }.add(turn)
        }
        override fun updateTurn(turn: FridayTurn) {
            val list = turnsMap.getOrPut(turn.conversationId) { mutableListOf() }
            val i = list.indexOfFirst { it.id == turn.id }
            if (i >= 0) list[i] = turn else list.add(turn)
        }
        override fun deleteConversation(id: String) {}
    }

    private class FakeGateways(private val brainProvider: () -> GatewayConfig?) : GatewayDirectory {
        override val gateways: StateFlow<List<GatewayConfig>> =
            MutableStateFlow(brainProvider()?.let { listOf(it) } ?: emptyList()).asStateFlow()
        override val brainSelection: StateFlow<String> = MutableStateFlow("").asStateFlow()
        override fun brainGateway(): GatewayConfig? = brainProvider()
        override fun voiceGateway(): GatewayConfig? = null
    }

    private class FakeChatBrain(private val flowFor: (List<GatewayTurn>) -> Flow<GatewayEvent>) : ChatBrain {
        var brain: GatewayConfig? = null
        override fun brainGateway(): GatewayConfig? = brain
        override fun brainUnavailableMessage(): String = "no brain"
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = flowFor(history)
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = flowFor(history)
        override fun brainCancel() {}
    }

    private class FakeContextEngine : ContextHistorySource {
        override suspend fun buildHistory(
            conversationId: String?,
            pendingTurns: List<FridayTurn>?,
        ): List<GatewayTurn> = (pendingTurns ?: emptyList()).map { GatewayTurn(it.role, it.content) }
        override fun onUserTurnPersisted(turn: FridayTurn) {}
        override fun onTurnCompleted(conversationId: String) {}
    }

    private fun hostScreen(
        activeStore: DefaultActiveConversationStore = DefaultActiveConversationStore(),
        flowFor: (List<GatewayTurn>) -> Flow<GatewayEvent>,
    ): Pair<FridayChatViewModel, DefaultActiveConversationStore> {
        val brain = FakeChatBrain(flowFor).apply { brain = readyBrain() }
        val gateways = FakeGateways { readyBrain() }
        val vm = FridayChatViewModel(gateways, store, brain, contextEngine, activeStore)
        composeTestRule.setContent {
            MaterialTheme {
                FridayChatScreen(
                    innerPadding = PaddingValues(0.dp),
                    onOpenMenu = {},
                    onToVoice = {},
                    onOpenProviderSelector = {},
                    conversationId = null,
                    viewModel = vm,
                )
            }
        }
        return vm to activeStore
    }

    // Sidebar path: a streaming send leaves inFlight=true + a partial assistant bubble, the
    // composer draft is typed, then the SIDEBAR production command (requestNewChat) fires.
    // Asserts the draft, messages, and in-flight all reset through the real production wiring.
    @Test
    fun sidebarNewConversation_clearsComposerDraft_messages_andInFlight() {
        val (vm, activeStore) = hostScreen(
            flowFor = {
                flow { emit(GatewayEvent.Delta("partial answer")); awaitCancellation() }
            },
        )

        // Send a real message so a user + partial assistant bubble are on screen and inFlight
        // is true (the stream suspends on awaitCancellation, so Stop stays visible).
        composeTestRule.onNodeWithTag("friday_chat_composer_input").performTextInput("realmsg1")
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_send)).performClick()
        composeTestRule.waitUntil(5_000L) { vm.inFlight.value && vm.messages.value.size >= 2 }
        composeTestRule.waitForIdle()

        // The send cleared the composer (placeholder back). Type a fresh draft that must NOT
        // survive New conversation: placeholder disappears while the draft is present.
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertExists()
        composeTestRule.onNodeWithTag("friday_chat_composer_input").performTextInput("draftforget1")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_stop)).assertExists()

        // Sidebar's production command — the exact call AppScaffold.onNewConversation runs.
        activeStore.requestNewChat()
        composeTestRule.waitUntil(5_000L) { vm.messages.value.isEmpty() && !vm.inFlight.value }
        composeTestRule.waitForIdle()

        // Draft cleared: the empty composer shows its placeholder again.
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertExists()
        // Messages + in-flight reset: empty-state greeting reappears, Stop row + bubbles gone.
        composeTestRule.onNodeWithTag("friday_chat_empty_state").assertExists()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_stop)).assertDoesNotExist()
        composeTestRule.onNodeWithText("realmsg1").assertDoesNotExist()
        assertFalse(vm.inFlight.value)
        assertTrue(vm.messages.value.isEmpty())

        vm.viewModelScope.cancel()
    }

    // Sidebar path, error banner: a send that errors leaves a user bubble + error banner; the
    // draft is typed; requestNewChat must clear the banner + draft + messages.
    @Test
    fun sidebarNewConversation_clearsErrorBanner_andDraft_andMessages() {
        val (vm, activeStore) = hostScreen(flowFor = { flow { emit(GatewayEvent.Error("boom1")) } })

        composeTestRule.onNodeWithTag("friday_chat_composer_input").performTextInput("realmsg3")
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_send)).performClick()
        composeTestRule.waitUntil(5_000L) { vm.error.value != null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("boom1").assertExists()
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertExists()
        composeTestRule.onNodeWithTag("friday_chat_composer_input").performTextInput("draftforget3")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertDoesNotExist()

        activeStore.requestNewChat()
        composeTestRule.waitUntil(5_000L) { vm.messages.value.isEmpty() && vm.error.value == null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("boom1").assertDoesNotExist()
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertExists()
        composeTestRule.onNodeWithText("realmsg3").assertDoesNotExist()
        composeTestRule.onNodeWithTag("friday_chat_empty_state").assertExists()
        assertNull(vm.error.value)
        assertTrue(vm.messages.value.isEmpty())

        vm.viewModelScope.cancel()
    }

    // Header path: a pre-opened conversation shows history bubbles; the draft is typed; the
    // header's new-chat affordance (friday_cd_new_chat -> viewModel::newChat) must clear the
    // draft and drop the history so the empty-state greeting reappears.
    @Test
    fun headerNewConversationButton_clearsDraft_andHistoryMessages() {
        store.turnsMap["c1"] = mutableListOf(
            FridayTurn("u1", "c1", "user", "seed-hello", 1L),
            FridayTurn("a1", "c1", "assistant", "seed-world", 2L),
        )
        val (vm, _) = hostScreen(flowFor = { flow { emit(GatewayEvent.Done("done")) } })
        // open() is synchronous: seeds messages from the store before the screen observes them.
        vm.open("c1")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("seed-hello").assertExists()
        composeTestRule.onNodeWithTag("friday_chat_composer_input").performTextInput("draftforget2")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertDoesNotExist()

        // Header's new-chat affordance -> viewModel::newChat (the command the sidebar routes into).
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_new_chat)).performClick()
        composeTestRule.waitUntil(5_000L) { vm.messages.value.isEmpty() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.friday_chat_placeholder)).assertExists()
        composeTestRule.onNodeWithText("seed-hello").assertDoesNotExist()
        composeTestRule.onNodeWithTag("friday_chat_empty_state").assertExists()
        assertTrue(vm.messages.value.isEmpty())

        vm.viewModelScope.cancel()
    }
}

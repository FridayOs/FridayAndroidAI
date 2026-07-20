package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.ChatBrain
import com.dark.tool_neuron.repo.gateway.GatewayDirectory
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Public regenerate() through fakes: exact brainContinue history, replace/create persistence, only-assistant no-op, cancel/Error preserve the stored answer with no partial write.
@OptIn(ExperimentalCoroutinesApi::class)
class FridayChatViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeConvoStore : FridayConvoStore {
        val convo = FridayConversation("c1", "t", "g1", 0, 0, 2)
        val turnsMap = mutableMapOf<String, MutableList<FridayTurn>>()
        val added = mutableListOf<FridayTurn>()
        val updated = mutableListOf<FridayTurn>()
        override val conversations = MutableStateFlow(listOf(convo))
        override fun refresh() {}
        override fun createConversation(gatewayId: String) = convo
        override fun getConversation(id: String) = convo
        override fun getTurns(conversationId: String): List<FridayTurn> = turnsMap[conversationId].orEmpty().toList()
        override fun addTurn(turn: FridayTurn) { turnsMap.getOrPut(turn.conversationId) { mutableListOf() }.add(turn); added += turn }
        override fun updateTurn(turn: FridayTurn) {
            val list = turnsMap.getOrPut(turn.conversationId) { mutableListOf() }
            val i = list.indexOfFirst { it.id == turn.id }
            if (i >= 0) list[i] = turn else list.add(turn)
            updated += turn
        }
        override fun deleteConversation(id: String) {}
    }

    // Configurable via constructor so hasGateway/hasReadyGateway tests can mutate the list and
    // brain-gateway lookup independently of ChatBrain's own gateway (see FakeChatBrain.brain).
    private class FakeGateways(
        initial: List<GatewayConfig> = emptyList(),
        private val brainProvider: () -> GatewayConfig? = { null },
    ) : GatewayDirectory {
        val gatewaysFlow = MutableStateFlow(initial)
        override val gateways: StateFlow<List<GatewayConfig>> = gatewaysFlow.asStateFlow()
        // Backs brainSelection independently of gatewaysFlow — selectBrain() below mutates this
        // alone, mirroring GatewayConfigRepository's real selectBrain()/brainId behavior, so
        // tests can prove hasReadyGateway/hasBrainSelected recompute on selection change alone.
        val brainSelectionFlow = MutableStateFlow("")
        override val brainSelection: StateFlow<String> = brainSelectionFlow.asStateFlow()
        override fun brainGateway(): GatewayConfig? = brainProvider()
        override fun voiceGateway(): GatewayConfig? = null
        fun selectBrain(id: String) { brainSelectionFlow.value = id }
    }

    private class FakeChatBrain(
        var continueFlow: (List<GatewayTurn>) -> Flow<GatewayEvent>,
    ) : ChatBrain {
        val continueHistories = mutableListOf<List<GatewayTurn>>()
        var brain: GatewayConfig? = GatewayConfig("g1", GatewayProvider.OPENAI, "L", "", "sk-x", "gpt-4o-mini", 0, 0)
        var cancelCalled = false
        override fun brainGateway(): GatewayConfig? = brain
        override fun brainUnavailableMessage(): String = "no brain"
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = continueFlow(history)
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
            continueHistories += history
            return continueFlow(history)
        }
        override fun brainCancel() { cancelCalled = true }
    }

    private class FakeContextEngine(private val store: FridayConvoStore) : ContextHistorySource {
        val persistedTurns = mutableListOf<FridayTurn>()
        val completedConversations = mutableListOf<String>()
        override suspend fun buildHistory(conversationId: String?, pendingTurns: List<FridayTurn>?): List<GatewayTurn> =
            (pendingTurns ?: conversationId?.let { store.getTurns(it) }.orEmpty()).map { GatewayTurn(it.role, it.content) }
        override fun onUserTurnPersisted(turn: FridayTurn) { persistedTurns += turn }
        override fun onTurnCompleted(conversationId: String) { completedConversations += conversationId }
    }

    private fun user(id: String, text: String) = FridayTurn(id, "c1", "user", text, id.hashCode().toLong())
    private fun assistant(id: String, text: String) = FridayTurn(id, "c1", "assistant", text, id.hashCode().toLong())

    // 4-component data class (not Triple) so existing `val (vm, store, brain) = vmWith(...)` call
    // sites keep destructuring the first 3 fields unmodified, while new tests can also read
    // .activeConversationStore. gateways/activeConversationStore are defaulted so trailing-lambda
    // call syntax (`vmWith(turns) { ... }`) stays valid for every pre-existing call site.
    private data class VmFixture(
        val vm: FridayChatViewModel,
        val store: FakeConvoStore,
        val brain: FakeChatBrain,
        val activeConversationStore: ActiveConversationStore,
    )

    private fun vmWith(
        turns: List<FridayTurn>,
        gateways: FakeGateways = FakeGateways(),
        activeConversationStore: ActiveConversationStore = DefaultActiveConversationStore(),
        flowFor: (List<GatewayTurn>) -> Flow<GatewayEvent>,
    ): VmFixture {
        val store = FakeConvoStore().apply { turnsMap["c1"] = turns.toMutableList() }
        val brain = FakeChatBrain(flowFor)
        val vm = FridayChatViewModel(gateways, store, brain, FakeContextEngine(store), activeConversationStore)
        return VmFixture(vm, store, brain, activeConversationStore)
    }

    @Test
    fun regenerate_sendsHistoryExcludingReplacedAnswer_andReplacesInPlace() = runTest {
        val (vm, store, brain) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
        ) { flow { emit(GatewayEvent.Delta("new")); emit(GatewayEvent.Done("new answer")) } }

        vm.open("c1")
        vm.regenerate()

        assertEquals(listOf(GatewayTurn("user", "hi")), brain.continueHistories.single())
        assertEquals("new answer", vm.messages.value.first { it.id == "a1" }.text)
        assertEquals("replace persists in place", "new answer", store.updated.single().content)
        assertEquals("a1", store.updated.single().id)
        assertTrue("replace path never adds a turn", store.added.isEmpty())
        assertFalse(vm.thinking.value)
    }

    @Test
    fun regenerate_withoutPriorAssistant_createsAndPersistsFreshTurn() = runTest {
        val (vm, store, _) = vmWith(
            listOf(user("u1", "hi")),
        ) { flow { emit(GatewayEvent.Done("fresh")) } }

        vm.open("c1")
        vm.regenerate()

        assertEquals("create path persists a new assistant turn", "fresh", store.added.single().content)
        assertEquals("assistant", store.added.single().role)
        assertTrue("create path never updates in place", store.updated.isEmpty())
    }

    @Test
    fun regenerate_onlyAssistant_isExplicitNoOp() = runTest {
        val (vm, store, brain) = vmWith(
            listOf(assistant("a1", "lonely")),
        ) { flow { emit(GatewayEvent.Done("should not happen")) } }

        vm.open("c1")
        vm.regenerate()

        assertTrue("only-assistant regenerate must not reach the brain", brain.continueHistories.isEmpty())
        assertTrue(store.updated.isEmpty())
        assertTrue(store.added.isEmpty())
        assertFalse(vm.thinking.value)
    }

    @Test
    fun regenerate_errorPreservesStoredAnswer_withNoPartialWrite() = runTest {
        val (vm, store, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
        ) { flow { emit(GatewayEvent.Delta("partial")); emit(GatewayEvent.Error("boom")) } }

        vm.open("c1")
        vm.regenerate()

        assertEquals("bubble restored to the stored answer on error", "old", vm.messages.value.first { it.id == "a1" }.text)
        assertEquals("boom", vm.error.value)
        assertTrue("error path never persists a partial", store.updated.isEmpty() && store.added.isEmpty())
        assertFalse(vm.thinking.value)
    }

    @Test
    fun regenerate_cancelledByNewChat_resetsThinking_dropsFreshBubble_noPartialWrite() = runTest {
        val (vm, store, _) = vmWith(
            listOf(user("u1", "hi")),
        ) { flow { emit(GatewayEvent.Delta("partial")); awaitCancellation() } }

        vm.open("c1")
        vm.regenerate()
        assertEquals("partial", vm.messages.value.first { !it.isUser }.text)

        vm.newChat()

        assertFalse("cancellation resets thinking", vm.thinking.value)
        assertTrue("newChat clears to an empty conversation", vm.messages.value.isEmpty())
        assertTrue("cancelled regenerate never persists a partial", store.updated.isEmpty() && store.added.isEmpty())
    }

    @Test
    fun regenerate_cancelledByReopen_restoresStoredAnswer_noPartialWrite() = runTest {
        val (vm, store, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
        ) { flow { emit(GatewayEvent.Delta("partial")); awaitCancellation() } }

        vm.open("c1")
        vm.regenerate()
        assertEquals("partial", vm.messages.value.first { it.id == "a1" }.text)

        vm.open("c1")

        assertFalse(vm.thinking.value)
        assertEquals("reopen restores the stored answer, not the partial", "old", vm.messages.value.first { it.id == "a1" }.text)
        assertTrue("cancelled regenerate never persists a partial", store.updated.isEmpty() && store.added.isEmpty())
    }

    // send() has no try/finally around its collect (unlike regenerate()), so cancelling it must
    // leave the partial bubble exactly as the last Delta wrote it — no restore/removal.
    @Test
    fun cancel_duringSend_stopsStream_keepsPartialBubble_callsBrainCancel() = runTest {
        val (vm, store, brain, _) = vmWith(emptyList()) {
            flow { emit(GatewayEvent.Delta("partial")); awaitCancellation() }
        }

        vm.send("hello")

        assertEquals("partial", vm.messages.value.last { !it.isUser }.text)
        assertTrue("user turn persisted before the stream starts", store.added.any { it.role == "user" && it.content == "hello" })
        assertFalse("brainCancel not yet called before cancel()", brain.cancelCalled)

        vm.cancel()

        assertTrue("cancel() must call router.brainCancel()", brain.cancelCalled)
        assertFalse(vm.thinking.value)
        assertEquals(
            "send()'s partial bubble is kept as-is on cancel (no finally/restore in the send path)",
            "partial",
            vm.messages.value.last { !it.isUser }.text,
        )
        assertTrue("no assistant turn persisted — Done never fired", store.added.none { it.role == "assistant" })
    }

    // hasReadyGateway is driven by gatewayRepo (FakeGateways), a separate fake from the ChatBrain
    // used by send()/regenerate() — must not be conflated with FakeChatBrain.brain.
    @Test
    fun hasReadyGateway_trueOnlyWhenBrainGatewayIsReady() = runTest {
        var current: GatewayConfig? = null
        val gateways = FakeGateways(brainProvider = { current })
        val (vm, _, _, _) = vmWith(emptyList(), gateways = gateways) { flow { } }

        assertFalse("no brain gateway configured -> not ready", vm.hasReadyGateway.value)
        assertFalse("no gateway at all -> hasGateway false", vm.hasGateway.value)

        current = GatewayConfig("g1", GatewayProvider.OPENAI, "L", "", "sk", "gpt-4o-mini", 0, 0, status = GatewayStatus.NOT_TESTED)
        gateways.gatewaysFlow.value = listOf(current!!)
        assertFalse("configured but NOT_TESTED -> hasReadyGateway false", vm.hasReadyGateway.value)
        assertTrue("configured (any status) -> hasGateway true", vm.hasGateway.value)

        current = current!!.copy(status = GatewayStatus.FAILED)
        gateways.gatewaysFlow.value = listOf(current!!)
        assertFalse("FAILED brain -> hasReadyGateway false", vm.hasReadyGateway.value)

        current = current!!.copy(status = GatewayStatus.READY)
        gateways.gatewaysFlow.value = listOf(current!!)
        assertTrue("READY brain -> hasReadyGateway true", vm.hasReadyGateway.value)
    }

    // Regression for the stale-flag bug: selectBrain() only mutates brainSelection, never the
    // gateways list, so hasReadyGateway/hasBrainSelected must recompute off brainSelection alone.
    @Test
    fun hasReadyGateway_and_hasBrainSelected_reactToBrainSelectionAlone() = runTest {
        val ready = GatewayConfig("g1", GatewayProvider.OPENAI, "L1", "", "sk", "gpt-4o-mini", 0, 0, status = GatewayStatus.READY)
        val failed = GatewayConfig("g2", GatewayProvider.OPENAI, "L2", "", "sk", "gpt-4o-mini", 0, 0, status = GatewayStatus.FAILED)
        var current: GatewayConfig? = ready
        val gateways = FakeGateways(initial = listOf(ready, failed), brainProvider = { current })
        val (vm, _, _, _) = vmWith(emptyList(), gateways = gateways) { flow { } }

        assertTrue("READY brain selected -> hasReadyGateway true", vm.hasReadyGateway.value)
        assertTrue("brain selected -> hasBrainSelected true", vm.hasBrainSelected.value)

        // Only brainSelection changes here — gatewaysFlow.value is untouched — proving the
        // flags are reactive to selection, not merely to the gateway list.
        current = failed
        gateways.selectBrain(failed.id)

        assertFalse("switched to FAILED brain -> hasReadyGateway false", vm.hasReadyGateway.value)
        assertTrue("still selected (just FAILED) -> hasBrainSelected true", vm.hasBrainSelected.value)
    }

    // VM-level guard for the no-provider path: router.brainGateway() == null (fully absent, not
    // merely not-READY) short-circuits send() with brainUnavailableMessage() and persists nothing.
    // The "route to the provider selector" behavior itself lives in FridayChatScreen (Phase 5 UI).
    @Test
    fun send_withNoBrainConfigured_setsErrorAndPersistsNothing() = runTest {
        val (vm, store, brain, _) = vmWith(emptyList()) { flow { emit(GatewayEvent.Done("should not run")) } }
        brain.brain = null

        vm.send("hello")

        assertEquals("no brain", vm.error.value)
        assertFalse(vm.thinking.value)
        assertTrue("no user turn persisted when brain is unavailable", store.added.isEmpty())
        assertTrue("no message added", vm.messages.value.isEmpty())
    }

    // ActiveConversationStore is the cross-screen (Voice<->Chat) continuity seam: init's
    // auto-open, explicit open(), newChat(), and send()'s create-on-first-message path must all
    // write through it.
    @Test
    fun activeConversationStore_writesOnInitOpen_newChat_andSendCreatedConversation() = runTest {
        val activeStore = DefaultActiveConversationStore()
        // FRI-574 phase 7 (B2): init no longer auto-opens the first conversation — that path
        // clobbered an in-progress Voice session. Seed the open state explicitly so the rest of
        // the cross-screen continuity story (open / newChat / send-created convo) can still be
        // exercised here.
        activeStore.set("c1")
        val (vm, _, _, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
            activeConversationStore = activeStore,
        ) {
            flow { emit(GatewayEvent.Done("ok")) }
        }

        assertEquals("init preserves the seeded active id", "c1", activeStore.activeConversationId.value)

        vm.newChat()
        assertNull("newChat clears the active id", activeStore.activeConversationId.value)

        vm.open("c1")
        assertEquals("explicit open() records the id", "c1", activeStore.activeConversationId.value)

        vm.newChat()
        assertNull(activeStore.activeConversationId.value)

        vm.send("hi")
        assertEquals("send() creating a new conversation records its id", "c1", activeStore.activeConversationId.value)
    }

    // FRI-574 phase 7 (B2): the sidebar's "New conversation" request is just an event on the
    // shared store. Ticking the counter must drive the VM's newChat() (clear messages, clear
    // active id, clear inFlight) without the screen having to know about it.
    @Test
    fun newChatRequest_resetsVmAndClearsActive() = runTest {
        val activeStore = DefaultActiveConversationStore()
        val (vm, _, _, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
            activeConversationStore = activeStore,
        ) { flow { emit(GatewayEvent.Done("ok")) } }

        // B2 removed the init-time auto-open, so the VM starts empty even though the
        // FakeConvoStore has history; that's the point — opening is now explicit, which
        // matches the real "user navigated here" intent.
        assertNull("init does not auto-open the first conversation", activeStore.activeConversationId.value)
        assertTrue(vm.messages.value.isEmpty())

        vm.open("c1")
        assertEquals("c1", activeStore.activeConversationId.value)
        assertEquals(2, vm.messages.value.size)

        activeStore.requestNewChat()

        assertNull("store request clears the active id", activeStore.activeConversationId.value)
        assertTrue("VM messages reset by the newChatRequest collector", vm.messages.value.isEmpty())
        assertFalse(vm.inFlight.value)
        assertNull(vm.error.value)
    }

    // FRI-574 B2 re-review: the composer draft lives as screen-local state, so the VM exposes a
    // monotonic composerResetSignal the screen observes to clear it. Prove BOTH new-chat entry
    // points bump it: the header's newChat() and the sidebar's requestNewChat() (routed through
    // newChat() by the newChatRequests collector). The shell/UI regression (draft -> drawer ->
    // New conversation -> empty composer) lives in the androidTest suite; this locks the seam.
    @Test
    fun composerResetSignal_bumpsOnHeaderNewChat_andSidebarRequest() = runTest {
        val activeStore = DefaultActiveConversationStore()
        val (vm, _, _, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
            activeConversationStore = activeStore,
        ) { flow { emit(GatewayEvent.Done("ok")) } }

        val initial = vm.composerResetSignal.value

        // Header path: direct newChat() call.
        vm.newChat()
        val afterHeader = vm.composerResetSignal.value
        assertTrue("header newChat() must bump the composer reset signal", afterHeader > initial)

        // Sidebar path: requestNewChat() routes through the newChatRequests collector -> newChat().
        activeStore.requestNewChat()
        assertTrue(
            "sidebar requestNewChat() must also bump the composer reset signal",
            vm.composerResetSignal.value > afterHeader,
        )
    }

    // FRI-574 phase 7 (B1): inFlight stays true the entire send→Done/Error window so a second
    // concurrent send cannot fire while the previous assistant answer is still streaming, the
    // Stop row stays visible the entire time, and cancel() flips both flags and routes through
    // brainCancel() without leaving the partial bubble behind.
    @Test
    fun inFlight_blocksSecondSendUntilCancel() = runTest {
        val (vm, store, brain, _) = vmWith(emptyList()) {
            flow { emit(GatewayEvent.Delta("partial")); awaitCancellation() }
        }

        vm.send("first")

        // First Delta clears `thinking` (typing-bubble done) but `inFlight` stays true so
        // the composer cannot fire a second message and Stop stays visible.
        assertFalse("thinking clears on first Delta", vm.thinking.value)
        assertTrue("inFlight still true mid-stream", vm.inFlight.value)
        assertEquals(1, store.added.count { it.role == "user" })
        assertEquals("partial", vm.messages.value.last { !it.isUser }.text)

        vm.send("second")
        assertEquals(
            "second concurrent send is suppressed while inFlight is true",
            1,
            store.added.count { it.role == "user" },
        )

        vm.cancel()
        assertFalse(vm.inFlight.value)
        assertTrue("cancel() must call router.brainCancel()", brain.cancelCalled)
        assertEquals(
            "partial bubble preserved on cancel — no restore in send()",
            "partial",
            vm.messages.value.last { !it.isUser }.text,
        )
    }

    // FRI-574 round-5 BLOCKER 2: openIfExists restores the persisted active conversation
    // when it still lives in the repo, and clears the stale persisted id when it doesn't
    // (process restart may rehydrate an id for a since-deleted conversation).
    @Test
    fun openIfExists_opensAndReturnsTrue_whenConversationExists() = runTest {
        val activeStore = DefaultActiveConversationStore()
        val (vm, store, _, _) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
            activeConversationStore = activeStore,
        ) { flow { emit(GatewayEvent.Done("ok")) } }

        assertTrue("existing conversation opens", vm.openIfExists("c1"))
        assertEquals("c1", activeStore.activeConversationId.value)
        assertEquals("turns are hydrated from the repo", 2, vm.messages.value.size)
        assertEquals("hi", vm.messages.value.first { it.isUser }.text)
    }

    @Test
    fun openIfExists_returnsFalseAndClearsActive_whenConversationMissing() = runTest {
        // A FridayConvoStore whose getConversation reports the id as gone (deleted) —
        // mirrors a stale persisted id rehydrated after process death.
        val missingStore = object : FridayConvoStore {
            override val conversations = MutableStateFlow<List<FridayConversation>>(emptyList())
            override fun refresh() {}
            override fun createConversation(gatewayId: String) =
                FridayConversation("c1", "t", "g1", 0, 0, 0)
            override fun getConversation(id: String): FridayConversation? = null
            override fun getTurns(conversationId: String): List<FridayTurn> = emptyList()
            override fun addTurn(turn: FridayTurn) {}
            override fun updateTurn(turn: FridayTurn) {}
            override fun deleteConversation(id: String) {}
        }
        val activeStore = DefaultActiveConversationStore().apply { set("ghost") }
        val brain = FakeChatBrain { flow { emit(GatewayEvent.Done("ok")) } }
        val vm = FridayChatViewModel(
            FakeGateways(),
            missingStore,
            brain,
            FakeContextEngine(missingStore),
            activeStore,
        )

        assertFalse("missing conversation does not open", vm.openIfExists("ghost"))
        assertNull("stale persisted id is cleared so it won't retry", activeStore.activeConversationId.value)
        assertTrue(vm.messages.value.isEmpty())
    }
}

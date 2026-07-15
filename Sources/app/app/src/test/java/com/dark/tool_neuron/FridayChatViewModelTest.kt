package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakeGateways : GatewayDirectory {
        override val gateways: StateFlow<List<GatewayConfig>> = MutableStateFlow(emptyList())
        override fun brainGateway(): GatewayConfig? = null
        override fun voiceGateway(): GatewayConfig? = null
    }

    private class FakeChatBrain(
        var continueFlow: (List<GatewayTurn>) -> Flow<GatewayEvent>,
    ) : ChatBrain {
        val continueHistories = mutableListOf<List<GatewayTurn>>()
        var brain: GatewayConfig? = GatewayConfig("g1", GatewayProvider.OPENAI, "L", "", "sk-x", "gpt-4o-mini", 0, 0)
        override fun brainGateway(): GatewayConfig? = brain
        override fun brainUnavailableMessage(): String = "no brain"
        override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = continueFlow(history)
        override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> {
            continueHistories += history
            return continueFlow(history)
        }
        override fun brainCancel() {}
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

    private fun vmWith(
        turns: List<FridayTurn>,
        flowFor: (List<GatewayTurn>) -> Flow<GatewayEvent>,
    ): Triple<FridayChatViewModel, FakeConvoStore, FakeChatBrain> {
        val store = FakeConvoStore().apply { turnsMap["c1"] = turns.toMutableList() }
        val brain = FakeChatBrain(flowFor)
        val vm = FridayChatViewModel(FakeGateways(), store, brain, FakeContextEngine(store))
        return Triple(vm, store, brain)
    }

    @Test
    fun regenerate_sendsHistoryExcludingReplacedAnswer_andReplacesInPlace() = runTest {
        val (vm, store, brain) = vmWith(
            listOf(user("u1", "hi"), assistant("a1", "old")),
        ) { flow { emit(GatewayEvent.Delta("new")); emit(GatewayEvent.Done("new answer")) } }

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

        vm.regenerate()
        assertEquals("partial", vm.messages.value.first { it.id == "a1" }.text)

        vm.open("c1")

        assertFalse(vm.thinking.value)
        assertEquals("reopen restores the stored answer, not the partial", "old", vm.messages.value.first { it.id == "a1" }.text)
        assertTrue("cancelled regenerate never persists a partial", store.updated.isEmpty() && store.added.isEmpty())
    }
}

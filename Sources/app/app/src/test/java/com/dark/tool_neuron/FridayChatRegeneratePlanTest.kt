package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure planRegeneration tests: history sent to brainContinue excludes the answer being replaced.
class FridayChatRegeneratePlanTest {

    private fun user(text: String) = FridayTurn(
        id = "u-" + text, conversationId = "c", role = "user", content = text, timestamp = 0,
    )

    private fun assistant(id: String, text: String, ts: Long = 1) = FridayTurn(
        id = id, conversationId = "c", role = "assistant", content = text, timestamp = ts,
    )

    @Test
    fun regenerate_excludesAssistantBeingReplaced() {
        val turns = listOf(
            user("hi"),
            assistant("a1", "old answer"),
        )
        val (history, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals("history must exclude the old assistant answer", listOf(GatewayTurn("user", "hi")), history)
        assertEquals("a1", existing?.id)
    }

    @Test
    fun regenerate_multiTurn_dropsFromLastAssistantOnward() {
        val turns = listOf(
            user("q1"),
            assistant("a1", "answer 1"),
            user("q2"),
            assistant("a2", "answer 2"),
        )
        val (history, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals(
            listOf(GatewayTurn("user", "q1"), GatewayTurn("assistant", "answer 1"), GatewayTurn("user", "q2")),
            history,
        )
        assertEquals("a2", existing?.id)
    }

    @Test
    fun regenerate_withoutAssistant_returnsFullHistoryAndCreatesFresh() {
        val turns = listOf(user("hi"), user("again"))
        val (history, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals(listOf(GatewayTurn("user", "hi"), GatewayTurn("user", "again")), history)
        assertEquals(null, existing)
    }

    @Test
    fun regenerateEmptyConversationStaysEmpty() {
        val (history, existing) = FridayChatViewModel.planRegeneration(emptyList())
        assertTrue(history.isEmpty())
        assertEquals(null, existing)
    }

    @Test
    fun regenerate_onlyAssistantKeepsEmptyHistory() {
        val turns = listOf(assistant("a1", "lonely"))
        val (history, existing) = FridayChatViewModel.planRegeneration(turns)
        assertTrue("history without the assistant answer must be empty", history.isEmpty())
        assertEquals("a1", existing?.id)
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.viewmodel.FridayChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val (pending, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals("pending turns must exclude the old assistant answer", listOf(user("hi")), pending)
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
        val (pending, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals(
            listOf(user("q1"), assistant("a1", "answer 1"), user("q2")),
            pending,
        )
        assertEquals("a2", existing?.id)
    }

    @Test
    fun regenerate_withoutAssistant_returnsFullHistoryAndCreatesFresh() {
        val turns = listOf(user("hi"), user("again"))
        val (pending, existing) = FridayChatViewModel.planRegeneration(turns)
        assertEquals(listOf(user("hi"), user("again")), pending)
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

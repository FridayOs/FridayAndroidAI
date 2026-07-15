package com.dark.tool_neuron

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.model.context.PackageInput
import com.dark.tool_neuron.model.context.SummarySnippet
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.context.ContextPackageBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPackageBuilderTest {

    private fun turn(id: String, role: String, content: String, ts: Long) =
        FridayTurn(id = id, conversationId = "c1", role = role, content = content, timestamp = ts)

    private fun memory(id: String, content: String, createdAt: Long) = MemoryRecord(
        id = id,
        content = content,
        category = MemoryCategory.FACT,
        source = MemorySource.EXPLICIT_USER,
        locale = "en",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun baseInput(
        turns: List<FridayTurn> = emptyList(),
        memories: List<MemoryRecord> = emptyList(),
        snippets: List<SummarySnippet> = emptyList(),
        summary: ConversationSummary? = null,
        locale: AppLanguage = AppLanguage.EN,
        tokenBudget: Int = 4096,
    ) = PackageInput(turns, memories, snippets, summary, locale, tokenBudget)

    @Test
    fun `usedTokens never exceeds the declared token budget`() {
        val turns = (1..20).map { i ->
            turn(id = "t$i", role = if (i % 2 == 1) "user" else "assistant", content = "message body $i ".repeat(10), ts = i.toLong())
        }
        val memories = (1..5).map { memory(id = "m$it", content = "fact number $it ".repeat(5), createdAt = it.toLong()) }
        val summary = ConversationSummary("c1", "t0", "older conversation summary ".repeat(20), 0, 0L)
        val snippets = (1..5).map { SummarySnippet(conversationId = "c1", chunkIndex = it, text = "snippet $it ".repeat(10), rank = 1.0) }

        for (budget in listOf(300, 800, 1500, 4096)) {
            val input = baseInput(turns, memories, snippets, summary, tokenBudget = budget)
            val pkg = ContextPackageBuilder.build(input)
            assertTrue("usedTokens=${pkg.usedTokens} budget=$budget", pkg.usedTokens <= budget)
        }
    }

    @Test
    fun `build is deterministic for identical input`() {
        val turns = (1..14).map { i -> turn("t$i", if (i % 2 == 1) "user" else "assistant", "content-$i", i.toLong()) }
        val input = baseInput(turns = turns, memories = listOf(memory("m1", "likes tea", 10L)))

        val first = ContextPackageBuilder.build(input)
        val second = ContextPackageBuilder.build(input)

        assertEquals(first, second)
    }

    @Test
    fun `memories are ordered by createdAt then id ascending`() {
        val memories = listOf(
            memory(id = "mem-b", content = "BBB", createdAt = 100L),
            memory(id = "mem-a", content = "AAA", createdAt = 100L),
        )
        val input = baseInput(memories = memories)

        val pkg = ContextPackageBuilder.build(input)
        val systemContent = pkg.history.first { it.role == "system" }.content

        assertTrue(systemContent.indexOf("AAA") < systemContent.indexOf("BBB"))
    }

    @Test
    fun `system block trims snippets before summary when over its 40 percent cap`() {
        val memories = listOf(memory(id = "m1", content = "M", createdAt = 1L))
        val summary = ConversationSummary("c1", "t0", "S".repeat(300), 0, 0L)
        val snippets = listOf(SummarySnippet(conversationId = "c1", chunkIndex = 0, text = "N".repeat(3000), rank = 1.0))
        val input = baseInput(memories = memories, summary = summary, snippets = snippets, tokenBudget = 2024)

        val pkg = ContextPackageBuilder.build(input)
        val systemContent = pkg.history.first { it.role == "system" }.content

        assertTrue("expected summary marker retained", systemContent.contains("S".repeat(300)))
        assertFalse("expected snippet marker trimmed first", systemContent.contains("N"))
    }

    @Test
    fun `system turn is omitted entirely when trimming still cannot fit the budget`() {
        val memories = listOf(memory(id = "m1", content = "M".repeat(200), createdAt = 1L))
        val summary = ConversationSummary("c1", "t0", "S".repeat(200), 0, 0L)
        val snippets = listOf(SummarySnippet(conversationId = "c1", chunkIndex = 0, text = "N".repeat(200), rank = 1.0))
        val input = baseInput(memories = memories, summary = summary, snippets = snippets, tokenBudget = 1034)

        val pkg = ContextPackageBuilder.build(input)

        assertTrue(pkg.history.none { it.role == "system" })
    }

    @Test
    fun `last user turn is always present and truncated when it alone overflows budget`() {
        val turns = listOf(
            turn("t1", "assistant", "an older short message", ts = 1L),
            turn("t2", "user", "x".repeat(5000), ts = 2L),
        )
        val input = baseInput(turns = turns, tokenBudget = 1074)

        val pkg = ContextPackageBuilder.build(input)

        assertEquals(1, pkg.history.size)
        val forced = pkg.history.single()
        assertEquals("user", forced.role)
        assertTrue(forced.content.length < 5000)
        assertEquals(200, forced.content.length)
        assertTrue(pkg.truncated)
    }

    @Test
    fun `VI locale adds the Vietnamese preamble line to the system turn`() {
        val input = baseInput(memories = listOf(memory("m1", "thich tra", 1L)), locale = AppLanguage.VI)

        val pkg = ContextPackageBuilder.build(input)
        val systemContent = pkg.history.first { it.role == "system" }.content

        assertTrue(systemContent.contains("Trả lời bằng tiếng Việt"))
    }

    @Test
    fun `empty memories summary and snippets yield raw turn window only`() {
        val turns = (1..4).map { i -> turn("t$i", if (i % 2 == 1) "user" else "assistant", "msg-$i", i.toLong()) }
        val input = baseInput(turns = turns)

        val pkg = ContextPackageBuilder.build(input)

        assertTrue(pkg.history.none { it.role == "system" })
        assertEquals(turns.map { it.role to it.content }, pkg.history.map { it.role to it.content })
    }

    @Test
    fun `output never contains data absent from the input (minimal-context contract)`() {
        val includedMemory = memory("m1", "INCLUDED_FACT", 1L)
        val input = baseInput(memories = listOf(includedMemory))

        val pkg = ContextPackageBuilder.build(input)
        val systemContent = pkg.history.first { it.role == "system" }.content

        assertTrue(systemContent.contains("INCLUDED_FACT"))
        assertFalse(systemContent.contains("EXCLUDED_FACT_NEVER_PASSED_IN"))
    }
}

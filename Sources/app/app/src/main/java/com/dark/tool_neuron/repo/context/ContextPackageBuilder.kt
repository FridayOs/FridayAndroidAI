package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.context.ContextPackage
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.PackageInput
import com.dark.tool_neuron.model.context.SummarySnippet
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.gateway.GatewayTurn

internal fun estimateTokens(s: String): Int = (s.length + 3) / 4

object ContextPackageBuilder {

    private const val RESPONSE_RESERVE = 1024
    private const val SYSTEM_BLOCK_RATIO = 0.4
    private const val ROLE_SYSTEM = "system"
    private const val ROLE_USER = "user"
    private const val CHARS_PER_TOKEN = 4

    fun build(input: PackageInput): ContextPackage {
        val available = (input.tokenBudget - RESPONSE_RESERVE).coerceAtLeast(0)
        val systemBudget = (available * SYSTEM_BLOCK_RATIO).toInt()

        val (systemContent, systemTokens) = buildSystemBlock(input, systemBudget)
        val historyBudget = (available - systemTokens).coerceAtLeast(0)
        val raw = buildRawWindow(input.turns, historyBudget)

        val history = buildList {
            if (systemContent != null) add(GatewayTurn(ROLE_SYSTEM, systemContent))
            addAll(raw.turns)
        }

        return ContextPackage(
            history = history,
            usedTokens = systemTokens + raw.tokens,
            truncated = raw.truncated,
        )
    }


    private fun buildSystemBlock(input: PackageInput, budget: Int): Pair<String?, Int> {
        val preamble = ContextPrompts.localePreamble(input.locale)
        var memoriesBlock = memoriesBlock(input.memories)
        var summaryBlock = input.summary?.content?.trim()?.takeIf { it.isNotEmpty() }
        var snippetsBlock = snippetsBlock(input.snippets)

        fun assemble(): String? {
            val parts = listOfNotNull(preamble, memoriesBlock, summaryBlock, snippetsBlock)
            return if (parts.isEmpty()) null else parts.joinToString("\n\n")
        }

        if (estimateTokens(assemble().orEmpty()) > budget) snippetsBlock = null
        if (estimateTokens(assemble().orEmpty()) > budget) summaryBlock = null
        if (estimateTokens(assemble().orEmpty()) > budget) memoriesBlock = null

        val content = assemble() ?: return null to 0
        return content to estimateTokens(content)
    }

    private fun memoriesBlock(memories: List<MemoryRecord>): String? {
        if (memories.isEmpty()) return null
        val ordered = memories.sortedWith(compareBy({ it.createdAt }, { it.id }))
        return buildString {
            append(ContextPrompts.KNOWN_FACTS_HEADER)
            ordered.forEach { append('\n').append("- ").append(it.content) }
        }
    }

    private fun snippetsBlock(snippets: List<SummarySnippet>): String? {
        if (snippets.isEmpty()) return null
        return snippets.joinToString("\n") { "- ${it.text}" }
    }


    private class RawWindow(val turns: List<GatewayTurn>, val tokens: Int, val truncated: Boolean)

    private fun buildRawWindow(turns: List<FridayTurn>, budget: Int): RawWindow {
        if (turns.isEmpty()) return RawWindow(emptyList(), 0, false)

        val lastUserTurn = turns.lastOrNull { it.role == ROLE_USER }

        val selected = mutableListOf<FridayTurn>()
        var used = 0
        for (i in turns.indices.reversed()) {
            val turn = turns[i]
            val tokens = estimateTokens(turn.content)
            if (used + tokens > budget) break
            selected.add(0, turn)
            used += tokens
        }

        var truncated = false
        val hasLastUser = lastUserTurn != null && selected.any { it.id == lastUserTurn.id }
        if (lastUserTurn != null && !hasLastUser) {
            val remaining = (budget - used).coerceAtLeast(0)
            val charBudget = remaining * CHARS_PER_TOKEN
            val content = lastUserTurn.content
            val fitted = if (content.length > charBudget) {
                truncated = true
                content.take(charBudget)
            } else {
                content
            }
            val forced = lastUserTurn.copy(content = fitted)
            val insertAt = selected.indexOfFirst { it.timestamp > forced.timestamp }
                .let { if (it == -1) selected.size else it }
            selected.add(insertAt, forced)
            used += estimateTokens(fitted)
        }

        return RawWindow(selected.map { GatewayTurn(it.role, it.content) }, used, truncated)
    }
}

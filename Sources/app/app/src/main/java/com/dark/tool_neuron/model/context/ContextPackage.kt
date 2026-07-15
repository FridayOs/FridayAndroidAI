package com.dark.tool_neuron.model.context

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.gateway.GatewayTurn


data class PackageInput(
    val turns: List<FridayTurn>,
    val memories: List<MemoryRecord>,
    val snippets: List<SummarySnippet>,
    val summary: ConversationSummary?,
    val locale: AppLanguage,
    val tokenBudget: Int,
)

data class ContextPackage(
    val history: List<GatewayTurn>,
    val usedTokens: Int,
    val truncated: Boolean,
)

sealed interface SummaryResult {
    data object NotNeeded : SummaryResult
    data class Success(val summary: ConversationSummary, val viaModel: Boolean) : SummaryResult
}

package com.dark.tool_neuron.model.context

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.gateway.GatewayTurn

// SummarySnippet is defined in RetrievedContext.kt (phase 2's retriever
// output: conversationId, chunkIndex, text, rank) and reused here.

// Pure input to ContextPackageBuilder.build. Caller (ContextEngine, phase 4)
// gathers everything up front - the builder itself does no I/O, reads no
// clock, and touches no singleton, so it only ever "sees" what is passed in
// here (minimal-context-only guarantee).
data class PackageInput(
    val turns: List<FridayTurn>,
    val memories: List<MemoryRecord>,
    val snippets: List<SummarySnippet>,
    val summary: ConversationSummary?,
    val locale: AppLanguage,
    val tokenBudget: Int,
)

// Deterministic, token-bounded gateway history ready to send to the active
// Brain Gateway. Never contains more than the caller-declared tokenBudget of
// content (see ContextPackageBuilder.RESPONSE_RESERVE for the reply reserve).
data class ContextPackage(
    val history: List<GatewayTurn>,
    val usedTokens: Int,
    val truncated: Boolean,
)

// Result of ConversationSummarizer.summarize. NotNeeded means the
// conversation is short enough that raw turns alone are enough context.
sealed interface SummaryResult {
    data object NotNeeded : SummaryResult
    data class Success(val summary: ConversationSummary, val viaModel: Boolean) : SummaryResult
}

package com.dark.tool_neuron.repo.context

import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.SummaryResult
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

interface LocalSummaryModel {
    fun isLoaded(): Boolean
    suspend fun compact(messagesJson: String, maxTokens: Int): String?
}

@Singleton
class InferenceSummaryModel @Inject constructor() : LocalSummaryModel {

    override fun isLoaded(): Boolean = InferenceClient.isModelLoaded.value

    override suspend fun compact(messagesJson: String, maxTokens: Int): String? {
        val builder = StringBuilder()
        var failed = false
        InferenceClient.compactConversation(messagesJson, maxTokens)
            .transformWhile { event ->
                emit(event)
                event !is InferenceEvent.Done && event !is InferenceEvent.Error
            }
            .collect { event ->
                when (event) {
                    is InferenceEvent.Token -> builder.append(event.text)
                    is InferenceEvent.Error -> failed = true
                    else -> {}
                }
            }
        if (failed) return null
        return builder.toString().trim().ifBlank { null }
    }
}

class ConversationSummarizer @Inject constructor(
    private val model: LocalSummaryModel,
) {

    suspend fun summarize(turns: List<FridayTurn>, locale: AppLanguage): SummaryResult {
        if (turns.size < MIN_TURNS_TO_SUMMARIZE) return SummaryResult.NotNeeded

        val conversationId = turns.last().conversationId
        val upToTurnId = turns.last().id
        val now = System.currentTimeMillis()

        if (model.isLoaded()) {
            val messagesJson = buildMessagesJson(turns, locale)
            val modelSummary = withTimeoutOrNull(ContextPrompts.SUMMARIZE_TIMEOUT_MS) {
                model.compact(messagesJson, ContextPrompts.SUMMARIZE_MAX_TOKENS)
            }
            if (!modelSummary.isNullOrBlank()) {
                val content = modelSummary.trim()
                return SummaryResult.Success(
                    summary = ConversationSummary(
                        conversationId = conversationId,
                        upToTurnId = upToTurnId,
                        content = content,
                        tokenEstimate = estimateTokens(content),
                        createdAt = now,
                    ),
                    viaModel = true,
                )
            }
        }

        val digest = fallbackDigest(turns, locale)
        return SummaryResult.Success(
            summary = ConversationSummary(
                conversationId = conversationId,
                upToTurnId = upToTurnId,
                content = digest,
                tokenEstimate = estimateTokens(digest),
                createdAt = now,
            ),
            viaModel = false,
        )
    }

    private fun buildMessagesJson(turns: List<FridayTurn>, locale: AppLanguage): String {
        val arr = JSONArray()
        turns.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        arr.put(JSONObject().put("role", ROLE_USER).put("content", ContextPrompts.summarizePrompt(locale)))
        return arr.toString()
    }

    private fun fallbackDigest(turns: List<FridayTurn>, locale: AppLanguage): String {
        val firstUser = turns.firstOrNull { it.role == ROLE_USER }
        val lastExchanges = turns.takeLast(EXCHANGE_TURN_COUNT)

        val excerpts = LinkedHashSet<String>()
        firstUser?.let { excerpts.add(excerpt(it.content)) }
        lastExchanges.forEach { excerpts.add(excerpt(it.content)) }

        val prefix = ContextPrompts.fallbackPrefix(locale)
        return "$prefix ${excerpts.joinToString(" ")}"
    }

    private fun excerpt(content: String): String = content.trim().take(EXCERPT_CHARS)

    companion object {
        const val MIN_TURNS_TO_SUMMARIZE = 12
        private const val EXCHANGE_TURN_COUNT = 4 // last 2 exchanges = 2 user + 2 assistant turns
        private const val EXCERPT_CHARS = 160
        private const val ROLE_USER = "user"
    }
}

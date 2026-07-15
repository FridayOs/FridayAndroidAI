package com.dark.tool_neuron.repo.context

import android.util.Log
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.model.context.ContextPackage
import com.dark.tool_neuron.model.context.ConversationSummary
import com.dark.tool_neuron.model.context.MemoryCategory
import com.dark.tool_neuron.model.context.MemoryRecord
import com.dark.tool_neuron.model.context.MemorySource
import com.dark.tool_neuron.model.context.PackageInput
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.context.SummaryResult
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface ContextHistorySource {
    suspend fun buildHistory(conversationId: String?, pendingTurns: List<FridayTurn>? = null): List<GatewayTurn>
    fun onUserTurnPersisted(turn: FridayTurn)
    fun onTurnCompleted(conversationId: String)
}

interface ContextEngineStore : ContextStoreOps {
    val memories: StateFlow<List<MemoryRecord>>
    fun createMemory(content: String, category: MemoryCategory, source: MemorySource, locale: String): MemoryRecord
    fun updateMemory(memory: MemoryRecord)
    fun deleteMemory(id: String)
    fun getSummary(conversationId: String): ConversationSummary?
    fun putSummary(summary: ConversationSummary)
    fun clearContextAndMemory()
    fun clearForConversation(conversationId: String)
}

interface LocaleSource {
    val selected: StateFlow<AppLanguage>
}

@Singleton
class ContextEngine @Inject constructor(
    private val store: ContextEngineStore,
    private val convoRepo: FridayConvoStore,
    private val summarizer: ConversationSummarizer,
    private val languageController: LocaleSource,
) : ContextHistorySource {

    private val retriever = ContextRetriever(store)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val summarizeJobs = ConcurrentHashMap<String, Job>()

    val memories: StateFlow<List<MemoryRecord>> get() = store.memories

    override suspend fun buildHistory(
        conversationId: String?,
        pendingTurns: List<FridayTurn>?,
    ): List<GatewayTurn> = withContext(Dispatchers.Default) {
        try {
            buildPackage(conversationId, pendingTurns).history
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "buildHistory degraded: ${e.javaClass.simpleName}")
            degradedPackage(conversationId, pendingTurns).history
        }
    }

    private suspend fun buildPackage(conversationId: String?, pendingTurns: List<FridayTurn>?): ContextPackage {
        val turns = pendingTurns ?: conversationId?.let { convoRepo.getTurns(it) }.orEmpty()
        val query = turns.lastOrNull { it.role == ROLE_USER }?.content.orEmpty()
        val retrieved = retriever.retrieve(query = query, conversationId = conversationId.orEmpty())
        val summary = conversationId?.let { store.getSummary(it) }
        return ContextPackageBuilder.build(
            PackageInput(
                turns = turns,
                memories = retrieved.memories,
                snippets = retrieved.snippets,
                summary = summary,
                locale = languageController.selected.value,
                tokenBudget = DEFAULT_BUDGET,
            ),
        )
    }

    private fun degradedPackage(conversationId: String?, pendingTurns: List<FridayTurn>?): ContextPackage {
        val turns = pendingTurns
            ?: conversationId?.let { runCatching { convoRepo.getTurns(it) }.getOrDefault(emptyList()) }.orEmpty()
        val locale = runCatching { languageController.selected.value }.getOrDefault(AppLanguage.SYSTEM)
        return ContextPackageBuilder.build(
            PackageInput(
                turns = turns,
                memories = emptyList(),
                snippets = emptyList(),
                summary = null,
                locale = locale,
                tokenBudget = DEFAULT_BUDGET,
            ),
        )
    }

    override fun onUserTurnPersisted(turn: FridayTurn) {
        val locale = languageController.selected.value.uiTag
        val extraction = MemoryExtractor.extract(turn.content, locale) ?: return
        try {
            store.createMemory(
                content = extraction.content,
                category = extraction.category,
                source = extraction.source,
                locale = extraction.locale,
            )
        } catch (e: Exception) {
            Log.w(TAG, "onUserTurnPersisted memory create failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onTurnCompleted(conversationId: String) {
        summarizeJobs.compute(conversationId) { _, existing ->
            if (existing != null && existing.isActive) return@compute existing
            engineScope.launch { runSummarize(conversationId) }
        }
    }

    private suspend fun runSummarize(conversationId: String) {
        try {
            val turns = convoRepo.getTurns(conversationId)
            val locale = languageController.selected.value
            when (val result = summarizer.summarize(turns, locale)) {
                is SummaryResult.Success -> {
                    if (convoRepo.getConversation(conversationId) != null) {
                        store.putSummary(result.summary)
                    }
                }
                SummaryResult.NotNeeded -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "onTurnCompleted summarize failed: ${e.javaClass.simpleName}")
        } finally {
            summarizeJobs.remove(conversationId)
        }
    }


    fun localeSignal(activeLocalModel: ModelInfo?): Pair<AppLanguage, Boolean?> {
        val locale = languageController.selected.value
        val supported = activeLocalModel?.let { LocalModelCapabilities.supportsLanguage(it, locale) }
        return locale to supported
    }


    fun getMemory(id: String): MemoryRecord? = store.getMemory(id)

    fun createMemory(content: String, category: MemoryCategory, source: MemorySource, locale: String): MemoryRecord =
        store.createMemory(content, category, source, locale)

    fun updateMemory(memory: MemoryRecord) = store.updateMemory(memory)

    fun deleteMemory(id: String) = store.deleteMemory(id)


    fun clearContextAndMemory() = store.clearContextAndMemory()

    fun clearForConversation(conversationId: String) = store.clearForConversation(conversationId)

    fun clearAllConversations() {
        convoRepo.conversations.value.forEach { convo ->
            convoRepo.deleteConversation(convo.id)
            store.clearForConversation(convo.id)
        }
    }

    companion object {
        private const val TAG = "ContextEngine"
        private const val ROLE_USER = "user"
        const val DEFAULT_BUDGET = 3072
    }
}

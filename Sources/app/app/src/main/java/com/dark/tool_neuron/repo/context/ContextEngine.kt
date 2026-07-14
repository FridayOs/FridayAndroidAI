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

// Seam both chat + voice VMs depend on so their tests keep hand-written
// fakes (no mockk/robolectric). ContextEngine is the sole production impl -
// composes ContextStore + ContextRetriever + ConversationSummarizer +
// ContextPackageBuilder behind the one call VMs make before BrainBridge.
interface ContextHistorySource {
    suspend fun buildHistory(conversationId: String?, pendingTurns: List<FridayTurn>? = null): List<GatewayTurn>
    fun onUserTurnPersisted(turn: FridayTurn)
    fun onTurnCompleted(conversationId: String)
}

// Full read/write seam ContextEngine depends on (extends the BM25-read-only
// ContextStoreOps that ContextRetriever uses) so ContextEngineTest exercises
// a hand-written fake instead of the real encrypted-vault-backed ContextStore.
// ContextStore is the sole production impl.
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

// Same seam rationale as ContextEngineStore: LanguageController is
// HXS-vault-backed (encrypted app_prefs), so ContextEngineTest depends on
// this read-only slice instead. LanguageController is the sole production impl.
interface LocaleSource {
    val selected: StateFlow<AppLanguage>
}

// Local-first context assembly. Sends ONLY the built package to BrainBridge -
// full transcript/memory vault never leaves this class. Never throws to the
// caller: any internal failure (vault I/O, retrieval, summarizer) degrades to
// a raw-window package with empty memories/snippets/summary so context
// machinery can never block a send. Errors are logged without content.
@Singleton
class ContextEngine @Inject constructor(
    private val store: ContextEngineStore,
    private val convoRepo: FridayConvoStore,
    private val summarizer: ConversationSummarizer,
    private val languageController: LocaleSource,
) : ContextHistorySource {

    private val retriever = ContextRetriever(store)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Single-flight per conversation: onTurnCompleted is fire-and-forget, a
    // second completion while one is still summarizing is a no-op skip.
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
        // Snippet retrieval is scoped by conversationId. NOTE: the native
        // BM25 index treats a blank chat_id as "no filter" (matches ALL
        // rows), so ContextRetriever explicitly skips snippet lookup when
        // the id is blank — a fresh chat (no conversationId yet) resolves
        // to memories-only via that guard, not via the index.
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

    // Synchronous, cheap: fixed regex marker set, no I/O beyond one vault
    // write when a marker actually fires.
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

    // Fire-and-forget on the engine-owned scope, never on the caller's job -
    // a VM cancelling its own scope (e.g. leaving the screen) must not abort
    // an in-flight summarize.
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
                    // Guard against a race with clearAllConversations: don't
                    // resurrect a summary for a conversation deleted while
                    // this job was running.
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

    // ── Locale signal (FRI-557 phase 5, consumed by FRI-583 UI) ──

    // Pure query: caller supplies the currently active local model (from
    // ModelRepository, e.g. models.value.firstOrNull { it.isActive }) since
    // ContextEngine has no direct ModelRepository dependency. Second value
    // is null when no active local model or capability is unknown - never a
    // faked "supported" (see LocalModelCapabilities). Read-only: this never
    // writes friday_language.
    fun localeSignal(activeLocalModel: ModelInfo?): Pair<AppLanguage, Boolean?> {
        val locale = languageController.selected.value
        val supported = activeLocalModel?.let { LocalModelCapabilities.supportsLanguage(it, locale) }
        return locale to supported
    }

    // ── Memory CRUD passthrough (FRI-583 UI) ──

    fun getMemory(id: String): MemoryRecord? = store.getMemory(id)

    fun createMemory(content: String, category: MemoryCategory, source: MemorySource, locale: String): MemoryRecord =
        store.createMemory(content, category, source, locale)

    fun updateMemory(memory: MemoryRecord) = store.updateMemory(memory)

    fun deleteMemory(id: String) = store.deleteMemory(id)

    // ── Clear ops (independent entry points, FRI-583 UI) ──

    // Wipes context_store_v1 only (memories + summaries + BM25 index).
    // Conversations/gateway_store_v1 untouched.
    fun clearContextAndMemory() = store.clearContextAndMemory()

    // Per-conversation variant: removes that conversation's summary + BM25
    // doc from context_store_v1. Called by the history screen alongside
    // convoRepo.deleteConversation so deleting a conversation never leaves
    // its summary orphaned at rest. Global memories untouched.
    fun clearForConversation(conversationId: String) = store.clearForConversation(conversationId)

    // Wipes friday_store_v1 conversations + their per-convo summaries/index.
    // Global memories + gateway_store_v1 untouched.
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

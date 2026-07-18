package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.context.ContextHistorySource
import com.dark.tool_neuron.repo.gateway.ChatBrain
import com.dark.tool_neuron.repo.gateway.GatewayDirectory
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class FridayChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val done: Boolean = true,
)

@HiltViewModel
class FridayChatViewModel @Inject constructor(
    private val gatewayRepo: GatewayDirectory,
    private val convoRepo: FridayConvoStore,
    private val router: ChatBrain,
    private val contextEngine: ContextHistorySource,
    // Trailing default keeps pre-existing 4-arg test call sites compiling unmodified;
    // Hilt's generated factory still supplies the real bound singleton explicitly.
    private val activeConversationStore: ActiveConversationStore = DefaultActiveConversationStore(),
) : ViewModel() {

    private val _messages = MutableStateFlow<List<FridayChatMessage>>(emptyList())
    val messages: StateFlow<List<FridayChatMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val hasGateway: StateFlow<Boolean> = gatewayRepo.gateways
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, gatewayRepo.gateways.value.isNotEmpty())

    // Distinct from hasGateway ("any configured"): true only when the active brain gateway is
    // READY. Recomputes whenever the gateway list OR the brain selection changes (selectBrain()
    // mutates brainSelection, not the gateways list, so both flows must be observed).
    val hasReadyGateway: StateFlow<Boolean> = combine(gatewayRepo.gateways, gatewayRepo.brainSelection) { _, _ ->
        gatewayRepo.brainGateway()?.status == GatewayStatus.READY
    }.stateIn(viewModelScope, SharingStarted.Eagerly, gatewayRepo.brainGateway()?.status == GatewayStatus.READY)

    // True once a brain gateway is selected, regardless of its READY/FAILED/NOT_TESTED status.
    // Drives the "open provider selector" gate: only the absence of a selection should redirect.
    val hasBrainSelected: StateFlow<Boolean> = combine(gatewayRepo.gateways, gatewayRepo.brainSelection) { _, _ ->
        gatewayRepo.brainGateway() != null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, gatewayRepo.brainGateway() != null)

    // "<label> · <model>" for the selected brain gateway, or null when none is selected.
    val brainLabel: StateFlow<String?> = combine(gatewayRepo.gateways, gatewayRepo.brainSelection) { _, _ ->
        gatewayRepo.brainGateway()?.let { "${it.label} · ${it.displayModel}" }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        gatewayRepo.brainGateway()?.let { "${it.label} · ${it.displayModel}" },
    )

    private var conversationId: String? = null
    private var replyJob: Job? = null

    init {
        convoRepo.conversations.value.firstOrNull()?.let { open(it.id) }
    }

    fun open(id: String) {
        replyJob?.cancel()
        _thinking.value = false
        _error.value = null
        conversationId = id
        activeConversationStore.set(id)
        _messages.value = convoRepo.getTurns(id).map {
            FridayChatMessage(id = it.id, isUser = it.role == "user", text = it.content)
        }
    }

    fun newChat() {
        replyJob?.cancel()
        _thinking.value = false
        _error.value = null
        conversationId = null
        activeConversationStore.set(null)
        _messages.value = emptyList()
    }

    // Explicit stop-generating hook (chat had none before; Voice already wires an equivalent
    // cancel). Keeps the partial assistant bubble as-is — only Delta/Done/Error handlers mutate it.
    fun cancel() {
        replyJob?.cancel()
        router.brainCancel()
        _thinking.value = false
    }

    fun clearError() { _error.value = null }

    // brain_continue: re-run from the turn before the last assistant answer without a new user message.
    fun regenerate() {
        val convoId = conversationId ?: return
        if (_thinking.value) return
        val turns = convoRepo.getTurns(convoId)
        val (pending, existing) = planRegeneration(turns)
        if (pending.isEmpty()) return
        val aiId = existing?.id ?: UUID.randomUUID().toString()
        // Reset only the visible bubble; the stored turn keeps its old content until Done succeeds.
        if (existing != null) {
            _messages.value = _messages.value.map { if (it.id == aiId) it.copy(text = "", done = false) else it }
        }
        _thinking.value = true
        replyJob = viewModelScope.launch {
            var settled = false
            try {
                val history = contextEngine.buildHistory(convoId, pendingTurns = pending)
                router.brainContinue(history).collect { event ->
                    when (event) {
                        is GatewayEvent.Delta -> {
                            if (_thinking.value) {
                                _thinking.value = false
                                if (_messages.value.none { it.id == aiId }) {
                                    _messages.value = _messages.value + FridayChatMessage(aiId, isUser = false, text = "", done = false)
                                }
                            }
                            _messages.value = _messages.value.map {
                                if (it.id == aiId) it.copy(text = it.text + event.text) else it
                            }
                        }
                        is GatewayEvent.Done -> {
                            settled = true
                            _thinking.value = false
                            val finalText = event.fullText
                            _messages.value = _messages.value.map {
                                if (it.id == aiId) it.copy(text = finalText, done = true) else it
                            }
                            if (finalText.isNotBlank()) {
                                persistRegenerated(convoId, aiId, existing, finalText)
                                contextEngine.onTurnCompleted(convoId)
                            }
                        }
                        is GatewayEvent.Error -> {
                            settled = true
                            _thinking.value = false
                            restoreRegenerated(aiId, existing)
                            _error.value = event.message
                        }
                    }
                }
            } finally {
                // Cancellation (open()/newChat()) rethrows with no terminal event: restore UI, never persist a partial. Guard convo so a switch doesn't clobber the new view.
                if (!settled && conversationId == convoId) {
                    _thinking.value = false
                    restoreRegenerated(aiId, existing)
                }
            }
        }
    }

    // Old answer stays: restore the replaced bubble to its stored content, or drop an empty fresh one.
    private fun restoreRegenerated(aiId: String, existing: FridayTurn?) {
        _messages.value = if (existing != null) {
            _messages.value.map { if (it.id == aiId) it.copy(text = existing.content, done = true) else it }
        } else {
            _messages.value.filterNot { it.id == aiId }
        }
    }

    // Replace the regenerated assistant turn in place, or create one when the history had none.
    private fun persistRegenerated(convoId: String, aiId: String, existing: FridayTurn?, finalText: String) {
        if (existing != null) {
            convoRepo.updateTurn(existing.copy(content = finalText))
        } else {
            convoRepo.addTurn(
                FridayTurn(
                    id = aiId,
                    conversationId = convoId,
                    role = "assistant",
                    content = finalText,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }
    }

    fun send(text: String, viaVoice: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _thinking.value) return
        // No brain surfaces an error the screen turns into the provider/model selector.
        val brain = router.brainGateway()
        if (brain == null) {
            _error.value = router.brainUnavailableMessage()
            return
        }

        val convoId = conversationId ?: convoRepo.createConversation(brain.id).id.also {
            conversationId = it
            activeConversationStore.set(it)
        }
        val now = System.currentTimeMillis()
        val userTurn = FridayTurn(
            id = UUID.randomUUID().toString(),
            conversationId = convoId,
            role = "user",
            content = trimmed,
            timestamp = now,
            viaVoice = viaVoice,
        )
        convoRepo.addTurn(userTurn)
        contextEngine.onUserTurnPersisted(userTurn)
        _messages.value = _messages.value + FridayChatMessage(userTurn.id, isUser = true, text = trimmed)
        _thinking.value = true

        val aiId = UUID.randomUUID().toString()

        // brain_turn: chat always routes through the active Brain Gateway.
        replyJob = viewModelScope.launch {
            val history = contextEngine.buildHistory(convoId)
            router.brainTurn(history).collect { event ->
                when (event) {
                    is GatewayEvent.Delta -> {
                        if (_thinking.value) {
                            _thinking.value = false
                            _messages.value = _messages.value + FridayChatMessage(aiId, isUser = false, text = "", done = false)
                        }
                        _messages.value = _messages.value.map {
                            if (it.id == aiId) it.copy(text = it.text + event.text) else it
                        }
                    }
                    is GatewayEvent.Done -> {
                        _thinking.value = false
                        val finalText = event.fullText
                        _messages.value = _messages.value.map {
                            if (it.id == aiId) it.copy(text = finalText, done = true) else it
                        }
                        if (finalText.isNotBlank()) {
                            convoRepo.addTurn(
                                FridayTurn(
                                    id = aiId,
                                    conversationId = convoId,
                                    role = "assistant",
                                    content = finalText,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                            contextEngine.onTurnCompleted(convoId)
                        }
                    }
                    is GatewayEvent.Error -> {
                        _thinking.value = false
                        _messages.value = _messages.value.filterNot { it.id == aiId }
                        _error.value = event.message
                    }
                }
            }
        }
    }

    companion object {
        fun planRegeneration(turns: List<FridayTurn>): Pair<List<FridayTurn>, FridayTurn?> {
            val idx = turns.indexOfLast { it.role == "assistant" }
            val source = if (idx >= 0) turns.subList(0, idx) else turns
            return source to (if (idx >= 0) turns[idx] else null)
        }
    }
}

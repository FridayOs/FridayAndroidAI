package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.friday.FridayTurn
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayRoleRouter
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val gatewayRepo: GatewayConfigRepository,
    private val convoRepo: FridayConversationRepository,
    private val router: GatewayRoleRouter,
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
        _messages.value = convoRepo.getTurns(id).map {
            FridayChatMessage(id = it.id, isUser = it.role == "user", text = it.content)
        }
    }

    fun newChat() {
        replyJob?.cancel()
        _thinking.value = false
        _error.value = null
        conversationId = null
        _messages.value = emptyList()
    }

    fun clearError() { _error.value = null }

    fun send(text: String, viaVoice: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _thinking.value) return
        // No brain surfaces an error the screen turns into the provider/model selector.
        val brain = router.brainGateway()
        if (brain == null) {
            _error.value = router.brainUnavailableMessage()
            return
        }

        val convoId = conversationId ?: convoRepo.createConversation(brain.id).id.also { conversationId = it }
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
        _messages.value = _messages.value + FridayChatMessage(userTurn.id, isUser = true, text = trimmed)
        _thinking.value = true

        val history = convoRepo.getTurns(convoId).map { GatewayTurn(it.role, it.content) }
        val aiId = UUID.randomUUID().toString()

        // brain_turn: chat always routes through the active Brain Gateway.
        replyJob = viewModelScope.launch {
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
}

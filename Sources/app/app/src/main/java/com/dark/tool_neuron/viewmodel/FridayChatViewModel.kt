package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FridayChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val done: Boolean = true,
)

@HiltViewModel
class FridayChatViewModel @Inject constructor() : ViewModel() {

    private val _messages = MutableStateFlow<List<FridayChatMessage>>(emptyList())
    val messages: StateFlow<List<FridayChatMessage>> = _messages.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    private var nextId = 0L
    private var replyJob: Job? = null

    fun newChat() {
        replyJob?.cancel()
        _thinking.value = false
        _messages.value = emptyList()
    }

    // M1 shell echo — real streaming lands with the direct gateway client in M1-03.
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _thinking.value) return
        _messages.value = _messages.value + FridayChatMessage(nextId++, isUser = true, text = trimmed)
        _thinking.value = true
        replyJob = viewModelScope.launch {
            delay(900)
            _thinking.value = false
            val reply = pickReply(trimmed)
            val aiId = nextId++
            _messages.value = _messages.value + FridayChatMessage(aiId, isUser = false, text = "", done = false)
            for (i in 1..reply.length) {
                _messages.value = _messages.value.map {
                    if (it.id == aiId) it.copy(text = reply.take(i)) else it
                }
                delay(14)
            }
            _messages.value = _messages.value.map {
                if (it.id == aiId) it.copy(text = reply, done = true) else it
            }
        }
    }

    private fun pickReply(text: String): String {
        val low = text.lowercase()
        return when {
            low.contains("dinner") || low.contains("food") -> REPLIES[1]
            low.contains("voice") || low.contains("recogni") -> REPLIES[2]
            else -> REPLIES[0]
        }
    }

    companion object {
        private val REPLIES = listOf(
            "I'm the Friday shell for M1 — connect a gateway model from the drawer and full replies will stream here soon.",
            "Try a sheet-pan salmon with broccoli and lemon, a chickpea curry over rice, or a quick veg stir-fry — each ready in about 25 minutes.",
            "Your phone turns your voice into a wave, slices it into tiny pieces, and matches the patterns to words it has learned from many examples.",
        )
    }
}

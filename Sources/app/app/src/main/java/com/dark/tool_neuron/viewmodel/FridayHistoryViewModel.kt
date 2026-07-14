package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.repo.FridayConversationRepository
import com.dark.tool_neuron.repo.context.ContextEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FridayHistoryViewModel @Inject constructor(
    private val convoRepo: FridayConversationRepository,
    private val contextEngine: ContextEngine,
) : ViewModel() {

    // HXS-backed Friday conversations. Survives process restart because the
    // repository re-reads the encrypted vault on construction.
    val conversations: StateFlow<List<FridayConversation>> = convoRepo.conversations

    fun refresh() = convoRepo.refresh()

    fun delete(id: String) {
        convoRepo.deleteConversation(id)
        // Also drop the conversation's summary + BM25 doc from
        // context_store_v1 so no summary content outlives its transcript.
        contextEngine.clearForConversation(id)
    }
}

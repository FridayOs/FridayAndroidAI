package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import kotlinx.coroutines.flow.StateFlow

// Persistence seam the Friday VMs use, so regenerate/voice paths test against a fake store off-device.
interface FridayConvoStore {
    val conversations: StateFlow<List<FridayConversation>>
    fun refresh()
    fun createConversation(gatewayId: String): FridayConversation
    fun getConversation(id: String): FridayConversation?
    fun getTurns(conversationId: String): List<FridayTurn>
    fun addTurn(turn: FridayTurn)
    fun updateTurn(turn: FridayTurn)
    fun deleteConversation(id: String)
}

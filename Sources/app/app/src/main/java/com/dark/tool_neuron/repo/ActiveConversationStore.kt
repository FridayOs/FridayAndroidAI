package com.dark.tool_neuron.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Process-scoped "currently open conversation" seam (FRI-574 phase 1) shared between
 * FridayChatViewModel and FridayVoiceViewModel so opening/creating a conversation on
 * either screen is visible to the other. Mirrors VoiceGatewayStatePort's process-lifetime
 * singleton shape, but this one is a plain mutable holder (not derived from another
 * repository's flow) — holds only an id, no message content, never touches HXS/network.
 *
 * FRI-574 phase 7 (B2): `newChatRequests` is a monotonic counter the sidebar's "New
 * conversation" action ticks. ViewModels collect it (after the initial value) so the
 * same real command path also clears any open conversation, without having to know
 * about the current view hierarchy.
 */
interface ActiveConversationStore {
    val activeConversationId: StateFlow<String?>
    val newChatRequests: StateFlow<Int>
    fun set(id: String?)
    fun requestNewChat()
}

@Singleton
class DefaultActiveConversationStore @Inject constructor() : ActiveConversationStore {
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _newChatRequests = MutableStateFlow(0)
    override val newChatRequests: StateFlow<Int> = _newChatRequests.asStateFlow()

    override fun set(id: String?) {
        _activeConversationId.value = id
    }

    override fun requestNewChat() {
        _activeConversationId.value = null
        _newChatRequests.value = _newChatRequests.value + 1
    }
}

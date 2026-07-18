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
 */
interface ActiveConversationStore {
    val activeConversationId: StateFlow<String?>
    fun set(id: String?)
}

@Singleton
class DefaultActiveConversationStore @Inject constructor() : ActiveConversationStore {
    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    override fun set(id: String?) {
        _activeConversationId.value = id
    }
}

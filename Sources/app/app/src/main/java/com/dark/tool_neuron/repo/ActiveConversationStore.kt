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

/*
 * FRI-574 round-5 BLOCKER 2: narrow persistence port so DefaultActiveConversationStore
 * can persist + restore the active conversation id across process death without taking a
 * hard dependency on AppPreferences (which is unconstructable off-device). The Hilt
 * binding in SecurityModule adapts AppPreferences to this; tests use the NoOp default so
 * existing no-arg DefaultActiveConversationStore() call sites keep their process-scoped
 * behavior.
 */
interface ActiveConversationPersistence {
    fun loadActiveConversationId(): String?
    fun saveActiveConversationId(id: String?)
}

private object NoOpPersistence : ActiveConversationPersistence {
    override fun loadActiveConversationId(): String? = null
    override fun saveActiveConversationId(id: String?) {}
}

@Singleton
class DefaultActiveConversationStore @Inject constructor(
    private val persistence: ActiveConversationPersistence,
) : ActiveConversationStore {
    // No-arg convenience constructor for tests/off-device call sites that want the
    // process-scoped (non-persisted) behavior. Secondary, NOT @Inject-annotated, so
    // Hilt/Dagger sees exactly one injectable constructor (the primary above) and binds
    // the real ActiveConversationPersistence into the production singleton.
    constructor() : this(NoOpPersistence)

    private val _activeConversationId = MutableStateFlow<String?>(null)
    override val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _newChatRequests = MutableStateFlow(0)
    override val newChatRequests: StateFlow<Int> = _newChatRequests.asStateFlow()

    // FRI-574 round-5 BLOCKER 2: restore the persisted active id at construction so a
    // process restart reopens the same conversation instead of losing it.
    init { _activeConversationId.value = persistence.loadActiveConversationId() }

    override fun set(id: String?) {
        _activeConversationId.value = id
        persistence.saveActiveConversationId(id)
    }

    override fun requestNewChat() {
        _activeConversationId.value = null
        persistence.saveActiveConversationId(null)
        _newChatRequests.value = _newChatRequests.value + 1
    }
}

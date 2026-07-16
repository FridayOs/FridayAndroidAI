package com.dark.tool_neuron.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// One-shot: a tapped event notification (ACTION_OPEN_EVENT) sets the display-lookup keys; the Friday
// events VM consumes them exactly once, resolves the retained event, and surfaces its card. The id /
// correlation are non-authoritative lookup keys, not a trust boundary — a forged id resolves to null
// (see InboundEventCenter.resolve). One-shot so a stale intent isn't re-consumed on rotation.
@Singleton
class PendingInboundEvent @Inject constructor() {

    private val _pending = MutableStateFlow<Pair<String, String?>?>(null)
    val pending: StateFlow<Pair<String, String?>?> = _pending.asStateFlow()

    fun set(eventId: String, correlationId: String?) {
        _pending.value = eventId to correlationId
    }

    fun consume(): Pair<String, String?>? {
        val was = _pending.value
        _pending.value = null
        return was
    }
}

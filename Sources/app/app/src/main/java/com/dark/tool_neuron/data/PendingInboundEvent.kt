package com.dark.tool_neuron.data

import com.dark.tool_neuron.model.friday.InboundEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// One-shot: a tapped event notification (ACTION_OPEN_EVENT) carries the full event payload reconstructed
// from its intent extras; the Friday events VM consumes it exactly once and surfaces its card via
// InboundEventCenter.surfaceFromNotification (which prefers the retained/coerced copy, else re-coerces the
// reconstructed one). The extras are attacker-reachable on the exported activity, so the reconstructed
// event is treated as untrusted PUSH data — never as an authoritative confirmation. One-shot so a stale
// intent isn't re-consumed on rotation.
@Singleton
class PendingInboundEvent @Inject constructor() {

    private val _pending = MutableStateFlow<InboundEvent?>(null)
    val pending: StateFlow<InboundEvent?> = _pending.asStateFlow()

    fun set(event: InboundEvent) {
        _pending.value = event
    }

    fun consume(): InboundEvent? {
        val was = _pending.value
        _pending.value = null
        return was
    }
}

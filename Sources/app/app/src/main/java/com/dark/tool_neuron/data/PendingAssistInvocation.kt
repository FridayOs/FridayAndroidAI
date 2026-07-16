package com.dark.tool_neuron.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// One-shot flag: assistant invocation (long-press home) sets it, FridayVoiceScreen
// consumes it exactly once after the auth/lock chain resolves onto the voice route.
@Singleton
class PendingAssistInvocation @Inject constructor() {

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun set() { _pending.value = true }

    fun consume(): Boolean {
        val was = _pending.value
        _pending.value = false
        return was
    }
}

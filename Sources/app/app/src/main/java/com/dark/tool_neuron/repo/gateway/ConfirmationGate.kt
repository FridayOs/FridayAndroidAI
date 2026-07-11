package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

// brain_confirm gate: a turn arms it, the UI resolves confirm(true)/cancel(false); thread-safe + unit-testable off-device.
class ConfirmationGate {

    private val pending = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private val _awaiting = MutableStateFlow(false)
    val awaiting: StateFlow<Boolean> = _awaiting.asStateFlow()

    fun arm(): Deferred<Boolean> {
        val latch = CompletableDeferred<Boolean>()
        pending.getAndSet(latch)?.complete(false)
        _awaiting.value = true
        return latch
    }

    fun confirm(): Boolean {
        val latch = pending.getAndSet(null) ?: return false
        _awaiting.value = false
        return latch.complete(true)
    }

    fun cancel(): Boolean {
        val latch = pending.getAndSet(null) ?: return false
        _awaiting.value = false
        return latch.complete(false)
    }
}

package com.dark.tool_neuron.repo.gateway

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

// brain_confirm semantics: a turn arms a gate before a user-gated action; the UI
// resolves it with confirm (true) or cancel (false). Pure + thread-safe so it is
// unit-testable off-device and shared by the router's confirm/cancel paths.
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

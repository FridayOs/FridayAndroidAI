package com.dark.tool_neuron.voice

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Thin, Android-free service<->VM signal bridge for the opt-in foreground-continuation service:
// the service observes sessionActive to know when to stopSelf, the VM collects stopRequests to
// cancel the turn when the notification's Stop action fires.
@Singleton
class VoiceSessionServiceGate @Inject constructor() {
    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    fun setSessionActive(active: Boolean) {
        _sessionActive.value = active
    }

    fun requestStop() {
        _stopRequests.tryEmit(Unit)
    }
}

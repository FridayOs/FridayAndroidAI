package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.repo.InboundEventCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Thin bridge from the singleton InboundEventCenter to the Friday screens' in-app event card. Also
// consumes a tapped-notification one-shot (PendingInboundEvent): surface the tapped event via
// surfaceFromNotification, which prefers the retained (already-coerced) copy and otherwise re-coerces
// the reconstructed extras so a forged intent can only cold-start a capped, non-actionable STATUS card.
@HiltViewModel
class FridayEventsViewModel @Inject constructor(
    private val center: InboundEventCenter,
    private val pendingInboundEvent: PendingInboundEvent,
) : ViewModel() {

    val activeEvent: StateFlow<InboundEvent?> = center.activeEvent

    init {
        viewModelScope.launch {
            pendingInboundEvent.pending.collect { pending ->
                if (pending == null) return@collect
                val event = pendingInboundEvent.consume() ?: return@collect
                center.surfaceFromNotification(event)
            }
        }
    }

    fun dismiss() = center.dismiss()
}

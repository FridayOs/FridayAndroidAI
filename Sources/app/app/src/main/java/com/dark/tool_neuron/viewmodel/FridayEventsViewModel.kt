package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.dark.tool_neuron.repo.gateway.event.PendingInboundConfirmation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Thin bridge from the singleton InboundEventCenter to the Friday screens' in-app event card. Also
// consumes a tapped-notification one-shot (PendingInboundEvent): surface the tapped event via
// surfaceFromNotification, which prefers the retained (already-coerced) copy and otherwise re-coerces
// the reconstructed extras so a forged intent can only cold-start a capped, non-actionable STATUS card.
//
// FRI-555 B1: also exposes InboundConfirmationCenter's pending inbound confirmation. confirmInbound/
// cancelInbound just resolve the pending record via the center — this VM never executes actionIntent,
// and this pending confirmation is intentionally NOT cross-wired with FridayVoiceViewModel's
// brain-local ConfirmationGate (separate state, separate resolution path).
@HiltViewModel
class FridayEventsViewModel @Inject constructor(
    private val center: InboundEventCenter,
    private val pendingInboundEvent: PendingInboundEvent,
    private val confirmationCenter: InboundConfirmationCenter,
) : ViewModel() {

    val activeEvent: StateFlow<InboundEvent?> = center.activeEvent

    val pendingInboundConfirmation: StateFlow<PendingInboundConfirmation?> = confirmationCenter.pending

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

    // FRI-555 R3-1: delegates to the center, which tears down the pending slot, the foreground
    // card, the retained `recent` entry, and the OS notification as a single unit -- not just the
    // pending confirmation slot.
    fun confirmInbound() {
        center.confirmActiveConfirmation()
    }

    fun cancelInbound() {
        center.cancelActiveConfirmation()
    }
}

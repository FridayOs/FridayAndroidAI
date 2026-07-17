package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.model.friday.InboundEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 B1: at most one pending inbound confirmation, resolved ONLY by an explicit user
// confirm()/cancel() tap. This class never calls any action executor — actionIntent is carried
// purely for display; confirm/cancel just resolve+clear the pending record. Re-arming (a newer
// verified CONFIRMATION_REQUESTED for a different eventId) supersedes and implicitly cancels
// whatever was previously pending, mirroring ConfirmationGate's single-slot semantics.
@Singleton
class InboundConfirmationCenter @Inject constructor() {

    private val _pending = MutableStateFlow<PendingInboundConfirmation?>(null)
    val pending: StateFlow<PendingInboundConfirmation?> = _pending.asStateFlow()

    fun arm(event: InboundEvent) {
        _pending.value = PendingInboundConfirmation(
            eventId = event.eventId,
            correlationId = event.correlationId,
            title = event.title,
            body = event.body,
            actionIntent = event.actionIntent,
        )
    }

    // Resolves the pending confirmation as accepted. False (safe no-op) when nothing is pending or
    // the id doesn't match the currently armed one (stale UI tap after a re-arm/cancel).
    fun confirm(eventId: String): Boolean = resolve(eventId) { it.eventId == eventId }

    fun cancel(eventId: String): Boolean = resolve(eventId) { it.eventId == eventId }

    // FRI-555 B3 integration point: a verified CANCEL for a correlation also clears any pending
    // inbound confirmation sharing that correlation (dismissIfCorrelated calls this).
    fun cancelByCorrelation(correlationId: String): Boolean =
        resolve(correlationId) { it.correlationId == correlationId }

    private inline fun resolve(key: String, matches: (PendingInboundConfirmation) -> Boolean): Boolean {
        val current = _pending.value ?: return false
        if (!matches(current)) return false
        _pending.value = null
        return true
    }
}

// Display-only snapshot of a verified inbound confirmation. actionIntent is data, never executed.
data class PendingInboundConfirmation(
    val eventId: String,
    val correlationId: String?,
    val title: String,
    val body: String,
    val actionIntent: String?,
)

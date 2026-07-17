package com.dark.tool_neuron.repo.gateway.event

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 B1: the designated integration seam for a future brain/outbound dispatch. This bridge
// only RECORDS the user's confirm/cancel decision -- it MUST NEVER execute the action itself.
// InboundConfirmationCenter calls exactly one of these, exactly once per pending confirmation, only
// after the user's explicit tap resolved it (see InboundConfirmationCenter.resolve).
interface InboundActionBridge {
    fun onConfirmed(confirmation: PendingInboundConfirmation)
    fun onCancelled(confirmation: PendingInboundConfirmation)
}

// Default binding: durably records the outcome (source+correlation+eventId+decision+atMs) via
// EventStateStore so the decision survives process death, and logs a single redacted machine line
// -- no title/body/actionIntent/payload ever reaches Logcat. No executor is wired here by design;
// see FRI-555 handoff note for the separate cross-module item that would bind a real executor to
// this seam.
@Singleton
class RecordingInboundActionBridge @Inject constructor(
    private val store: EventStateStore,
) : InboundActionBridge {

    override fun onConfirmed(confirmation: PendingInboundConfirmation) = record(confirmation, "CONFIRMED")

    override fun onCancelled(confirmation: PendingInboundConfirmation) = record(confirmation, "CANCELLED")

    private fun record(confirmation: PendingInboundConfirmation, decision: String) {
        val atMs = System.currentTimeMillis()
        val sourceId = confirmation.sourceId.orEmpty()
        val correlationId = confirmation.correlationId.orEmpty()
        val key = "$OUTCOME_KEY_PREFIX$sourceId|$correlationId"
        store.write(key, "$atMs\t$decision\t${confirmation.eventId}")
        Log.i(TAG, "outcome recorded (source=$sourceId decision=$decision)")
    }

    private companion object {
        const val TAG = "InboundActionBridge"
        const val OUTCOME_KEY_PREFIX = "fri555.confirm_outcome."
    }
}

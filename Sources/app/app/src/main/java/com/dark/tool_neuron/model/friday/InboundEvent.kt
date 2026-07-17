package com.dark.tool_neuron.model.friday

// Display model for an inbound event (task/status/confirmation). Shape matches the FRI-555 push
// envelope sketch (eventId, sourceType, correlationId, kind, title/body, confirmation action) so a
// verified envelope maps 1:1 later. No persistence here — events are ephemeral UI this task.
data class InboundEvent(
    val eventId: String,
    val sourceType: InboundSource,
    val correlationId: String?,
    val kind: InboundEventKind,
    val title: String,
    val body: String,
    val urgency: InboundUrgency,
    val receivedAt: Long,
    // FRI-555 B1: data-only action payload from a VERIFIED CONFIRMATION_REQUESTED envelope. Never
    // executed anywhere in this codebase — it is surfaced to InboundConfirmationCenter for display
    // only; user confirm/cancel just resolves the pending record. Defaults to null so every existing
    // constructor call (LOCAL/PUSH events, all pre-B1 tests) keeps compiling unchanged.
    val actionIntent: String? = null,
    // FRI-555 B3: the signed envelope's sourceId (OPENCLAW/HERMES instance id), set only for
    // VERIFIED events by InboundEventPolicy. Defaults to null so every existing constructor call
    // (LOCAL/PUSH events, all pre-B3 tests) keeps compiling unchanged. Used to scope notification
    // identity and cancel/dismiss to the correct source so one source's cancel can never purge
    // another source's event sharing the same correlationId.
    val sourceId: String? = null,
) {
    // Derived: only CONFIRMATION events render actionable confirm/cancel UI.
    val requiresConfirmation: Boolean get() = kind == InboundEventKind.CONFIRMATION
}

// FRI-555: VERIFIED is a signed OpenClaw/Hermes envelope that passed EventGateway verification
// (HMAC + timestamp + nonce). Only VERIFIED bypasses InboundEventCenter's unverified-push
// coercion, so only a verified CONFIRMATION renders the actionable confirm/cancel card.
enum class InboundSource { PUSH, LOCAL, VERIFIED }

enum class InboundEventKind { TASK, STATUS, CONFIRMATION }

enum class InboundUrgency { NORMAL, HIGH, URGENT }

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

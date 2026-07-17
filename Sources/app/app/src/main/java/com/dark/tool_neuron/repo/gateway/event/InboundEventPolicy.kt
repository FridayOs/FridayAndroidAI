package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundEventType
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import javax.inject.Inject

// FRI-555: maps a verified envelope to a delivery decision. `nowMs` is explicit (not read from
// the envelope) so InboundEvent.receivedAt reflects local receipt time, matching NonceStore's/
// InboundEventVerifier's clock-injection convention. actionIntent is carried through ONLY for
// CONFIRMATION_REQUESTED (data-only; InboundConfirmationCenter/UI display it, nothing executes it).
class InboundEventPolicy @Inject constructor(
    private val dedupeStore: DedupeStore,
) {
    fun decide(env: InboundEventEnvelope, nowMs: Long): PolicyDecision {
        // Redelivery of a legitimate at-least-once retry (same eventId) must drop before any other
        // processing, including CANCEL — a duplicate cancel is still a duplicate.
        if (!dedupeStore.firstSeen(env.eventId, nowMs)) return PolicyDecision.Drop("duplicate")

        if (env.type == InboundEventType.CANCEL) return PolicyDecision.Cancel(env.correlationId)

        val kind = kindFor(env.type) ?: return PolicyDecision.Drop("unsupported_type")
        val event = InboundEvent(
            eventId = env.eventId,
            sourceType = InboundSource.VERIFIED,
            correlationId = env.correlationId,
            kind = kind,
            title = env.title,
            body = env.body,
            urgency = effectiveUrgency(env),
            receivedAt = nowMs,
            actionIntent = if (env.type == InboundEventType.CONFIRMATION_REQUESTED) env.actionIntent else null,
            sourceId = env.sourceId,
        )
        return PolicyDecision.Deliver(event)
    }

    private fun kindFor(type: InboundEventType): InboundEventKind? = when (type) {
        InboundEventType.TASK_ASSIGNED -> InboundEventKind.TASK
        InboundEventType.PROGRESS,
        InboundEventType.COMPLETION,
        InboundEventType.FAILURE -> InboundEventKind.STATUS
        InboundEventType.CONFIRMATION_REQUESTED -> InboundEventKind.CONFIRMATION
        InboundEventType.CANCEL -> null // handled by caller before this is reached
    }

    // FAILURE/CONFIRMATION_REQUESTED default to HIGH; the envelope's own urgency wins if higher.
    private fun effectiveUrgency(env: InboundEventEnvelope): InboundUrgency {
        val default = when (env.type) {
            InboundEventType.FAILURE, InboundEventType.CONFIRMATION_REQUESTED -> InboundUrgency.HIGH
            else -> InboundUrgency.NORMAL
        }
        return if (env.urgency.ordinal > default.ordinal) env.urgency else default
    }
}

sealed class PolicyDecision {
    data class Deliver(val event: InboundEvent) : PolicyDecision()
    data class Cancel(val correlationId: String?) : PolicyDecision()
    data class Drop(val reason: String) : PolicyDecision()
}

package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.EventSourceKind
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundEventType
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.DedupeStore
import com.dark.tool_neuron.repo.gateway.event.InboundEventPolicy
import com.dark.tool_neuron.repo.gateway.event.PolicyDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// FRI-555 policy: verified-envelope -> delivery decision. Dedupe runs before type dispatch (a
// duplicate CANCEL is still a duplicate); actionIntent is dropped here by construction since
// InboundEvent has no field for it.
class InboundEventPolicyTest {

    private fun envelope(
        eventId: String = "ev-1",
        type: InboundEventType = InboundEventType.TASK_ASSIGNED,
        correlationId: String? = "c-1",
        urgency: InboundUrgency = InboundUrgency.NORMAL,
    ): InboundEventEnvelope = InboundEventEnvelope(
        version = 1,
        eventId = eventId,
        sourceId = "openclaw-1",
        sourceKind = EventSourceKind.OPENCLAW,
        issuedAtMs = 1_000L,
        nonce = "n-1",
        type = type,
        correlationId = correlationId,
        title = "Task ready",
        body = "Do the thing",
        urgency = urgency,
        actionIntent = "run-it",
        signature = "sig",
    )

    @Test
    fun decide_duplicateEventId_dropsSecondCall() {
        val policy = InboundEventPolicy(DedupeStore())
        val env = envelope()
        val first = policy.decide(env, nowMs = 5_000L)
        assertTrue(first is PolicyDecision.Deliver)
        val second = policy.decide(env, nowMs = 5_001L)
        assertEquals(PolicyDecision.Drop("duplicate"), second)
    }

    @Test
    fun decide_duplicateCancel_alsoDropped() {
        val policy = InboundEventPolicy(DedupeStore())
        val env = envelope(type = InboundEventType.CANCEL)
        policy.decide(env, nowMs = 5_000L)
        val second = policy.decide(env, nowMs = 5_001L)
        assertEquals(PolicyDecision.Drop("duplicate"), second)
    }

    @Test
    fun decide_cancelType_returnsCancelWithCorrelationId() {
        val policy = InboundEventPolicy(DedupeStore())
        val env = envelope(type = InboundEventType.CANCEL, correlationId = "c-1")
        val decision = policy.decide(env, nowMs = 5_000L)
        assertEquals(PolicyDecision.Cancel("c-1"), decision)
    }

    @Test
    fun decide_cancelType_withNullCorrelationId() {
        val policy = InboundEventPolicy(DedupeStore())
        val env = envelope(type = InboundEventType.CANCEL, correlationId = null)
        val decision = policy.decide(env, nowMs = 5_000L)
        assertEquals(PolicyDecision.Cancel(null), decision)
    }

    @Test
    fun decide_taskAssigned_deliversTaskKind() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(envelope(type = InboundEventType.TASK_ASSIGNED), nowMs = 5_000L)
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundEventKind.TASK, deliver.event.kind)
        assertEquals(InboundSource.VERIFIED, deliver.event.sourceType)
        assertEquals(5_000L, deliver.event.receivedAt)
    }

    @Test
    fun decide_progressCompletionFailure_deliverStatusKind() {
        val policy = InboundEventPolicy(DedupeStore())
        for ((idx, type) in listOf(InboundEventType.PROGRESS, InboundEventType.COMPLETION, InboundEventType.FAILURE).withIndex()) {
            val decision = policy.decide(envelope(eventId = "ev-$idx", type = type), nowMs = 5_000L)
            val deliver = decision as PolicyDecision.Deliver
            assertEquals("$type must map to STATUS", InboundEventKind.STATUS, deliver.event.kind)
        }
    }

    @Test
    fun decide_confirmationRequested_deliversConfirmationKind() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(envelope(type = InboundEventType.CONFIRMATION_REQUESTED), nowMs = 5_000L)
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundEventKind.CONFIRMATION, deliver.event.kind)
    }

    @Test
    fun decide_failureDefaultsUrgencyToHigh_whenEnvelopeUrgencyLower() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(
            envelope(type = InboundEventType.FAILURE, urgency = InboundUrgency.NORMAL),
            nowMs = 5_000L,
        )
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundUrgency.HIGH, deliver.event.urgency)
    }

    @Test
    fun decide_confirmationRequested_defaultsUrgencyToHigh() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(
            envelope(type = InboundEventType.CONFIRMATION_REQUESTED, urgency = InboundUrgency.NORMAL),
            nowMs = 5_000L,
        )
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundUrgency.HIGH, deliver.event.urgency)
    }

    @Test
    fun decide_envelopeUrgencyHigherThanDefault_wins() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(
            envelope(type = InboundEventType.FAILURE, urgency = InboundUrgency.URGENT),
            nowMs = 5_000L,
        )
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundUrgency.URGENT, deliver.event.urgency)
    }

    @Test
    fun decide_taskAssigned_keepsNormalUrgency_whenEnvelopeNormal() {
        val policy = InboundEventPolicy(DedupeStore())
        val decision = policy.decide(
            envelope(type = InboundEventType.TASK_ASSIGNED, urgency = InboundUrgency.NORMAL),
            nowMs = 5_000L,
        )
        val deliver = decision as PolicyDecision.Deliver
        assertEquals(InboundUrgency.NORMAL, deliver.event.urgency)
    }
}

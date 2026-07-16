package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.InboundEventCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure decision logic of the inbound event center: urgency->channel routing, unverified-push
// conservatism (CONFIRMATION downgrade + length caps), and malformed-push drops.
class InboundEventCenterTest {

    private fun event(
        kind: InboundEventKind = InboundEventKind.STATUS,
        source: InboundSource = InboundSource.PUSH,
        title: String = "t",
        body: String = "b",
        urgency: InboundUrgency = InboundUrgency.NORMAL,
    ) = InboundEvent("e1", source, null, kind, title, body, urgency, 0L)

    @Test
    fun channelRouting_byUrgency() {
        assertEquals(InboundEventCenter.CHANNEL_NORMAL, InboundEventCenter.channelIdFor(InboundUrgency.NORMAL))
        assertEquals(InboundEventCenter.CHANNEL_HIGH, InboundEventCenter.channelIdFor(InboundUrgency.HIGH))
        assertEquals(InboundEventCenter.CHANNEL_URGENT, InboundEventCenter.channelIdFor(InboundUrgency.URGENT))
    }

    @Test
    fun pushConfirmation_isDowngradedToStatus() {
        val coerced = InboundEventCenter.coercePush(event(kind = InboundEventKind.CONFIRMATION))
        assertEquals(InboundEventKind.STATUS, coerced.kind)
        assertFalse("unverified push must never render confirm UI", coerced.requiresConfirmation)
    }

    @Test
    fun pushTitleAndBody_areLengthCapped() {
        val coerced = InboundEventCenter.coercePush(
            event(title = "x".repeat(500), body = "y".repeat(2000))
        )
        assertEquals(InboundEventCenter.TITLE_CAP, coerced.title.length)
        assertEquals(InboundEventCenter.BODY_CAP, coerced.body.length)
    }

    @Test
    fun localEventKinds_areNotDowngradedByCoercion() {
        // coercePush applies only to PUSH sources at publish time; TASK/STATUS pass through unchanged.
        val task = InboundEventCenter.coercePush(event(kind = InboundEventKind.TASK))
        assertEquals(InboundEventKind.TASK, task.kind)
    }

    @Test
    fun fromPushData_mapsCompletePayload() {
        val e = InboundEventCenter.fromPushData(
            mapOf(
                "eventId" to "ev-9", "title" to "Reminder", "body" to "Standup in 5",
                "kind" to "task", "urgency" to "high", "correlationId" to "c-1",
            )
        )!!
        assertEquals("ev-9", e.eventId)
        assertEquals(InboundSource.PUSH, e.sourceType)
        assertEquals(InboundEventKind.TASK, e.kind)
        assertEquals(InboundUrgency.HIGH, e.urgency)
        assertEquals("c-1", e.correlationId)
    }

    @Test
    fun fromPushData_missingIdOrTitle_isDropped() {
        assertNull(InboundEventCenter.fromPushData(mapOf("title" to "t")))
        assertNull(InboundEventCenter.fromPushData(mapOf("eventId" to "e")))
        assertNull(InboundEventCenter.fromPushData(emptyMap()))
    }

    @Test
    fun fromPushData_unknownKindAndUrgency_defaultConservatively() {
        val e = InboundEventCenter.fromPushData(
            mapOf("eventId" to "e", "title" to "t", "kind" to "EXPLODE", "urgency" to "MEGA")
        )!!
        assertEquals(InboundEventKind.STATUS, e.kind)
        assertEquals(InboundUrgency.NORMAL, e.urgency)
    }

    @Test
    fun requiresConfirmation_onlyForConfirmationKind() {
        assertTrue(event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.LOCAL).requiresConfirmation)
        assertFalse(event(kind = InboundEventKind.TASK).requiresConfirmation)
        assertFalse(event(kind = InboundEventKind.STATUS).requiresConfirmation)
    }

    private class FakeForeground(var foreground: Boolean) : com.dark.tool_neuron.repo.ForegroundSignal {
        override fun isForeground(): Boolean = foreground
    }

    private class RecordingNotifier : com.dark.tool_neuron.repo.EventNotifier {
        val notified = mutableListOf<Pair<InboundEvent, String>>()
        override fun notify(event: InboundEvent, channelId: String) { notified += event to channelId }
    }

    @Test
    fun publish_foreground_surfacesCardNotNotification() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(true), notifier)
        val e = event(source = InboundSource.LOCAL)
        center.publish(e)
        assertEquals(e, center.activeEvent.value)
        assertTrue("no double-surface: foreground must not notify", notifier.notified.isEmpty())
        center.dismiss()
        assertNull(center.activeEvent.value)
    }

    @Test
    fun publish_background_notifiesOnUrgencyChannel_noCard() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(false), notifier)
        center.publish(event(source = InboundSource.LOCAL, urgency = InboundUrgency.URGENT))
        assertNull(center.activeEvent.value)
        assertEquals(1, notifier.notified.size)
        assertEquals(InboundEventCenter.CHANNEL_URGENT, notifier.notified[0].second)
    }

    @Test
    fun publish_pushConfirmation_isCoercedBeforeAnySurface() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(true), notifier)
        center.publish(event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.PUSH))
        assertEquals(InboundEventKind.STATUS, center.activeEvent.value!!.kind)
    }

    @Test
    fun resolve_returnsRetainedBackgroundEvent_thenSurface() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        val e = event(source = InboundSource.LOCAL).copy(eventId = "bg-1")
        center.publish(e)
        assertNull("background publish must not auto-surface a card", center.activeEvent.value)
        val resolved = center.resolve("bg-1", null)
        assertEquals(e, resolved)
        center.surface(resolved!!)
        assertEquals(e, center.activeEvent.value)
    }

    @Test
    fun resolve_unknownId_returnsNull() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        center.publish(event(source = InboundSource.LOCAL).copy(eventId = "known"))
        assertNull(center.resolve("forged", null))
    }

    @Test
    fun resolve_correlationMismatch_returnsNull_matchReturnsEvent() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        val e = event(source = InboundSource.LOCAL).copy(eventId = "c-evt", correlationId = "corr-A")
        center.publish(e)
        assertNull("wrong correlation must not resolve", center.resolve("c-evt", "corr-B"))
        assertEquals(e, center.resolve("c-evt", "corr-A"))
        assertEquals("id-only lookup still resolves", e, center.resolve("c-evt", null))
    }

    @Test
    fun recent_evictsOldestPastCap() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        repeat(InboundEventCenter.RECENT_CAP + 1) { i ->
            center.publish(event(source = InboundSource.LOCAL).copy(eventId = "e$i"))
        }
        assertNull("eldest entry past cap is evicted", center.resolve("e0", null))
        assertEquals("newest is retained", "e${InboundEventCenter.RECENT_CAP}",
            center.resolve("e${InboundEventCenter.RECENT_CAP}", null)!!.eventId)
    }

    @Test
    fun reconstruct_mapsFullPayload_pushSourced() {
        val e = InboundEventCenter.reconstruct(
            eventId = "ev-1", correlationId = "c-1", kind = "task",
            title = "Reminder", body = "Standup", urgency = "high", receivedAt = 42L,
        )!!
        assertEquals("ev-1", e.eventId)
        assertEquals(InboundSource.PUSH, e.sourceType)
        assertEquals(InboundEventKind.TASK, e.kind)
        assertEquals(InboundUrgency.HIGH, e.urgency)
        assertEquals("c-1", e.correlationId)
        assertEquals(42L, e.receivedAt)
    }

    @Test
    fun reconstruct_blankIdOrTitle_returnsNull() {
        assertNull(InboundEventCenter.reconstruct(null, null, "task", "t", "b", "high", 0L))
        assertNull(InboundEventCenter.reconstruct("e", null, "task", null, "b", "high", 0L))
        assertNull(InboundEventCenter.reconstruct(" ", null, "task", "t", "b", "high", 0L))
    }

    @Test
    fun surfaceFromNotification_prefersRetainedCoercedCopy() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        val retained = event(kind = InboundEventKind.TASK, source = InboundSource.LOCAL).copy(eventId = "bg-9")
        center.publish(retained)
        // Tap hands back a reconstructed PUSH copy; resolve prefers the retained LOCAL TASK.
        val reconstructed = InboundEventCenter.reconstruct("bg-9", null, "confirmation", "t", "b", "normal", 0L)!!
        center.surfaceFromNotification(reconstructed)
        assertEquals(retained, center.activeEvent.value)
    }

    @Test
    fun surfaceFromNotification_forgedEvent_reCoercedToStatus() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        // Nothing retained (process death). A forged CONFIRMATION must cold-start as a capped STATUS card.
        val forged = InboundEventCenter.reconstruct(
            "forged", null, "confirmation", "x".repeat(500), "y".repeat(2000), "urgent", 0L,
        )!!
        center.surfaceFromNotification(forged)
        val surfaced = center.activeEvent.value!!
        assertEquals("forged", surfaced.eventId)
        assertEquals(InboundEventKind.STATUS, surfaced.kind)
        assertFalse(surfaced.requiresConfirmation)
        assertEquals(InboundEventCenter.TITLE_CAP, surfaced.title.length)
        assertEquals(InboundEventCenter.BODY_CAP, surfaced.body.length)
    }
}

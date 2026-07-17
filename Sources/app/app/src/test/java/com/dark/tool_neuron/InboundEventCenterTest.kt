package com.dark.tool_neuron

import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.repo.gateway.event.InboundActionBridge
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.dark.tool_neuron.repo.gateway.event.PendingInboundConfirmation
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
        val cancelled = mutableListOf<String>()
        override fun notify(event: InboundEvent, channelId: String) { notified += event to channelId }
        override fun cancel(notificationKey: String) { cancelled += notificationKey }
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
    fun publish_verifiedTitleAndBody_areLengthCapped() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        center.publish(
            event(source = InboundSource.VERIFIED, title = "x".repeat(500), body = "y".repeat(2000))
                .copy(eventId = "v-1")
        )
        val surfaced = center.resolve("v-1", null)!!
        assertEquals(InboundEventCenter.TITLE_CAP, surfaced.title.length)
        assertEquals(InboundEventCenter.BODY_CAP, surfaced.body.length)
    }

    @Test
    fun publish_pushSameEventIdAsRetainedVerified_doesNotOverwriteCache() {
        val center = InboundEventCenter(FakeForeground(false), RecordingNotifier())
        val verified = event(source = InboundSource.VERIFIED, title = "verified title").copy(eventId = "dup-1")
        center.publish(verified)
        center.publish(event(source = InboundSource.PUSH, title = "spoofed title").copy(eventId = "dup-1"))
        val resolved = center.resolve("dup-1", null)!!
        assertEquals(InboundSource.VERIFIED, resolved.sourceType)
        assertEquals("verified title", resolved.title)
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

    // FRI-555 B3: notification identity keying, scoped to sourceId+correlationId so two sources
    // sharing a correlationId never collide/replace each other.
    @Test
    fun notificationKeyFor_compositeWhenBothSourceIdAndCorrelationIdPresent() {
        val withBoth = event().copy(eventId = "e1", correlationId = "c-1", sourceId = "src-1")
        assertEquals(InboundEventCenter.compositeKey("src-1", "c-1"), InboundEventCenter.notificationKeyFor(withBoth))
    }

    @Test
    fun notificationKeyFor_fallsBackToEventId_whenSourceIdMissing() {
        val withoutSourceId = event().copy(eventId = "e1", correlationId = "c-1", sourceId = null)
        assertEquals("e1", InboundEventCenter.notificationKeyFor(withoutSourceId))
    }

    @Test
    fun notificationKeyFor_fallsBackToEventId_whenCorrelationIdMissing() {
        val withoutCorrelation = event().copy(eventId = "e1", correlationId = null, sourceId = "src-1")
        assertEquals("e1", InboundEventCenter.notificationKeyFor(withoutCorrelation))
    }

    @Test
    fun notificationKeyFor_sameForSharedSourceAndCorrelation_evenWithDifferentEventIds() {
        val progress = event().copy(eventId = "e1", correlationId = "c-1", sourceId = "src-1")
        val completion = event().copy(eventId = "e2", correlationId = "c-1", sourceId = "src-1")
        assertEquals(
            InboundEventCenter.notificationKeyFor(progress),
            InboundEventCenter.notificationKeyFor(completion),
        )
    }

    // FRI-555 B3: two sources sharing a correlationId must never collide/replace each other's
    // tray notification.
    @Test
    fun notificationKeyFor_differsAcrossSourceId_sameCorrelationId() {
        val hermes = event().copy(eventId = "e1", correlationId = "c-1", sourceId = "hermes-1")
        val openclaw = event().copy(eventId = "e2", correlationId = "c-1", sourceId = "openclaw-1")
        assertTrue(
            "different sourceIds sharing a correlationId must have distinct notification identity",
            InboundEventCenter.notificationKeyFor(hermes) != InboundEventCenter.notificationKeyFor(openclaw),
        )
    }

    // FRI-555 B3: cancel support — clears the card, purges retained entries sharing BOTH sourceId
    // and correlationId, and always tells the notifier to cancel the OS notification.
    @Test
    fun dismissIfCorrelated_clearsCard_purgesRecent_cancelsNotification() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(true), notifier)
        val active = event().copy(eventId = "e1", correlationId = "c-1", sourceId = "src-1")
        center.publish(active)
        assertEquals(active, center.activeEvent.value)

        val result = center.dismissIfCorrelated("src-1", "c-1")

        assertTrue(result)
        assertNull("active card must be cleared", center.activeEvent.value)
        assertEquals(listOf(InboundEventCenter.compositeKey("src-1", "c-1")), notifier.cancelled)
    }

    @Test
    fun dismissIfCorrelated_purgesMatchingRecentEntries_leavesUnrelatedAlone() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(false), notifier)
        center.publish(event().copy(eventId = "e1", correlationId = "c-1", sourceId = "src-1"))
        center.publish(event().copy(eventId = "e2", correlationId = "c-1", sourceId = "src-1"))
        center.publish(event().copy(eventId = "e3", correlationId = "other", sourceId = "src-1"))

        val result = center.dismissIfCorrelated("src-1", "c-1")

        assertTrue(result)
        assertNull("matching entry e1 purged", center.resolve("e1", null))
        assertNull("matching entry e2 purged", center.resolve("e2", null))
        assertEquals("unrelated correlation left alone", "e3", center.resolve("e3", null)!!.eventId)
    }

    @Test
    fun dismissIfCorrelated_nothingMatches_returnsFalse_stillCancelsNotification() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(false), notifier)
        center.publish(event().copy(eventId = "e1", correlationId = "unrelated", sourceId = "src-1"))

        val result = center.dismissIfCorrelated("src-x", "c-none")

        assertFalse(result)
        assertEquals(
            "cancel is a safe no-op, always attempted",
            listOf(InboundEventCenter.compositeKey("src-x", "c-none")),
            notifier.cancelled,
        )
    }

    // FRI-555 B3: an OpenClaw cancel must never purge a Hermes entry sharing the same
    // correlationId (or vice versa) — dismissIfCorrelated is scoped to BOTH keys.
    @Test
    fun dismissIfCorrelated_differentSourceId_sameCorrelation_doesNotAffectOtherSource() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(false), notifier)
        val hermesEvent = event().copy(eventId = "h-1", correlationId = "c-1", sourceId = "hermes-1")
        val openclawEvent = event().copy(eventId = "o-1", correlationId = "c-1", sourceId = "openclaw-1")
        center.publish(hermesEvent)
        center.publish(openclawEvent)

        val result = center.dismissIfCorrelated("openclaw-1", "c-1")

        assertTrue(result)
        assertEquals(
            "hermes entry sharing the correlationId but a different sourceId must survive",
            hermesEvent,
            center.resolve("h-1", null),
        )
        assertNull("openclaw entry purged", center.resolve("o-1", null))
    }

    @Test
    fun dismissIfCorrelated_differentSourceId_sameCorrelation_doesNotClearActiveCard() {
        val notifier = RecordingNotifier()
        val center = InboundEventCenter(FakeForeground(true), notifier)
        val hermesActive = event().copy(eventId = "h-2", correlationId = "c-2", sourceId = "hermes-1")
        center.publish(hermesActive)
        assertEquals(hermesActive, center.activeEvent.value)

        val result = center.dismissIfCorrelated("openclaw-1", "c-2")

        assertFalse("no matching source+correlation pair, dismiss reports false", result)
        assertEquals(
            "active card from a different source sharing the correlationId must survive",
            hermesActive,
            center.activeEvent.value,
        )
    }

    // FRI-555 B1: publish() arms the InboundConfirmationCenter on the POST-coercion kind only.
    @Test
    fun publish_verifiedConfirmation_armsConfirmationCenter() {
        val confirmationCenter = InboundConfirmationCenter()
        val center = InboundEventCenter(FakeForeground(true), RecordingNotifier(), confirmationCenter)
        center.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.VERIFIED).copy(eventId = "conf-1")
        )
        assertEquals("verified confirmation must arm the pending confirmation", "conf-1", confirmationCenter.pending.value!!.eventId)
    }

    @Test
    fun publish_pushConfirmation_isCoercedBeforeArming_neverArmsConfirmationCenter() {
        val confirmationCenter = InboundConfirmationCenter()
        val center = InboundEventCenter(FakeForeground(true), RecordingNotifier(), confirmationCenter)
        center.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.PUSH).copy(eventId = "conf-2")
        )
        assertNull("unverified/coerced push must never arm a pending confirmation", confirmationCenter.pending.value)
    }

    @Test
    fun dismissIfCorrelated_clearsPendingConfirmation_sharingCorrelation() {
        val confirmationCenter = InboundConfirmationCenter()
        val center = InboundEventCenter(FakeForeground(true), RecordingNotifier(), confirmationCenter)
        center.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.VERIFIED)
                .copy(eventId = "conf-3", correlationId = "corr-x", sourceId = "src-1")
        )
        assertEquals("conf-3", confirmationCenter.pending.value!!.eventId)

        val result = center.dismissIfCorrelated("src-1", "corr-x")

        assertTrue(result)
        assertNull("cancel must also clear the pending confirmation sharing this correlation", confirmationCenter.pending.value)
    }

    // FRI-555 B3: an OpenClaw cancel must never resolve a Hermes confirmation sharing the same
    // correlationId (or vice versa).
    @Test
    fun dismissIfCorrelated_differentSourceId_sameCorrelation_doesNotClearPendingConfirmation() {
        val confirmationCenter = InboundConfirmationCenter()
        val center = InboundEventCenter(FakeForeground(true), RecordingNotifier(), confirmationCenter)
        center.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.VERIFIED)
                .copy(eventId = "conf-4", correlationId = "corr-y", sourceId = "hermes-1")
        )
        assertEquals("conf-4", confirmationCenter.pending.value!!.eventId)

        val result = center.dismissIfCorrelated("openclaw-1", "corr-y")

        assertFalse(result)
        assertEquals(
            "pending confirmation from a different source sharing the correlationId must survive",
            "conf-4",
            confirmationCenter.pending.value!!.eventId,
        )
    }

    private class RecordingBridge : InboundActionBridge {
        var confirmedCount = 0
        var cancelledCount = 0
        var cancelled: PendingInboundConfirmation? = null

        override fun onConfirmed(confirmation: PendingInboundConfirmation) {
            confirmedCount++
        }

        override fun onCancelled(confirmation: PendingInboundConfirmation) {
            cancelled = confirmation
            cancelledCount++
        }
    }

    // FRI-555 B1: a verified CANCEL reaching InboundEventCenter.dismissIfCorrelated must resolve the
    // real pending confirmation through the bridge as CANCELLED (never CONFIRMED) -- proves the actual
    // production wiring (dismissIfCorrelated -> confirmationCenter.cancelByCorrelation -> bridge),
    // not just InboundConfirmationCenter in isolation.
    @Test
    fun dismissIfCorrelated_verifiedCancel_resolvesPendingConfirmationAsBridgeCancelled() {
        val bridge = RecordingBridge()
        val confirmationCenter = InboundConfirmationCenter(FakeEventStateStore(), bridge) { 1_000L }
        val center = InboundEventCenter(FakeForeground(true), RecordingNotifier(), confirmationCenter)
        center.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.VERIFIED)
                .copy(eventId = "conf-5", correlationId = "corr-z", sourceId = "src-9")
        )

        val result = center.dismissIfCorrelated("src-9", "corr-z")

        assertTrue(result)
        assertEquals(1, bridge.cancelledCount)
        assertEquals(0, bridge.confirmedCount)
        assertEquals("conf-5", bridge.cancelled!!.eventId)
        assertNull("resolved confirmation must clear the pending slot", confirmationCenter.pending.value)
    }

    // FRI-555 B1: a cold start (fresh InboundEventCenter/InboundConfirmationCenter over the SAME
    // durable EventStateStore, `recent` cache empty) tapping the notification for a durably-armed
    // verified confirmation must restore and surface a CONFIRMATION (requiresConfirmation == true),
    // never the coerced STATUS fallback a forged/unknown tap would get.
    @Test
    fun surfaceFromNotification_coldStart_matchingDurablePending_surfacesVerifiedConfirmation() {
        val store = FakeEventStateStore()
        val firstConfirmationCenter = InboundConfirmationCenter(store, RecordingBridge()) { 1_000L }
        val firstCenter = InboundEventCenter(FakeForeground(true), RecordingNotifier(), firstConfirmationCenter)
        firstCenter.publish(
            event(kind = InboundEventKind.CONFIRMATION, source = InboundSource.VERIFIED)
                .copy(eventId = "conf-6", correlationId = "corr-cold", sourceId = "src-cold", title = "Approve deploy", body = "Ship v2")
        )

        // Simulate process death: brand-new center/confirmationCenter instances, `recent` cache empty,
        // restored only from the shared durable store.
        val recreatedConfirmationCenter = InboundConfirmationCenter(store, RecordingBridge()) { 2_000L }
        val recreatedCenter = InboundEventCenter(FakeForeground(true), RecordingNotifier(), recreatedConfirmationCenter)
        val tapped = InboundEventCenter.reconstruct(
            eventId = "conf-6", correlationId = "corr-cold", kind = "confirmation",
            title = "ignored", body = "ignored", urgency = "normal", receivedAt = 0L,
        )!!

        recreatedCenter.surfaceFromNotification(tapped)

        val surfaced = recreatedCenter.activeEvent.value!!
        assertEquals("conf-6", surfaced.eventId)
        assertEquals(InboundEventKind.CONFIRMATION, surfaced.kind)
        assertTrue("cold-started tap of a durable verified confirmation must surface a confirmation, not STATUS", surfaced.requiresConfirmation)
        assertEquals(InboundSource.VERIFIED, surfaced.sourceType)
        assertEquals("Approve deploy", surfaced.title)
        assertEquals("Ship v2", surfaced.body)
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.repo.gateway.event.InboundActionBridge
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.dark.tool_neuron.repo.gateway.event.PendingInboundConfirmation
import com.dark.tool_neuron.viewmodel.FridayEventsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

// The notification-tap consumer: a pending id resolves the retained event into activeEvent, exactly
// once; an unknown id surfaces nothing. Background publish never auto-surfaces a card.
@OptIn(ExperimentalCoroutinesApi::class)
class FridayEventsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun localEvent(id: String, correlationId: String? = null) = InboundEvent(
        eventId = id,
        sourceType = InboundSource.LOCAL,
        correlationId = correlationId,
        kind = InboundEventKind.STATUS,
        title = "t",
        body = "b",
        urgency = InboundUrgency.NORMAL,
        receivedAt = 0L,
    )

    // What a tapped notification hands back: a PUSH-sourced event reconstructed from intent extras.
    private fun pushEvent(id: String, correlationId: String? = null, kind: InboundEventKind = InboundEventKind.STATUS) = InboundEvent(
        eventId = id,
        sourceType = InboundSource.PUSH,
        correlationId = correlationId,
        kind = kind,
        title = "t",
        body = "b",
        urgency = InboundUrgency.NORMAL,
        receivedAt = 0L,
    )

    private class FakeForeground(val fg: Boolean) : com.dark.tool_neuron.repo.ForegroundSignal {
        override fun isForeground(): Boolean = fg
    }

    private class NoopNotifier : com.dark.tool_neuron.repo.EventNotifier {
        override fun notify(event: InboundEvent, channelId: String) {}
        override fun cancel(notificationKey: String) {}
    }

    private class RecordingNotifier : com.dark.tool_neuron.repo.EventNotifier {
        val cancelled = mutableListOf<String>()
        override fun notify(event: InboundEvent, channelId: String) {}
        override fun cancel(notificationKey: String) { cancelled += notificationKey }
    }

    // FRI-555 R3-1: records confirm/cancel outcomes without hitting the durable
    // RecordingInboundActionBridge, mirroring InboundEventCenterTest's fake.
    private class RecordingBridge : InboundActionBridge {
        var confirmedCount = 0
        var cancelledCount = 0
        override fun onConfirmed(confirmation: PendingInboundConfirmation) { confirmedCount++ }
        override fun onCancelled(confirmation: PendingInboundConfirmation) { cancelledCount++ }
    }

    private fun center() = InboundEventCenter(FakeForeground(false), NoopNotifier())

    @Test
    fun pendingEvent_resurfacesRetainedCopy_intoActiveEvent() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        val retained = localEvent("e1")
        center.publish(retained)
        assertNull("background publish must not auto-surface", center.activeEvent.value)
        val vm = FridayEventsViewModel(center, pending, InboundConfirmationCenter())
        // Notification tap hands back a reconstructed PUSH copy; resolve prefers the retained LOCAL one.
        pending.set(pushEvent("e1"))
        advanceUntilIdle()
        assertEquals(retained, vm.activeEvent.value)
    }

    @Test
    fun forgedPendingEvent_coldStartsAsCappedStatusCard() = runTest {
        // Process death cleared `recent`, so resolve() misses; the reconstructed (attacker-reachable)
        // extras are re-coerced — a forged CONFIRMATION surfaces only as a non-actionable STATUS card.
        val center = center()
        val pending = PendingInboundEvent()
        val vm = FridayEventsViewModel(center, pending, InboundConfirmationCenter())
        pending.set(pushEvent("forged", kind = InboundEventKind.CONFIRMATION))
        advanceUntilIdle()
        val surfaced = vm.activeEvent.value
        assertEquals("forged", surfaced!!.eventId)
        assertEquals(InboundEventKind.STATUS, surfaced.kind)
        assertNull("re-coerced card is never actionable", surfaced.requiresConfirmation.takeIf { it })
    }

    @Test
    fun consumeIsOneShot_dismissedEventDoesNotReSurfaceOnItsOwn() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        center.publish(localEvent("e1"))
        val vm = FridayEventsViewModel(center, pending, InboundConfirmationCenter())
        pending.set(pushEvent("e1"))
        advanceUntilIdle()
        assertEquals("e1", vm.activeEvent.value!!.eventId)
        vm.dismiss()
        advanceUntilIdle()
        assertNull("one-shot consume must not re-fire the same intent", vm.activeEvent.value)
    }

    // FRI-555 R3-1: confirmInbound/cancelInbound must delegate to the center's teardown path, which
    // clears the pending slot AND the active card, the retained recent entry, and the OS
    // notification — not just resolve the pending confirmation record. The VM's `center` and
    // `confirmationCenter` constructor args must be the SAME wired instance for this to hold, since
    // confirmInbound() now calls through center.confirmActiveConfirmation() rather than the
    // confirmationCenter field directly.
    private fun confirmationEvent(id: String, correlationId: String?, sourceId: String?) = InboundEvent(
        eventId = id,
        sourceType = InboundSource.VERIFIED,
        correlationId = correlationId,
        kind = InboundEventKind.CONFIRMATION,
        title = "t",
        body = "b",
        urgency = InboundUrgency.NORMAL,
        receivedAt = 0L,
        sourceId = sourceId,
    )

    @Test
    fun confirmInbound_delegatesToCenter_tearsDownCardRecentAndNotification() {
        val notifier = RecordingNotifier()
        val bridge = RecordingBridge()
        val confirmationCenter = InboundConfirmationCenter(FakeEventStateStore(), bridge) { 1_000L }
        val center = InboundEventCenter(FakeForeground(true), notifier, confirmationCenter)
        center.publish(confirmationEvent("conf-vm-1", "corr-vm-1", "src-vm-1"))
        val vm = FridayEventsViewModel(center, PendingInboundEvent(), confirmationCenter)
        assertEquals("conf-vm-1", vm.pendingInboundConfirmation.value!!.eventId)
        assertEquals("conf-vm-1", vm.activeEvent.value!!.eventId)

        vm.confirmInbound()

        assertNull("confirmInbound must delegate to the center and clear the pending record", vm.pendingInboundConfirmation.value)
        assertNull("active card must be torn down", vm.activeEvent.value)
        assertNull("retained recent entry must be purged", center.resolve("conf-vm-1", null))
        assertEquals(listOf(InboundEventCenter.compositeKey("src-vm-1", "corr-vm-1")), notifier.cancelled)
        assertEquals(1, bridge.confirmedCount)
        assertEquals(0, bridge.cancelledCount)
    }

    @Test
    fun cancelInbound_delegatesToCenter_tearsDownCardRecentAndNotification() {
        val notifier = RecordingNotifier()
        val bridge = RecordingBridge()
        val confirmationCenter = InboundConfirmationCenter(FakeEventStateStore(), bridge) { 1_000L }
        val center = InboundEventCenter(FakeForeground(true), notifier, confirmationCenter)
        center.publish(confirmationEvent("conf-vm-2", "corr-vm-2", "src-vm-2"))
        val vm = FridayEventsViewModel(center, PendingInboundEvent(), confirmationCenter)
        assertEquals("conf-vm-2", vm.pendingInboundConfirmation.value!!.eventId)

        vm.cancelInbound()

        assertNull("cancelInbound must delegate to the center and clear the pending record", vm.pendingInboundConfirmation.value)
        assertNull("active card must be torn down", vm.activeEvent.value)
        assertNull("retained recent entry must be purged", center.resolve("conf-vm-2", null))
        assertEquals(listOf(InboundEventCenter.compositeKey("src-vm-2", "corr-vm-2")), notifier.cancelled)
        assertEquals(0, bridge.confirmedCount)
        assertEquals(1, bridge.cancelledCount)
    }
}

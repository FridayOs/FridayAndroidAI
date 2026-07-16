package com.dark.tool_neuron

import com.dark.tool_neuron.data.PendingInboundEvent
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.InboundEventCenter
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
    }

    private fun center() = InboundEventCenter(FakeForeground(false), NoopNotifier())

    @Test
    fun pendingEvent_resurfacesRetainedCopy_intoActiveEvent() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        val retained = localEvent("e1")
        center.publish(retained)
        assertNull("background publish must not auto-surface", center.activeEvent.value)
        val vm = FridayEventsViewModel(center, pending)
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
        val vm = FridayEventsViewModel(center, pending)
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
        val vm = FridayEventsViewModel(center, pending)
        pending.set(pushEvent("e1"))
        advanceUntilIdle()
        assertEquals("e1", vm.activeEvent.value!!.eventId)
        vm.dismiss()
        advanceUntilIdle()
        assertNull("one-shot consume must not re-fire the same intent", vm.activeEvent.value)
    }
}

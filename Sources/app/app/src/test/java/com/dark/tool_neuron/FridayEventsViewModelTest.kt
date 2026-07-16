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

    private class FakeForeground(val fg: Boolean) : com.dark.tool_neuron.repo.ForegroundSignal {
        override fun isForeground(): Boolean = fg
    }

    private class NoopNotifier : com.dark.tool_neuron.repo.EventNotifier {
        override fun notify(event: InboundEvent, channelId: String) {}
    }

    private fun center() = InboundEventCenter(FakeForeground(false), NoopNotifier())

    @Test
    fun pendingId_resolvesRetainedEvent_intoActiveEvent() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        val e = localEvent("e1")
        center.publish(e)
        assertNull("background publish must not auto-surface", center.activeEvent.value)
        val vm = FridayEventsViewModel(center, pending)
        pending.set("e1", null)
        advanceUntilIdle()
        assertEquals(e, vm.activeEvent.value)
    }

    @Test
    fun unknownPendingId_leavesActiveEventNull() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        val vm = FridayEventsViewModel(center, pending)
        pending.set("forged", null)
        advanceUntilIdle()
        assertNull(vm.activeEvent.value)
    }

    @Test
    fun consumeIsOneShot_dismissedEventDoesNotReSurfaceOnItsOwn() = runTest {
        val center = center()
        val pending = PendingInboundEvent()
        center.publish(localEvent("e1"))
        val vm = FridayEventsViewModel(center, pending)
        pending.set("e1", null)
        advanceUntilIdle()
        assertEquals("e1", vm.activeEvent.value!!.eventId)
        vm.dismiss()
        advanceUntilIdle()
        assertNull("one-shot consume must not re-fire the same intent", vm.activeEvent.value)
    }
}

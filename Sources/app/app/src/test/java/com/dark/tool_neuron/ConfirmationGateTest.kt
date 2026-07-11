package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.ConfirmationGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmationGateTest {

    @Test
    fun confirmResolvesArmedLatchToTrue() = runTest {
        val gate = ConfirmationGate()
        val latch = gate.arm()
        assertTrue("gate must report awaiting once armed", gate.awaiting.value)
        assertTrue("confirm must resolve a pending latch", gate.confirm())
        assertEquals(true, latch.await())
        assertFalse("awaiting clears after confirm", gate.awaiting.value)
    }

    @Test
    fun cancelResolvesArmedLatchToFalse() = runTest {
        val gate = ConfirmationGate()
        val latch = gate.arm()
        assertTrue(gate.cancel())
        assertEquals(false, latch.await())
        assertFalse(gate.awaiting.value)
    }

    @Test
    fun confirmWithNothingArmedReturnsFalse() {
        val gate = ConfirmationGate()
        assertFalse("confirm is a no-op when nothing is awaiting", gate.confirm())
        assertFalse(gate.awaiting.value)
    }

    @Test
    fun armingTwiceFailsThePriorLatch() = runTest {
        val gate = ConfirmationGate()
        val first = gate.arm()
        val second = gate.arm()
        assertEquals("a superseded latch resolves to false", false, first.await())
        assertTrue(gate.confirm())
        assertEquals(true, second.await())
    }
}

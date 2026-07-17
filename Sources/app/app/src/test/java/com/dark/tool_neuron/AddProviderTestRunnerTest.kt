package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import com.dark.tool_neuron.viewmodel.AddProviderTestRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Race coverage for AddProviderTestRunner (FRI-582 P8, adversarial-review follow-up).
 *
 * The runner must guarantee a probe result publishes ONLY when its launch generation is still
 * current — proven independent of coroutine cancellation. The fake probe below is deliberately
 * NON-cooperative: it catches CancellationException and still returns Ready on a non-cancellable
 * gate. So if the explicit generation check were removed, these tests would fail even though the
 * job was cancelled — that is what distinguishes "guard works" from "cancellation happened to win".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProviderTestRunnerTest {

    /** A probe that ignores cancellation: awaits [gate] under NonCancellable, returns whatever it yields. */
    private fun nonCooperativeProbe(gate: CompletableDeferred<GatewayTestResult>): suspend () -> GatewayTestResult = {
        try {
            gate.await()
        } catch (ce: CancellationException) {
            // Simulate a client that swallows cancellation and still produces a result.
            withContext(NonCancellable) { gate.await() }
        }
    }

    @Test
    fun `stale probe result is discarded after invalidate, even when probe ignores cancellation`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val gate = CompletableDeferred<GatewayTestResult>()
        var published: GatewayTestResult? = null
        var started = false

        runner.run(
            probe = nonCooperativeProbe(gate),
            onStart = { started = true },
            onResult = { published = it },
            onFailure = {},
        )
        advanceUntilIdle()
        assertTrue("onStart runs synchronously on launch", started)

        // User edits a credential field mid-probe → generation bumps + job cancelled.
        runner.invalidate()
        // Non-cooperative probe now yields Ready DESPITE the cancellation.
        gate.complete(GatewayTestResult.Ready)
        advanceUntilIdle()

        assertNull("stale Ready must NOT publish after invalidate (generation guard, not cancellation)", published)
    }

    @Test
    fun `late result is discarded after cancel (teardown) when probe ignores cancellation`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val gate = CompletableDeferred<GatewayTestResult>()
        var published: GatewayTestResult? = null

        runner.run(
            probe = nonCooperativeProbe(gate),
            onStart = {},
            onResult = { published = it },
            onFailure = {},
        )
        advanceUntilIdle()

        // ViewModel teardown mid-probe.
        runner.cancel()
        gate.complete(GatewayTestResult.Ready)
        advanceUntilIdle()

        assertNull("result after teardown must NOT publish (cancel bumps generation)", published)
    }

    @Test
    fun `current probe result publishes when not superseded`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val gate = CompletableDeferred<GatewayTestResult>()
        var published: GatewayTestResult? = null

        runner.run(
            probe = { gate.await() },
            onStart = {},
            onResult = { published = it },
            onFailure = {},
        )
        advanceUntilIdle()
        gate.complete(GatewayTestResult.Ready)
        advanceUntilIdle()

        assertEquals(GatewayTestResult.Ready, published)
    }

    @Test
    fun `a newer test supersedes an older in-flight probe`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val firstGate = CompletableDeferred<GatewayTestResult>()
        val secondGate = CompletableDeferred<GatewayTestResult>()
        val published = mutableListOf<GatewayTestResult>()

        runner.run(probe = nonCooperativeProbe(firstGate), onStart = {}, onResult = { published += it }, onFailure = {})
        advanceUntilIdle()
        // Second test starts before the first returns → supersedes it.
        runner.run(probe = { secondGate.await() }, onStart = {}, onResult = { published += it }, onFailure = {})
        advanceUntilIdle()

        // First (stale) yields Ready despite being superseded; then the second (current) completes.
        firstGate.complete(GatewayTestResult.Ready)
        advanceUntilIdle()
        secondGate.complete(GatewayTestResult.Ready)
        advanceUntilIdle()

        assertEquals("only the current test's result publishes", 1, published.size)
    }

    @Test
    fun `unexpected probe exception invokes onFailure, never leaves caller in TESTING`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val gate = CompletableDeferred<GatewayTestResult>()
        var published: GatewayTestResult? = null
        var failed = false

        runner.run(
            probe = { gate.await(); throw IllegalStateException("malformed response") },
            onStart = {},
            onResult = { published = it },
            onFailure = { failed = true },
        )
        advanceUntilIdle()
        gate.complete(GatewayTestResult.Ready) // unblocks probe, which then throws
        advanceUntilIdle()

        assertTrue("non-cancellation exception must route to onFailure", failed)
        assertNull("no result should publish on failure", published)
    }

    @Test
    fun `stale probe exception is discarded after invalidate`() = runTest {
        val runner = AddProviderTestRunner(TestScope(StandardTestDispatcher(testScheduler)))
        val gate = CompletableDeferred<Unit>()
        var failed = false

        runner.run(
            probe = {
                try {
                    gate.await()
                } catch (ce: CancellationException) {
                    withContext(NonCancellable) { gate.await() }
                }
                throw IllegalStateException("late failure")
            },
            onStart = {},
            onResult = {},
            onFailure = { failed = true },
        )
        advanceUntilIdle()
        runner.invalidate() // superseded by a field edit
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("a stale failure must NOT publish either (generation guard)", !failed)
    }
}

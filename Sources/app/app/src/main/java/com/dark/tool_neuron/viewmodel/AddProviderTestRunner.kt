package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Generation-guarded orchestration for the Add Provider connection probe (FRI-582 P8).
 *
 * A probe result may ONLY publish if its launch generation is still current. Every
 * connection-relevant field edit (via [invalidate]) and every new [run] bumps the generation, so a
 * stale probe — superseded by an edit or a newer test — can never publish its result. Crucially the
 * guard is an explicit generation check, NOT reliance on coroutine cancellation: even a client that
 * suppresses cancellation and returns Ready after an edit cannot restore a stale READY, closing the
 * race flagged in adversarial review. Kept free of Android/Hilt so it is directly JVM-unit-testable
 * (see AddProviderTestRunnerTest); the ViewModel owns only StateFlow wiring.
 *
 * Generation mutation and result publication both run on [scope]'s (Main-confined) dispatcher, so no
 * lock is needed — reads/writes of [generation] are serialized with the setters that call invalidate.
 */
class AddProviderTestRunner(private val scope: CoroutineScope) {

    private var generation = 0
    private var job: Job? = null

    /** Invalidate any in-flight/completed probe: the current result loses the right to publish. */
    fun invalidate() {
        generation++
        job?.cancel()
        job = null
    }

    /**
     * Launch [probe] under a fresh generation. [onStart] runs synchronously (e.g. status→TESTING).
     * [onResult] runs only if this launch is still the current generation when the probe returns.
     * [onFailure] runs (also generation-guarded) if the probe throws a non-cancellation exception —
     * so an unexpected transport/parse/runtime error can never leave the UI stuck in TESTING.
     * CancellationException is always re-thrown (never surfaced as a failure).
     */
    fun run(
        probe: suspend () -> GatewayTestResult,
        onStart: () -> Unit,
        onResult: (GatewayTestResult) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        job?.cancel()
        val launchGeneration = ++generation
        onStart()
        job = scope.launch {
            val result = try {
                probe()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Explicit ownership check — a stale failure must not publish either.
                if (launchGeneration == generation) onFailure(t)
                return@launch
            }
            // Explicit ownership check — independent of whether probe honored cancellation.
            if (launchGeneration != generation) return@launch
            onResult(result)
        }
    }

    /**
     * Teardown (e.g. ViewModel.onCleared). Bumps the generation BEFORE cancelling so a late result
     * from a non-cooperative probe that ignores cancellation still fails the ownership check and
     * cannot mutate state after teardown.
     */
    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }
}

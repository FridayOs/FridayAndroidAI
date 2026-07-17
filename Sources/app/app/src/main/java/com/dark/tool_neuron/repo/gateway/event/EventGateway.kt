package com.dark.tool_neuron.repo.gateway.event

import android.util.Log
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventEnvelope
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555: verified inbound event orchestrator sitting in front of the existing display/delivery
// scaffold (InboundEventCenter, bound as EventDeliverySink). Pipeline: consent gate -> parse ->
// verify -> policy -> deliver. Fail-closed at every stage; no side effect on any rejection. Logs
// redact everything except a short machine reason/id — never signature, key material, or payload.
@Singleton
class EventGateway internal constructor(
    private val consent: InboundEventConsent,
    private val verifier: InboundEventVerifier,
    private val policy: InboundEventPolicy,
    private val sink: EventDeliverySink,
    private val correlationTracker: CorrelationTracker,
    private val nowMs: () -> Long,
) {
    // Dagger does not resolve Kotlin default-parameter values for injection, so production wiring
    // goes through this explicit secondary constructor (mirrors InboundEventCenter's pattern)
    // instead of relying on `nowMs` defaulting at the injection site.
    @Inject constructor(
        consent: InboundEventConsent,
        verifier: InboundEventVerifier,
        policy: InboundEventPolicy,
        sink: EventDeliverySink,
        correlationTracker: CorrelationTracker,
    ) : this(consent, verifier, policy, sink, correlationTracker, { System.currentTimeMillis() })

    // Returns true only when the envelope resulted in a delivery or a matched cancel; false for
    // every rejection/drop path. Callers must not infer anything about the reason from the boolean.
    fun accept(raw: Map<String, String>): Boolean {
        if (!consent.inboundEnabled()) return false

        val env = InboundEventEnvelope.parse(raw) ?: run {
            Log.i(TAG, "dropped (malformed)")
            return false
        }

        val now = nowMs()
        val verifyResult = verifier.verify(env, now)
        if (verifyResult is VerifyResult.Rejected) {
            Log.i(TAG, "dropped (${verifyResult.reason})")
            return false
        }

        return when (val decision = policy.decide(env, now)) {
            is PolicyDecision.Deliver -> {
                // Out-of-order guard: a same-correlation envelope that isn't newer than the last
                // accepted one (e.g. a delayed PROGRESS arriving after COMPLETION) is dropped with
                // no publish. No correlationId means no ordering constraint (always accepted).
                if (!correlationTracker.accept(env.sourceId, env.correlationId, env.issuedAtMs)) {
                    Log.i(TAG, "dropped (stale)")
                    return false
                }
                sink.publish(decision.event)
                true
            }
            is PolicyDecision.Cancel -> {
                // Same ordering guard as Deliver: a legitimately-signed, non-replay CANCEL that
                // arrives network-reordered after a newer PROGRESS/COMPLETION for the same
                // correlation must not tear down an already-progressed task. accept() performs the
                // monotonic check AND records issuedAt; clear() then removes the key entirely so a
                // later Deliver for a fresh correlation isn't blocked by this stale bookkeeping.
                val correlationId = decision.correlationId ?: return false
                if (!correlationTracker.accept(env.sourceId, correlationId, env.issuedAtMs)) {
                    Log.i(TAG, "dropped (stale)")
                    return false
                }
                correlationTracker.clear(env.sourceId, correlationId)
                sink.dismissIfCorrelated(correlationId)
            }
            is PolicyDecision.Drop -> {
                Log.i(TAG, "dropped (${decision.reason})")
                false
            }
        }
    }

    companion object {
        private const val TAG = "EventGateway"
    }
}

// Colocated seam (mirrors InboundEventCenter's ForegroundSignal/EventNotifier pattern) so
// EventGateway can deliver/cancel without InboundEventPort.kt needing a new method — that port
// stays producer-agnostic and unmodified. InboundEventCenter.publish already satisfies the first
// method; only dismissIfCorrelated is new.
interface EventDeliverySink {
    fun publish(event: InboundEvent)
    fun dismissIfCorrelated(correlationId: String): Boolean
}

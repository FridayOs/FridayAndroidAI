package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.model.friday.InboundEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 B1: at most one pending inbound confirmation, resolved ONLY by an explicit user
// confirm()/cancel() tap. Durable across process death (persisted via EventStateStore) and
// correlated to the InboundActionBridge approval seam — confirm/cancel record a distinct outcome
// through the bridge, but this class NEVER calls any action executor itself: actionIntent is
// carried purely for display. Re-arming (a newer verified CONFIRMATION_REQUESTED for a different
// eventId) supersedes and implicitly cancels whatever was previously pending, mirroring
// ConfirmationGate's single-slot semantics.
@Singleton
class InboundConfirmationCenter internal constructor(
    private val store: EventStateStore,
    private val bridge: InboundActionBridge,
    private val nowMs: () -> Long,
) {

    @Inject constructor(store: EventStateStore, bridge: InboundActionBridge) : this(
        store,
        bridge,
        System::currentTimeMillis,
    )

    // No-arg convenience constructor: preserves InboundEventCenter's existing
    // `= InboundConfirmationCenter()` default parameter and the many pre-B1 2-arg
    // InboundEventCenterTest call sites that never wire a confirmation center explicitly.
    // Backed by trivial in-memory/no-op collaborators — production always resolves through the
    // @Inject constructor above with the real Hilt-provided EventStateStore/InboundActionBridge.
    constructor() : this(InMemoryEventStateStore(), NoOpInboundActionBridge(), System::currentTimeMillis)

    private val _pending = MutableStateFlow(loadPending())
    val pending: StateFlow<PendingInboundConfirmation?> = _pending.asStateFlow()

    // FRI-555 R4-1: @Synchronized so arm/confirm/cancel/cancelByCorrelation serialize on `this` --
    // a UI confirm() racing an FCM-thread cancelByCorrelation() (or a re-arm racing a resolve) can
    // never both observe the same pending record; exactly one caller wins the claim.
    @Synchronized
    fun arm(event: InboundEvent) {
        val record = PendingInboundConfirmation(
            eventId = event.eventId,
            sourceId = event.sourceId,
            correlationId = event.correlationId,
            title = event.title,
            body = event.body,
            actionIntent = event.actionIntent,
            armedAtMs = nowMs(),
        )
        _pending.value = record
        store.write(PENDING_KEY, serialize(record))
    }

    // Resolves the pending confirmation as accepted, recording the outcome through the bridge.
    // False (safe no-op) when nothing is pending or the id doesn't match the currently armed one
    // (stale UI tap after a re-arm/cancel).
    @Synchronized
    fun confirm(eventId: String): Boolean = resolve({ it.eventId == eventId }, bridge::onConfirmed)

    @Synchronized
    fun cancel(eventId: String): Boolean = resolve({ it.eventId == eventId }, bridge::onCancelled)

    // FRI-555 B3 integration point: a verified CANCEL for a source+correlation also clears any
    // pending inbound confirmation sharing BOTH keys (dismissIfCorrelated calls this) -- scoped so an
    // OpenClaw cancel can never resolve a Hermes confirmation sharing the same correlationId.
    @Synchronized
    fun cancelByCorrelation(sourceId: String, correlationId: String): Boolean =
        resolve({ it.sourceId == sourceId && it.correlationId == correlationId }, bridge::onCancelled)

    // FRI-555 R4-1: only ever called from the @Synchronized public methods above, so it always
    // runs under this monitor -- claim (clear state+store) BEFORE invoking the bridge so a losing
    // concurrent resolver sees the record already gone (or a different re-armed record) and
    // returns false instead of double-resolving.
    private fun resolve(
        matches: (PendingInboundConfirmation) -> Boolean,
        onResolved: (PendingInboundConfirmation) -> Unit,
    ): Boolean {
        val current = _pending.value ?: return false
        if (!matches(current)) return false
        _pending.value = null
        store.write(PENDING_KEY, "")
        onResolved(current)
        return true
    }

    private fun loadPending(): PendingInboundConfirmation? {
        val raw = store.read(PENDING_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return deserialize(raw)
    }

    private companion object {
        const val PENDING_KEY = "fri555.pending_confirm"
        const val NULL_SENTINEL = "-"

        // Fields (eventId/title/body are validated clean per B4, but title/body are free-text so
        // this still Base64-encodes every text field to stay delimiter-safe on a single tab-joined
        // line) — armedAtMs is a plain decimal, never user-controlled content.
        fun serialize(p: PendingInboundConfirmation): String {
            val encoder = Base64.getEncoder()
            fun enc(s: String?): String = if (s == null) NULL_SENTINEL else encoder.encodeToString(s.toByteArray(Charsets.UTF_8))
            return listOf(
                enc(p.eventId),
                enc(p.sourceId),
                enc(p.correlationId),
                enc(p.title),
                enc(p.body),
                enc(p.actionIntent),
                p.armedAtMs.toString(),
            ).joinToString("\t")
        }

        // Unparseable/corrupt persisted lines are dropped, never crash the center (defense-in-depth,
        // mirrors CorrelationTracker/NonceStore/DedupeStore's tolerant store parsing).
        fun deserialize(raw: String): PendingInboundConfirmation? {
            val parts = raw.split("\t")
            if (parts.size != 7) return null
            val decoder = Base64.getDecoder()
            fun dec(s: String): String? {
                if (s == NULL_SENTINEL) return null
                return runCatching { String(decoder.decode(s), Charsets.UTF_8) }.getOrNull()
            }
            val eventId = parts[0].let { if (it == NULL_SENTINEL) null else dec(it) } ?: return null
            val sourceId = dec(parts[1])
            val correlationId = dec(parts[2])
            val title = parts[3].let { if (it == NULL_SENTINEL) null else dec(it) } ?: return null
            val body = parts[4].let { if (it == NULL_SENTINEL) null else dec(it) } ?: return null
            val actionIntent = dec(parts[5])
            val armedAtMs = parts[6].toLongOrNull() ?: return null
            return PendingInboundConfirmation(
                eventId = eventId,
                sourceId = sourceId,
                correlationId = correlationId,
                title = title,
                body = body,
                actionIntent = actionIntent,
                armedAtMs = armedAtMs,
            )
        }
    }
}

// Display-only snapshot of a verified inbound confirmation. actionIntent is data, never executed.
data class PendingInboundConfirmation(
    val eventId: String,
    val sourceId: String?,
    val correlationId: String?,
    val title: String,
    val body: String,
    val actionIntent: String?,
    val armedAtMs: Long,
)

// Ephemeral, non-durable EventStateStore backing the no-arg InboundConfirmationCenter()
// convenience constructor only — never wired in production (Hilt always injects
// PrefsEventStateStore via the @Inject constructor).
private class InMemoryEventStateStore : EventStateStore {
    private val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String) {
        map[key] = value
    }
}

// No-op InboundActionBridge backing the no-arg InboundConfirmationCenter() convenience
// constructor only — never wired in production (Hilt always injects RecordingInboundActionBridge
// via the @Inject constructor).
private class NoOpInboundActionBridge : InboundActionBridge {
    override fun onConfirmed(confirmation: PendingInboundConfirmation) = Unit
    override fun onCancelled(confirmation: PendingInboundConfirmation) = Unit
}

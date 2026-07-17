package com.dark.tool_neuron.repo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.dark.tool_neuron.activity.MainActivity
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.model.friday.InboundEventKind
import com.dark.tool_neuron.model.friday.InboundSource
import com.dark.tool_neuron.model.friday.InboundUrgency
import com.dark.tool_neuron.repo.gateway.event.CancelClaim
import com.dark.tool_neuron.repo.gateway.event.EventDeliverySink
import com.dark.tool_neuron.repo.gateway.event.InboundConfirmationCenter
import com.friday.ai.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Reads the process foreground state so publish() can pick card-vs-notification. Seam so JVM tests drive it.
internal interface ForegroundSignal {
    fun isForeground(): Boolean
}

// Delivers a system notification for an event. Seam so JVM tests assert channel routing without Android.
internal interface EventNotifier {
    fun notify(event: InboundEvent, channelId: String)

    // FRI-555 B3: cancels the OS notification posted under notificationKeyFor(event)'s id. Safe to
    // call even when nothing is currently posted under that key (no-op).
    fun cancel(notificationKey: String)
}

// Single consumer of InboundEventPort. Foreground -> in-app card (activeEvent flow); background ->
// system notification on a channel picked by urgency. Push-sourced events are conservatively coerced
// (CONFIRMATION downgraded, length-capped) since FRI-555 verification hasn't landed. No persistence,
// no runtime fake events — producers call publish(); tests construct events through the port.
@Singleton
class InboundEventCenter internal constructor(
    private val foreground: ForegroundSignal,
    private val notifier: EventNotifier,
    // FRI-555 B1: default keeps every pre-B1 2-arg test call site compiling unchanged; production
    // wiring goes through the @Inject constructor below with the real Hilt-provided singleton.
    private val confirmationCenter: InboundConfirmationCenter = InboundConfirmationCenter(),
) : InboundEventPort, EventDeliverySink {

    @Inject constructor(@ApplicationContext context: Context, confirmationCenter: InboundConfirmationCenter) : this(
        ProcessForegroundSignal(),
        AndroidEventNotifier(context),
        confirmationCenter,
    )

    private val _activeEvent = MutableStateFlow<InboundEvent?>(null)
    val activeEvent: StateFlow<InboundEvent?> = _activeEvent.asStateFlow()

    // Bounded in-memory retention so a background event tap can re-surface its (already-coerced) card.
    // LRU access-order, capped — no persistence (FRI-555 owns durable delivery), no content logging.
    private val recent = object : LinkedHashMap<String, InboundEvent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, InboundEvent>): Boolean =
            size > RECENT_CAP
    }

    override fun publish(event: InboundEvent) {
        val safe = when (event.sourceType) {
            InboundSource.PUSH -> coercePush(event)
            InboundSource.VERIFIED -> event.copy(
                title = event.title.take(TITLE_CAP),
                body = event.body.take(BODY_CAP),
                // FRI-555 R3-2: cap actionIntent alongside title/body so the capped value flows into
                // both confirmationCenter.arm (persistence) and _activeEvent (UI) -- ConfirmationCard's
                // "already length-capped upstream" comment is enforced here.
                actionIntent = event.actionIntent?.take(ACTION_CAP),
            )
            InboundSource.LOCAL -> event
        }
        // A retained VERIFIED entry must never be clobbered by a lower-trust (PUSH) publish sharing
        // the same eventId — otherwise a later notification tap would resolve the downgraded copy
        // and hide the legitimate verified card.
        synchronized(recent) {
            val existing = recent[safe.eventId]
            val protectVerified = existing?.sourceType == InboundSource.VERIFIED && safe.sourceType != InboundSource.VERIFIED
            if (!protectVerified) recent[safe.eventId] = safe
        }
        // FRI-555 B1: arm on the POST-coercion kind only — coercePush() already downgrades any PUSH
        // CONFIRMATION to STATUS above, so this alone guarantees an unverified/coerced push can never
        // arm a pending confirmation; only a genuinely VERIFIED (or LOCAL) CONFIRMATION reaches here.
        // Defense-in-depth: kind==CONFIRMATION already implies VERIFIED today (coercePush downgrades
        // PUSH confirmations to STATUS before this point), but gate on sourceType explicitly too so a
        // future LOCAL confirmation producer can never arm without going through verification.
        // FRI-555 R7-1: arm() captures the confirmation's OWN fresh generation; a superseding
        // non-arming delivery (STATUS/PROGRESS for the same identity) instead calls noteState() to
        // bump the generation and invalidate any earlier resolution's teardown-suppression. Routing
        // the arming delivery through arm() ONLY (never noteState) keeps the round-5 confirm-vs-CANCEL
        // race exactly-once: the confirmation's own publish must not bump its own generation, else the
        // losing CANCEL would mismatch and double-tear-down.
        val armed = safe.sourceType == InboundSource.VERIFIED && safe.kind == InboundEventKind.CONFIRMATION
        if (armed) {
            confirmationCenter.arm(safe)
        } else if (safe.sourceId != null && safe.correlationId != null) {
            confirmationCenter.noteState(safe.sourceId, safe.correlationId)
        }
        if (foreground.isForeground()) {
            _activeEvent.value = safe
        } else {
            notifier.notify(safe, channelIdFor(safe.urgency))
        }
    }

    // Look up a retained event by its notification's display keys. Correlation is checked only when
    // both sides carry one; a forged/unknown id (never published by us) returns null. No auth here —
    // resolution only re-surfaces an event we already published and coerced.
    fun resolve(eventId: String, correlationId: String?): InboundEvent? {
        val event = synchronized(recent) { recent[eventId] } ?: return null
        if (correlationId != null && event.correlationId != null && event.correlationId != correlationId) return null
        return event
    }

    fun surface(event: InboundEvent) { _activeEvent.value = event }

    // A tapped notification hands back the event reconstructed from its full-payload extras. Prefer the
    // retained (already-coerced) copy; if `recent` was cleared (process death), re-coerce the reconstructed
    // one so the card still surfaces. Re-coercion is why the extras being attacker-reachable on the exported
    // activity is safe: a forged CONFIRMATION cold-starts only as a capped, non-actionable STATUS card.
    // FRI-555 B1: EXCEPT when a durable verified pending confirmation exists for this exact tapped
    // eventId (or its source+correlation) -- then that verified record, never the untrusted PUSH-tagged
    // extras, is what gets (re)armed and surfaced, so a real confirmation cold-starts as a confirmation
    // instead of being downgraded to STATUS. A forged tap that doesn't match any durable pending
    // confirmation still falls through to the existing re-coerce-to-STATUS path below.
    fun surfaceFromNotification(reconstructed: InboundEvent) {
        val durablePending = confirmationCenter.pending.value
        val matchesPending = durablePending != null && (
            durablePending.eventId == reconstructed.eventId ||
                (
                    durablePending.sourceId != null && durablePending.sourceId == reconstructed.sourceId &&
                        durablePending.correlationId != null && durablePending.correlationId == reconstructed.correlationId
                    )
            )
        if (durablePending != null && matchesPending) {
            val verified = InboundEvent(
                eventId = durablePending.eventId,
                sourceType = InboundSource.VERIFIED,
                correlationId = durablePending.correlationId,
                kind = InboundEventKind.CONFIRMATION,
                title = durablePending.title,
                body = durablePending.body,
                urgency = reconstructed.urgency,
                receivedAt = reconstructed.receivedAt,
                actionIntent = durablePending.actionIntent,
                sourceId = durablePending.sourceId,
            )
            confirmationCenter.arm(verified)
            surface(verified)
            return
        }
        surface(resolve(reconstructed.eventId, reconstructed.correlationId) ?: coercePush(reconstructed))
    }

    fun dismiss() { _activeEvent.value = null }

    // FRI-555 B3: verified CANCEL support, scoped to sourceId+correlationId so an OpenClaw cancel can
    // never purge a Hermes card/notification/confirmation sharing the same correlationId. Clears the
    // active card (if it's the one being cancelled, matched on BOTH keys), purges every retained
    // `recent` entry sharing BOTH keys (so a later notification tap can't re-surface a cancelled
    // task), and unconditionally cancels the OS notification posted under the composite key —
    // NotificationManagerCompat.cancel is a safe no-op when nothing is posted, so this stays correct
    // whether or not anything was actually showing.
    // FRI-555 B1: a verified CANCEL for this source+correlation also clears any pending inbound
    // confirmation sharing it, so a stale confirm/cancel tap can't resolve an already-cancelled task.
    // FRI-555 R5-1: exactly-once teardown across a UI-confirm-vs-FCM-CANCEL race. claimCancel is the
    // single atomic decision point: ALREADY_RESOLVED means a concurrent confirmActiveConfirmation()/
    // cancelActiveConfirmation() already tore down this exact card/recent/notification, so this call
    // must skip teardown entirely (never double-purge, never double-cancel the OS notification) and
    // still report true since the identity WAS resolved. WON or NONE both fall through to the same
    // teardown as before -- including the unconditional notifier.cancel, which is what makes a plain
    // (never-armed) task's stray tray notification always get cleared even after `recent` evicted it.
    override fun dismissIfCorrelated(sourceId: String, correlationId: String): Boolean {
        val claim = confirmationCenter.claimCancel(sourceId, correlationId)
        if (claim == CancelClaim.ALREADY_RESOLVED) return true

        val current = _activeEvent.value
        val cardCleared = current?.sourceId == sourceId && current.correlationId == correlationId
        if (cardCleared) _activeEvent.value = null

        val purged = synchronized(recent) {
            val toRemove = recent.entries
                .filter { it.value.sourceId == sourceId && it.value.correlationId == correlationId }
                .map { it.key }
            toRemove.forEach { recent.remove(it) }
            toRemove.isNotEmpty()
        }

        notifier.cancel(compositeKey(sourceId, correlationId))
        return cardCleared || purged || claim == CancelClaim.WON
    }

    // FRI-555 R3-1: the user-tap confirm/cancel path. dismissIfCorrelated (above) already tears down
    // every surface for a verified CANCEL event; this mirrors that teardown for the OTHER resolution
    // path -- an explicit user confirm/cancel tap on the in-app card -- which previously only
    // resolved the pending slot via InboundConfirmationCenter and left the foreground card, the
    // retained `recent` entry, and the OS notification alive. Both delegate to confirmationCenter's
    // single-shot resolve, so a stale re-tap after the pending slot is already resolved is a safe
    // no-op (returns false, no second bridge call, nothing torn down twice).
    fun confirmActiveConfirmation(): Boolean = resolveActiveConfirmation(confirmationCenter::confirm)

    fun cancelActiveConfirmation(): Boolean = resolveActiveConfirmation(confirmationCenter::cancel)

    private fun resolveActiveConfirmation(resolve: (String) -> Boolean): Boolean {
        val pending = confirmationCenter.pending.value ?: return false
        if (!resolve(pending.eventId)) return false

        if (_activeEvent.value?.eventId == pending.eventId) _activeEvent.value = null
        synchronized(recent) { recent.remove(pending.eventId) }

        val sourceId = pending.sourceId
        val correlationId = pending.correlationId
        val key = if (sourceId != null && correlationId != null) compositeKey(sourceId, correlationId) else pending.eventId
        notifier.cancel(key)
        return true
    }

    companion object {
        const val CHANNEL_NORMAL = "friday_events_normal"
        const val CHANNEL_HIGH = "friday_events_high"
        const val CHANNEL_URGENT = "friday_events_urgent"
        const val TITLE_CAP = 120
        const val BODY_CAP = 400
        // FRI-555 R3-2: actionIntent is data-only display (never executed), but still length-capped
        // like title/body so an oversized signed envelope can't blow up the ConfirmationCard.
        const val ACTION_CAP = 200
        const val RECENT_CAP = 32

        // FRI-555 B3: notification identity. Same-source-same-correlation events (e.g. PROGRESS ->
        // COMPLETION for one task) share this key so they REPLACE the tray notification instead of
        // stacking; a source+correlationId pair scopes identity so an OpenClaw and a Hermes event
        // sharing the same correlationId never collide/replace each other. Falls back to the bare
        // eventId when either sourceId or correlationId is absent (no ordering/identity constraint).
        fun notificationKeyFor(event: InboundEvent): String {
            val sourceId = event.sourceId
            val correlationId = event.correlationId
            return if (sourceId != null && correlationId != null) compositeKey(sourceId, correlationId) else event.eventId
        }

        // FRI-555 B3: shared composite-key format for dismissIfCorrelated's OS-notification cancel and
        // notificationKeyFor's posting key, so a cancel always targets exactly what was posted.
        fun compositeKey(sourceId: String, correlationId: String): String = "$sourceId|$correlationId"

        fun channelIdFor(urgency: InboundUrgency): String = when (urgency) {
            InboundUrgency.NORMAL -> CHANNEL_NORMAL
            InboundUrgency.HIGH -> CHANNEL_HIGH
            InboundUrgency.URGENT -> CHANNEL_URGENT
        }

        // Unverified push conservatism: a push claiming CONFIRMATION is downgraded to STATUS (no
        // actionable confirm UI from an unverified source), and title/body are length-capped. FRI-555
        // replaces this with a verified envelope + policy; the model and port stay.
        fun coercePush(event: InboundEvent): InboundEvent {
            val kind = if (event.kind == InboundEventKind.CONFIRMATION) InboundEventKind.STATUS else event.kind
            return event.copy(
                kind = kind,
                title = event.title.take(TITLE_CAP),
                body = event.body.take(BODY_CAP),
            )
        }

        // Malformed push data -> null (dropped silently upstream, no content logging). eventId + a
        // non-blank title are the minimum to surface anything.
        fun fromPushData(data: Map<String, String>): InboundEvent? {
            val eventId = data["eventId"]?.takeIf { it.isNotBlank() } ?: return null
            val title = data["title"]?.takeIf { it.isNotBlank() } ?: return null
            return InboundEvent(
                eventId = eventId,
                sourceType = InboundSource.PUSH,
                correlationId = data["correlationId"],
                kind = parseKind(data["kind"]),
                title = title,
                body = data["body"].orEmpty(),
                urgency = parseUrgency(data["urgency"]),
                receivedAt = 0L,
            )
        }

        // Rebuild an event from a tapped notification's full-payload extras (see AndroidEventNotifier).
        // sourceType=PUSH because intent extras are attacker-reachable on the exported activity, so
        // surfaceFromNotification re-coerces the result. Null when id/title are blank.
        fun reconstruct(
            eventId: String?,
            correlationId: String?,
            kind: String?,
            title: String?,
            body: String?,
            urgency: String?,
            receivedAt: Long,
        ): InboundEvent? {
            val id = eventId?.takeIf { it.isNotBlank() } ?: return null
            val realTitle = title?.takeIf { it.isNotBlank() } ?: return null
            return InboundEvent(
                eventId = id,
                sourceType = InboundSource.PUSH,
                correlationId = correlationId,
                kind = parseKind(kind),
                title = realTitle,
                body = body.orEmpty(),
                urgency = parseUrgency(urgency),
                receivedAt = receivedAt,
            )
        }

        private fun parseKind(raw: String?): InboundEventKind = when (raw?.uppercase()) {
            "TASK" -> InboundEventKind.TASK
            "CONFIRMATION" -> InboundEventKind.CONFIRMATION
            else -> InboundEventKind.STATUS
        }

        private fun parseUrgency(raw: String?): InboundUrgency = when (raw?.uppercase()) {
            "HIGH" -> InboundUrgency.HIGH
            "URGENT" -> InboundUrgency.URGENT
            else -> InboundUrgency.NORMAL
        }
    }
}

private class ProcessForegroundSignal : ForegroundSignal {
    override fun isForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
}

private class AndroidEventNotifier(private val context: Context) : EventNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun notify(event: InboundEvent, channelId: String) {
        ensureChannel(channelId, event.urgency)
        val open = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_EVENT
            putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, event.eventId)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_CORRELATION, event.correlationId)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_KIND, event.kind.name)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_TITLE, event.title)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_BODY, event.body)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_URGENCY, event.urgency.name)
            putExtra(MainActivity.EXTRA_OPEN_EVENT_RECEIVED_AT, event.receivedAt)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, event.eventId.hashCode(), open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        // OS silently drops when POST_NOTIFICATIONS (API 33+) isn't granted — card path is unaffected.
        // Keyed by correlation (fallback eventId) so same-task PROGRESS/COMPLETION replace, not stack.
        NotificationManagerCompat.from(context).notify(
            InboundEventCenter.notificationKeyFor(event).hashCode(),
            notification,
        )
    }

    override fun cancel(notificationKey: String) {
        NotificationManagerCompat.from(context).cancel(notificationKey.hashCode())
    }

    private fun ensureChannel(channelId: String, urgency: InboundUrgency) {
        val importance = when (urgency) {
            InboundUrgency.NORMAL -> NotificationManager.IMPORTANCE_DEFAULT
            else -> NotificationManager.IMPORTANCE_HIGH
        }
        val name = when (channelId) {
            InboundEventCenter.CHANNEL_URGENT -> context.getString(R.string.friday_events_channel_urgent)
            InboundEventCenter.CHANNEL_HIGH -> context.getString(R.string.friday_events_channel_high)
            else -> context.getString(R.string.friday_events_channel_normal)
        }
        val channel = NotificationChannel(channelId, name, importance).apply {
            if (urgency == InboundUrgency.URGENT) enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}

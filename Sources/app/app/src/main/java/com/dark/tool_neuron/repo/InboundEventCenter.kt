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
            InboundSource.VERIFIED -> event.copy(title = event.title.take(TITLE_CAP), body = event.body.take(BODY_CAP))
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
        if (safe.sourceType == InboundSource.VERIFIED && safe.kind == InboundEventKind.CONFIRMATION) {
            confirmationCenter.arm(safe)
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
    fun surfaceFromNotification(reconstructed: InboundEvent) {
        surface(resolve(reconstructed.eventId, reconstructed.correlationId) ?: coercePush(reconstructed))
    }

    fun dismiss() { _activeEvent.value = null }

    // FRI-555 B3: verified CANCEL support. Clears the active card (if it's the one being cancelled),
    // purges every retained `recent` entry sharing the correlation (so a later notification tap
    // can't re-surface a cancelled task), and unconditionally cancels the OS notification posted
    // under this correlation's key — NotificationManagerCompat.cancel is a safe no-op when nothing
    // is posted, so this stays correct whether or not anything was actually showing.
    // FRI-555 B1: a verified CANCEL for this correlation also clears any pending inbound
    // confirmation sharing it, so a stale confirm/cancel tap can't resolve an already-cancelled task.
    override fun dismissIfCorrelated(correlationId: String): Boolean {
        val current = _activeEvent.value
        val cardCleared = current?.correlationId == correlationId
        if (cardCleared) _activeEvent.value = null

        val purged = synchronized(recent) {
            val toRemove = recent.entries.filter { it.value.correlationId == correlationId }.map { it.key }
            toRemove.forEach { recent.remove(it) }
            toRemove.isNotEmpty()
        }

        val confirmationCleared = confirmationCenter.cancelByCorrelation(correlationId)
        notifier.cancel(correlationId)
        return cardCleared || purged || confirmationCleared
    }

    companion object {
        const val CHANNEL_NORMAL = "friday_events_normal"
        const val CHANNEL_HIGH = "friday_events_high"
        const val CHANNEL_URGENT = "friday_events_urgent"
        const val TITLE_CAP = 120
        const val BODY_CAP = 400
        const val RECENT_CAP = 32

        // FRI-555 B3: notification identity. Same-correlation events (e.g. PROGRESS -> COMPLETION
        // for one task) share this key so they REPLACE the tray notification instead of stacking;
        // events with no correlationId fall back to their own eventId.
        fun notificationKeyFor(event: InboundEvent): String = event.correlationId ?: event.eventId

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

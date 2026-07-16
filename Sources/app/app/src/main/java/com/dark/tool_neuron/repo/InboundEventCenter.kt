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
}

// Single consumer of InboundEventPort. Foreground -> in-app card (activeEvent flow); background ->
// system notification on a channel picked by urgency. Push-sourced events are conservatively coerced
// (CONFIRMATION downgraded, length-capped) since FRI-555 verification hasn't landed. No persistence,
// no runtime fake events — producers call publish(); tests construct events through the port.
@Singleton
class InboundEventCenter internal constructor(
    private val foreground: ForegroundSignal,
    private val notifier: EventNotifier,
) : InboundEventPort {

    @Inject constructor(@ApplicationContext context: Context) : this(
        ProcessForegroundSignal(),
        AndroidEventNotifier(context),
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
        val safe = if (event.sourceType == InboundSource.PUSH) coercePush(event) else event
        synchronized(recent) { recent[safe.eventId] = safe }
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

    fun dismiss() { _activeEvent.value = null }

    companion object {
        const val CHANNEL_NORMAL = "friday_events_normal"
        const val CHANNEL_HIGH = "friday_events_high"
        const val CHANNEL_URGENT = "friday_events_urgent"
        const val TITLE_CAP = 120
        const val BODY_CAP = 400
        const val RECENT_CAP = 32

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
            val kind = when (data["kind"]?.uppercase()) {
                "TASK" -> InboundEventKind.TASK
                "CONFIRMATION" -> InboundEventKind.CONFIRMATION
                else -> InboundEventKind.STATUS
            }
            val urgency = when (data["urgency"]?.uppercase()) {
                "HIGH" -> InboundUrgency.HIGH
                "URGENT" -> InboundUrgency.URGENT
                else -> InboundUrgency.NORMAL
            }
            return InboundEvent(
                eventId = eventId,
                sourceType = InboundSource.PUSH,
                correlationId = data["correlationId"],
                kind = kind,
                title = title,
                body = data["body"].orEmpty(),
                urgency = urgency,
                receivedAt = 0L,
            )
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
        NotificationManagerCompat.from(context).notify(event.eventId.hashCode(), notification)
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

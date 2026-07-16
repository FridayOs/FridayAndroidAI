package com.dark.tool_neuron.service.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.friday.ai.R
import com.dark.tool_neuron.voice.VoiceSessionServiceGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

// Session-scoped, opt-in mic-type foreground service (friday_voice_foreground_continue pref).
// Started at session-start (the foreground moment) to satisfy Android 14+ mic-FGS start-time
// rules; runs in the main :app process since session state is in-process singletons. Never
// sticky — a swipe-kill of the app stops the service along with the session.
@AndroidEntryPoint
class VoiceSessionForegroundService : Service() {

    @Inject lateinit var gate: VoiceSessionServiceGate

    private val serviceScope = CoroutineScope(SupervisorJob())

    override fun onBind(intent: Intent?): Nothing? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        // WHY: session end can be reported by the VM (teardown) before the notification's own Stop
        // action fires, so the service must also stop itself when sessionActive flips false.
        gate.sessionActive
            .onEach { active -> if (!active) stopSelfCleanly() }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            gate.requestStop()
            stopSelfCleanly()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopSelfCleanly() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.friday_voice_channel_name), NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceSessionForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle(getString(R.string.friday_voice_notif_title))
            .setContentText(getString(R.string.friday_voice_notif_body))
            .setOngoing(true)
            .addAction(0, getString(R.string.friday_voice_notif_stop), stopPi)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.dark.tool_neuron.voice.ACTION_STOP"
        private const val CHANNEL_ID = "friday_voice_session"
        private const val NOTIFICATION_ID = 0x46524956

        fun intent(context: Context): Intent = Intent(context, VoiceSessionForegroundService::class.java)
    }
}

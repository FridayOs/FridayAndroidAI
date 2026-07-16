package com.dark.tool_neuron.data.firebase

import android.util.Log
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.repo.InboundEventPort
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// FCM token refresh + push routing sink. Only fires when Firebase is configured
// (no google-services.json -> FCM never provisions -> onNewToken never called).
// Pushes the fresh token into the Firestore device registry so routing stays
// current. Never receives or stores prompt/transcript/assistant content.
@AndroidEntryPoint
class FridayMessagingService : FirebaseMessagingService() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var profileStore: FirebaseProfileStore
    @Inject lateinit var inboundEvents: InboundEventPort

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        prefs.fridayFcmToken = token
        val uid = prefs.fridayUserId
        if (uid.isBlank()) return
        scope.launch {
            runCatching { profileStore.updateFcmToken(uid, token) }
                .onFailure { Log.w(TAG, "device token update failed") }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Push routing only. Map data -> InboundEvent (conservative: unverified CONFIRMATION is
        // downgraded, lengths capped — see InboundEventCenter). Malformed payloads drop silently;
        // never log payload content (pushes may carry user-adjacent metadata).
        val event = InboundEventCenter.fromPushData(message.data)
        if (event == null) {
            Log.i(TAG, "push dropped (malformed)")
            return
        }
        inboundEvents.publish(event)
    }

    companion object {
        private const val TAG = "FridayFcm"
    }
}

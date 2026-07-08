package com.dark.tool_neuron.data.firebase

import android.util.Log
import com.dark.tool_neuron.data.AppPreferences
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
        // Push routing only. Payloads carry no assistant content by design; a
        // future notification surface consumes message.data here.
        Log.i(TAG, "push received")
    }

    companion object {
        private const val TAG = "FridayFcm"
    }
}

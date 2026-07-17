package com.dark.tool_neuron.data.firebase

import android.util.Log
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.repo.InboundEventCenter
import com.dark.tool_neuron.repo.InboundEventPort
import com.dark.tool_neuron.repo.gateway.event.EventGateway
import com.dark.tool_neuron.repo.gateway.event.EventRouting
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
    @Inject lateinit var eventGateway: EventGateway

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
        // Any envelope-distinctive key (signature/nonce/sourceId/sourceKind/version/type) routes
        // through the verified EventGateway pipeline, even a partial/malformed one -- the gateway
        // fails closed on missing fields, so this must never require ALL keys (a partial signed
        // payload must not fall through to the legacy unverified path). Everything else keeps the
        // legacy unverified-push path (conservative: unverified CONFIRMATION is downgraded,
        // lengths capped — see InboundEventCenter). Malformed payloads drop silently; never log
        // payload content.
        if (EventRouting.isSignedEnvelopeNamespace(message.data)) {
            eventGateway.accept(message.data)
            return
        }

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

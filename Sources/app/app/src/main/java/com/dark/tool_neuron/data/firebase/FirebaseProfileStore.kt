package com.dark.tool_neuron.data.firebase

import android.os.Build
import com.friday.ai.BuildConfig
import com.dark.tool_neuron.data.AppPreferences
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CloudProfile(
    val displayName: String,
    val email: String,
    val avatarUrl: String,
    val plan: String,
)

// Firestore metadata for the signed-in user. Scope is strictly:
//   /users/{uid}                          — profile metadata
//   /users/{uid}/devices/{deviceId}       — device + push routing metadata
// No prompt/transcript/context/memory/gateway/model/health data ever lands here.
@Singleton
class FirebaseProfileStore @Inject constructor(
    private val prefs: AppPreferences,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    suspend fun upsertProfile(user: FirebaseUser): CloudProfile {
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Friday User"
        val email = user.email?.takeIf { it.isNotBlank() } ?: "${user.uid}@friday.local"
        val avatarUrl = user.photoUrl?.toString().orEmpty()

        val ref = db.collection("users").document(user.uid)
        val existing = runCatching { ref.get().await() }.getOrNull()
        val plan = existing?.getString("plan")?.takeIf { it.isNotBlank() } ?: "Free plan"

        val doc = hashMapOf(
            "displayName" to displayName,
            "email" to email,
            "avatarUrl" to avatarUrl,
            "plan" to plan,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (existing == null || !existing.exists()) {
            doc["createdAt"] = FieldValue.serverTimestamp()
        }
        ref.set(doc, SetOptions.merge()).await()

        return CloudProfile(displayName, email, avatarUrl, plan)
    }

    // Device registry + FCM push routing. deviceId is a random per-install id
    // sealed in the encrypted app_prefs vault (see AppPreferences.deviceRegistryId)
    // — never Settings.Secure.ANDROID_ID or any OS-attested/global identity.
    suspend fun registerDevice(uid: String, policyVersion: Long) {
        val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        val doc = hashMapOf(
            "platform" to "android",
            "appVersion" to BuildConfig.VERSION_NAME,
            "sdkInt" to Build.VERSION.SDK_INT.toLong(),
            "model" to Build.MODEL,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "policyVersion" to policyVersion,
        )
        if (fcmToken != null) {
            doc["fcmToken"] = fcmToken
            prefs.fridayFcmToken = fcmToken
        }
        db.collection("users").document(uid)
            .collection("devices").document(prefs.deviceRegistryId())
            .set(doc, SetOptions.merge()).await()
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        prefs.fridayFcmToken = token
        db.collection("users").document(uid)
            .collection("devices").document(prefs.deviceRegistryId())
            .set(
                hashMapOf(
                    "fcmToken" to token,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            ).await()
    }
}

package com.dark.tool_neuron.data.firebase

import android.content.Context
import com.friday.ai.BuildConfig
import com.google.firebase.FirebaseApp

// Single gate for every Firebase touch. Firebase auto-init needs
// app/google-services.json, which is local-only + gitignored. Without it the
// google-services plugin never runs, no FirebaseApp is provisioned, and
// BuildConfig.FIREBASE_CONFIGURED is false. Runtime code must call ready()
// before any Auth/Firestore/FCM/RemoteConfig access and surface setupError()
// instead of letting FirebaseApp.getInstance() throw.
object FirebaseCloud {

    const val SETUP_MESSAGE: String =
        "Firebase backend is selected but not configured. Add app/google-services.json " +
            "(see Sources/app/FIREBASE_BACKEND_MODE.md) and rebuild, or switch backend mode to friday_api."

    fun ready(context: Context): Boolean {
        if (!BuildConfig.FIREBASE_CONFIGURED) return false
        return runCatching { FirebaseApp.getInstance(); true }
            .recoverCatching {
                FirebaseApp.initializeApp(context.applicationContext) != null
            }
            .getOrDefault(false)
    }

    fun setupError(): Nothing = error(SETUP_MESSAGE)

    // The google-services plugin emits default_web_client_id as a string
    // resource. Resolve it by name so this compiles without config present.
    fun webClientId(context: Context): String {
        val id = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName,
        )
        require(id != 0) {
            "default_web_client_id missing. google-services.json did not provision an OAuth web client id."
        }
        return context.getString(id)
    }
}

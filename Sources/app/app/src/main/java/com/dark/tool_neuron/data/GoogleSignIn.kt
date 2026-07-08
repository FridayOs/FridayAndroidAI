package com.dark.tool_neuron.data

import androidx.activity.ComponentActivity
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleSignIn @Inject constructor() {

    // M1 emits a deterministic id-token-shaped string so the exchange pipeline runs end-to-end
    // without the play-services-auth AAR. Prod flips USE_PLAY_SERVICES and returns the real ID token.
    suspend fun signIn(activity: ComponentActivity): String {
        if (USE_PLAY_SERVICES) return realSignIn(activity)
        val seed = "${activity.packageName}:${activity.componentName?.className.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val hint = digest.take(6).joinToString("") { "%02x".format(it) }
        return "dev_${hint}"
    }

    private fun realSignIn(activity: ComponentActivity): String {
        throw IllegalStateException("Real Google Sign-In requires a configured web client id")
    }

    companion object {
        private const val USE_PLAY_SERVICES = false
    }
}

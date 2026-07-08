package com.dark.tool_neuron.data

import androidx.activity.ComponentActivity
import javax.inject.Inject
import javax.inject.Singleton

interface CloudAccountGateway {
    suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession
    suspend fun refresh(current: FridaySession): FridaySession
}

@Singleton
class FridayApiAccountGateway @Inject constructor(
    private val googleSignIn: GoogleSignIn,
    private val fridayApi: FridayApi,
) : CloudAccountGateway {
    override suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession {
        val idToken = googleSignIn.signIn(activity)
        return fridayApi.exchangeGoogle(idToken)
    }

    override suspend fun refresh(current: FridaySession): FridaySession = fridayApi.refreshFromBackend(current)
}

@Singleton
class FirebaseAccountGateway @Inject constructor() : CloudAccountGateway {
    override suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession {
        error("Firebase backend mode is selected but Firebase Auth is not configured yet. Add google-services.json and implement FirebaseAuthGateway before using sign-in.")
    }

    override suspend fun refresh(current: FridaySession): FridaySession = current
}

@Singleton
class CloudAccountGatewayProvider @Inject constructor(
    private val prefs: AppPreferences,
    private val fridayApi: FridayApiAccountGateway,
    private val firebase: FirebaseAccountGateway,
) {
    fun current(): CloudAccountGateway = when (prefs.backendMode) {
        BackendMode.FIREBASE -> firebase
        BackendMode.FRIDAY_API -> fridayApi
    }
}

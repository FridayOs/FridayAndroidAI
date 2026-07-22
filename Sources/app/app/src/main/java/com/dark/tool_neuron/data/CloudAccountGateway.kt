package com.dark.tool_neuron.data

import android.content.Context
import androidx.activity.ComponentActivity
import com.dark.tool_neuron.data.firebase.FirebaseCloud
import com.dark.tool_neuron.data.firebase.FirebaseGoogleAuth
import com.dark.tool_neuron.data.firebase.FirebasePolicy
import com.dark.tool_neuron.data.firebase.FirebaseProfileStore
import androidx.annotation.VisibleForTesting
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
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
class FirebaseAccountGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseGoogleAuth,
    private val profileStore: FirebaseProfileStore,
    private val policy: FirebasePolicy,
) : CloudAccountGateway {

    // Google -> Firebase Auth -> Firestore profile + device registry. The
    // Firebase ID token rides the FridaySession.jwt slot so AccountRepository /
    // ScaffoldViewModel gates stay backend-agnostic. Missing google-services.json
    // is surfaced by FirebaseGoogleAuth via FirebaseCloud.setupError().
    override suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession {
        val user = firebaseAuth.signIn(activity)
        val profile = profileStore.upsertProfile(user)
        val policyVersion = policy.refreshPolicyVersion()
        profileStore.registerDevice(user.uid, policyVersion)
        val idToken = user.getIdToken(false).await().token.orEmpty()
        return FridaySession(
            jwt = idToken,
            userId = user.uid,
            displayName = profile.displayName,
            email = profile.email,
            avatarUrl = profile.avatarUrl,
            plan = profile.plan,
        )
    }

    // Re-mints the Firebase ID token and re-reads profile metadata. Falls back to
    // the current session if Firebase is offline so a transient failure doesn't
    // sign the user out; a revoked/expired account surfaces on the next call.
    override suspend fun refresh(current: FridaySession): FridaySession {
        // Config-less build: no FirebaseApp is provisioned, so touching
        // FirebaseAuth.getInstance() would throw. Return the current session
        // untouched rather than crash — sign-in already gates on the same check.
        if (!FirebaseCloud.ready(context)) return current
        val user = FirebaseAuth.getInstance().currentUser ?: return current
        val idToken = runCatching { user.getIdToken(true).await().token }.getOrNull()
            ?: current.jwt
        val profile = runCatching { profileStore.upsertProfile(user) }.getOrNull()
        return current.copy(
            jwt = idToken,
            userId = user.uid,
            displayName = profile?.displayName ?: current.displayName,
            email = profile?.email ?: current.email,
            avatarUrl = profile?.avatarUrl ?: current.avatarUrl,
            plan = profile?.plan ?: current.plan,
        )
    }
}

@Singleton
class CloudAccountGatewayProvider {

    // Resolves the active gateway. Production wires this to the backend-mode
    // switch below via the @Inject constructor; a test can bind a fixed gateway
    // through the @VisibleForTesting constructor without any Firebase/API deps.
    private val resolver: () -> CloudAccountGateway

    @Inject
    constructor(
        prefs: AppPreferences,
        fridayApi: FridayApiAccountGateway,
        firebase: FirebaseAccountGateway,
    ) {
        resolver = {
            when (prefs.backendMode) {
                BackendMode.FIREBASE -> firebase
                BackendMode.FRIDAY_API -> fridayApi
            }
        }
    }

    /**
     * Test-only seam: bind a single [CloudAccountGateway] so an instrumented test
     * can drive the REAL [com.dark.tool_neuron.viewmodel.AccountViewModel] sign-in
     * path against a fake gateway. Production never calls this constructor — the
     * Hilt-injected primary constructor keeps backend-mode selection unchanged.
     */
    @VisibleForTesting
    constructor(gateway: CloudAccountGateway) {
        resolver = { gateway }
    }

    fun current(): CloudAccountGateway = resolver()
}

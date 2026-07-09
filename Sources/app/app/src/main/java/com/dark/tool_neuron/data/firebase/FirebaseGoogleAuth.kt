package com.dark.tool_neuron.data.firebase

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Google -> Firebase Auth via Credential Manager. Fetches a Google ID token
// scoped to the OAuth web client id emitted by google-services.json, then
// exchanges it for a Firebase credential. No play-services-auth AAR — this uses
// the modern androidx.credentials pipeline. Requires FirebaseCloud.ready().
@Singleton
class FirebaseGoogleAuth @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun signIn(activity: ComponentActivity): FirebaseUser {
        if (!FirebaseCloud.ready(context)) FirebaseCloud.setupError()

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(FirebaseCloud.webClientId(context))
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = runCatching {
            CredentialManager.create(context).getCredential(activity, request)
        }.getOrElse { t ->
            // Credential Manager throws GetCredentialException subclasses for
            // "no account signed in" / "user cancelled". Translate to a typed
            // SignInException so the UI can render a localized, actionable
            // message instead of leaking "[28433]" to the user.
            if (t is CancellationException) throw t
            if (t is GetCredentialException) throw SignInException(SignInErrorMapper.fromGetCredential(t))
            throw SignInException(SignInError.Other(t))
        }
        val credential = result.credential
        require(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential.type}"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken

        val auth = FirebaseAuth.getInstance()
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCredential).await()
        return authResult.user
            ?: error("Firebase sign-in returned no user")
    }

    fun signOut() {
        if (!FirebaseCloud.ready(context)) return
        runCatching { FirebaseAuth.getInstance().signOut() }
    }
}

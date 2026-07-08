package com.dark.tool_neuron.data

import com.dark.tool_neuron.data.firebase.FirebaseGoogleAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val prefs: AppPreferences,
    private val firebaseAuth: FirebaseGoogleAuth,
) {
    private val _state = MutableStateFlow(AccountState.from(prefs))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    val hasJwt: Boolean
        get() = prefs.fridayJwt.isNotBlank()

    val jwt: String
        get() = prefs.fridayJwt

    fun persist(session: FridaySession) {
        prefs.fridayJwt = session.jwt
        prefs.fridayUserId = session.userId
        prefs.fridayUserName = session.displayName
        prefs.fridayUserEmail = session.email
        prefs.fridayAvatarUrl = session.avatarUrl
        prefs.fridayUserPlan = session.plan
        _state.value = AccountState.from(prefs)
    }

    fun signOut() {
        // Config-less / friday_api mode: FirebaseGoogleAuth.signOut() self-gates
        // on FirebaseCloud.ready(), so this is a no-op there and never throws.
        firebaseAuth.signOut()
        prefs.clearFridayAccount()
        _state.value = AccountState.Unauthenticated
    }
}

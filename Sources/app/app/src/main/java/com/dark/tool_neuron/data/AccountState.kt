package com.dark.tool_neuron.data

sealed interface AccountState {
    data object Unauthenticated : AccountState

    data class Authenticated(
        val userId: String,
        val displayName: String,
        val email: String,
        val avatarUrl: String,
        val plan: String,
    ) : AccountState

    companion object {
        fun from(prefs: AppPreferences): AccountState {
            if (prefs.fridayJwt.isBlank()) return Unauthenticated
            return Authenticated(
                userId = prefs.fridayUserId,
                displayName = prefs.fridayUserName,
                email = prefs.fridayUserEmail,
                avatarUrl = prefs.fridayAvatarUrl,
                plan = prefs.fridayUserPlan.ifBlank { "Free plan" },
            )
        }
    }
}

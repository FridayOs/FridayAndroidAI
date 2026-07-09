package com.dark.tool_neuron.data.firebase

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException

// Typed outcome of Google sign-in. Lets the UI layer render a localized,
// actionable message without leaking raw credential-manager exception text
// like "16:[28433]".
sealed interface SignInError {
    // No Google account is signed in on the device. Offer an "Open Settings"
    // CTA that launches the system add-account flow.
    data object NoCredential : SignInError

    // User dismissed the Google account chooser. No message, just hide.
    data object Cancelled : SignInError

    // Everything else (network, backend, unknown). Show a generic retry.
    data class Other(val cause: Throwable) : SignInError
}

object SignInErrorMapper {
    fun fromGetCredential(throwable: Throwable): SignInError = when (throwable) {
        is NoCredentialException -> SignInError.NoCredential
        is GetCredentialCancellationException -> SignInError.Cancelled
        is GetCredentialException -> SignInError.Other(throwable)
        else -> SignInError.Other(throwable)
    }
}

// Carries a typed SignInError across the gateway boundary so the ViewModel
// never has to inspect raw credential-manager exceptions.
class SignInException(val error: SignInError) : Exception()
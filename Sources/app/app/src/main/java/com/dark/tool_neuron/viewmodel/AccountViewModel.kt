package com.dark.tool_neuron.viewmodel

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AccountRepository
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.data.CloudAccountGatewayProvider
import com.dark.tool_neuron.data.firebase.SignInError
import com.dark.tool_neuron.data.firebase.SignInException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val gatewayProvider: CloudAccountGatewayProvider,
) : ViewModel() {

    val state: StateFlow<AccountState> = accountRepository.state

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    // Only NoCredential lands here so the screen can show the dialog. Other
    // failures (Cancelled, network, backend) ride _snackbar so they don't
    // trap the user in a blocking dialog.
    private val _noGoogleAccount = MutableStateFlow(false)
    val noGoogleAccount: StateFlow<Boolean> = _noGoogleAccount.asStateFlow()

    private val _snackbar = Channel<String>(capacity = Channel.BUFFERED)
    val snackbar = _snackbar.receiveAsFlow()

    fun signInWithGoogle(activity: ComponentActivity) {
        if (_signingIn.value) return
        _signingIn.value = true
        _noGoogleAccount.value = false
        viewModelScope.launch {
            try {
                val gateway = gatewayProvider.current()
                val session = withContext(Dispatchers.IO) {
                    gateway.signInWithGoogle(activity)
                }
                val refreshed = runCatching {
                    withContext(Dispatchers.IO) { gateway.refresh(session) }
                }.getOrDefault(session)
                accountRepository.persist(refreshed)
            } catch (e: SignInException) {
                when (val err = e.error) {
                    SignInError.NoCredential -> _noGoogleAccount.value = true
                    SignInError.Cancelled -> _snackbar.trySend("cancelled")
                    is SignInError.Other -> {
                        // Keep the raw cause in logcat for triage; UI gets a
                        // generic, non-blocking snackbar instead of leaking
                        // "[28433]"-style strings.
                        android.util.Log.w("AccountViewModel", "signIn failed", err.cause)
                        _snackbar.trySend("generic")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AccountViewModel", "signIn failed", e)
                _snackbar.trySend("generic")
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun dismissNoGoogleAccount() {
        _noGoogleAccount.value = false
    }

    fun signOut() {
        accountRepository.signOut()
    }
}
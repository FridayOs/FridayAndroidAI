package com.dark.tool_neuron.viewmodel

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AccountRepository
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.data.CloudAccountGatewayProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signInWithGoogle(activity: ComponentActivity) {
        if (_signingIn.value) return
        _signingIn.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val gateway = gatewayProvider.current()
                val session = withContext(Dispatchers.IO) {
                    gateway.signInWithGoogle(activity)
                }
                // Refresh is backend-mode specific. Firebase currently returns
                // the same session until FirebaseAuth/Firestore wiring lands.
                val refreshed = runCatching {
                    withContext(Dispatchers.IO) { gateway.refresh(session) }
                }.getOrDefault(session)
                accountRepository.persist(refreshed)
            } catch (e: Exception) {
                _error.value = e.message ?: "Sign-in failed"
            } finally {
                _signingIn.value = false
            }
        }
    }

    fun signOut() {
        accountRepository.signOut()
    }

    fun clearError() {
        _error.value = null
    }
}

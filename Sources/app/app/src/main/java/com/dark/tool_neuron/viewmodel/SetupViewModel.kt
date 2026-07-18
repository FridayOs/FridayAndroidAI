package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.PinStrength
import com.dark.tool_neuron.data.SecurityManager
import com.friday.ai.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager
) : ViewModel() {

    private val _selectedMode = MutableStateFlow<String?>(null)
    val selectedMode = _selectedMode.asStateFlow()

    // Password setup flow
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword = _confirmPassword.asStateFlow()

    private val _isConfirmStep = MutableStateFlow(false)
    val isConfirmStep = _isConfirmStep.asStateFlow()

    // Localized error surfaced as a string-resource id (mirrors OnboardingAddProviderViewModel's
    // _errorRes pattern) so SetupPasswordScreen resolves the text via stringResource(). The
    // too-short case needs a %1$d format arg (the live PinStrength.MIN_LENGTH), carried separately.
    private val _errorRes = MutableStateFlow<Int?>(null)
    val errorRes = _errorRes.asStateFlow()

    private val _errorArg = MutableStateFlow<Int?>(null)
    val errorArg = _errorArg.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    fun selectMode(mode: String) {
        _selectedMode.value = mode
        _password.value = ""
        _confirmPassword.value = ""
        _isConfirmStep.value = false
        clearError()
    }

    fun appendDigit(digit: Char) {
        if (_isConfirmStep.value) {
            if (_confirmPassword.value.length < MIN_PIN_LENGTH) {
                _confirmPassword.value += digit
                clearError()
            }
        } else {
            if (_password.value.length < MIN_PIN_LENGTH) {
                _password.value += digit
                clearError()
            }
        }
    }

    fun deleteLast() {
        if (_isConfirmStep.value) {
            val c = _confirmPassword.value
            if (c.isNotEmpty()) _confirmPassword.value = c.dropLast(1)
        } else {
            val p = _password.value
            if (p.isNotEmpty()) _password.value = p.dropLast(1)
        }
        clearError()
    }

    fun clearAll() {
        if (_isConfirmStep.value) {
            _confirmPassword.value = ""
        } else {
            _password.value = ""
        }
        clearError()
    }

    fun submitPassword(onSuccess: () -> Unit) {
        val pwd = _password.value
        when (val eval = PinStrength.evaluate(pwd)) {
            is PinStrength.Result.TooShort -> {
                _errorRes.value = R.string.friday_setup_pin_error_too_short
                _errorArg.value = eval.min
                return
            }
            PinStrength.Result.AllSameDigit -> {
                setError(R.string.friday_setup_pin_error_all_same)
                return
            }
            PinStrength.Result.Sequential -> {
                setError(R.string.friday_setup_pin_error_sequential)
                return
            }
            PinStrength.Result.CommonlyUsed -> {
                setError(R.string.friday_setup_pin_error_common)
                return
            }
            PinStrength.Result.Ok -> Unit
        }

        if (!_isConfirmStep.value) {
            _isConfirmStep.value = true
            return
        }

        if (_confirmPassword.value != pwd) {
            _confirmPassword.value = ""
            setError(R.string.friday_setup_pin_error_mismatch)
            return
        }

        if (_isSubmitting.value) return
        _isSubmitting.value = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) { security.setPassword(pwd) }
            prefs.setupDone = true
            prefs.securitySetupDone = true
            prefs.onboardingComplete = true
            _isSubmitting.value = false
            onSuccess()
        }
    }

    fun goBack() {
        if (_isConfirmStep.value) {
            _isConfirmStep.value = false
            _confirmPassword.value = ""
            clearError()
        } else {
            _selectedMode.value = null
            _password.value = ""
        }
    }

    fun completeWithNoLock() {
        security.setupWithoutLock()
        prefs.setupDone = true
        prefs.securitySetupDone = true
        prefs.onboardingComplete = true
    }

    private fun setError(resId: Int) {
        _errorRes.value = resId
        _errorArg.value = null
    }

    private fun clearError() {
        _errorRes.value = null
        _errorArg.value = null
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
    }
}

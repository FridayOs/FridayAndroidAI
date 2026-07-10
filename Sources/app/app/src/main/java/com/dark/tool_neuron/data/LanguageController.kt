package com.dark.tool_neuron.data

import com.dark.tool_neuron.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageController @Inject constructor(
    private val prefs: AppPreferences,
) {
    private val _selected = MutableStateFlow(readSelected())
    val selected: StateFlow<AppLanguage> = _selected.asStateFlow()

    fun setSelected(value: AppLanguage) {
        prefs.putString(KEY_LANGUAGE, value.name)
        _selected.value = value
    }

    fun markFirstSelectionDone() {
        prefs.putBoolean(KEY_LANGUAGE_SELECTED, true)
    }

    fun isFirstSelectionDone(): Boolean = prefs.getBoolean(KEY_LANGUAGE_SELECTED)

    private fun readSelected(): AppLanguage =
        AppLanguage.fromId(prefs.getString(KEY_LANGUAGE))

    companion object {
        const val KEY_LANGUAGE = "friday_language"
        const val KEY_LANGUAGE_SELECTED = "friday_language_selected"
    }
}

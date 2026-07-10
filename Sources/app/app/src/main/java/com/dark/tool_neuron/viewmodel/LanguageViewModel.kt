package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppCompatLocaleWrapper
import com.dark.tool_neuron.data.LanguageController
import com.dark.tool_neuron.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val controller: LanguageController,
    private val localeWrapper: AppCompatLocaleWrapper,
) : ViewModel() {

    val selected: StateFlow<AppLanguage> = controller.selected

    fun choose(value: AppLanguage) {
        controller.setSelected(value)
    }

    fun confirm() {
        controller.markFirstSelectionDone()
    }

    fun currentTag(): String = localeWrapper.currentTag()
}

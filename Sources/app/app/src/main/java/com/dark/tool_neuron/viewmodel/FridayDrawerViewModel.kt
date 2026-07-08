package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FridayDrawerViewModel @Inject constructor(
    modelRepository: ModelRepository,
    private val themeController: ThemeController,
    private val prefs: AppPreferences,
) : ViewModel() {

    val gatewayModels: StateFlow<List<ModelInfo>> = modelRepository.models
        .map { list -> list.filter { it.providerType == ProviderType.GGUF } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedModelId = MutableStateFlow(prefs.fridaySelectedModelId)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    val themeMode: StateFlow<ThemeController.Mode> = themeController.mode

    fun selectModel(id: String) {
        prefs.fridaySelectedModelId = id
        _selectedModelId.value = id
    }

    fun setThemeMode(mode: ThemeController.Mode) {
        themeController.setMode(mode)
    }
}

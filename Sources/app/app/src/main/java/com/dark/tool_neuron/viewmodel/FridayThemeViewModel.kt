package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.ThemeController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FridayThemeViewModel @Inject constructor(
    private val themeController: ThemeController,
) : ViewModel() {

    val mode: StateFlow<ThemeController.Mode> = themeController.mode

    fun toggle() {
        val next = if (themeController.mode.value == ThemeController.Mode.DARK) {
            ThemeController.Mode.LIGHT
        } else {
            ThemeController.Mode.DARK
        }
        themeController.setMode(next)
    }

    fun setMode(mode: ThemeController.Mode) {
        themeController.setMode(mode)
    }
}

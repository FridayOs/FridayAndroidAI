package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.FridayAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/*
 * Theme step (FRI-582 P5): exposes mode + accent only. Onboarding always
 * runs on the FRIDAY base palette (ColorPalette.FRIDAY) — accent is the
 * only variable the user picks here. Settings' theme screen uses a
 * separate ThemingViewModel and is unaffected by this rewire.
 */
@HiltViewModel
class SetupThemeViewModel @Inject constructor(
    private val themeController: ThemeController,
) : ViewModel() {

    val mode: StateFlow<ThemeController.Mode> = themeController.mode
    val accent: StateFlow<FridayAccent> = themeController.accent

    fun selectMode(value: ThemeController.Mode) = themeController.setMode(value)

    fun selectAccent(value: FridayAccent) = themeController.setAccent(value)

    /** Force the FRIDAY base palette on entry; idempotent, safe to call every composition. */
    fun ensureFridayBase() {
        if (themeController.palette.value != ColorPalette.FRIDAY) {
            themeController.setPalette(ColorPalette.FRIDAY)
        }
    }
}

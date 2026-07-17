package com.dark.tool_neuron.data

import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.FridayAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeController @Inject constructor(
    private val prefs: AppPreferences,
) {
    enum class Mode { SYSTEM, LIGHT, DARK }

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _palette = MutableStateFlow(readPalette())
    val palette: StateFlow<ColorPalette> = _palette.asStateFlow()

    private val _accent = MutableStateFlow(readAccent())
    val accent: StateFlow<FridayAccent> = _accent.asStateFlow()

    fun setMode(value: Mode) {
        prefs.putString(KEY_MODE, value.name)
        _mode.value = value
    }

    fun setPalette(value: ColorPalette) {
        prefs.putString(KEY_PALETTE, value.name)
        _palette.value = value
    }

    fun setAccent(value: FridayAccent) {
        prefs.putString(KEY_ACCENT, value.id)
        _accent.value = value
    }

    private fun readMode(): Mode {
        val raw = prefs.getString(KEY_MODE, Mode.SYSTEM.name)
        return runCatching { Mode.valueOf(raw) }.getOrDefault(Mode.SYSTEM)
    }

    private fun readPalette(): ColorPalette {
        val raw = prefs.getString(KEY_PALETTE, ColorPalette.DYNAMIC.name)
        return runCatching { ColorPalette.valueOf(raw) }.getOrDefault(ColorPalette.DYNAMIC)
    }

    private fun readAccent(): FridayAccent {
        val raw = prefs.getString(KEY_ACCENT, FridayAccent.SUN.id)
        return FridayAccent.fromId(raw)
    }

    companion object {
        private const val KEY_MODE = "theme_mode"
        private const val KEY_PALETTE = "theme_palette"
        private const val KEY_ACCENT = "theme_accent"
    }
}

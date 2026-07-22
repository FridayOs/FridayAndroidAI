package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.SetupThemeViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-07 — device-capture matrix for SetupThemeScreen: EN/VI x light/dark x
 * 4 accents (sun/ember/violet/mint) = 16 cells, matching evidence/reference/SetupThemeScreen__*.
 * Hosts the REAL screen through a REAL SetupThemeViewModel backed by a REAL on-device
 * ThemeController (HXS-backed AppPreferences), mirroring SetupThemeInteractionTest's setup.
 * Per-cell accent selection is driven through vm.selectAccent(...) (the screen reads its own
 * accent/mode state from the ViewModel, not from LocalFridayAccent) AND mirrored into
 * Fri633ThemedHost's accent/dark params so the surrounding chrome (e.g. ActionButton icon
 * tint) matches; vm.selectMode(...) is set to LIGHT/DARK per cell so the mode-card selection
 * shown in the capture is consistent with the rendered theme.
 */
@RunWith(AndroidJUnit4::class)
class SetupThemeDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        prefs = AppPreferences(context, AppKeyStore(context), HxsEncryptor())
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "app_prefs").deleteRecursively()
    }

    @Test
    fun capture_light_dark_en_vi_x_accent_grid() {
        val controller = ThemeController(prefs)
        val vm = SetupThemeViewModel(controller)
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")
        val accentState = mutableStateOf(FridayAccent.SUN)

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = accentState.value,
                languageTag = localeTagState.value,
            ) {
                SetupThemeScreen(
                    innerPadding = PaddingValues(0.dp),
                    onBack = {},
                    viewModel = vm,
                )
            }
        }
        composeTestRule.waitForIdle()

        data class Cell(
            val localeLabel: String,
            val languageTag: String,
            val dark: Boolean,
            val themeLabel: String,
            val accent: FridayAccent,
            val accentLabel: String,
        )
        val locales = listOf("EN" to "en", "VI" to "vi")
        val themes = listOf(false to "light", true to "dark")
        val accents = listOf(
            FridayAccent.SUN to "sun",
            FridayAccent.EMBER to "ember",
            FridayAccent.VIOLET to "violet",
            FridayAccent.MINT to "mint",
        )
        val cells = locales.flatMap { (localeLabel, languageTag) ->
            themes.flatMap { (dark, themeLabel) ->
                accents.map { (accent, accentLabel) ->
                    Cell(localeLabel, languageTag, dark, themeLabel, accent, accentLabel)
                }
            }
        }
        for (cell in cells) {
            localeTagState.value = cell.languageTag
            darkState.value = cell.dark
            accentState.value = cell.accent
            vm.selectAccent(cell.accent)
            vm.selectMode(if (cell.dark) ThemeController.Mode.DARK else ThemeController.Mode.LIGHT)
            composeTestRule.waitForIdle()
            Fri633CaptureHarness.save(
                composeTestRule.onRoot(),
                "SetupThemeScreen__${cell.localeLabel}__${cell.themeLabel}__${cell.accentLabel}.png",
            )
        }
    }
}

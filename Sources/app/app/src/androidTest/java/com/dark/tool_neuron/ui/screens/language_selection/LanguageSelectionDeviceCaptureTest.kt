package com.dark.tool_neuron.ui.screens.language_selection

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
import com.dark.tool_neuron.data.AppCompatLocaleWrapper
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.LanguageController
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.LanguageViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-07 — device-capture matrix for LanguageSelectionScreen: EN/VI x
 * light/dark x sun accent (4 cells), matching evidence/reference/LanguageSelectionScreen__*.
 * Hosts the REAL screen through a REAL LanguageViewModel/LanguageController (HXS-backed
 * AppPreferences), mirroring LanguageSelectionCopyTest's setup. Single setContent call;
 * cells are captured by mutating external mutableState (theme/locale) between captures and
 * recomposing, per Fri633ThemedHost. NOTE (disclosed, not a bug): this screen renders a
 * FIXED bilingual EN+VI copy regardless of locale (see LanguageSelectionScreen.kt) — the
 * EN and VI capture pairs are expected to be near-identical; that is the real screen's
 * behavior, not a capture defect.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSelectionDeviceCaptureTest {

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
    fun capture_light_dark_en_vi_grid() {
        val controller = LanguageController(prefs)
        val vm = LanguageViewModel(controller, AppCompatLocaleWrapper(context, controller))
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                LanguageSelectionScreen(
                    innerPadding = PaddingValues(0.dp),
                    onContinue = {},
                    viewModel = vm,
                )
            }
        }
        composeTestRule.waitForIdle()

        data class Cell(val localeLabel: String, val languageTag: String, val dark: Boolean, val themeLabel: String)
        val cells = listOf(
            Cell("EN", "en", dark = false, themeLabel = "light"),
            Cell("EN", "en", dark = true, themeLabel = "dark"),
            Cell("VI", "vi", dark = false, themeLabel = "light"),
            Cell("VI", "vi", dark = true, themeLabel = "dark"),
        )
        for (cell in cells) {
            localeTagState.value = cell.languageTag
            darkState.value = cell.dark
            composeTestRule.waitForIdle()
            Fri633CaptureHarness.save(
                composeTestRule.onRoot(),
                "LanguageSelectionScreen__${cell.localeLabel}__${cell.themeLabel}__sun.png",
            )
        }
    }
}

package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.SetupThemeViewModel
import com.friday.ai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 — Theme step (design-spec §6, HTML lines 230-286) interaction +
 * copy coverage: locks the 4 accent display names verbatim
 * (friday_accent_sun/ember/violet/mint), and locks that tapping a mode card
 * / accent swatch updates the real [SetupThemeViewModel] state (which
 * mirrors [ThemeController]'s HXS-backed prefs, driving the live preview).
 *
 * Hosts the REAL [SetupThemeScreen] through a REAL [SetupThemeViewModel]
 * backed by a REAL on-device [ThemeController] — no fakes/mocks.
 * ThemeController is a concrete final class over the HXS-backed
 * AppPreferences, so this mirrors LanguageSelectionCopyTest's HXS setup
 * (BootIntegrity relaxed + disk cleanup).
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class SetupThemeInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    private fun str(id: Int) = context.getString(id)

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

    private fun hostScreen(): SetupThemeViewModel {
        val controller = ThemeController(prefs)
        val vm = SetupThemeViewModel(controller)
        composeTestRule.setContent {
            SetupThemeScreen(
                innerPadding = PaddingValues(0.dp),
                onBack = {},
                viewModel = vm,
            )
        }
        composeTestRule.waitForIdle()
        return vm
    }

    @Test
    fun accent_display_names_are_p0_verbatim() {
        hostScreen()

        composeTestRule.onNodeWithText(str(R.string.friday_accent_sun)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_accent_ember)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_accent_violet)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_accent_mint)).assertIsDisplayed()
    }

    @Test
    fun tapping_dark_mode_updates_view_model_mode() {
        val vm = hostScreen()

        composeTestRule.onNodeWithText(str(R.string.friday_setup_theme_mode_dark_title)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(ThemeController.Mode.DARK, vm.mode.value)
    }

    @Test
    fun tapping_ember_accent_updates_view_model_accent() {
        val vm = hostScreen()

        composeTestRule.onNodeWithText(str(R.string.friday_accent_ember)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(FridayAccent.EMBER, vm.accent.value)
    }
}

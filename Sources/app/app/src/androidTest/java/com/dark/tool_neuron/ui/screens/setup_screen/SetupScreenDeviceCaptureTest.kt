package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-07 — device-capture matrix for SetupScreen (Protect Friday): EN/VI x
 * light/dark x sun accent (4 cells), matching evidence/reference/SetupScreen__*.
 * [SetupScreen] is a pure-callback composable (no VM, no HXS setup needed) per
 * ProtectFridayCtaTest.kt; selection state is hoisted here (kept unselected, matching the
 * screen's initial/default state). The content gates behind an 80ms-delayed
 * AnimatedVisibility entrance (real delay() in a LaunchedEffect, not frame-clock driven —
 * ComposeTestRule's default idling does not block on it), so each cell is captured only
 * after polling for the CTA tag to exist, mirroring ProtectFridayCtaTest's waitForCta().
 * SetupScreen internally calls rememberLauncherForActivityResult(...) for a POST_NOTIFICATIONS
 * permission launcher, which requires LocalActivityResultRegistryOwner. createComposeRule()'s
 * ComposeContentTestRule interface does not expose the underlying hosting Activity, so
 * createAndroidComposeRule<ComponentActivity>() is used instead (test-only change) to obtain
 * composeTestRule.activity and explicitly provide it as the ActivityResultRegistryOwner.
 *
 * FRI-633 phase-09 Codex-QA rework (Fix 2b): adds the __selected interaction-state cell —
 * a real `SecurityOption` chosen (the `setup_option_no_lock` card, per ProtectFridayCtaTest's
 * established click sequence), which hoists `selectedMode = "none"` and enables the real
 * `protect_friday_cta`. EN/light only, matching the FridayLoginDeviceCaptureTest convention.
 */
@RunWith(AndroidJUnit4::class)
class SetupScreenDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun waitForCta() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("protect_friday_cta").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun capture_light_dark_en_vi_grid_and_selected_state() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")
        var selectedMode by mutableStateOf<String?>(null)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides composeTestRule.activity) {
                Fri633ThemedHost(
                    dark = darkState.value,
                    accent = FridayAccent.SUN,
                    languageTag = localeTagState.value,
                ) {
                    SetupScreen(
                        innerPadding = PaddingValues(0.dp),
                        selectedMode = selectedMode,
                        onModeSelected = { selectedMode = it },
                        onContinue = {},
                        onBack = {},
                    )
                }
            }
        }
        waitForCta()

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
                "SetupScreen__${cell.localeLabel}__${cell.themeLabel}__sun.png",
            )
        }

        // __selected — return to EN/light and pick the "no lock" security option.
        localeTagState.value = "en"
        darkState.value = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setup_option_no_lock").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupScreen__EN__light__sun__selected.png")
    }
}

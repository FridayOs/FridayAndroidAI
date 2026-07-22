package com.dark.tool_neuron.ui.screens.onboarding_tour

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-07 — device-capture matrix for FeatureTourScreen: EN/VI x light/dark x
 * sun accent (4 cells), matching evidence/reference/FeatureTourScreen__*. [FeatureTourScreen]
 * is a pure-callback composable (no VM, no HXS setup needed) per FeatureTourCopyTest.kt.
 * Single setContent call; cells captured by mutating external mutableState between captures,
 * per the established Fri633ThemedHost pattern.
 */
@RunWith(AndroidJUnit4::class)
class FeatureTourDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture_light_dark_en_vi_grid() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                FeatureTourScreen(
                    innerPadding = PaddingValues(0.dp),
                    onContinue = {},
                    onBack = {},
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
                "FeatureTourScreen__${cell.localeLabel}__${cell.themeLabel}__sun.png",
            )
        }
    }
}

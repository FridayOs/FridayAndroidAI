package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.dark.tool_neuron.viewmodel.PackCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-07 — device-capture matrix for ModelSetupScreen: EN/VI x light/dark x
 * 4 accents (sun/ember/violet/mint) = 16 cells, matching evidence/reference/ModelSetupScreen__*.
 * [ModelSetupScreen] is a pure-callback composable (no VM, no HXS setup needed) per
 * ModelSetupPathCtaTest.kt/ModelSetupPackCtaTest.kt; all callbacks are no-ops/trivial —
 * captured in its default (no path selected) state. Single setContent call; cells captured
 * by mutating external mutableState between captures, per the established Fri633ThemedHost
 * pattern.
 *
 * FRI-633 phase-09 Codex-QA rework (Fix 2b): adds two real interaction-state cells, EN/light/
 * sun only (matches FridayLoginDeviceCaptureTest's interaction-state convention):
 * - __pathselected — the LOCAL path card (only card with a testTag, `model_path_local` per
 *   ModelSetupScreen.kt) is selected, revealing the real ModelSetupPackList.
 * - __packunavailable — the screen's real fail-closed inline-error path (ModelSetupCta.kt:
 *   `packUnavailable` state + `model_setup_pack_unavailable` testTag), driven by wiring
 *   `onLocalPackConfirmed` to a mutable flag this test flips to `false` before the CTA click —
 *   a genuine drivable error state, not a fabricated/invented one.
 */
@RunWith(AndroidJUnit4::class)
class ModelSetupDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun capture_light_dark_en_vi_x_accent_grid_and_interaction_states() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")
        val accentState = mutableStateOf(FridayAccent.SUN)
        val packConfirmResult = mutableStateOf(true)

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = accentState.value,
                languageTag = localeTagState.value,
            ) {
                ModelSetupScreen(
                    innerPadding = PaddingValues(0.dp),
                    onLocalPackConfirmed = { packConfirmResult.value },
                    onOpenStore = {},
                    onLocalImport = { _, _, _, _ -> },
                    onSkip = {},
                    onBack = {},
                    onChooseGateway = {},
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
            composeTestRule.waitForIdle()
            Fri633CaptureHarness.save(
                composeTestRule.onRoot(),
                "ModelSetupScreen__${cell.localeLabel}__${cell.themeLabel}__${cell.accentLabel}.png",
            )
        }

        // Return to EN/light/sun for the interaction-state cells (spec: EN light only).
        localeTagState.value = "en"
        darkState.value = false
        accentState.value = FridayAccent.SUN
        composeTestRule.waitForIdle()

        // __pathselected — the LOCAL path card is selected, revealing the real pack list.
        // performScrollTo() first, matching ModelSetupPackCtaTest.kt's established pattern:
        // the verticalScroll Column's later children (pack list, CTA) fall below the initial
        // 390x844 viewport once the local pack list is revealed, so a click without first
        // scrolling the target node into view does not reliably register.
        composeTestRule.onNodeWithTag("model_path_local").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(
            composeTestRule.onRoot(),
            "ModelSetupScreen__EN__light__sun__pathselected.png",
        )

        // __packunavailable — a pack is chosen, `onLocalPackConfirmed` is wired to return
        // false, and the CTA click surfaces the screen's real fail-closed inline error.
        composeTestRule.onNodeWithTag("pack_card_${PackCatalog.PACK_CHAT_ONLY}").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        packConfirmResult.value = false
        composeTestRule.onNodeWithTag("model_setup_cta").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("model_setup_pack_unavailable").fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(
            composeTestRule.onRoot(),
            "ModelSetupScreen__EN__light__sun__packunavailable.png",
        )
    }
}

package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.friday.ai.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-07 — device-capture matrix for TermsConditionsScreen: EN/VI x
 * light/dark x sun accent (4 cells), matching evidence/reference/TermsConditionsScreen__*.
 * [TermsConditionsScreen] is a pure-callback composable (no VM, no HXS setup needed) per
 * TermsConditionsCopyTest.kt. Single setContent call; cells are captured by mutating
 * external mutableState (theme/locale) between captures and recomposing, per
 * Fri633ThemedHost/LanguageSelectionDeviceCaptureTest's established pattern.
 *
 * FRI-633 phase-09 Codex-QA rework (Fix 2b): adds the __privacy interaction-state cell —
 * the screen's real internal `showPrivacyDialog` toggle (TermsConditionsScreen.kt), driven
 * by clicking the trailing LazyColumn item's privacy-link text (no testTag exists on this
 * item or dialog, so it is targeted by its verbatim string resource, same technique as
 * ModelSetupPathCtaTest's untagged path cards). EN/light only, matching the
 * FridayLoginDeviceCaptureTest interaction-state convention.
 *
 * FRI-633 phase-09 Fix 2b re-run (evidence-gap fix): the privacy-link item is the 7th entry
 * in TermsConditionsScreen's LazyColumn (6 TermsCard entries + the link) and is not composed
 * within the initial 390x844 viewport, so a plain `performScrollTo()` on the not-yet-composed
 * text node throws "could not find any node" (Compose's SemanticsNodeInteraction can only
 * scroll to nodes already present in the tree). Fixed by scrolling the LazyColumn itself
 * (the sole `hasScrollAction()` node in this composition) to the target node via
 * `performScrollToNode(hasText(...))` — identical technique to
 * OnboardingProvidersDeviceCaptureTest's `onNodeWithTag("onboarding_providers_list")
 * .performScrollToNode(hasText(...))`, minus the tag lookup since this LazyColumn has none.
 */
@RunWith(AndroidJUnit4::class)
class TermsConditionsDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    @Test
    fun capture_light_dark_en_vi_grid_and_privacy_dialog() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                TermsConditionsScreen(
                    innerPadding = PaddingValues(0.dp),
                    onAccept = {},
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
                "TermsConditionsScreen__${cell.localeLabel}__${cell.themeLabel}__sun.png",
            )
        }

        // __privacy — return to EN/light and open the real privacy dialog.
        localeTagState.value = "en"
        darkState.value = false
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(str(R.string.onboarding_terms_privacy_link)))
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_link)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_title)).assertExistsForCapture()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "TermsConditionsScreen__EN__light__sun__privacy.png")

        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_got_it)).performClick()
        composeTestRule.waitForIdle()
    }
}

/** Local no-op-return helper so a missing dialog fails loudly with a clear assertion name. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsForCapture() {
    this.fetchSemanticsNode("Expected privacy dialog title node to exist before capture")
}

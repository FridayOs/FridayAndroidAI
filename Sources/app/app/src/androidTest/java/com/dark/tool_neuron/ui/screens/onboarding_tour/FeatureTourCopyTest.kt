package com.dark.tool_neuron.ui.screens.onboarding_tour

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.friday.ai.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 — locks the Feature Tour screen's P0-verbatim visible copy
 * (design-spec §4, HTML TOUR array lines 1432-1443): 5 cards
 * (onboarding_tour_card1..5_title/body), the F4 "change later" footnote
 * (friday_setup_change_later), and the in-screen Continue CTA
 * (onboarding_tour_continue_cta) invoking `onContinue`.
 *
 * [FeatureTourScreen] is a pure-callback composable (no VM) — hosted
 * directly, no HXS setup needed. The content column is not lazily composed
 * (fixed Column, not LazyColumn) but is vertically scrollable
 * (`Modifier.verticalScroll`) since 5 cards + footnote overflow small
 * viewports (e.g. ~360x760dp devices); below-fold nodes are scrolled into
 * view via `performScrollTo()` before asserting.
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class FeatureTourCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private fun hostScreen(onContinue: () -> Unit = {}, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            FeatureTourScreen(
                innerPadding = PaddingValues(0.dp),
                onContinue = onContinue,
                onBack = onBack,
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun cards_are_p0_verbatim() {
        hostScreen()

        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card1_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card1_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card2_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card2_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card3_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card3_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card4_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card4_body)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card5_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_card5_body)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun change_later_footnote_is_displayed() {
        hostScreen()

        composeTestRule.onNodeWithText(str(R.string.friday_setup_change_later)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun continue_cta_invokes_on_continue() {
        var continued = false
        hostScreen(onContinue = { continued = true })

        composeTestRule.onNodeWithText(str(R.string.onboarding_tour_continue_cta)).performClick()
        composeTestRule.waitForIdle()

        assertTrue(continued)
    }
}

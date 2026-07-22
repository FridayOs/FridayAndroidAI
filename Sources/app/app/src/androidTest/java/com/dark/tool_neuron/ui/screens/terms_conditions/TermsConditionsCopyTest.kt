package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.friday.ai.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 — locks the Terms & Conditions screen's P0-verbatim visible copy
 * (design-spec §3, HTML TERMS array lines 1417-1430): 6 cards
 * (onboarding_terms_card1..6_title/body), the "View full privacy details"
 * link opening an AlertDialog with the privacy body copy, and the explicit
 * back chevron invoking `onBack`.
 *
 * [TermsConditionsScreen] is a pure-callback composable (no VM) — hosted
 * directly, no HXS setup needed. The 6 cards live in a LazyColumn with no
 * `testTag` (nothing to add for test convenience per FRI-633 constraints),
 * so below-fold cards (4-6) + the privacy link are reached via
 * `hasScrollAction()` — the list is the screen's only scrollable node —
 * rather than `onNodeWithTag`.
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class TermsConditionsCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private fun scrollToAndAssertDisplayed(text: String) {
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(text))
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun hostScreen(onAccept: () -> Unit = {}, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            TermsConditionsScreen(
                innerPadding = PaddingValues(0.dp),
                onAccept = onAccept,
                onBack = onBack,
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun above_fold_cards_are_p0_verbatim() {
        hostScreen()

        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card1_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card1_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card2_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card2_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card3_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_card3_body)).assertIsDisplayed()
    }

    @Test
    fun below_fold_cards_are_p0_verbatim() {
        hostScreen()

        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card4_title))
        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card4_body))
        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card5_title))
        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card5_body))
        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card6_title))
        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_card6_body))
    }

    @Test
    fun privacy_link_opens_dialog_with_privacy_copy() {
        hostScreen()

        scrollToAndAssertDisplayed(str(R.string.onboarding_terms_privacy_link))
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_link)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_got_it)).assertIsDisplayed()
    }

    @Test
    fun back_chevron_invokes_on_back() {
        var backInvoked = false
        var acceptInvoked = false
        hostScreen(onAccept = { acceptInvoked = true }, onBack = { backInvoked = true })

        composeTestRule
            .onNodeWithContentDescription(str(R.string.friday_onboarding_back_content_description))
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue(backInvoked)
        // Accept CTA lives in the bottom bar, not on the screen itself — the
        // chevron tap must not accidentally accept terms.
        assertFalse(acceptInvoked)
    }
}

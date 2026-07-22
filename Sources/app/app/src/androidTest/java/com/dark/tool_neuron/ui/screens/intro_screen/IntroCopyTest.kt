package com.dark.tool_neuron.ui.screens.intro_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.friday.ai.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 — locks the Intro splash's P0-verbatim visible copy (design-spec §1,
 * HTML:48-62): brand title "FRIDAY AI" (literal, R.string.onboarding_intro_title),
 * tagline, and the "Local-first * User-owned * Private by default" pill badge.
 *
 * Also locks the tap-to-skip affordance: [IntroScreen] is a pure-callback
 * composable (no VM) — tapping anywhere on the full-bleed root invokes
 * `onFinish` immediately (the `finished` guard makes this idempotent against
 * the screen's own 2600ms auto-advance timer).
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class IntroCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    private fun hostScreen(onFinish: () -> Unit) {
        composeTestRule.setContent {
            IntroScreen(
                innerPadding = PaddingValues(0.dp),
                onFinish = onFinish,
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun title_tagline_and_pill_are_p0_verbatim() {
        hostScreen(onFinish = {})

        composeTestRule.onNodeWithText(str(R.string.onboarding_intro_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_intro_tagline)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_intro_pill)).assertIsDisplayed()
    }

    @Test
    fun tapping_root_invokes_on_finish() {
        var finished = false
        hostScreen(onFinish = { finished = true })

        composeTestRule.onRoot().performClick()
        composeTestRule.waitForIdle()

        assertTrue(finished)
    }
}

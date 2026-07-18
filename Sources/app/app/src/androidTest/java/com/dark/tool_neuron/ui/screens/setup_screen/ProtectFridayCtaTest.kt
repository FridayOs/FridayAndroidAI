package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRI-582 Codex-QA blocker fix (Blocker 2): Protect Friday's sticky Continue
 * CTA (`protect_friday_cta`) must stay disabled until a security mode is
 * picked, then fire onContinue on click. Renders [SetupScreen] directly with
 * fake callbacks — no Hilt/ViewModel wiring needed since selection state is
 * hoisted to the caller.
 */
@RunWith(AndroidJUnit4::class)
class ProtectFridayCtaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cta_disabled_until_mode_selected_then_fires_onContinue() {
        var selectedMode by mutableStateOf<String?>(null)
        var continueClicks = 0

        composeTestRule.setContent {
            SetupScreen(
                innerPadding = PaddingValues(0.dp),
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                onContinue = { continueClicks++ },
                onBack = {},
            )
        }

        waitForCta()
        composeTestRule.onNodeWithTag("protect_friday_cta").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("setup_option_no_lock").performScrollTo().performClick()
        assertEquals("none", selectedMode)

        composeTestRule.onNodeWithTag("protect_friday_cta").assertIsEnabled()
        composeTestRule.onNodeWithTag("protect_friday_cta").performScrollTo().performClick()

        assertEquals(1, continueClicks)
    }

    @Test
    fun selecting_a_mode_enables_cta_and_click_invokes_onContinue() {
        var selectedMode: String? = "none"
        var continueClicks = 0

        composeTestRule.setContent {
            SetupScreen(
                innerPadding = PaddingValues(0.dp),
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                onContinue = { continueClicks++ },
                onBack = {},
            )
        }

        waitForCta()
        composeTestRule.onNodeWithTag("protect_friday_cta").assertIsEnabled()
        composeTestRule.onNodeWithTag("protect_friday_cta").performScrollTo().performClick()

        assertEquals(1, continueClicks)
    }

    @Test
    fun back_chevron_fires_onBack() {
        var backClicks = 0
        composeTestRule.setContent {
            SetupScreen(
                innerPadding = PaddingValues(0.dp),
                selectedMode = null,
                onModeSelected = {},
                onContinue = {},
                onBack = { backClicks++ },
            )
        }

        val backDescription = context().getString(R.string.friday_onboarding_back_content_description)
        composeTestRule.onNodeWithContentDescription(backDescription).performClick()

        assertEquals(1, backClicks)
    }

    /**
     * [SetupScreen] gates its content behind an 80ms-delayed [AnimatedVisibility]
     * entrance (design intro animation) driven by a real `delay()` inside a
     * `LaunchedEffect` — not a frame-clock animation, so ComposeTestRule's
     * default idling doesn't block on it. Poll for the CTA node to exist
     * before asserting/interacting to avoid racing that entrance animation.
     */
    private fun waitForCta() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("protect_friday_cta").fetchSemanticsNodes().isNotEmpty()
        }
    }
}

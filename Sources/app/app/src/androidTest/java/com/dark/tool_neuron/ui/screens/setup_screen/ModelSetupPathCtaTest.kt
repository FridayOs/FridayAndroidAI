package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * FRI-633 — extends ModelSetupPackCtaTest coverage (design-spec §7, HTML lines
 * 295-345 + `pathDefs` array 1206-1210) to the two untested Model Setup CTA
 * paths: GATEWAY ("Add gateway" -> onChooseGateway) and SKIP ("Continue" ->
 * onSkip). The LOCAL path (pack selection gating + onLocalPackConfirmed) is
 * already covered by ModelSetupPackCtaTest and is intentionally not
 * duplicated here.
 *
 * [ModelSetupScreen] is a pure-callback composable (no VM) — hosted
 * directly, no HXS wiring needed. The gateway/skip path cards have no
 * `testTag` (only the LOCAL card does, per ModelSetupScreen.kt), so they are
 * selected via their verbatim title text instead.
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class ModelSetupPathCtaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int) = context.getString(id)

    @Test
    fun gateway_path_shows_add_gateway_cta_and_invokes_on_choose_gateway() {
        var gatewayChosen = false
        var storeOpened = false
        var skipped = false

        composeTestRule.setContent {
            ModelSetupScreen(
                innerPadding = PaddingValues(0.dp),
                onLocalPackConfirmed = { true },
                onOpenStore = { storeOpened = true },
                onLocalImport = { _, _, _, _ -> },
                onSkip = { skipped = true },
                onBack = {},
                onChooseGateway = { gatewayChosen = true },
            )
        }

        composeTestRule
            .onNodeWithText(str(R.string.friday_model_setup_path_gateway_title))
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText(str(R.string.friday_model_setup_cta_gateway))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("model_setup_cta").assertIsEnabled()
        composeTestRule.onNodeWithTag("model_setup_cta").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(gatewayChosen)
        assertFalse(storeOpened)
        assertFalse(skipped)
    }

    @Test
    fun skip_path_shows_continue_cta_and_invokes_on_skip() {
        var gatewayChosen = false
        var skipped = false

        composeTestRule.setContent {
            ModelSetupScreen(
                innerPadding = PaddingValues(0.dp),
                onLocalPackConfirmed = { true },
                onOpenStore = {},
                onLocalImport = { _, _, _, _ -> },
                onSkip = { skipped = true },
                onBack = {},
                onChooseGateway = { gatewayChosen = true },
            )
        }

        composeTestRule
            .onNodeWithText(str(R.string.friday_model_setup_path_skip_title))
            .performScrollTo()
            .performClick()

        composeTestRule
            .onNodeWithText(str(R.string.friday_model_setup_continue))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("model_setup_cta").assertIsEnabled()
        composeTestRule.onNodeWithTag("model_setup_cta").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertTrue(skipped)
        assertFalse(gatewayChosen)
    }
}

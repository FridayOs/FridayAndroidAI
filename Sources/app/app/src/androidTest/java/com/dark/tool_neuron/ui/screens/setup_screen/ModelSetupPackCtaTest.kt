package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.viewmodel.PackCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRI-582 Codex-QA blocker fix (Blocker 3): Model Setup's local-pack CTA
 * (`model_setup_cta`) must stay disabled until a local pack is picked (even
 * after the "Use local models" path is chosen), then confirm the exact
 * selected pack id via onLocalPackConfirmed on click. Renders
 * [ModelSetupScreen] directly with fake callbacks — no Hilt wiring needed.
 */
@RunWith(AndroidJUnit4::class)
class ModelSetupPackCtaTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun cta_disabled_until_pack_selected_then_confirms_selected_pack() {
        var confirmedPackId: String? = null

        composeTestRule.setContent {
            ModelSetupScreen(
                innerPadding = PaddingValues(0.dp),
                onLocalPackConfirmed = { confirmedPackId = it },
                onOpenStore = {},
                onLocalImport = { _, _, _, _ -> },
                onSkip = {},
                onBack = {},
            )
        }

        composeTestRule.onNodeWithTag("model_setup_cta").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("model_path_local").performScrollTo().performClick()

        // Path picked but no pack yet -> CTA still disabled.
        composeTestRule.onNodeWithTag("model_setup_cta").assertIsNotEnabled()
        assertNull(confirmedPackId)

        composeTestRule.onNodeWithTag("pack_card_${PackCatalog.PACK_CHAT_ONLY}").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("model_setup_cta").assertIsEnabled()
        composeTestRule.onNodeWithTag("model_setup_cta").performScrollTo().performClick()

        assertEquals(PackCatalog.PACK_CHAT_ONLY, confirmedPackId)
    }
}

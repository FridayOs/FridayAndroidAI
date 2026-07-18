package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatComposer
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatHeader
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceHeader
import com.friday.ai.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 B4 — localized accessibility semantics regression. The chat header, voice
 * header, and composer are stateless production composables, so this hosts them directly
 * and asserts every interactive icon exposes a localized contentDescription that TalkBack
 * can announce (menu, provider pill, new-chat/to-chat, mic, send). Locks the a11y contract
 * so a future refactor can't silently drop the semantics labels.
 */
@RunWith(AndroidJUnit4::class)
class FridayShellAccessibilityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context().getString(id)

    @Test
    fun chatHeader_exposesLocalizedContentDescriptions() {
        composeTestRule.setContent {
            FridayChatHeader(
                onOpenMenu = {},
                onProviderClick = {},
                onNewChat = {},
                hasGateway = true,
                hasReadyGateway = true,
                label = "Work · gpt-4o-mini",
                hasBrainSelected = true,
            )
        }

        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_menu)).assertExists()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_provider)).assertExists()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_new_chat)).assertExists()
    }

    @Test
    fun voiceHeader_exposesLocalizedContentDescriptions() {
        composeTestRule.setContent {
            FridayVoiceHeader(
                onOpenMenu = {},
                onToChat = {},
                brainConfigured = true,
                gatewayLabel = "Work",
                ready = true,
                onProviderClick = {},
            )
        }

        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_menu)).assertExists()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_provider)).assertExists()
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_to_chat)).assertExists()
    }

    @Test
    fun composer_exposesMicAndSendContentDescriptions() {
        composeTestRule.setContent {
            FridayChatComposer(
                value = "hello",
                onValueChange = {},
                onSend = {},
                onMicClick = {},
                thinking = false,
            )
        }

        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_mic)).assertExists()
        // Non-blank input + not thinking -> send is enabled and announced.
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_cd_send))
            .assertExists()
            .assertIsEnabled()
    }
}

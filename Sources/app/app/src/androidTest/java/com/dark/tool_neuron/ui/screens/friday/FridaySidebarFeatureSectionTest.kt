package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 phase 6 — FridaySidebarFeatureSection is stateless (no Hilt/VM dep), so this
 * hosts the real production composable directly with fake callbacks. Locks: Live
 * voice/New conversation rows fire their live callbacks; Skills/MCP (disabled
 * previews) both route to onComingSoon; both disabled rows show the "coming soon" badge.
 */
@RunWith(AndroidJUnit4::class)
class FridaySidebarFeatureSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context().getString(id)

    @Test
    fun liveVoiceRow_firesOnOpenVoice_only() {
        var voiceClicks = 0
        var newConvoClicks = 0
        var comingSoonClicks = 0
        composeTestRule.setContent {
            FridaySidebarFeatureSection(
                onOpenVoice = { voiceClicks++ },
                onNewConversation = { newConvoClicks++ },
                onComingSoon = { comingSoonClicks++ },
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_live_voice)).performClick()

        assertEquals(1, voiceClicks)
        assertEquals(0, newConvoClicks)
        assertEquals(0, comingSoonClicks)
    }

    @Test
    fun newConversationRow_firesOnNewConversation_only() {
        var voiceClicks = 0
        var newConvoClicks = 0
        composeTestRule.setContent {
            FridaySidebarFeatureSection(
                onOpenVoice = { voiceClicks++ },
                onNewConversation = { newConvoClicks++ },
                onComingSoon = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_new_conversation)).performClick()

        assertEquals(0, voiceClicks)
        assertEquals(1, newConvoClicks)
    }

    @Test
    fun skillsRow_disabledPreview_firesOnComingSoon() {
        var comingSoonClicks = 0
        composeTestRule.setContent {
            FridaySidebarFeatureSection(onOpenVoice = {}, onNewConversation = {}, onComingSoon = { comingSoonClicks++ })
        }

        composeTestRule.onNodeWithText(str(R.string.friday_skills)).performClick()

        assertEquals(1, comingSoonClicks)
    }

    @Test
    fun mcpRow_disabledPreview_firesOnComingSoon() {
        var comingSoonClicks = 0
        composeTestRule.setContent {
            FridaySidebarFeatureSection(onOpenVoice = {}, onNewConversation = {}, onComingSoon = { comingSoonClicks++ })
        }

        composeTestRule.onNodeWithText(str(R.string.friday_mcp)).performClick()

        assertEquals(1, comingSoonClicks)
    }

    @Test
    fun bothDisabledRows_showComingSoonBadge() {
        composeTestRule.setContent {
            FridaySidebarFeatureSection(onOpenVoice = {}, onNewConversation = {}, onComingSoon = {})
        }

        composeTestRule.onAllNodesWithText(str(R.string.friday_coming_soon)).assertCountEquals(2)
    }
}

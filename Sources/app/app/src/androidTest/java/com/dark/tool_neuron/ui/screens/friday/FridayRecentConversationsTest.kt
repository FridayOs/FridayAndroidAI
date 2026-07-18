package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.viewmodel.RecentConvoUi
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 phase 6 — FridayRecentConversations is a pure renderer over already-windowed
 * VM state (FridayRecentConversationsViewModel itself stays unit-test-blocked: its
 * GatewayConfigRepository dependency is a concrete class with real Context+JNI HXS
 * vault construction in `init`, uninstantiable in a plain JVM test — documented gap,
 * see phase-06 report). This locks the composable's own contract: row tap ->
 * onOpenConversation(id), empty-state copy, loading/all-loaded status rows, and the
 * scroll-near-bottom -> onLoadMore() trigger the composable owns via its internal
 * LaunchedEffect + snapshotFlow over LazyListState.layoutInfo.
 */
@RunWith(AndroidJUnit4::class)
class FridayRecentConversationsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context().getString(id)

    private fun convo(id: String, title: String, isActive: Boolean = false) = RecentConvoUi(
        id = id,
        title = title,
        relativeTime = "1m ago",
        providerLabel = "OpenAI",
        providerLabelRes = null,
        isActive = isActive,
    )

    @Test
    fun emptyState_showsNoConvosCopy_noRows() {
        composeTestRule.setContent {
            FridayRecentConversations(
                items = emptyList(),
                canLoadMore = false,
                isLoading = false,
                isEmpty = true,
                onLoadMore = {},
                onOpenConversation = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_no_convos)).assertExists()
        composeTestRule.onNodeWithText(str(R.string.friday_start_new_chat)).assertExists()
    }

    @Test
    fun rowTap_invokesOnOpenConversation_withCorrectId() {
        var openedId: String? = null
        composeTestRule.setContent {
            FridayRecentConversations(
                items = listOf(convo("c1", "First chat"), convo("c2", "Second chat")),
                canLoadMore = false,
                isLoading = false,
                isEmpty = false,
                onLoadMore = {},
                onOpenConversation = { openedId = it },
            )
        }

        composeTestRule.onNodeWithText("Second chat").performClick()

        assertEquals("c2", openedId)
    }

    @Test
    fun loadingState_showsLoadingMoreRow() {
        composeTestRule.setContent {
            FridayRecentConversations(
                items = listOf(convo("c1", "First chat")),
                canLoadMore = true,
                isLoading = true,
                isEmpty = false,
                onLoadMore = {},
                onOpenConversation = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_loading_more)).assertExists()
    }

    @Test
    fun exhaustedState_showsAllLoadedRow() {
        composeTestRule.setContent {
            FridayRecentConversations(
                items = listOf(convo("c1", "First chat")),
                canLoadMore = false,
                isLoading = false,
                isEmpty = false,
                onLoadMore = {},
                onOpenConversation = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_all_loaded)).assertExists()
    }

    @Test
    fun scrollingNearBottom_invokesOnLoadMore() {
        var loadMoreCalls = 0
        val items = (1..20).map { convo("c$it", "Chat number $it") }
        composeTestRule.setContent {
            FridayRecentConversations(
                items = items,
                canLoadMore = true,
                isLoading = false,
                isEmpty = false,
                onLoadMore = { loadMoreCalls++ },
                onOpenConversation = {},
                modifier = Modifier.height(200.dp),
            )
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(19)
        composeTestRule.waitForIdle()

        assertTrue("scrolling within 2 of the last item must trigger onLoadMore()", loadMoreCalls > 0)
    }
}

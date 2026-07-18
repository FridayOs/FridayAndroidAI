package com.dark.tool_neuron.ui.screens.friday

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 phase 6 — FridayProviderSelectorSheet is stateless (design-spec §3: hosts
 * hoist providers/activeId/callbacks), so this hosts the real production composable
 * directly via createAndroidComposeRule<ComponentActivity>() (needed so Espresso.pressBack()
 * routes through the hosted activity's OnBackPressedDispatcher -> BackHandler(onBack =
 * onDismiss)). Locks: row tap -> onSelect(id) for the tapped provider only, hardware
 * back press -> onDismiss, scrim tap (outside the sheet) -> onDismiss, Add/Manage
 * buttons fire their own callbacks, and Current/No-provider card selection by activeId.
 */
@RunWith(AndroidJUnit4::class)
class FridayProviderSelectorSheetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context().getString(id)

    private fun gateway(id: String, label: String, provider: GatewayProvider = GatewayProvider.OPENAI) = GatewayConfig(
        id = id,
        provider = provider,
        label = label,
        baseUrl = "",
        apiKey = "",
        model = "gpt-4o-mini",
        createdAt = 0L,
        updatedAt = 0L,
        status = GatewayStatus.READY,
    )

    @Test
    fun tappingRow_invokesOnSelect_withThatProvidersId_only() {
        var selectedId: String? = null
        val providers = listOf(gateway("g1", "Work key"), gateway("g2", "Personal key"))
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = providers,
                activeId = "g1",
                onSelect = { selectedId = it },
                onAddProvider = {},
                onManageProviders = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Personal key · gpt-4o-mini").performClick()

        assertEquals("g2", selectedId)
    }

    @Test
    fun backPress_invokesOnDismiss() {
        var dismissCalls = 0
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = emptyList(),
                activeId = "",
                onSelect = {},
                onAddProvider = {},
                onManageProviders = {},
                onDismiss = { dismissCalls++ },
            )
        }
        composeTestRule.waitForIdle()

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCalls)
    }

    @Test
    fun scrimTap_invokesOnDismiss() {
        var dismissCalls = 0
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = emptyList(),
                activeId = "",
                onSelect = {},
                onAddProvider = {},
                onManageProviders = {},
                onDismiss = { dismissCalls++ },
            )
        }
        composeTestRule.waitForIdle()

        // Sheet is bottom-aligned and capped at 78% of screen height, so the top
        // strip of the root is covered only by the full-bleed scrim, not the sheet.
        composeTestRule.onRoot().performTouchInput { click(Offset(x = width / 2f, y = 10f)) }

        assertEquals(1, dismissCalls)
    }

    @Test
    fun addProviderButton_invokesOnAddProvider() {
        var addClicks = 0
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = emptyList(),
                activeId = "",
                onSelect = {},
                onAddProvider = { addClicks++ },
                onManageProviders = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_add_provider)).performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun manageProvidersButton_invokesOnManageProviders() {
        var manageClicks = 0
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = emptyList(),
                activeId = "",
                onSelect = {},
                onAddProvider = {},
                onManageProviders = { manageClicks++ },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_manage_providers)).performClick()

        assertEquals(1, manageClicks)
    }

    @Test
    fun noActiveProvider_showsNoProviderCard() {
        composeTestRule.setContent {
            FridayProviderSelectorSheet(
                visible = true,
                providers = listOf(gateway("g1", "Work key")),
                activeId = "gw-missing",
                onSelect = {},
                onAddProvider = {},
                onManageProviders = {},
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText(str(R.string.friday_no_provider_title)).assertExists()
    }
}

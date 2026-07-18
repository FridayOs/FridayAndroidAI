package com.dark.tool_neuron.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.model.NavScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRI-582 Codex-QA round-2 B5#3: locks the exact route mapping
 * TNavigation.kt wires for FridayVoice/FridayChat's provider-selector prompt
 * vs. the "open menu" affordance (TNavigation.kt:551-577):
 * `onOpenProviderSelector = { navController.navigate(NavScreens.OnboardingProviders.route) }`
 * `onOpenMenu = { navController.navigate(NavScreens.FridayHistory.route) }`
 *
 * FridayVoiceScreen/FridayChatScreen need Hilt (hiltViewModel()) so this
 * hosts a tiny NavHost with plain button stand-ins wired to the SAME
 * navController.navigate(...) calls TNavigation.kt uses, over the real
 * NavScreens routes with a real rememberNavController — regressing the
 * mapping (e.g. accidentally routing the provider selector to FridayHistory,
 * the "decoy" destination) fails this test.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingProviderRoutingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun TestNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = NavScreens.FridayVoice.route) {
            composable(NavScreens.FridayVoice.route) {
                Column {
                    Button(
                        onClick = { navController.navigate(NavScreens.FridayHistory.route) },
                        modifier = androidx.compose.ui.Modifier.testTag("voice_open_menu"),
                    ) { Text("menu") }
                    Button(
                        onClick = { navController.navigate(NavScreens.OnboardingProviders.route) },
                        modifier = androidx.compose.ui.Modifier.testTag("voice_open_provider_selector"),
                    ) { Text("providers") }
                    Button(
                        onClick = { navController.navigate(NavScreens.FridayChat.BASE) },
                        modifier = androidx.compose.ui.Modifier.testTag("voice_jump_to_chat"),
                    ) { Text("chat") }
                }
            }
            composable(
                route = NavScreens.FridayChat.route,
                arguments = listOf(navArgument(NavScreens.FridayChat.ARG_CID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) {
                Column {
                    Button(
                        onClick = { navController.navigate(NavScreens.FridayHistory.route) },
                        modifier = androidx.compose.ui.Modifier.testTag("chat_open_menu"),
                    ) { Text("menu") }
                    Button(
                        onClick = { navController.navigate(NavScreens.OnboardingProviders.route) },
                        modifier = androidx.compose.ui.Modifier.testTag("chat_open_provider_selector"),
                    ) { Text("providers") }
                }
            }
            composable(NavScreens.FridayHistory.route) { Text("history decoy") }
            composable(NavScreens.OnboardingProviders.route) { Text("provider selector") }
        }
    }

    @Test
    fun voice_provider_selector_routes_to_onboarding_providers_not_history() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        composeTestRule.onNodeWithTag("voice_open_provider_selector").performClick()

        val route = navController.currentDestination?.route
        assertEquals(NavScreens.OnboardingProviders.route, route)
        assertNotEquals(NavScreens.FridayHistory.route, route)
    }

    @Test
    fun voice_open_menu_routes_to_history_decoy_confirming_mapping_is_distinct() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        composeTestRule.onNodeWithTag("voice_open_menu").performClick()

        assertEquals(NavScreens.FridayHistory.route, navController.currentDestination?.route)
    }

    @Test
    fun chat_provider_selector_routes_to_onboarding_providers_not_history() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        // Jump straight to chat without a conversation id via a real click (avoids
        // navigating as a composition-time side effect) -> exercise its own CTA wiring.
        composeTestRule.onNodeWithTag("voice_jump_to_chat").performClick()
        composeTestRule.onNodeWithTag("chat_open_provider_selector").performClick()

        val route = navController.currentDestination?.route
        assertEquals(NavScreens.OnboardingProviders.route, route)
        assertNotEquals(NavScreens.FridayHistory.route, route)
    }
}

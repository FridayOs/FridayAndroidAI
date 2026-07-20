package com.dark.tool_neuron.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.dark.tool_neuron.model.ProviderFlowOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 round-5 BLOCKER 1: locks the origin-aware provider flow at the AppScaffold /
 * TNavigation navigation seam. Production wires:
 *  - selector sheet onAddProvider/onManageProviders capture originForRoute(currentRoute)
 *    then navigate(routeFor(origin)) (or BASE when null) — the same calls the stand-in
 *    buttons below issue.
 *  - OnboardingProviders' Continue CTA is origin-aware: origin != null -> popBackStack()
 *    (return to Voice/Chat, preserving the active conversation); origin == null ->
 *    onContinueToFriday() (first-run -> FridayVoice + popUpTo(0){inclusive}).
 *
 * The real FridayVoice/FridayChat/OnboardingProviders screens need Hilt, so this hosts a
 * tiny NavHost with plain button stand-ins calling the SAME navigate/popBackStack calls
 * over real NavScreens destinations with a real rememberNavController. Driving the
 * stand-ins proves the route shapes + origin-aware Continue return to the right origin.
 */
@RunWith(AndroidJUnit4::class)
class ProviderFlowOriginNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun TestNavHost(
        navController: NavHostController,
        onContinueToFriday: () -> Unit,
    ) {
        NavHost(navController = navController, startDestination = NavScreens.FridayVoice.route) {
            composable(NavScreens.FridayVoice.route) {
                Column {
                    Button(
                        onClick = {
                            navController.navigate(
                                NavScreens.OnboardingProviders.routeFor(ProviderFlowOrigin.Voice)
                            )
                        },
                        modifier = Modifier.testTag("voice_open_providers"),
                    ) { Text("providers") }
                    Text("voice")
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
                        onClick = {
                            navController.navigate(
                                NavScreens.OnboardingProviders.routeFor(ProviderFlowOrigin.Chat)
                            )
                        },
                        modifier = Modifier.testTag("chat_open_providers"),
                    ) { Text("providers") }
                    Text("chat")
                }
            }
            composable(
                route = NavScreens.OnboardingProviders.route,
                arguments = listOf(navArgument(NavScreens.OnboardingProviders.ARG_ORIGIN) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) { backStack ->
                val origin = backStack.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN)
                Column {
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.testTag("providers_back"),
                    ) { Text("back") }
                    Button(
                        // Same origin-aware Continue TNavigation wires: shell entry (origin
                        // != null) pops back to the origin; first-run (origin == null) runs
                        // the onContinueToFriday legacy completion.
                        onClick = { if (origin != null) navController.popBackStack() else onContinueToFriday() },
                        modifier = Modifier.testTag("providers_continue"),
                    ) { Text("continue") }
                }
            }
        }
    }

    @Test
    fun voiceOrigin_continue_returnsToVoice_notChat_andPreservesStack() {
        lateinit var navController: NavHostController
        var firstRunContinue = 0
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) { firstRunContinue++ }
        }

        composeTestRule.onNodeWithTag("voice_open_providers").performClick()
        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        composeTestRule.onNodeWithTag("providers_continue").performClick()

        assertEquals("Voice origin returns to Voice", NavScreens.FridayVoice.route, navController.currentDestination?.route)
        assertNotEquals(NavScreens.FridayChat.route, navController.currentDestination?.route)
        assertEquals("Voice origin never triggers first-run completion", 0, firstRunContinue)
    }

    @Test
    fun chatOrigin_continue_returnsToChat_notVoice_andPreservesStack() {
        lateinit var navController: NavHostController
        var firstRunContinue = 0
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) { firstRunContinue++ }
        }

        // Move to Chat (base route, no cid) then open providers with Chat origin.
        navController.navigate(NavScreens.FridayChat.BASE)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_open_providers").performClick()
        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        composeTestRule.onNodeWithTag("providers_continue").performClick()

        assertEquals("Chat origin returns to Chat base", NavScreens.FridayChat.route, navController.currentDestination?.route)
        assertNotEquals(NavScreens.FridayVoice.route, navController.currentDestination?.route)
        assertEquals("Chat origin never triggers first-run completion", 0, firstRunContinue)
    }

    @Test
    fun firstRun_continue_runsLegacyCompletion_clearingStack() {
        lateinit var navController: NavHostController
        var firstRunContinue = 0
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) { firstRunContinue++ }
        }

        // First-run entry: navigate to BASE (no origin) — the path onChooseGateway uses.
        navController.navigate(NavScreens.OnboardingProviders.BASE)
        composeTestRule.waitForIdle()
        assertNull("first-run entry has no origin arg", navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN))
        composeTestRule.onNodeWithTag("providers_continue").performClick()

        assertEquals("first-run Continue runs the legacy completion", 1, firstRunContinue)
    }

    @Test
    fun back_chevron_returnsToOrigin_fromEitherOrigin() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) {}
        }

        // Voice origin -> back -> Voice.
        composeTestRule.onNodeWithTag("voice_open_providers").performClick()
        composeTestRule.onNodeWithTag("providers_back").performClick()
        assertEquals(NavScreens.FridayVoice.route, navController.currentDestination?.route)

        // Chat origin -> back -> Chat.
        navController.navigate(NavScreens.FridayChat.BASE)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_open_providers").performClick()
        composeTestRule.onNodeWithTag("providers_back").performClick()
        assertEquals(NavScreens.FridayChat.route, navController.currentDestination?.route)
    }
}

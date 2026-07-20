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
import com.dark.tool_neuron.ui.screens.system_ui.onboardingProvidersRouteFromOrigin
import com.dark.tool_neuron.ui.screens.system_ui.originForRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-574 round-6 BLOCKER 2: locks the origin-aware provider flow by exercising the REAL
 * production seams — AppScaffold.originForRoute / onboardingProvidersRouteFromOrigin
 * (AppScaffold.kt, top-level) and TNavigation.onboardingProvidersContinue
 * (TNavigation.kt:112) — instead of re-implementing routeFor/Continue logic in a mini
 * NavHost. The stand-in buttons below call those production functions, and the Continue
 * stand-in calls onboardingProvidersContinue(...), so an edit to the production routing
 * decision fails this test.
 *
 * Pure-function tests (originForRoute_* / onboardingProvidersRouteFromOrigin_*) assert the
 * seam directly with no Compose rule. Compose-driven tests host a tiny NavHost with plain
 * button stand-ins (real FridayVoice/FridayChat/OnboardingProviders screens need Hilt,
 * which has no @HiltAndroidTest infra in this repo) and drive every navigation via button
 * clicks + waitForIdle() — no direct navController.navigate(...) in test bodies, which was
 * the cause of the round-5 teardown crash ("State must be at least 'CREATED'...").
 *
 * Coverage: Add AND Manage from BOTH Voice and Chat origins, Continue (origin-aware ->
 * returns to origin), back (-> origin), first-run (BASE, no origin -> onContinueToFriday).
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
                    // Stand-in for AppScaffold's selector-sheet onAddProvider/onManageProviders
                    // callbacks: they call onboardingProvidersRouteFromOrigin(currentRoute),
                    // where currentRoute == NavScreens.FridayVoice.route on this destination.
                    Button(
                        onClick = {
                            navController.navigate(
                                onboardingProvidersRouteFromOrigin(NavScreens.FridayVoice.route)
                            )
                        },
                        modifier = Modifier.testTag("voice_add_provider"),
                    ) { Text("add") }
                    Button(
                        onClick = {
                            navController.navigate(
                                onboardingProvidersRouteFromOrigin(NavScreens.FridayVoice.route)
                            )
                        },
                        modifier = Modifier.testTag("voice_manage_providers"),
                    ) { Text("manage") }
                    // Stand-in for AppScaffold's onChooseGateway (first-run, no Friday surface):
                    // onboardingProvidersRouteFromOrigin(null) == BASE.
                    Button(
                        onClick = {
                            navController.navigate(onboardingProvidersRouteFromOrigin(null))
                        },
                        modifier = Modifier.testTag("voice_first_run_providers"),
                    ) { Text("first-run") }
                    // Stand-in for TNavigation's Voice onToChat: carry-no-cid -> Chat BASE.
                    // Lets Chat-origin tests reach Chat via a real click instead of a direct
                    // navController.navigate(...) in the test body (which raced composition and
                    // crashed teardown with "State must be at least 'CREATED'...").
                    Button(
                        onClick = { navController.navigate(NavScreens.FridayChat.BASE) },
                        modifier = Modifier.testTag("voice_go_to_chat"),
                    ) { Text("to chat") }
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
                                onboardingProvidersRouteFromOrigin(NavScreens.FridayChat.route)
                            )
                        },
                        modifier = Modifier.testTag("chat_add_provider"),
                    ) { Text("add") }
                    Button(
                        onClick = {
                            navController.navigate(
                                onboardingProvidersRouteFromOrigin(NavScreens.FridayChat.route)
                            )
                        },
                        modifier = Modifier.testTag("chat_manage_providers"),
                    ) { Text("manage") }
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
                    // Stand-in for TNavigation's OnboardingProviders onContinueToFriday wiring:
                    // it calls onboardingProvidersContinue(originName, navController, onContinueToFriday).
                    Button(
                        onClick = onboardingProvidersContinue(origin, navController, onContinueToFriday),
                        modifier = Modifier.testTag("providers_continue"),
                    ) { Text("continue") }
                }
            }
        }
    }

    // --- Pure-function assertions on the production seam (no Compose rule) ---

    @Test
    fun originForRoute_voiceRoute_returnsVoice() {
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Voice,
            originForRoute(NavScreens.FridayVoice.route),
        )
    }

    @Test
    fun originForRoute_chatRoute_returnsChat() {
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Chat,
            originForRoute(NavScreens.FridayChat.route),
        )
    }

    @Test
    fun originForRoute_chatBase_returnsChat() {
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Chat,
            originForRoute(NavScreens.FridayChat.BASE),
        )
    }

    @Test
    fun originForRoute_unknownRoute_returnsNull() {
        assertNull(originForRoute("some_other_route"))
        assertNull(originForRoute(null))
    }

    @Test
    fun onboardingProvidersRouteFromOrigin_voiceRoute_isOriginVoiceRoute() {
        assertEquals(
            NavScreens.OnboardingProviders.routeFor(com.dark.tool_neuron.model.ProviderFlowOrigin.Voice),
            onboardingProvidersRouteFromOrigin(NavScreens.FridayVoice.route),
        )
    }

    @Test
    fun onboardingProvidersRouteFromOrigin_chatRoute_isOriginChatRoute() {
        assertEquals(
            NavScreens.OnboardingProviders.routeFor(com.dark.tool_neuron.model.ProviderFlowOrigin.Chat),
            onboardingProvidersRouteFromOrigin(NavScreens.FridayChat.route),
        )
    }

    @Test
    fun onboardingProvidersRouteFromOrigin_nullRoute_isBase() {
        assertEquals(
            NavScreens.OnboardingProviders.BASE,
            onboardingProvidersRouteFromOrigin(null),
        )
    }

    // --- Compose-driven tests exercising the seam through stand-in buttons ---

    @Test
    fun voiceOrigin_addProvider_navigatesToOnboardingProvidersWithVoiceOrigin() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) {}
        }

        composeTestRule.onNodeWithTag("voice_add_provider").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Voice.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
    }

    @Test
    fun voiceOrigin_manageProvider_navigatesToOnboardingProviders() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) {}
        }

        composeTestRule.onNodeWithTag("voice_manage_providers").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Voice.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
    }

    @Test
    fun chatOrigin_addProvider_navigatesToOnboardingProvidersWithChatOrigin() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) {}
        }
        // Move to Chat via a real click (voice_go_to_chat) so navigation happens through
        // the NavHost lifecycle, not a composition-racing direct navController.navigate.
        composeTestRule.onNodeWithTag("voice_go_to_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_add_provider").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Chat.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
    }

    @Test
    fun chatOrigin_manageProvider_navigatesToOnboardingProvidersWithChatOrigin() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) {}
        }
        composeTestRule.onNodeWithTag("voice_go_to_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_manage_providers").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertEquals(
            com.dark.tool_neuron.model.ProviderFlowOrigin.Chat.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
    }

    @Test
    fun voiceOrigin_continue_returnsToVoice_notChat_andPreservesStack() {
        lateinit var navController: NavHostController
        var firstRunContinue = 0
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) { firstRunContinue++ }
        }

        composeTestRule.onNodeWithTag("voice_add_provider").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        composeTestRule.onNodeWithTag("providers_continue").performClick()
        composeTestRule.waitForIdle()

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
        composeTestRule.onNodeWithTag("voice_go_to_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_add_provider").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        composeTestRule.onNodeWithTag("providers_continue").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Chat origin returns to Chat base", NavScreens.FridayChat.route, navController.currentDestination?.route)
        assertNotEquals(NavScreens.FridayVoice.route, navController.currentDestination?.route)
        assertEquals("Chat origin never triggers first-run completion", 0, firstRunContinue)
    }

    @Test
    fun firstRun_continue_runsLegacyCompletion() {
        lateinit var navController: NavHostController
        var firstRunContinue = 0
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController) { firstRunContinue++ }
        }

        // First-run entry: the Voice stand-in's first-run button calls the production seam
        // onboardingProvidersRouteFromOrigin(null) == BASE (the onChooseGateway path).
        composeTestRule.onNodeWithTag("voice_first_run_providers").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertNull(
            "first-run entry has no origin arg",
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
        // Continue via the production seam: origin == null -> onContinueToFriday() (counter++);
        // no popBackStack, so the back stack stays non-empty and teardown is clean.
        composeTestRule.onNodeWithTag("providers_continue").performClick()
        composeTestRule.waitForIdle()

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
        composeTestRule.onNodeWithTag("voice_add_provider").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("providers_back").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NavScreens.FridayVoice.route, navController.currentDestination?.route)

        // Chat origin -> back -> Chat.
        composeTestRule.onNodeWithTag("voice_go_to_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_add_provider").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("providers_back").performClick()
        composeTestRule.waitForIdle()
        assertEquals(NavScreens.FridayChat.route, navController.currentDestination?.route)
    }
}

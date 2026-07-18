package com.dark.tool_neuron.ui.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.viewmodel.OnboardingBackNav
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FRI-582 Codex-QA round-2 B5#4: locks the real onboarding back-chevron
 * back-stack behavior TNavigation.kt's local `onboardingBack(currentRoute)`
 * implements (TNavigation.kt:113-120):
 * ```
 * OnboardingBackNav.previousRoute(currentRoute)?.let { prev ->
 *     navController.navigate(prev) {
 *         popUpTo(currentRoute) { inclusive = true }
 *         launchSingleTop = true
 *     }
 * }
 * ```
 * `onboardingBack` is a private local function, not exported, so this hosts a
 * tiny NavHost over the real ORDER routes (Terms -> Tour -> SetupScreen ->
 * SetupTheme -> ModelSetup) with each screen's back button calling the exact
 * same [OnboardingBackNav.previousRoute] + popUpTo(currentRoute){inclusive}
 * pattern verbatim, over a real [rememberNavController] back stack. Proves
 * both the resulting destination at each step AND that popUpTo(inclusive)
 * keeps the back stack from growing unbounded (a regression there would let
 * repeated back+forward cycles leak entries).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingBackStackNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val order = listOf(
        NavScreens.TermsConditions.route,
        NavScreens.FeatureTour.route,
        NavScreens.SetupScreen.route,
        NavScreens.SetupTheme.route,
        NavScreens.ModelSetup.route,
    )

    @Composable
    private fun onboardingBack(navController: NavHostController, currentRoute: String): () -> Unit = {
        OnboardingBackNav.previousRoute(currentRoute)?.let { prev ->
            navController.navigate(prev) {
                popUpTo(currentRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    @Composable
    private fun TestNavHost(navController: NavHostController, startDestination: String = NavScreens.ModelSetup.route) {
        NavHost(navController = navController, startDestination = startDestination) {
            order.forEach { route ->
                composable(route) {
                    Button(
                        onClick = onboardingBack(navController, route),
                        modifier = Modifier.testTag("back_$route"),
                    ) { Text("back") }
                }
            }
            // ORDER floor: OnboardingBackNav.PREVIOUS has no entry for
            // TermsConditions's real predecessor (LanguageSelection), so a
            // back click there must resolve to null and no-op. Registered
            // here (outside `order`) since it is the destination Terms'
            // back click actually navigates to, not a member of ORDER itself.
            composable(NavScreens.LanguageSelection.route) {
                Button(
                    onClick = onboardingBack(navController, NavScreens.LanguageSelection.route),
                    modifier = Modifier.testTag("back_${NavScreens.LanguageSelection.route}"),
                ) { Text("back") }
            }
        }
    }

    @Test
    fun repeated_back_clicks_follow_exact_ORDER_reverse_from_model_setup_to_terms() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        // Start: ModelSetup. ORDER-reverse: SetupTheme -> SetupScreen -> FeatureTour -> TermsConditions.
        assertEquals(NavScreens.ModelSetup.route, navController.currentDestination?.route)

        composeTestRule.onNodeWithTag("back_${NavScreens.ModelSetup.route}").performClick()
        assertEquals(NavScreens.SetupTheme.route, navController.currentDestination?.route)

        composeTestRule.onNodeWithTag("back_${NavScreens.SetupTheme.route}").performClick()
        assertEquals(NavScreens.SetupScreen.route, navController.currentDestination?.route)

        composeTestRule.onNodeWithTag("back_${NavScreens.SetupScreen.route}").performClick()
        assertEquals(NavScreens.FeatureTour.route, navController.currentDestination?.route)

        composeTestRule.onNodeWithTag("back_${NavScreens.FeatureTour.route}").performClick()
        assertEquals(NavScreens.TermsConditions.route, navController.currentDestination?.route)
    }

    @Test
    fun popUpTo_inclusive_keeps_back_stack_from_growing_unbounded() {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController)
        }

        val stackSizeAtStart = navController.currentBackStack.value.size

        composeTestRule.onNodeWithTag("back_${NavScreens.ModelSetup.route}").performClick()
        composeTestRule.onNodeWithTag("back_${NavScreens.SetupTheme.route}").performClick()
        composeTestRule.onNodeWithTag("back_${NavScreens.SetupScreen.route}").performClick()

        // popUpTo(currentRoute){inclusive=true} replaces the current entry each
        // hop instead of pushing on top of it, so the stack size stays flat
        // across 3 back-hops, not growing to start+3.
        assertEquals(
            "back-stack must not accumulate entries across repeated onboardingBack hops",
            stackSizeAtStart,
            navController.currentBackStack.value.size,
        )
        assertEquals(NavScreens.FeatureTour.route, navController.currentDestination?.route)
    }

    @Test
    fun terms_conditions_back_click_navigates_to_language_selection() {
        // OnboardingBackNav.previousRoute(TermsConditions) == LanguageSelection
        // (its real predecessor) -> back click must land there, not no-op.
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController, startDestination = NavScreens.TermsConditions.route)
        }

        composeTestRule.onNodeWithTag("back_${NavScreens.TermsConditions.route}").performClick()
        assertEquals(NavScreens.LanguageSelection.route, navController.currentDestination?.route)
    }

    @Test
    fun language_selection_has_no_further_back_target_click_is_a_noop() {
        // OnboardingBackNav.previousRoute(LanguageSelection) is null (ORDER
        // floor - LanguageSelection has no PREVIOUS entry) -> the back click
        // must be a no-op, staying on LanguageSelection rather than crashing
        // or navigating to some earlier onboarding screen.
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            TestNavHost(navController, startDestination = NavScreens.LanguageSelection.route)
        }

        composeTestRule.onNodeWithTag("back_${NavScreens.LanguageSelection.route}").performClick()
        assertEquals(NavScreens.LanguageSelection.route, navController.currentDestination?.route)
    }
}

package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FRI-582 Codex-QA blocker fix (Blocker 1): verifies [OnboardingBackNav] maps
 * every onboarding step to its ORDER-previous route (HTML:1187) and returns
 * null for steps with no back affordance (intro, language) or any
 * non-onboarding route.
 */
class OnboardingBackNavTest {

    @Test
    fun terms_previous_is_language_selection() {
        assertEquals(
            NavScreens.LanguageSelection.route,
            OnboardingBackNav.previousRoute(NavScreens.TermsConditions.route),
        )
    }

    @Test
    fun feature_tour_previous_is_terms() {
        assertEquals(
            NavScreens.TermsConditions.route,
            OnboardingBackNav.previousRoute(NavScreens.FeatureTour.route),
        )
    }

    @Test
    fun setup_screen_previous_is_feature_tour() {
        assertEquals(
            NavScreens.FeatureTour.route,
            OnboardingBackNav.previousRoute(NavScreens.SetupScreen.route),
        )
    }

    @Test
    fun setup_theme_previous_is_setup_screen() {
        assertEquals(
            NavScreens.SetupScreen.route,
            OnboardingBackNav.previousRoute(NavScreens.SetupTheme.route),
        )
    }

    @Test
    fun model_setup_previous_is_setup_theme() {
        assertEquals(
            NavScreens.SetupTheme.route,
            OnboardingBackNav.previousRoute(NavScreens.ModelSetup.route),
        )
    }

    @Test
    fun intro_has_no_previous_route() {
        assertNull(OnboardingBackNav.previousRoute(NavScreens.IntroScreen.route))
    }

    @Test
    fun language_selection_has_no_previous_route() {
        assertNull(OnboardingBackNav.previousRoute(NavScreens.LanguageSelection.route))
    }

    @Test
    fun non_onboarding_route_has_no_previous_route() {
        assertNull(OnboardingBackNav.previousRoute(NavScreens.HomeScreen.route))
        assertNull(OnboardingBackNav.previousRoute(NavScreens.Settings.route))
        assertNull(OnboardingBackNav.previousRoute("unknown_route"))
    }
}

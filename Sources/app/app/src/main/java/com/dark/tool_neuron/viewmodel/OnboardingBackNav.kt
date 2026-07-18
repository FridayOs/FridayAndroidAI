package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens

/**
 * Pure previous-route lookup for onboarding back navigation (design `goBack`,
 * HTML:1499-1501). ORDER: intro -> language -> terms -> tour -> lock(Protect)
 * -> themeSetup -> modelSetup -> (providers|voice). Intro and language have no
 * back affordance in the design, so callers of [previousRoute] never look
 * those routes up; they and any non-onboarding route resolve to null here.
 */
object OnboardingBackNav {
    private val PREVIOUS: Map<String, String> = mapOf(
        NavScreens.TermsConditions.route to NavScreens.LanguageSelection.route,
        NavScreens.FeatureTour.route to NavScreens.TermsConditions.route,
        NavScreens.SetupScreen.route to NavScreens.FeatureTour.route,
        NavScreens.SetupTheme.route to NavScreens.SetupScreen.route,
        NavScreens.ModelSetup.route to NavScreens.SetupTheme.route,
    )

    /** Returns the ORDER-previous route for [current], or null when there is none. */
    fun previousRoute(current: String): String? = PREVIOUS[current]
}

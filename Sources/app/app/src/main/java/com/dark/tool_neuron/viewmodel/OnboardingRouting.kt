package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens

/**
 * Production seam for onboarding entry-point navigation targets (FRI-582 QA
 * round-3 B5). `TNavigation.kt` and the corresponding androidTest both call
 * these constants so editing the routes here fails the tests.
 */
object FridayEntryRouting {
    // First speak/send after "set up later" opens the onboarding provider selector, NOT chat history.
    val providerSelectorRoute: String = NavScreens.OnboardingProviders.route

    // Hamburger menu opens conversation history.
    val menuRoute: String = NavScreens.FridayHistory.route
}

/**
 * Fail-closed onboarding-completion seam (FRI-582 QA round-3 B5). Onboarding
 * may only advance when the pack actually enqueued ([DownloadPackOutcome.Success]).
 */
object ModelSetupCompletion {
    fun completesOnboarding(outcome: DownloadPackOutcome): Boolean = outcome is DownloadPackOutcome.Success
}

/**
 * Locale-change decision seam (FRI-582 QA round-3 B1/B5). A recreate() is
 * needed only when the picked tag differs from the Activity's live config
 * locale.
 */
object OnboardingLocaleTransition {
    fun localeChanged(selectedTag: String, activityTag: String): Boolean =
        selectedTag.isNotBlank() && !selectedTag.equals(activityTag, ignoreCase = true)
}

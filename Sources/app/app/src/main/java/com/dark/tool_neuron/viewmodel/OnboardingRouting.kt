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

    /**
     * Executes the Language-Continue transition in the fixed order one-tap
     * locale change depends on (FRI-582 QA round-4 B1). The ordering is the
     * production contract, so it lives here — not inline in the screen — and is
     * regression-locked by OnboardingRoutingTest:
     *  1. [confirm] first — persists the language-selected flag SYNCHRONOUSLY so
     *     the gate that [advance] consults already resolves to Terms.
     *  2. [advance] before [recreate] — navigates so the saved NavController back
     *     stack captures the next route; recreate()'s restoreState() then shows
     *     THAT route in the new locale (one tap, no double-Continue).
     *  3. [recreate] only when the locale actually changed AND an Activity exists
     *     ([recreate] non-null); otherwise the CTA still advances (never dead).
     *
     * @return true when a recreate was requested (locale changed and available).
     */
    fun apply(
        selectedTag: String,
        activityTag: String,
        confirm: () -> Unit,
        advance: () -> Unit,
        recreate: (() -> Unit)?,
    ): Boolean {
        confirm()
        return if (localeChanged(selectedTag, activityTag) && recreate != null) {
            advance()
            recreate()
            true
        } else {
            advance()
            false
        }
    }
}

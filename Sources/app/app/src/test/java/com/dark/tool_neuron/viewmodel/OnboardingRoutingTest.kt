package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FRI-582 Codex-QA round-3 B5: JVM coverage for the pure production seams in
 * OnboardingRouting.kt. TNavigation.kt and LanguageSelectionScreen.kt call
 * these SAME objects, so editing them to something incorrect fails these
 * tests directly (no re-implementation/copy of the decision logic here).
 */
class OnboardingRoutingTest {

    @Test
    fun fridayEntryRouting_providerSelectorRoute_matches_onboardingProviders() {
        assertEquals(NavScreens.OnboardingProviders.route, FridayEntryRouting.providerSelectorRoute)
    }

    @Test
    fun fridayEntryRouting_menuRoute_matches_fridayHistory() {
        assertEquals(NavScreens.FridayHistory.route, FridayEntryRouting.menuRoute)
    }

    @Test
    fun fridayEntryRouting_providerSelectorRoute_and_menuRoute_are_distinct() {
        assertNotEquals(FridayEntryRouting.providerSelectorRoute, FridayEntryRouting.menuRoute)
    }

    @Test
    fun modelSetupCompletion_success_completes_onboarding() {
        assertTrue(ModelSetupCompletion.completesOnboarding(DownloadPackOutcome.Success(listOf("lfm25-350m"))))
    }

    @Test
    fun modelSetupCompletion_missing_does_not_complete_onboarding() {
        assertFalse(ModelSetupCompletion.completesOnboarding(DownloadPackOutcome.Missing(listOf("x"))))
    }

    @Test
    fun modelSetupCompletion_unknownPack_does_not_complete_onboarding() {
        assertFalse(ModelSetupCompletion.completesOnboarding(DownloadPackOutcome.UnknownPack))
    }

    @Test
    fun onboardingLocaleTransition_differing_tags_change_locale() {
        assertTrue(OnboardingLocaleTransition.localeChanged("vi", "en"))
    }

    @Test
    fun onboardingLocaleTransition_blank_selected_tag_never_changes_locale() {
        assertFalse(OnboardingLocaleTransition.localeChanged("", "en"))
    }

    @Test
    fun onboardingLocaleTransition_case_insensitive_equal_tags_do_not_change_locale() {
        assertFalse(OnboardingLocaleTransition.localeChanged("en", "EN"))
    }

    @Test
    fun onboardingLocaleTransition_identical_tags_do_not_change_locale() {
        assertFalse(OnboardingLocaleTransition.localeChanged("vi", "vi"))
    }

    // FRI-582 QA round-4 B1: lock the confirm→advance→recreate ORDER (the round-2
    // regression) at the production seam LanguageSelectionScreen calls. Reordering,
    // dropping advance(), or recreating before advancing breaks these.
    @Test
    fun localeTransition_apply_on_change_runs_confirm_then_advance_then_recreate_in_order() {
        val calls = mutableListOf<String>()
        val requested = OnboardingLocaleTransition.apply(
            selectedTag = "vi",
            activityTag = "en",
            confirm = { calls += "confirm" },
            advance = { calls += "advance" },
            recreate = { calls += "recreate" },
        )
        assertEquals(listOf("confirm", "advance", "recreate"), calls)
        assertTrue("locale change with an Activity requests a recreate", requested)
    }

    @Test
    fun localeTransition_apply_without_change_runs_confirm_then_advance_no_recreate() {
        val calls = mutableListOf<String>()
        val requested = OnboardingLocaleTransition.apply(
            selectedTag = "en",
            activityTag = "en",
            confirm = { calls += "confirm" },
            advance = { calls += "advance" },
            recreate = { calls += "recreate" },
        )
        assertEquals(listOf("confirm", "advance"), calls)
        assertFalse("unchanged locale never recreates", requested)
    }

    @Test
    fun localeTransition_apply_with_null_recreate_still_advances_no_recreate() {
        val calls = mutableListOf<String>()
        val requested = OnboardingLocaleTransition.apply(
            selectedTag = "vi",
            activityTag = "en",
            confirm = { calls += "confirm" },
            advance = { calls += "advance" },
            recreate = null,
        )
        assertEquals(listOf("confirm", "advance"), calls)
        assertFalse("no Activity → advance only, CTA never dead", requested)
    }
}

package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FRI-582 Codex-QA round-2 B5#2 (gate-resume half): [OnboardingGateLogic] is
 * the pure decision table behind [ScaffoldViewModel.resolveStartDestination]
 * — it reads the SAME persisted flags [AppPreferences.modelSetupDone] etc.
 * feed it, so proving resume order here proves resume correctness for a
 * restarted app without needing the encrypted HXS-backed prefs store (that
 * round-trip is covered separately, instrumented, in
 * ModelPathPersistenceTest). Covers: a fully "mid model-setup" install
 * resumes on ModelSetup; once modelDone flips true (the exact effect of
 * completing/persisting a local pack choice, see
 * ModelStoreViewModel.recordModelPathChoice + ScaffoldViewModel.markModelSetupDone)
 * resume skips straight past Model Setup to lock/voice; and the
 * themeSetupDone-backward-compat clause stays intact for pre-existing
 * installs (modelDone=true, themeSetupDone=false).
 */
class OnboardingGateLogicTest {

    private fun resolve(
        hasJwt: Boolean = true,
        languageSelected: Boolean = true,
        tcAccepted: Boolean = true,
        onboarded: Boolean = true,
        securityDone: Boolean = true,
        themeSetupDone: Boolean = true,
        modelDone: Boolean = false,
        lockEnabled: Boolean = false,
    ) = OnboardingGateLogic.resolveOnboardingRoute(
        hasJwt = hasJwt,
        languageSelected = languageSelected,
        tcAccepted = tcAccepted,
        onboarded = onboarded,
        securityDone = securityDone,
        themeSetupDone = themeSetupDone,
        modelDone = modelDone,
        lockEnabled = lockEnabled,
    )

    @Test
    fun resume_mid_flow_before_model_setup_lands_on_model_setup() {
        // Everything up to (and incl.) Theme is done, model setup is not.
        assertEquals(NavScreens.ModelSetup.route, resolve(modelDone = false))
    }

    @Test
    fun resume_after_model_setup_persisted_skips_model_setup_no_lock() {
        // modelDone=true (set by ScaffoldViewModel.markModelSetupDone after
        // ModelStoreViewModel.recordModelPathChoice persists the pack choice)
        // -> resume must NOT re-show ModelSetup.
        assertEquals(NavScreens.FridayVoice.route, resolve(modelDone = true, lockEnabled = false))
    }

    @Test
    fun resume_after_model_setup_persisted_with_lock_enabled_goes_to_password() {
        assertEquals(NavScreens.PasswordScreen.route, resolve(modelDone = true, lockEnabled = true))
    }

    @Test
    fun backward_compat_model_done_but_theme_not_done_skips_theme_step() {
        // Pre-existing installs: modelDone=true, themeSetupDone=false (flag
        // didn't exist yet) must NOT get re-trapped on the Theme step.
        assertEquals(
            NavScreens.FridayVoice.route,
            resolve(themeSetupDone = false, modelDone = true, lockEnabled = false),
        )
    }

    @Test
    fun theme_not_done_and_model_not_done_still_enforces_theme_first() {
        // Genuinely mid-flow (never finished onboarding) -> Theme still gates
        // before Model Setup.
        assertEquals(
            NavScreens.SetupTheme.route,
            resolve(themeSetupDone = false, modelDone = false),
        )
    }

    @Test
    fun earlier_incomplete_steps_take_priority_over_model_setup_state() {
        assertEquals(NavScreens.FridayLogin.route, resolve(hasJwt = false, modelDone = true))
        assertEquals(NavScreens.LanguageSelection.route, resolve(languageSelected = false, modelDone = true))
        assertEquals(NavScreens.TermsConditions.route, resolve(tcAccepted = false, modelDone = true))
        assertEquals(NavScreens.FeatureTour.route, resolve(onboarded = false, modelDone = true))
        assertEquals(NavScreens.SetupScreen.route, resolve(securityDone = false, modelDone = true))
    }
}

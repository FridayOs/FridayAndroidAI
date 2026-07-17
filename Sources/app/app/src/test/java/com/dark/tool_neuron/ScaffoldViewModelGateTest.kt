package com.dark.tool_neuron

import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.viewmodel.OnboardingGateLogic
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Pure JVM tests for OnboardingGateLogic.resolveOnboardingRoute (FRI-582 P10):
 * behavior-preserving extraction of ScaffoldViewModel.resolveStartDestination()'s
 * decision order. No Hilt/Context/HXS involved. Covers each step gate in order,
 * the restart-resume fix (themeSetupDone=false, modelDone=false -> SetupTheme),
 * and the backward-compat clause (modelDone=true bypasses Theme for pre-existing
 * installs).
 */
class ScaffoldViewModelGateTest {

    // All-steps-done baseline; individual tests flip one flag at a time.
    private fun route(
        hasJwt: Boolean = true,
        languageSelected: Boolean = true,
        tcAccepted: Boolean = true,
        onboarded: Boolean = true,
        securityDone: Boolean = true,
        themeSetupDone: Boolean = true,
        modelDone: Boolean = true,
        lockEnabled: Boolean = false,
    ): String = OnboardingGateLogic.resolveOnboardingRoute(
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
    fun `no jwt routes to login regardless of other flags`() {
        assertEquals(NavScreens.FridayLogin.route, route(hasJwt = false, languageSelected = false, tcAccepted = false))
    }

    @Test
    fun `jwt present but language not selected routes to language selection`() {
        assertEquals(NavScreens.LanguageSelection.route, route(languageSelected = false))
    }

    @Test
    fun `terms not accepted routes to terms conditions`() {
        assertEquals(NavScreens.TermsConditions.route, route(tcAccepted = false))
    }

    @Test
    fun `not onboarded routes to feature tour`() {
        assertEquals(NavScreens.FeatureTour.route, route(onboarded = false))
    }

    @Test
    fun `security not done routes to setup screen`() {
        assertEquals(NavScreens.SetupScreen.route, route(securityDone = false))
    }

    @Test
    fun `theme not done and model not done routes to setup theme`() {
        assertEquals(NavScreens.SetupTheme.route, route(themeSetupDone = false, modelDone = false))
    }

    @Test
    fun `restart-resume mid-flow on theme step returns to setup theme`() {
        // securityDone=true, themeSetupDone=false, modelDone=false -> the fixed
        // restart bug: must resume on SetupTheme, not skip past it.
        assertEquals(
            NavScreens.SetupTheme.route,
            route(securityDone = true, themeSetupDone = false, modelDone = false),
        )
    }

    @Test
    fun `backward-compat existing install with modelDone true is not re-trapped on theme`() {
        // securityDone=true, themeSetupDone=false, modelDone=true -> proceeds past
        // Theme (themeDone = themeSetupDone || modelDone) straight through to the
        // post-model gates; existing installs must not see SetupTheme.
        val result = route(securityDone = true, themeSetupDone = false, modelDone = true, lockEnabled = false)
        assertEquals(NavScreens.FridayVoice.route, result)
    }

    @Test
    fun `model not done routes to model setup once theme is satisfied`() {
        assertEquals(NavScreens.ModelSetup.route, route(themeSetupDone = true, modelDone = false))
    }

    @Test
    fun `all steps done with lock enabled routes to password screen`() {
        assertEquals(NavScreens.PasswordScreen.route, route(lockEnabled = true))
    }

    @Test
    fun `all steps done with lock disabled routes to friday voice`() {
        assertEquals(NavScreens.FridayVoice.route, route(lockEnabled = false))
    }
}

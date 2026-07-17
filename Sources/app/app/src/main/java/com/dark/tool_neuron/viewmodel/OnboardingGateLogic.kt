package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.NavScreens

/*
 * Pure onboarding/lock gate resolution (FRI-582 P10 extraction). Behavior-
 * preserving extraction of the decision order from
 * ScaffoldViewModel.resolveStartDestination() — no Context/HXS/DI refs, so the
 * full step order (incl. the themeSetupDone restart fix + modelDone
 * backward-compat clause) is unit-testable on plain JVM.
 */
object OnboardingGateLogic {
    fun resolveOnboardingRoute(
        hasJwt: Boolean,
        languageSelected: Boolean,
        tcAccepted: Boolean,
        onboarded: Boolean,
        securityDone: Boolean,
        themeSetupDone: Boolean,
        modelDone: Boolean,
        lockEnabled: Boolean,
    ): String {
        if (!hasJwt) return NavScreens.FridayLogin.route
        if (!languageSelected) return NavScreens.LanguageSelection.route
        // Backward compat: existing installs that finished onboarding before this
        // flag existed have modelDone=true but themeSetupDone=false (default).
        // Treat theme as satisfied once model setup is done so they are not
        // re-trapped on the Theme step; only enforce Theme while model setup is
        // still pending (i.e. for installs actually mid-flow on/before Theme).
        val themeDone = themeSetupDone || modelDone
        if (!tcAccepted) return NavScreens.TermsConditions.route
        if (!onboarded) return NavScreens.FeatureTour.route
        if (!securityDone) return NavScreens.SetupScreen.route
        if (!themeDone) return NavScreens.SetupTheme.route // NEW — fixes restart bug
        if (!modelDone) return NavScreens.ModelSetup.route
        if (lockEnabled) return NavScreens.PasswordScreen.route
        return NavScreens.FridayVoice.route
    }
}

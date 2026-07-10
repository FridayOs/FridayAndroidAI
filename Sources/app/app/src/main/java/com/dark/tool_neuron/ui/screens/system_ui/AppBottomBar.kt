package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesBottomBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenBottomBar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenBottomBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupThemeBottomBar
import com.dark.tool_neuron.ui.screens.terms_conditions.TermsConditionsBottomBar
import com.friday.ai.R

@Composable
fun AppBottomBar(
    currentRoute: String?,
    navController: NavHostController,
    onOnboardingComplete: () -> Unit = {},
    onThemeSetupComplete: () -> Unit = {},
    onTermsAccepted: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenBottomBar(navController)
        NavScreens.TermsConditions.route -> TermsConditionsBottomBar(
            buttonLabel = R.string.friday_tc_continue,
            onAccept = onTermsAccepted,
        )
        NavScreens.DevNotes.route -> DevNotesBottomBar(navController, onContinue = onOnboardingComplete)
        NavScreens.PasswordScreen.route -> PasswordScreenBottomBar()
        NavScreens.SetupTheme.route -> SetupThemeBottomBar(onContinue = onThemeSetupComplete)
        NavScreens.ModelSetup.route -> Unit
        NavScreens.FridaySplash.route -> Unit
        NavScreens.FridayLogin.route -> Unit
        NavScreens.FridayVoice.route -> Unit
        NavScreens.FridayChat.route -> Unit
        NavScreens.FridayHistory.route -> Unit
        else -> Unit
    }
}

package com.dark.tool_neuron.ui.navigation

import com.dark.tool_neuron.data.AccountState

/**
 * Pure production decision for post-login navigation.
 *
 * Returns the destination route to navigate to once the account becomes
 * [AccountState.Authenticated], or `null` while still unauthenticated. The
 * destination is resolved lazily via [resolveNext] so a fresh
 * no-language/no-onboarding install still routes through the gate chain rather
 * than jumping straight to Friday Voice.
 *
 * Extracted from TNavigation's FridayLogin `LaunchedEffect` so the real decision
 * is unit-testable — altering or deleting it fails FridayLoginSuccessNavTest.
 */
fun loginSuccessDestination(
    state: AccountState,
    resolveNext: () -> String,
): String? = if (state is AccountState.Authenticated) resolveNext() else null

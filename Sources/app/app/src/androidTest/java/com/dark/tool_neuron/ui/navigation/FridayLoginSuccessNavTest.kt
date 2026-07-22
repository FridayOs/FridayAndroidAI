package com.dark.tool_neuron.ui.navigation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.data.AccountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-11 QA rework round 4 (Blocker 1d — success navigation):
 *
 * Asserts the REAL production success-navigation decision, [loginSuccessDestination]
 * (ui/navigation/LoginSuccessNavigation.kt), which TNavigation's FridayLogin
 * destination calls from its `LaunchedEffect(accountState)`. This is the actual
 * production function — NOT a mirrored `LaunchedEffect`/NavHost copy — so deleting
 * or altering the production decision (e.g. dropping the Authenticated guard, or
 * returning a fixed destination that skips the gate chain) fails these tests.
 *
 * No production auth bypass: the decision is a pure state→route mapping; the real
 * sign-in gateway is never invoked here.
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginSuccessNavTest {

    private val nextRoute = "resolved_next_destination"

    private fun authenticated() = AccountState.Authenticated(
        userId = "user-1",
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        avatarUrl = "",
        plan = "Free plan",
    )

    @Test
    fun authenticated_state_resolves_next_destination() {
        val destination = loginSuccessDestination(authenticated()) { nextRoute }
        assertEquals(
            "Authenticated must navigate to the freshly-resolved next destination",
            nextRoute,
            destination,
        )
    }

    @Test
    fun unauthenticated_state_returns_null_and_stays_on_login() {
        val destination = loginSuccessDestination(AccountState.Unauthenticated) { nextRoute }
        assertNull("Unauthenticated must not navigate away from the login surface", destination)
    }

    @Test
    fun resolver_is_evaluated_lazily_only_when_authenticated() {
        // Guards the gate-chain contract: resolveNext() (which walks the language/
        // onboarding gates) must run ONLY on success, never while unauthenticated.
        var resolveCalls = 0
        loginSuccessDestination(AccountState.Unauthenticated) { resolveCalls++; nextRoute }
        assertEquals("resolveNext must not run while unauthenticated", 0, resolveCalls)

        loginSuccessDestination(authenticated()) { resolveCalls++; nextRoute }
        assertEquals("resolveNext must run exactly once on success", 1, resolveCalls)
    }
}

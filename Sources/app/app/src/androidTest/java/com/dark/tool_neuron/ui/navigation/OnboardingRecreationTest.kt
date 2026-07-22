package com.dark.tool_neuron.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.activity.MainActivity
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.LanguageController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.model.ProviderFlowOrigin
import com.dark.tool_neuron.ui.screens.system_ui.onboardingProvidersRouteFromOrigin
import com.friday.ai.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-08 Codex-QA rework — Blocker 3: real runtime process-death/recreation
 * evidence (not just pure OnboardingGateLogicTest coverage).
 *
 * Two test classes, two Compose rule types, because the two scenarios need incompatible
 * hosting strategies:
 *
 * 1. [OnboardingRecreationTest] — mid-flow step + recreate() through the REAL app.
 *    Uses `createAndroidComposeRule<MainActivity>()`, which OWNS the Activity lifecycle
 *    (launches a fresh `MainActivity` per `@Test` and guarantees teardown afterwards).
 *    This is required (not just preferred) for the SetupTheme step because
 *    `SetupThemeScreen(viewModel: SetupThemeViewModel = hiltViewModel())` needs a real
 *    Hilt-backed Activity; a bare stand-in NavHost (as used by
 *    [ProviderFlowOriginNavTest]) cannot construct it. `recreate()` is driven via
 *    `composeTestRule.activityRule.scenario.recreate()`.
 *
 *    NOTE (FRI-633 round-3 isolation fix): this class previously used
 *    `createEmptyComposeRule()` + a manual `ActivityScenario.launch(MainActivity::class.java)`.
 *    `createEmptyComposeRule()` does not launch its own Activity — it inspects whatever
 *    Activity instrumentation currently has resumed. Under full-suite ordering, a preceding
 *    `ui.navigation` test that also launches `MainActivity` via `ActivityScenario`
 *    (`FridayLoginFullActivityDeviceCaptureTest`, same package, runs earlier alphabetically)
 *    could leave the instrumentation-resumed Activity in a state where the freshly
 *    re-launched `MainActivity` was reused/stale, so the empty compose rule observed a tree
 *    that never showed the seeded onboarding surface -> spurious `ComposeTimeoutException`
 *    (tests passed in isolation, failed only in the full run). `createAndroidComposeRule`
 *    owns its own Activity instance end-to-end, which removes the cross-test coupling
 *    without touching any assertion or production code.
 *
 *    Mid-flow state is seeded through the real production seam (`prefs.fridayJwt`,
 *    `LanguageController.markFirstSelectionDone()`,
 *    `prefs.tcAccepted`/`onboardingComplete`/`securitySetupDone`) BEFORE launch — no
 *    production auth/gate bypass. `AppScaffold`'s `IntroScreen.onFinish` re-resolves the
 *    route fresh via `ScaffoldViewModel.resolveStartDestination()` after its ~2.6s
 *    auto-advance, so seeded prefs correctly drive which screen the real gate lands on;
 *    tests `waitUntil` past that delay rather than trying to tap Intro's untagged
 *    skip-Box. Screens are identified by their distinct heading strings (no direct
 *    NavController access is available when driving through the real Activity).
 *
 * 2. [ProviderFlowOriginRecreationTest] — `StateRestorationTester` requires a FULL
 *    `createComposeRule()` (it owns `setContent`), which is incompatible with
 *    `createEmptyComposeRule()`, so it lives in its own class. Mirrors
 *    [ProviderFlowOriginNavTest]'s stand-in-NavHost pattern (real screens need Hilt,
 *    which has no @HiltAndroidTest infra here) and drives the REAL production seam
 *    `onboardingProvidersRouteFromOrigin` to reach an origin-bearing
 *    `OnboardingProviders` destination, then uses
 *    `StateRestorationTester.emulateSavedInstanceStateRestore()` to simulate
 *    process-death/recreation and asserts the `ProviderFlowOrigin` nav argument and
 *    route both survive.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingRecreationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    private fun str(id: Int) = context.getString(id)

    /**
     * `createAndroidComposeRule<MainActivity>()` launches its `MainActivity` instance as
     * part of the JUnit `@Rule` chain, which runs BEFORE `@Before`/`@Test` (rules wrap
     * outside before/after methods). That first, rule-driven launch therefore happens
     * before this class's `@Before` has reset `PolicyEngine`/`BootIntegrity` and before the
     * per-test seeding (`seedAuthedPastLanguageAndTerms()` etc.) has written any prefs, so
     * its initial resolved screen is not meaningful for assertions.
     *
     * Call this once per test, immediately after seeding is complete and before the first
     * `waitUntil`, to force a fresh `onCreate()` that reads the fully-seeded, fully-reset
     * state -- functionally replacing the old `ActivityScenario.launch(...)` call that used
     * to run strictly after seeding. This keeps the Activity fully isolated per test (fixing
     * the FRI-633 full-suite-only flake) while still asserting the exact same "first landed
     * screen" + "recreate() preserves state" behavior as before.
     */
    private fun recreateAfterSeeding() {
        composeTestRule.activityRule.scenario.recreate()
    }

    @Before
    fun setup() {
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        prefs = AppPreferences(context, AppKeyStore(context), HxsEncryptor())
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "app_prefs").deleteRecursively()
    }

    /** Real-seam seeding shared by both mid-flow scenarios: authed, past language + T&C. */
    private fun seedAuthedPastLanguageAndTerms() {
        prefs.fridayJwt = "test-jwt"
        prefs.fridayUserId = "test-user"
        LanguageController(prefs).markFirstSelectionDone()
        prefs.tcAccepted = true
    }

    @Test
    fun midFlow_featureTour_survivesRecreate_withMonotonicFlags() {
        seedAuthedPastLanguageAndTerms()
        // onboardingComplete left false -> OnboardingGateLogic resolves to FeatureTour.
        recreateAfterSeeding()

        val tourTitle = str(R.string.onboarding_tour_title)
        // 20s (not 8s): under full ui-package ordering the real MainActivity cold-launch
        // through Intro's ~2.6s auto-advance can exceed 8s on a device warm from preceding
        // capture tests. Non-weakening — same assertion, more tolerance for device load.
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(tourTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(tourTitle).assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(tourTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(tourTitle).assertIsDisplayed()

        // Monotonic: re-read the real on-disk prefs after recreate() and confirm the
        // seeded flags were never cleared/reset by the recreated Activity/ViewModels.
        assertTrue("jwt survives recreate", prefs.fridayJwt.isNotBlank())
        assertEquals("test-user", prefs.fridayUserId)
        assertTrue("language flag survives recreate", LanguageController(prefs).isFirstSelectionDone())
        assertTrue("tcAccepted survives recreate", prefs.tcAccepted)
        assertFalse("onboardingComplete stays false mid-flow", prefs.onboardingComplete)
    }

    @Test
    fun midFlow_setupTheme_survivesRecreate_withMonotonicFlags() {
        seedAuthedPastLanguageAndTerms()
        prefs.onboardingComplete = true
        prefs.securitySetupDone = true
        // themeSetupDone + modelSetupDone left false -> OnboardingGateLogic resolves to
        // SetupTheme (the deeper mid-flow step).
        recreateAfterSeeding()

        val themeTitle = str(R.string.friday_setup_theme_title)
        // 20s (not 8s): see rationale in midFlow_featureTour — cold MainActivity launch
        // through Intro auto-advance under full-package ordering can exceed 8s. Non-weakening.
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(themeTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(themeTitle).assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()

        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(themeTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(themeTitle).assertIsDisplayed()

        assertTrue("onboardingComplete survives recreate", prefs.onboardingComplete)
        assertTrue("securitySetupDone survives recreate", prefs.securitySetupDone)
        assertFalse("themeSetupDone stays false mid-flow", prefs.themeSetupDone)
        assertFalse("modelSetupDone stays false mid-flow", prefs.modelSetupDone)
    }
}

/**
 * See [OnboardingRecreationTest] header for why this variant needs its own class/rule
 * (`StateRestorationTester` requires a full `createComposeRule()`).
 */
@RunWith(AndroidJUnit4::class)
class ProviderFlowOriginRecreationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun OriginNavHost(navController: NavHostController) {
        NavHost(navController = navController, startDestination = NavScreens.FridayVoice.route) {
            composable(NavScreens.FridayVoice.route) {
                Column {
                    // Real production seam, same as ProviderFlowOriginNavTest: stand-in for
                    // AppScaffold's onAddProvider/onManageProviders callbacks.
                    Button(
                        onClick = {
                            navController.navigate(
                                onboardingProvidersRouteFromOrigin(NavScreens.FridayVoice.route)
                            )
                        },
                        modifier = Modifier.testTag("voice_add_provider"),
                    ) { Text("add") }
                }
            }
            composable(
                route = NavScreens.OnboardingProviders.route,
                arguments = listOf(navArgument(NavScreens.OnboardingProviders.ARG_ORIGIN) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }),
            ) {
                Text("providers")
            }
        }
    }

    @Test
    fun providerFlowOrigin_preservedAcross_simulatedStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)
        lateinit var navController: NavHostController
        restorationTester.setContent {
            navController = rememberNavController()
            OriginNavHost(navController)
        }

        composeTestRule.onNodeWithTag("voice_add_provider").performClick()
        composeTestRule.waitForIdle()

        assertEquals(NavScreens.OnboardingProviders.route, navController.currentDestination?.route)
        assertEquals(
            ProviderFlowOrigin.Voice.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )

        // Simulates process-death/recreation (config change / kill+restore) without an
        // actual ActivityScenario relaunch — the NavHost's saved back stack must restore.
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        assertEquals(
            "route survives simulated state restoration",
            NavScreens.OnboardingProviders.route,
            navController.currentDestination?.route,
        )
        assertEquals(
            "ProviderFlowOrigin nav arg survives simulated state restoration",
            ProviderFlowOrigin.Voice.name,
            navController.currentBackStackEntry?.arguments?.getString(NavScreens.OnboardingProviders.ARG_ORIGIN),
        )
    }
}

package com.dark.tool_neuron.evidence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.LanguageController
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.GatewayConfigRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-10 QA rework round 2 (Blocker 3) -- REAL process-death evidence.
 *
 * Unlike OnboardingRecreationTest.kt (read-only reference; simulates process death via
 * scenario.recreate()/StateRestorationTester, both in-process config-change-style
 * recreations that never actually kill the OS process), this file provides *seed-only*
 * and *assert-only* instrumented test methods. Each is invoked as a SEPARATE
 * `am instrument -e class ...#method` step by the bash harness
 * (evidence/harness/process-death.sh), interleaved with REAL adb-driven
 * `am force-stop` / `am kill` / `am start` operations against the actual device
 * process -- not simulated. Each method here does not launch/observe any Activity; it
 * only seeds or re-reads on-disk state (real AppPreferences production seam), because
 * the harness itself observes the resulting UI surface entirely out-of-process via
 * `uiautomator dump` (a live ComposeTestRule cannot survive a real process kill, so it
 * is never used for the observation step -- only for seeding/read-back, both of which
 * run before/after the actual kill, never straddling it).
 *
 * No production code is modified or bypassed: seeded flags are the same real
 * AppPreferences fields OnboardingGateLogic.resolveOnboardingRoute reads in production
 * (see OnboardingGateLogic.kt) -- identical convention to
 * OnboardingRecreationTest.kt's seedAuthedPastLanguageAndTerms(), and the
 * gateway_store_v1 wipe follows the same precedent as GatewayConfigRepositoryTest.kt.
 */
@RunWith(AndroidJUnit4::class)
class ProcessDeathHarnessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    private fun openPrefs(wipeFirst: Boolean) {
        if (wipeFirst) {
            File(context.filesDir, "app_prefs").deleteRecursively()
            File(context.filesDir, "gateway_store_v1").deleteRecursively()
        }
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        prefs = AppPreferences(context, AppKeyStore(context), HxsEncryptor())
    }

    /**
     * Checkpoint A seed -- mirrors OnboardingRecreationTest's
     * seedAuthedPastLanguageAndTerms() + onboardingComplete left false, so a real cold
     * `am start` resolves via OnboardingGateLogic.resolveOnboardingRoute to
     * NavScreens.FeatureTour ("Meet Friday" heading, onboarding_tour_title).
     */
    @Test
    fun seedMidFlowFeatureTour() {
        openPrefs(wipeFirst = true)
        prefs.fridayJwt = "test-jwt"
        prefs.fridayUserId = "test-user"
        LanguageController(prefs).markFirstSelectionDone()
        prefs.tcAccepted = true
        // onboardingComplete intentionally left false -> FeatureTour is the resolved route.
    }

    /**
     * Checkpoint A read-back -- run AFTER the real force-stop/cold-start cycle
     * completes; re-opens the on-disk store fresh (no wipe) and asserts every seeded
     * flag is still exactly as seeded (monotonic across a real process kill, not
     * silently reset by the relaunch) and onboardingComplete is still false (still
     * genuinely mid-flow, not accidentally advanced).
     */
    @Test
    fun assertMidFlowFeatureTourPrefsStillMonotonic() {
        openPrefs(wipeFirst = false)
        assertTrue("jwt survives real process death", prefs.fridayJwt.isNotBlank())
        assertEquals("test-user", prefs.fridayUserId)
        assertTrue(
            "language flag survives real process death",
            LanguageController(prefs).isFirstSelectionDone(),
        )
        assertTrue("tcAccepted survives real process death", prefs.tcAccepted)
        assertFalse("onboardingComplete stays false mid-flow", prefs.onboardingComplete)
    }

    /**
     * Checkpoint B seed -- fully onboarded (hasJwt/language/terms/onboarded/security/
     * theme/model all done, lock left disabled via a freshly-wiped AuthState.DEFAULT)
     * so a real cold `am start` resolves via OnboardingGateLogic straight to
     * NavScreens.FridayVoice. From there the harness drives two real ADB taps
     * (header pill content-desc "Change provider and model" -> "Add provider" button
     * text) to reach the real onboardingProvidersRouteFromOrigin(...) destination
     * before the real am-kill/relaunch step -- the ProviderFlowOrigin nav argument
     * this creates lives only in NavController's SavedStateHandle-backed back stack,
     * never on disk, so it is the correct target for testing survival across a real
     * (task-preserving) process kill rather than a full force-stop.
     */
    @Test
    fun seedProviderOriginEntry() {
        openPrefs(wipeFirst = true)
        prefs.fridayJwt = "test-jwt"
        prefs.fridayUserId = "test-user"
        LanguageController(prefs).markFirstSelectionDone()
        prefs.tcAccepted = true
        prefs.onboardingComplete = true
        prefs.securitySetupDone = true
        prefs.themeSetupDone = true
        prefs.modelSetupDone = true
        // lockEnabled stays false: SecurityManager.isLockEnabled reads
        // AuthState.DEFAULT (securityMode != SECURITY_APP_PASSWORD) from the
        // freshly-wiped store above.

        // FRI-633 QA rework round 3 (Blocker 2b) -- seed one real (non-mocked) gateway
        // config via the same production repo/seam used by GatewayConfigRepositoryTest
        // so OnboardingProvidersScreen's "Continue to Friday" button (gated on
        // `gateways.isNotEmpty()`, see OnboardingProvidersScreen.kt) actually renders
        // after the real process kill + relaunch, letting the harness drive a real tap
        // to exercise the origin-restore contract instead of being blocked by an empty
        // provider list.
        GatewayConfigRepository(context, AppKeyStore(context), HxsEncryptor()).create(
            provider = GatewayProvider.OPENAI,
            label = "Harness Test",
            baseUrl = "",
            apiKey = "sk-harness-test-fake",
            model = "gpt-4o-mini",
        )
    }

    /**
     * Checkpoint B origin-restore proof (FRI-633 QA rework round 3, Blocker 2b) -- run
     * AFTER the harness performs a REAL adb tap on the "Continue to Friday" button that
     * appears post-relaunch on OnboardingProvidersScreen. The screen's own heading text
     * ("AI Providers") cannot distinguish two different production code paths that both
     * land on it (see codex-qa-verdict-r3.txt finding 2):
     *
     *   - origin-preserved (the real contract): `onboardingProvidersContinue(originName,
     *     ...)` (TNavigation.kt) sees a non-null `originName` survived the real process
     *     kill and ONLY calls `navController.popBackStack()` -- it never touches disk
     *     state.
     *   - origin-lost (the regression QA flagged): if the `origin` nav argument had
     *     reverted to null across the real kill, the SAME Continue tap instead falls
     *     through to the first-run `onFirstRunContinue()` closure (AppScaffold.kt's
     *     `onContinueToFriday`), which calls `scaffoldViewModel.markProviderStepDone()`
     *     -- writing `AppPreferences.providerStepDone = true` (synchronously flushed to
     *     disk via `putBoolean`'s `storage.flushAll()`) -- before navigating to
     *     FridayVoice via `popUpTo(0)`.
     *
     * `providerStepDone` is written by NO other production code path (verified: only
     * `ScaffoldViewModel.markProviderStepDone()`, called only from that first-run
     * closure). `seedProviderOriginEntry()` deliberately leaves it unset (false) on the
     * freshly-wiped store. So re-opening the real on-disk store after the real Continue
     * tap and asserting it is STILL false is a deterministic, disk-backed proof that the
     * origin-preserving popBackStack branch ran -- not the first-run completion
     * callback -- the exact contract the QA verdict says the bare "AI Providers" heading
     * check cannot prove.
     */
    @Test
    fun assertProviderOriginRestoresToVoice() {
        openPrefs(wipeFirst = false)
        assertFalse(
            "providerStepDone must stay false: it is only ever set by the first-run " +
                "onContinueToFriday completion callback, which must NOT run when " +
                "Continue is reached via a surviving provider-origin (popBackStack " +
                "back to the origin screen) instead of a lost/null origin",
            prefs.providerStepDone,
        )
        assertTrue("jwt survives real process death", prefs.fridayJwt.isNotBlank())
        assertTrue(
            "onboardingComplete stays true (already fully onboarded before the " +
                "provider-origin detour)",
            prefs.onboardingComplete,
        )
    }
}

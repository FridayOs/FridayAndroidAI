package com.dark.tool_neuron.ui.navigation

import android.os.SystemClock
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.activity.MainActivity
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.model.AppLanguage
import com.friday.ai.R
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-10 QA rework round 2 (Blocker 1b) — re-captures the CURRENT (post-1a-fix)
 * FridayLoginScreen at the TRUE device viewport WITH system bars/safe area, via a real
 * full-activity launch (NOT the compose-only FridayLoginSurfaceTest hosting used for the
 * original evidence matrix). Empty on-disk prefs -> OnboardingGateLogic.resolveOnboardingRoute
 * (hasJwt=false) resolves to NavScreens.FridayLogin unconditionally regardless of any other
 * flag, so no auth bypass is needed to reach this screen for real through MainActivity.
 *
 * Locale/theme are driven through the REAL @Singleton LanguageController/ThemeController
 * instances (not fresh test-local ones -- MainActivity.attachBaseContext/onCreate reads
 * those already-initialized in-memory StateFlows, so a fresh instance built from the same
 * AppPreferences would never be observed by the real Activity). Fetched via the new
 * production Fri633LocaleThemeEntryPoint (TNavigation.kt, same package as this test) using
 * EntryPointAccessors.fromApplication -- the exact same pattern already proven by the
 * pre-existing ActiveConversationStoreEntryPoint / ProductionActiveConversationStoreDiTest.kt,
 * required because this repo has no @HiltAndroidTest test-app infra, so a test-only
 * @EntryPoint would not be part of the main-variant Hilt component EntryPointAccessors
 * resolves against.
 *
 * Each cell is captured via UiAutomation.takeScreenshot() (real device pixels, system bars
 * included) rather than composeTestRule.onRoot().captureToImage() (Compose-content-only,
 * no status/nav bar), through the now-public Fri633CaptureHarness.save(Bitmap, filename)
 * overload.
 *
 * Success = the real FridayLogin heading text appears after IntroScreen's ~2.6s auto-advance
 * + OnboardingGateLogic resolution -- mirrors the exact waitUntil pattern already used by
 * OnboardingRecreationTest.kt (read-only reference, not modified by this file) for the same
 * real Intro-then-gate-resolution flow.
 *
 * FRI-633 round-8 fix (flaky capture root cause + fix, SUPERSEDED by round-14 -- kept for
 * history): rounds 5-7 gated readiness with `composeTestRule.waitUntil { onAllNodesWithText(
 * ...).fetchSemanticsNodes() ... }`, which timed out (`ComposeTimeoutException`, 20s) at cell
 * 1's very first check on every `am instrument`/`connectedDebugAndroidTest` run
 * (round5-login-fullactivity-capture.log, round7-login-fullactivity-capture.log -- both fail
 * at the identical line). Diagnostic logging showed ALL three semantics predicates
 * (heading/password-field/intro-title) stayed `false` for the ENTIRE 20s window -- not merely
 * slow, but a permanently empty query result, meaning `composeTestRule`'s Compose
 * semantics-tree bridge desynced from the real foreground Activity after `scenario.recreate()`
 * and never resynced for the rest of that test instance. Round 8 switched readiness gating to
 * out-of-process `UiAutomation.getRootInActiveWindow()` polling instead, reasoning it could
 * never attach to a stale/orphaned Compose test root.
 *
 * FRI-633 round-14 fix (supersedes round-8's readiness mechanism): rounds 8-13's
 * UiAutomation-polling gate itself reproduced an equally permanent timeout -- round-13
 * diagnostics showed the polled accessibility text set frozen byte-for-byte on Intro's static
 * content for the full 20s+ budget. Root cause: continuous out-of-process
 * `rootInActiveWindow()` IPC queries (compounded by `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` +
 * `TYPES_ALL_MASK` and IntroScreen's two continuously-playing animations, which independently
 * generate accessibility events) starved the main-thread Choreographer/coroutine dispatch that
 * IntroScreen's `LaunchedEffect(Unit) { delay(2600); onFinish() }` needs to complete. The
 * original round-8 "stale root" rationale was diagnosed against `createEmptyComposeRule()`,
 * which this file no longer uses -- round-7 already switched to `createAndroidComposeRule`,
 * removing that justification. [OnboardingRecreationTest] proves Compose-semantics
 * `waitUntil` IS reliable for the identical `createAndroidComposeRule<MainActivity>()` +
 * `recreate()` + Intro-transition path, so [waitForCellLoginForeground] now uses that same
 * in-process mechanism again. `composeTestRule` is kept for Activity lifecycle ownership
 * (`activityRule.scenario.recreate()`) AND readiness observation; `UiAutomation` is retained
 * only for the final `takeScreenshot()` call (real device pixels incl. system bars, not
 * Compose-content-only).
 *
 * The three-condition non-weakening requirement from round 5 is preserved, substituting the
 * password TEXT-FIELD's visible label (`R.string.friday_password_label`, e.g. "Password"/
 * "Mật khẩu") for the password field's `testTag` -- Compose test tags are only exposed on the
 * real accessibility tree when the app sets `Modifier.semantics { testTagsAsResourceId = true
 * }` somewhere in the hierarchy, which this app does not do (adding it would be a production
 * change, out of scope for FRI-633's androidTest-only fix). The visible password label is
 * equally Login-screen-unique and is real, user/screen-reader-visible content, so it is not a
 * weaker signal than the test tag it replaces. All three conditions --
 * heading present, password label present, intro tagline (NOT the invariant brand title --
 * see [waitForCellLoginForeground] doc) absent -- must hold simultaneously; a cell that never
 * reaches that state fails closed via [fail], never capturing a wrong/unverified frame.
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginFullActivityDeviceCaptureTest {

    // FRI-633 round-7 fix: was `createEmptyComposeRule()` + manual per-cell
    // `ActivityScenario.launch(MainActivity::class.java)`. This exact combination was already
    // root-caused and fixed once before in this codebase (see OnboardingRecreationTest's
    // round-3 comment, same package): `createEmptyComposeRule()` does not launch/own an
    // Activity itself -- it only inspects whichever Activity instrumentation currently has
    // resumed. On the very FIRST `ActivityScenario.launch` of the whole instrumented process
    // (cell 1 only), the compose test's synchronization can attach to a stale/disconnected
    // root and never observe further recomposition, so `waitUntil` polls a frozen semantics
    // tree forever regardless of timeout length -- confirmed by diagnostic logging (see
    // Logs/plans/260721-1315-fri633-full-onboarding/): all three predicate sub-conditions
    // stayed `false` for the ENTIRE 20s window, only on cell 1. `createAndroidComposeRule`
    // owns the Activity's full lifecycle (launch + recreate), the same proven fix already
    // applied to OnboardingRecreationTest.
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        File(context.filesDir, "app_prefs").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        prefs = AppPreferences(context, AppKeyStore(context), HxsEncryptor())
        // No jwt/language/tc/etc. seeded: empty prefs -> OnboardingGateLogic.hasJwt=false
        // -> NavScreens.FridayLogin, unconditionally (checked first, before every other flag).
    }

    @After
    fun cleanup() {
        // FRI-633 round-4 fix: this test drives the app-wide @Singleton LanguageController/
        // ThemeController (via Fri633LocaleThemeEntryPoint) through 4 cells ending on
        // VI/DARK, and Hilt singletons persist in-memory for the entire instrumented test
        // process (not per-test/per-Activity). Without resetting them here, every
        // subsequently-launched MainActivity in the SAME full-suite run -- regardless of
        // which test class or Compose rule type it uses -- has attachBaseContext() wrap it
        // in Vietnamese (AppCompatLocaleWrapper.currentTag() reads the leftover in-memory
        // languageController.selected.value), which silently starves any waitUntil that
        // polls for an English string via the raw, non-wrapped targetContext -- exactly
        // the OnboardingRecreationTest full-suite-only ComposeTimeoutException. Reset to
        // the documented "no override" defaults (AppLanguage.SYSTEM / ThemeController.Mode.
        // SYSTEM) so this test's cell iteration never leaks into later tests.
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            Fri633LocaleThemeEntryPoint::class.java,
        )
        entryPoint.languageController().setSelected(AppLanguage.SYSTEM)
        entryPoint.themeController().setMode(ThemeController.Mode.SYSTEM)

        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "app_prefs").deleteRecursively()
    }

    @Test
    fun capture_base_grid_full_activity_with_system_bars() {
        data class Cell(
            val localeLabel: String,
            val language: AppLanguage,
            val mode: ThemeController.Mode,
            val themeLabel: String,
        )
        val cells = listOf(
            Cell("EN", AppLanguage.EN, ThemeController.Mode.LIGHT, "light"),
            Cell("EN", AppLanguage.EN, ThemeController.Mode.DARK, "dark"),
            Cell("VI", AppLanguage.VI, ThemeController.Mode.LIGHT, "light"),
            Cell("VI", AppLanguage.VI, ThemeController.Mode.DARK, "dark"),
        )

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            Fri633LocaleThemeEntryPoint::class.java,
        )
        val languageController = entryPoint.languageController()
        val themeController = entryPoint.themeController()

        for (cell in cells) {
            languageController.setSelected(cell.language)
            themeController.setMode(cell.mode)

            // NOTE: getString(...) below must use a context configured for cell.language --
            // targetContext's configuration is fixed at process start, not per-cell, so we
            // read the resolved heading/label/tagline text via a language-tagged
            // configuration context (mirrors what the real Activity renders for THIS cell,
            // see [localizedString]).
            val headingText = localizedString(cell.language, R.string.friday_login_heading)
            val passwordLabelText = localizedString(cell.language, R.string.friday_password_label)
            // FRI-633 round-8 fix: use the intro TAGLINE, not `onboarding_intro_title`
            // ("FRIDAY AI") -- the title string is identical to `R.string.app_name`, which
            // FridayDrawerHeader also renders, making it a bad "intro is gone" discriminator
            // if that text were ever reachable from the same window. The tagline
            // ("onboarding_intro_tagline") is intro-screen-unique and differs per language, so
            // it is resolved per cell exactly like the heading/label above.
            val introTaglineText = localizedString(cell.language, R.string.onboarding_intro_tagline)

            // FRI-633 round-7 fix: force a fresh onCreate() that reads the just-set
            // language/theme singleton state for THIS cell, mirroring
            // OnboardingRecreationTest.recreateAfterSeeding(). createAndroidComposeRule's own
            // rule-driven launch (before @Before/@Test) is not meaningful for cell 1 (runs
            // before prefs reset / BootIntegrity relaxed / locale-theme set), and cells 2-4
            // need a genuinely fresh Activity instance rather than the previous cell's stale
            // one -- recreate() gives every cell the same fresh-launch semantics uniformly.
            composeTestRule.activityRule.scenario.recreate()

            // FRI-633 round-14 fix: readiness is gated via in-process Compose semantics
            // (`composeTestRule.waitUntil`/`onAllNodesWithText`, see [waitForCellLoginForeground]
            // doc) -- the round-8-13 out-of-process UiAutomation-polling gate reproduced its
            // own permanent-timeout flake (see class doc). Non-weakening: still requires ALL
            // THREE simultaneously -- (1) Login heading present, (2) Login-unique password
            // label present, (3) intro tagline fully torn down (not merely occluded) -- fails
            // closed via `fail(...)` if a cell genuinely never reaches that state within the
            // 20s budget (cold Hilt graph + DB/encryption bring-up + JIT warmup on the slowest
            // cell).
            waitForCellLoginForeground(
                cell = cell,
                headingText = headingText,
                passwordLabelText = passwordLabelText,
                introTaglineText = introTaglineText,
                timeoutMillis = 20_000,
            )
            // Instrumentation-level idle sync (not composeTestRule.waitForIdle(), which
            // depends on the same potentially-desynced Compose test bridge) -- lets the last
            // frame settle on the main looper before the out-of-process screenshot.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            SystemClock.sleep(300)

            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            checkNotNull(screenshot) { "uiAutomation.takeScreenshot() returned null for $cell" }
            Fri633CaptureHarness.save(
                screenshot,
                "FridayLoginScreen__fullactivity__${cell.localeLabel}__${cell.themeLabel}__na.png",
            )
        }
    }

    /**
     * Blocks until the in-process Compose semantics tree simultaneously satisfies all three
     * Login-readiness conditions for [cell], or fails the test closed via [fail] after
     * [timeoutMillis].
     *
     * FRI-633 round-14 fix: switched from out-of-process `UiAutomation.rootInActiveWindow`
     * polling (rounds 8-13) to `composeTestRule.waitUntil` + `onAllNodesWithText`, matching
     * [OnboardingRecreationTest]'s PROVEN-reliable pattern for the exact same
     * `createAndroidComposeRule<MainActivity>()` + `recreate()` + IntroScreen 2.6s
     * auto-advance production path (that control test passes cleanly through Intro to
     * FeatureTour/SetupTheme). The round-8 rationale for abandoning Compose semantics
     * (a "stale root" flake) was diagnosed against `createEmptyComposeRule()`, which this
     * file no longer uses -- round-7 already switched to `createAndroidComposeRule`, so
     * that original justification no longer applies. Round-13 diagnostics confirmed the
     * UiAutomation-observed accessibility text set stayed byte-for-byte frozen on Intro's
     * static content for the full 20s+ budget with zero transitional content, consistent
     * with the out-of-process polling itself (continuous `rootInActiveWindow()` IPC
     * queries under `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` + `TYPES_ALL_MASK`) starving the
     * main-thread Choreographer/coroutine dispatch IntroScreen's
     * `LaunchedEffect(Unit) { delay(2600); onFinish() }` needs to complete -- especially
     * given IntroScreen plays two continuous animations (a video texture + a GIF) that
     * already generate a steady stream of accessibility events on their own. Switching to
     * the in-process Compose semantics tree removes that IPC/query pressure entirely.
     * Non-weakening: the same three conditions (heading present, password label present,
     * intro tagline gone) are still required simultaneously; `substring = true` mirrors the
     * prior `String.contains` matching semantics.
     */
    private fun waitForCellLoginForeground(
        cell: Any,
        headingText: String,
        passwordLabelText: String,
        introTaglineText: String,
        timeoutMillis: Long,
    ) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
                composeTestRule.onAllNodesWithText(headingText, substring = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText(passwordLabelText, substring = true)
                        .fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText(introTaglineText, substring = true)
                        .fetchSemanticsNodes().isEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            fail(
                "Timed out after ${timeoutMillis}ms waiting for Login foreground for $cell " +
                    "(need heading='$headingText', passwordLabel='$passwordLabelText' present, " +
                    "introTagline='$introTaglineText' gone): ${e.message}",
            )
        }
    }

    /** Resolves [resId] against a Context pinned to [language]'s tag, independent of the
     * process-wide targetContext configuration (which does not change per-cell). */
    private fun localizedString(language: AppLanguage, resId: Int): String {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(language.uiTag))
        return context.createConfigurationContext(config).resources.getString(resId)
    }
}

package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.SessionHolder
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.friday.ai.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-10 QA rework round 2 (Blocker 2, Fix 2c) — device-capture matrix for
 * [SetupPasswordScreen], the REAL production first-run App Lock password-setup surface
 * (TNavigation.kt:224-245: `app_password` mode selected + continue -> committed=true ->
 * SetupPasswordScreen, wired to a real [SetupViewModel]). This is NOT the runtime-unlock
 * [PasswordScreen] covered by PasswordScreenDeviceCaptureTest/MATRIX.md 4.1 (that is a
 * different surface entirely, retired from any "first-run setup" framing there).
 *
 * Hosts the REAL SetupPasswordScreen through a REAL SetupViewModel built by walking its
 * concrete dependency chain on-device (no Hilt test app, no mocks) -- same real-VM pattern
 * as FridayLoginDeviceCaptureTest.hostScreen()/SetupThemeDeviceCaptureTest, and the same
 * AppKeyStore -> AppPreferences -> SessionHolder -> SecurityManager construction chain
 * already proven safe for real (non-mocked) SecurityManager.setPassword() calls (which
 * invoke the real native AuthNative.setup) in PhaseOneSecurityTest.kt et al.
 *
 * The test mirrors TNavigation's exact wiring: `password = if (isConfirmStep) confirmPassword
 * else password`, onDigit = viewModel::appendDigit, onDelete = viewModel::deleteLast,
 * onClear = viewModel::clearAll, onSubmit = { viewModel.submitPassword(onSuccess = {}) },
 * onBack per TNavigation's isConfirmStep branch.
 *
 * No accent axis: SetupPasswordScreen has no accent-conditional styling (only inherits
 * MaterialTheme.colorScheme.primary for the TnIcons.OAuth icon tint, which already varies
 * with the forced Fri633ThemedHost light/dark scheme) -- confirmed by reading the composable
 * in full. EN/VI x light/dark base grid (4 cells) plus EN/light-only interaction states.
 *
 * Disclosed, real limitation: PinActionRow's submit action is UI-disabled below
 * SetupViewModel.MIN_PIN_LENGTH (6) digits (`enabled = password.length >= MIN_PIN_LENGTH`),
 * so PinStrength.Result.TooShort can never be reached via a real button click in production
 * (the UI gate always blocks it first). The __error_tooshort cell below therefore drives
 * `viewModel.submitPassword {}` directly (bypassing the disabled UI control) to render that
 * branch for evidence purposes -- documented here rather than silently faked through a normal
 * click. Every other state (empty, partial/disabled, all-same/sequential/common weak-PIN
 * errors reachable via 6 real digit taps, confirm step, mismatch, back) is driven through the
 * real on-screen controls (PinNumberPad has no testTags -- confirmed by reading PinPad.kt in
 * full -- so digits/back/clear/submit are located via their real visible label/text).
 *
 * SecureScreen (FLAG_SECURE) wraps this screen just like PasswordScreen; MATRIX.md 4.1
 * already verified real-device PixelCopy-based captureToImage() does not blank FLAG_SECURE
 * content for the structurally-identical PasswordScreen on this same device/harness -- the
 * pulled PNGs from this test must still be independently inspected (never assumed) before
 * being recorded as valid evidence in MATRIX.md.
 */
@RunWith(AndroidJUnit4::class)
class SetupPasswordDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var prefs: AppPreferences

    private fun str(id: Int) = context.getString(id)

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

    @Test
    fun capture_base_grid_and_interaction_states() {
        val encryptor = HxsEncryptor()
        val keyStore = AppKeyStore(context)
        val session = SessionHolder(encryptor)
        val security = SecurityManager(prefs, session, encryptor, keyStore)
        val viewModel = SetupViewModel(prefs, security)
        viewModel.selectMode("app_password")

        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                val password by viewModel.password.collectAsStateWithLifecycle()
                val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
                val isConfirmStep by viewModel.isConfirmStep.collectAsStateWithLifecycle()
                val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
                val errorArg by viewModel.errorArg.collectAsStateWithLifecycle()
                SetupPasswordScreen(
                    innerPadding = PaddingValues(0.dp),
                    password = if (isConfirmStep) confirmPassword else password,
                    isConfirmStep = isConfirmStep,
                    errorRes = errorRes,
                    errorArg = errorArg,
                    onDigit = viewModel::appendDigit,
                    onDelete = viewModel::deleteLast,
                    onClear = viewModel::clearAll,
                    onSubmit = { viewModel.submitPassword(onSuccess = {}) },
                    onBack = {
                        if (isConfirmStep) viewModel.goBack()
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
        // SetupPasswordScreenContent gates all content (title/subtitle/PIN dots/keypad) behind a
        // LaunchedEffect(Unit) { delay(80); visible = true } + AnimatedVisibility entrance
        // animation. A real capture defect was found here: the base-grid EN/light and EN/dark
        // cells below are FIRST in the loop and match the composable's already-current default
        // state (locale "en", dark=false at setContent time), so setting them again triggers no
        // recomposition for waitForIdle() to block on -- the resulting PNGs were blank
        // (confirmed by pixel inspection: 13.0K vs 55-69K for every other real-content cell).
        // Wait for the real production animation to finish composing before the FIRST capture,
        // exactly mirroring the fix already applied to SetupPasswordInteractionTest.hostScreen().
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("0").fetchSemanticsNodes().isNotEmpty()
        }

        // Base grid: create-PIN empty state, EN/VI x light/dark (4 cells).
        data class Cell(val localeLabel: String, val languageTag: String, val dark: Boolean, val themeLabel: String)
        val cells = listOf(
            Cell("EN", "en", dark = false, themeLabel = "light"),
            Cell("EN", "en", dark = true, themeLabel = "dark"),
            Cell("VI", "vi", dark = false, themeLabel = "light"),
            Cell("VI", "vi", dark = true, themeLabel = "dark"),
        )
        for (cell in cells) {
            localeTagState.value = cell.languageTag
            darkState.value = cell.dark
            composeTestRule.waitForIdle()
            Fri633CaptureHarness.save(
                composeTestRule.onRoot(),
                "SetupPasswordScreen__${cell.localeLabel}__${cell.themeLabel}__na.png",
            )
        }

        // Return to EN/light for the interaction-state cells (spec: EN light only).
        localeTagState.value = "en"
        darkState.value = false
        composeTestRule.waitForIdle()

        val nextLabel = str(R.string.friday_setup_password_action_next)
        val confirmLabel = str(R.string.friday_setup_password_action_confirm)
        val backLabel = str(R.string.friday_setup_password_action_back)

        // __partial -- 3 of 6 digits entered, submit ("Next") is real-UI disabled.
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(nextLabel).assertIsNotEnabled()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__partial.png")
        viewModel.clearAll()
        composeTestRule.waitForIdle()

        // __error_allsame -- 6 identical digits -> PinStrength.Result.AllSameDigit.
        repeat(6) { composeTestRule.onNodeWithText("1").performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(nextLabel).performClick()
        val allSameMsg = str(R.string.friday_setup_pin_error_all_same)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(allSameMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__error_allsame.png")
        viewModel.clearAll()
        composeTestRule.waitForIdle()

        // __error_sequential -- monotonic +1 digits -> PinStrength.Result.Sequential.
        listOf("1", "2", "3", "4", "5", "6").forEach { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(nextLabel).performClick()
        val sequentialMsg = str(R.string.friday_setup_pin_error_sequential)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(sequentialMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__error_sequential.png")
        viewModel.clearAll()
        composeTestRule.waitForIdle()

        // __error_common -- "112233": present in PinStrength.COMMON but not monotonic (first
        // diff is 0), so it reaches CommonlyUsed rather than Sequential -- confirmed by reading
        // PinStrength.evaluate()'s branch order (Sequential is checked before CommonlyUsed).
        listOf("1", "1", "2", "2", "3", "3").forEach { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(nextLabel).performClick()
        val commonMsg = str(R.string.friday_setup_pin_error_common)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(commonMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__error_common.png")
        viewModel.clearAll()
        composeTestRule.waitForIdle()

        // __error_tooshort -- disclosed direct-VM-call cell (see class doc): the real "Next"
        // button is disabled below 6 digits, so PinStrength.Result.TooShort is unreachable via
        // a real click. Drive the same production submitPassword() call directly to render it.
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.waitForIdle()
        viewModel.submitPassword {}
        val tooShortMsg = context.getString(R.string.friday_setup_pin_error_too_short, SetupViewModel.MIN_PIN_LENGTH)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(tooShortMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__error_tooshort.png")
        viewModel.clearAll()
        composeTestRule.waitForIdle()

        // __confirm -- 6 valid (non-weak) digits submitted -> advances to the confirm step
        // with an empty confirm-PIN dot row.
        listOf("2", "4", "6", "8", "1", "3").forEach { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(nextLabel).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(confirmLabel).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__confirm.png")

        // __mismatch -- confirm-step digits that don't match the original PIN.
        listOf("1", "1", "1", "1", "1", "1").forEach { composeTestRule.onNodeWithText(it).performClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(confirmLabel).performClick()
        val mismatchMsg = str(R.string.friday_setup_pin_error_mismatch)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(mismatchMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__mismatch.png")

        // __back -- from the post-mismatch confirm step, Back resets to the create step
        // (confirm PIN cleared, error cleared, original 6-digit PIN retained).
        composeTestRule.onNodeWithText(backLabel).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(nextLabel).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "SetupPasswordScreen__EN__light__na__back.png")
    }
}

package com.dark.tool_neuron.ui.screens.password_screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-09 Codex-QA rework (blocker 2, Fix 2c) — device-capture matrix for
 * [PasswordScreen] (App Lock entry, canonical surface #6). [PasswordScreen] is a pure-callback
 * composable (all of password/error/isVerifying/lockedUntilMs/wiped are hoisted params, no VM
 * dependency) per PasswordScreen.kt — same class of screen as SetupScreen/ModelSetupScreen, so
 * this test hosts it directly with test-hoisted state, no PasswordViewModel/SecurityManager
 * wiring needed. Real production error strings are used verbatim (e.g. "Wrong PIN", matching
 * PasswordViewModel.submit()'s VerifyResult.WrongPin branch) rather than invented text.
 *
 * Disclosed, real limitations (not test bugs — confirmed by reading PasswordScreen.kt/PinPad.kt
 * in full):
 * - PasswordScreen.kt has zero stringResource() calls; every UI string is a hardcoded English
 *   literal. VI-locale cells below will show identical English text — this is an actual
 *   production gap, documented here and in MATRIX.md (Fix 2e), not silently hidden.
 * - PasswordScreen wraps its content in SecureScreen (FLAG_SECURE on the hosting window for as
 *   long as it's composed). Real-device PixelCopy-based capture can blank out FLAG_SECURE
 *   content; whether that happens here must be verified by inspecting the pulled PNGs after the
 *   instrumented run — if captures come back blank, that is reported as a genuine fail-closed
 *   finding in MATRIX.md, not silently accepted as valid evidence.
 * - __locked and __wiped are driven by direct parameter injection (not through a real lockout/
 *   wipe pathway, since there is no SecurityManager here) — this mirrors the same direct-
 *   injection technique already used for pure-callback screens elsewhere in this matrix
 *   (e.g. SetupScreen's hoisted selectedMode). The WipedScreen's real "Restart" button
 *   (finishAffinity + killProcess) is never clicked in-test.
 */
@RunWith(AndroidJUnit4::class)
class PasswordScreenDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun capture_light_dark_en_vi_grid_and_interaction_states() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")
        val passwordState = mutableStateOf("")
        val errorState = mutableStateOf<String?>(null)
        val isVerifyingState = mutableStateOf(false)
        val lockedUntilMsState = mutableStateOf(0L)
        val wipedState = mutableStateOf(false)

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                PasswordScreen(
                    innerPadding = PaddingValues(0.dp),
                    password = passwordState.value,
                    error = errorState.value,
                    isVerifying = isVerifyingState.value,
                    lockedUntilMs = lockedUntilMsState.value,
                    wiped = wipedState.value,
                    onDigit = { digit -> if (passwordState.value.length < 6) passwordState.value += digit },
                    onDelete = { passwordState.value = passwordState.value.dropLast(1) },
                    onClear = { passwordState.value = "" },
                    onSubmit = {
                        // Test-controlled double for SecurityManager.verifyPassword, mirroring
                        // production's VerifyResult.WrongPin branch verbatim (real string).
                        passwordState.value = ""
                        errorState.value = "Wrong PIN"
                    },
                )
            }
        }
        waitForText("Enter your password")

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
                "PasswordScreen__${cell.localeLabel}__${cell.themeLabel}__na.png",
            )
        }

        // Return to EN/light for the interaction-state cells (spec: EN light only).
        localeTagState.value = "en"
        darkState.value = false
        composeTestRule.waitForIdle()

        // __partial — three digits entered via the real PinNumberPad digit buttons.
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "PasswordScreen__EN__light__na__partial.png")

        // __error — fill to the 6-digit PIN length, then Unlock surfaces the real "Wrong PIN"
        // error text (test double stands in for SecurityManager; string matches production).
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.onNodeWithText("5").performClick()
        composeTestRule.onNodeWithText("6").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unlock").performClick()
        waitForText("Wrong PIN")
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "PasswordScreen__EN__light__na__error.png")

        // __locked — direct parameter injection (no real lockout pathway in this pure-callback
        // hosting); lockedUntilMs set far enough in the future that produceState's initial
        // `now` value is still less than it on first composition.
        errorState.value = null
        lockedUntilMsState.value = System.currentTimeMillis() + 5 * 60 * 1000L
        waitForText("Locked")
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "PasswordScreen__EN__light__na__locked.png")

        // __wiped — direct parameter injection. The real "Restart" button (finishAffinity +
        // killProcess) is never clicked.
        lockedUntilMsState.value = 0L
        wipedState.value = true
        waitForText("Vault wiped")
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "PasswordScreen__EN__light__na__wiped.png")
    }
}

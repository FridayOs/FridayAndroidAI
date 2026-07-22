package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.evidence.localizedContext
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.friday.ai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * FRI-633 phase-11 QA rework round 4 (Blocker 1b): deterministic capture+assert of
 * every Friday login auth state ACROSS the axes the Login surface actually varies on.
 *
 * Hosts the stateless [FridayLoginContent] under [Fri633ThemedHost] (the shared
 * device-capture theming used by FridayLoginDeviceCaptureTest) so each PNG renders with
 * the REAL FRIDAY palette — white `#FFFFFF` / dark `#141416` background, Figtree
 * typography — instead of the raw Material3 default `#FFFBFE` (the lavender/pink tint QA
 * flagged). The repo has NO Hilt test infra, so hosting content + local fakes is the
 * established pattern.
 *
 * AXES:
 *  - Locale EN/VI  → varies (every string via stringResource; host pins LocalContext).
 *  - Theme light/dark → varies (mode toggle icon + MaterialTheme.colorScheme neutrals).
 *  - Accent SUN/EMBER/VIOLET/MINT → INVARIANT, so fixed to SUN and NOT multiplied.
 *    Proof: FridayLoginContent reads only NEUTRAL MaterialTheme roles
 *    (background/surface/surfaceVariant/onSurface/outlineVariant/onSurfaceVariant) plus
 *    the hardcoded [com.dark.tool_neuron.ui.util.FridayPalette] Primary/OnPrimary yellow.
 *    It never reads `LocalFridayAccent` nor `colorScheme.primary`; the accent swap in
 *    ToolNeuronTheme/Fri633ThemedHost only rewrites `colorScheme.primary`/`onPrimary`,
 *    which this surface never consumes — so all 4 accents render pixel-identical. This
 *    also matches the canonical Login (no accent picker exists pre-auth).
 *
 * This is a test SEAM, never a production auth bypass: `signInWithGoogle` runs unchanged
 * through the real [FridayLoginScreen] wrapper in production; here callbacks are no-ops
 * and each auth state is a plain input. Every state is asserted present via semantics
 * BEFORE capture, so a missing state fails closed (no blank/partial PNG).
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginAuthStateCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Localized lookup so VI cells assert the VI copy (not the default-locale string). */
    private fun strFor(tag: String, id: Int) = localizedContext(context, tag).getString(id)

    private val snackbarHostState = SnackbarHostState()
    private lateinit var capturedScope: CoroutineScope

    // Accent is invariant for this surface (see class doc) — fixed to SUN.
    private data class Cell(val locale: String, val tag: String, val dark: Boolean, val theme: String)
    private val cells = listOf(
        Cell("EN", "en", dark = false, theme = "light"),
        Cell("EN", "en", dark = true, theme = "dark"),
        Cell("VI", "vi", dark = false, theme = "light"),
        Cell("VI", "vi", dark = true, theme = "dark"),
    )
    private val darkState = mutableStateOf(false)
    private val tagState = mutableStateOf("en")

    private fun hostContent(
        signingIn: Boolean = false,
        noGoogleAccount: Boolean = false,
    ) {
        composeTestRule.setContent {
            capturedScope = rememberCoroutineScope()
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = tagState.value,
            ) {
                FridayLoginContent(
                    innerPadding = PaddingValues(0.dp),
                    signingIn = signingIn,
                    noGoogleAccount = noGoogleAccount,
                    mode = if (darkState.value) ThemeController.Mode.DARK else ThemeController.Mode.LIGHT,
                    logo = null,
                    snackbarHostState = snackbarHostState,
                    onGoogleClick = {},
                    onThemeToggle = {},
                    onDismissNoGoogle = {},
                    onAddGoogleAccount = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun applyCell(cell: Cell) {
        tagState.value = cell.tag
        darkState.value = cell.dark
        composeTestRule.waitForIdle()
    }

    private fun dismissSnackbar() {
        composeTestRule.runOnIdle { snackbarHostState.currentSnackbarData?.dismiss() }
        composeTestRule.waitForIdle()
    }

    /** Keeps the snackbar on-screen for a deterministic assert/capture. */
    private fun showSnackbarIndefinitely(message: String) {
        dismissSnackbar()
        capturedScope.launch {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Compose-content PNG capture through the shared [Fri633CaptureHarness]. Always called
     * AFTER the state is asserted present, so a missing state fails closed.
     */
    private fun capture(state: String, cell: Cell) {
        Fri633CaptureHarness.save(
            composeTestRule.onRoot(),
            "FridayLoginScreen__authstate__${state}__${cell.locale}__${cell.theme}__sun.png",
        )
    }

    @Test
    fun loading_state_shows_google_progress_spinner() {
        hostContent(signingIn = true)
        for (cell in cells) {
            applyCell(cell)
            // Loading: GoogleCircleButton swaps its logo for the progress spinner.
            composeTestRule.onNodeWithTag("friday_login_google_progress").assertIsDisplayed()
            composeTestRule.onNodeWithTag("friday_login_google_button").assertIsDisplayed()
            capture("loading", cell)
        }
    }

    @Test
    fun idle_state_shows_no_google_progress_spinner() {
        hostContent(signingIn = false)
        for (cell in cells) {
            applyCell(cell)
            // Guards against a false-positive loading assert: idle must NOT show the spinner.
            composeTestRule.onNodeWithTag("friday_login_google_progress").assertDoesNotExist()
            composeTestRule.onNodeWithTag("friday_login_google_button").assertIsDisplayed()
            capture("idle", cell)
        }
    }

    @Test
    fun base_login_surface_renders() {
        hostContent()
        for (cell in cells) {
            applyCell(cell)
            // Base login: heading present, no auth overlay.
            composeTestRule.onNodeWithText(strFor(cell.tag, R.string.friday_login_heading))
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("friday_login_google_button").assertIsDisplayed()
            capture("base", cell)
        }
    }

    @Test
    fun no_google_account_state_shows_dialog() {
        hostContent(noGoogleAccount = true)
        for (cell in cells) {
            applyCell(cell)
            composeTestRule.onNodeWithText(strFor(cell.tag, R.string.friday_login_error_no_credential))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(strFor(cell.tag, R.string.friday_login_error_no_credential_body))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText(strFor(cell.tag, R.string.friday_login_action_open_settings))
                .assertIsDisplayed()
            capture("nogoogle", cell)
        }
    }

    @Test
    fun error_snackbar_state_shows_generic_message() {
        hostContent()
        for (cell in cells) {
            applyCell(cell)
            val msg = strFor(cell.tag, R.string.friday_login_error_generic)
            showSnackbarIndefinitely(msg)
            composeTestRule.onNodeWithText(msg).assertIsDisplayed()
            capture("error", cell)
        }
        dismissSnackbar()
    }

    @Test
    fun cancel_snackbar_state_shows_cancelled_message() {
        hostContent()
        for (cell in cells) {
            applyCell(cell)
            val msg = strFor(cell.tag, R.string.friday_login_error_cancelled)
            showSnackbarIndefinitely(msg)
            composeTestRule.onNodeWithText(msg).assertIsDisplayed()
            capture("cancel", cell)
        }
        dismissSnackbar()
    }
}

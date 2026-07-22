package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AccountRepository
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.CloudAccountGatewayProvider
import com.dark.tool_neuron.data.FridayApi
import com.dark.tool_neuron.data.FridayApiAccountGateway
import com.dark.tool_neuron.data.FirebaseAccountGateway
import com.dark.tool_neuron.data.GoogleSignIn
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.data.firebase.FirebaseGoogleAuth
import com.dark.tool_neuron.data.firebase.FirebasePolicy
import com.dark.tool_neuron.data.firebase.FirebaseProfileStore
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayThemeViewModel
import com.friday.ai.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-09 Codex-QA rework (blocker 2, Fix 2a) — device-capture matrix for the
 * CURRENT (post-phase-08 honest-wiring) FridayLoginScreen: EN/VI x light/dark x na accent
 * base grid (4 cells, replaces the stale device/FridayLoginScreen__EN__light__na.png that
 * MATRIX.md §3.2 already disclosed as captured from the pre-rework installed APK) PLUS
 * EN/light interaction-state cells (__filled/__reveal/__validation/__comingsoon/__legal)
 * that the phase-09 spec (Fix 2a) calls out as missing entirely from the prior matrix.
 *
 * Hosts the REAL FridayLoginScreen through REAL AccountViewModel/FridayThemeViewModel
 * instances built by walking the concrete dependency chain on-device — same real-VM
 * pattern as FridayLoginSurfaceTest.hostScreen(), with Fri633ThemedHost added on top (same
 * technique as every other *DeviceCaptureTest) so light/dark/accent/locale are forced
 * deterministically instead of relying on the real ThemeController Hilt singleton.
 * themeViewModel.setMode(...) is also driven per cell so the in-screen theme-toggle icon
 * (Sun/Moon) stays consistent with the Fri633ThemedHost-forced theme, exactly mirroring
 * SetupThemeDeviceCaptureTest's vm.selectMode(...) pairing.
 *
 * No production code is touched. No production auth bypass. Google sign-in network result
 * is never asserted/awaited here — the Google button is not exercised at all.
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginDeviceCaptureTest {

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
        val firebaseAuth = FirebaseGoogleAuth(context)
        val accountRepository = AccountRepository(prefs, firebaseAuth)
        val googleSignIn = GoogleSignIn()
        val fridayApi = FridayApi(prefs)
        val fridayApiGateway = FridayApiAccountGateway(googleSignIn, fridayApi)
        val profileStore = FirebaseProfileStore(prefs)
        val policy = FirebasePolicy(context)
        val firebaseGateway = FirebaseAccountGateway(context, firebaseAuth, profileStore, policy)
        val gatewayProvider = CloudAccountGatewayProvider(prefs, fridayApiGateway, firebaseGateway)
        val accountViewModel = AccountViewModel(accountRepository, gatewayProvider)
        val themeController = ThemeController(prefs)
        val themeViewModel = FridayThemeViewModel(themeController)

        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
                FridayLoginScreen(
                    innerPadding = PaddingValues(0.dp),
                    accountViewModel = accountViewModel,
                    themeViewModel = themeViewModel,
                )
            }
        }
        composeTestRule.waitForIdle()

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
            themeViewModel.setMode(if (cell.dark) ThemeController.Mode.DARK else ThemeController.Mode.LIGHT)
            composeTestRule.waitForIdle()
            Fri633CaptureHarness.save(
                composeTestRule.onRoot(),
                "FridayLoginScreen__${cell.localeLabel}__${cell.themeLabel}__na.png",
            )
        }

        // Return to EN/light for the interaction-state cells (spec: EN light only).
        localeTagState.value = "en"
        darkState.value = false
        themeViewModel.setMode(ThemeController.Mode.LIGHT)
        composeTestRule.waitForIdle()

        // __validation — Login Now with blank fields shows the validation snackbar. Must be
        // driven BEFORE any field is filled (validation only fires when name/password blank).
        val validationMsg = str(R.string.friday_login_validation_message)
        composeTestRule.onNodeWithTag("friday_login_now_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(validationMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "FridayLoginScreen__EN__light__na__validation.png")

        // Let the transient snackbar clear before the next capture so it doesn't bleed into
        // the __filled cell's frame. Real-device AccessibilityManager can extend Material3's
        // default SnackbarDuration.Short well past the nominal 4s, so this uses a generous
        // timeout (observed real-device clear time exceeded 6s during Fix 2a verification).
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(validationMsg).fetchSemanticsNodes().isEmpty()
        }

        // __filled — name + password typed.
        composeTestRule.onNodeWithTag("friday_login_name_input").performTextInput("Ada")
        composeTestRule.onNodeWithTag("friday_login_password_input").performTextInput("secret123")
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "FridayLoginScreen__EN__light__na__filled.png")

        // __reveal — password shown via the reveal toggle (still filled).
        val showCd = str(R.string.friday_login_password_show_cd)
        composeTestRule.onNodeWithContentDescription(showCd).performClick()
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "FridayLoginScreen__EN__light__na__reveal.png")

        // __comingsoon — Login Now with fields filled shows the coming-soon snackbar.
        val comingSoonMsg = str(R.string.friday_login_coming_soon)
        composeTestRule.onNodeWithTag("friday_login_now_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(comingSoonMsg).fetchSemanticsNodes().isNotEmpty()
        }
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "FridayLoginScreen__EN__light__na__comingsoon.png")
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText(comingSoonMsg).fetchSemanticsNodes().isEmpty()
        }

        // __legal — terms dialog open (phase-08 honest-wiring legal links).
        composeTestRule.onNodeWithTag("friday_login_terms_link").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("friday_login_legal_dialog").assertExistsForCapture()
        Fri633CaptureHarness.save(composeTestRule.onRoot(), "FridayLoginScreen__EN__light__na__legal.png")
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_got_it)).performClick()
        composeTestRule.waitForIdle()
    }
}

/** Local no-op-return helper so a missing dialog fails loudly with a clear assertion name. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsForCapture() {
    this.fetchSemanticsNode("Expected friday_login_legal_dialog to exist before capture")
}

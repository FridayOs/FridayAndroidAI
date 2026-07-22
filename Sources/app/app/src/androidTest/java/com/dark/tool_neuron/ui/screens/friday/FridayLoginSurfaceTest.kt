package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import androidx.activity.ComponentActivity
import com.dark.tool_neuron.data.AccountRepository
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.CloudAccountGateway
import com.dark.tool_neuron.data.CloudAccountGatewayProvider
import com.dark.tool_neuron.data.FridayApi
import com.dark.tool_neuron.data.FridayApiAccountGateway
import com.dark.tool_neuron.data.FirebaseAccountGateway
import com.dark.tool_neuron.data.FridaySession
import com.dark.tool_neuron.data.GoogleSignIn
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.data.firebase.FirebaseGoogleAuth
import com.dark.tool_neuron.data.firebase.FirebasePolicy
import com.dark.tool_neuron.data.firebase.FirebaseProfileStore
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayThemeViewModel
import com.friday.ai.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-05 — Auth surface (canonical P1, Friday Agent.dc.html:44-107)
 * honest-wiring coverage: locks the full visible copy verbatim, and locks
 * that every control invokes a REAL callback with visible feedback — no
 * fake success, no navigation, no dead onClick.
 *
 * Hosts the REAL [FridayLoginScreen] through REAL [AccountViewModel] and
 * [FridayThemeViewModel] instances built by walking their concrete
 * dependency chain on-device (no Hilt test app) — mirrors
 * SetupThemeInteractionTest's HXS-backed AppPreferences setup. Every test uses
 * the real dependency chain; the only exception is the Google-button wiring test,
 * which fakes the single CloudAccountGateway boundary (the external OS credential
 * leaf) through the production provider seam — see its KDoc below.
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless. Google sign-in network result is not
 * asserted here — only that the button is real and invokes the real VM call.
 *
 * [google_button_click_invokes_real_view_model_sign_in] proves the real Google
 * button onClick is wired to the real [AccountViewModel.signInWithGoogle] through
 * production code, but holds the gateway boundary suspended via the production
 * [CloudAccountGatewayProvider] test seam so NO OS-owned Google credential activity
 * is launched. An earlier revision drove the real CredentialManager in-UI; on-device
 * that spawned an async Google Play Services activity that stole foreground focus and
 * destroyed the NEXT test class's ComposeTestRule host ("No compose hierarchies found
 * in the app"), making the combined gate order-dependent (QA round-6). Suspending the
 * gateway removes that cross-class race entirely without weakening the real button →
 * real VM wiring assertion; the full gateway-outcome → UI-state mapping is covered by
 * FridayLoginAuthMappingTest against the same production seam. No method ordering is
 * required because no test leaves external OS state behind.
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginSurfaceTest {

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

    private data class LoginScreenHandles(
        val accountViewModel: AccountViewModel,
        val themeViewModel: FridayThemeViewModel,
    )

    private fun hostScreen(): LoginScreenHandles {
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
        composeTestRule.setContent {
            FridayLoginScreen(
                innerPadding = PaddingValues(0.dp),
                accountViewModel = accountViewModel,
                themeViewModel = themeViewModel,
            )
        }
        composeTestRule.waitForIdle()
        return LoginScreenHandles(accountViewModel, themeViewModel)
    }

    /**
     * Fake gateway bound through the production [CloudAccountGatewayProvider] test seam —
     * the same boundary AccountViewModel already depends on. Lets the Google-button wiring
     * test drive the real sign-in entry point deterministically with no real CredentialManager
     * / OS credential activity. The activity argument is irrelevant to the fake.
     */
    private class FakeGateway(
        private val onSignIn: suspend () -> FridaySession,
    ) : CloudAccountGateway {
        override suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession = onSignIn()
        override suspend fun refresh(current: FridaySession): FridaySession = current
    }

    private fun authenticatedSession() = FridaySession(
        jwt = "test-jwt-non-blank",
        userId = "user-1",
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        avatarUrl = "",
        plan = "Free plan",
    )

    /** Hosts the REAL [FridayLoginScreen] with an [AccountViewModel] whose gateway is [gateway]. */
    private fun hostScreenWithGateway(gateway: CloudAccountGateway): LoginScreenHandles {
        val firebaseAuth = FirebaseGoogleAuth(context)
        val accountRepository = AccountRepository(prefs, firebaseAuth)
        // Production provider, @VisibleForTesting single-gateway seam — current() returns the fake.
        val gatewayProvider = CloudAccountGatewayProvider(gateway)
        val accountViewModel = AccountViewModel(accountRepository, gatewayProvider)
        val themeController = ThemeController(prefs)
        val themeViewModel = FridayThemeViewModel(themeController)
        composeTestRule.setContent {
            FridayLoginScreen(
                innerPadding = PaddingValues(0.dp),
                accountViewModel = accountViewModel,
                themeViewModel = themeViewModel,
            )
        }
        composeTestRule.waitForIdle()
        return LoginScreenHandles(accountViewModel, themeViewModel)
    }

    @Test
    fun heading_and_ctas_are_p1_verbatim() {
        hostScreen()

        composeTestRule.onNodeWithText(str(R.string.friday_login_heading)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_login_create_account_prompt)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_login_account_link)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_name_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_password_label)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_login_forgot_password)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("friday_login_now_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friday_login_register_now_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friday_login_google_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friday_login_facebook_button").assertIsDisplayed()
    }

    @Test
    fun typing_into_name_and_password_updates_fields() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_name_input").performTextInput("Ada")
        composeTestRule.onNodeWithTag("friday_login_password_input").performTextInput("secret123")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
    }

    @Test
    fun password_reveal_toggle_flips_visibility_content_description() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_password_input").performTextInput("secret123")
        composeTestRule.onNodeWithContentDescription(str(R.string.friday_login_password_show_cd))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(str(R.string.friday_login_password_hide_cd))
            .assertIsDisplayed()
    }

    @Test
    fun login_now_with_blank_fields_shows_validation_not_fake_success() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_now_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.friday_login_validation_message)).assertIsDisplayed()
    }

    @Test
    fun login_now_with_filled_fields_shows_coming_soon_not_fake_success() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_name_input").performTextInput("Ada")
        composeTestRule.onNodeWithTag("friday_login_password_input").performTextInput("secret123")
        composeTestRule.onNodeWithTag("friday_login_now_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.friday_login_coming_soon)).assertIsDisplayed()
    }

    @Test
    fun register_forgot_and_facebook_show_coming_soon_feedback() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_register_now_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_login_coming_soon)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("friday_login_forgot_password").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_login_coming_soon)).assertIsDisplayed()

        composeTestRule.onNodeWithTag("friday_login_facebook_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(str(R.string.friday_login_coming_soon)).assertIsDisplayed()
    }

    @Test
    fun theme_toggle_click_flips_theme_controller_mode() {
        val handles = hostScreen()
        val initialMode = handles.themeViewModel.mode.value

        composeTestRule.onNodeWithTag("friday_login_theme_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            handles.themeViewModel.mode.value != initialMode
        }
        // toggle() always lands on DARK unless already DARK (see FridayThemeViewModel.toggle),
        // so regardless of the SYSTEM/LIGHT starting mode this is the deterministic outcome.
        assertEquals(ThemeController.Mode.DARK, handles.themeViewModel.mode.value)
    }

    @Test
    fun account_link_click_shows_coming_soon_feedback() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_account_link").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(str(R.string.friday_login_coming_soon)).assertIsDisplayed()
    }

    @Test
    fun google_button_click_invokes_real_view_model_sign_in() {
        // Real button onClick → real AccountViewModel.signInWithGoogle through production
        // wiring. The gateway is held suspended via the production CloudAccountGatewayProvider
        // seam so signingIn stays true while in-flight and NO OS-owned Google credential
        // activity launches (which previously leaked into the next test class and destroyed its
        // Compose host — QA round-6). FridayLoginAuthMappingTest covers the full
        // gateway-outcome → UI-state mapping against the same seam. Never asserts network success.
        val gate = CompletableDeferred<Unit>()
        val handles = hostScreenWithGateway(FakeGateway { gate.await(); authenticatedSession() })

        composeTestRule.onNodeWithTag("friday_login_google_button").performClick()

        // signingIn flips true synchronously in the real VM before the coroutine dispatches.
        composeTestRule.waitUntil(timeoutMillis = 5_000) { handles.accountViewModel.signingIn.value }

        // Release the suspended gateway so the coroutine settles cleanly, leaving no in-flight work.
        gate.complete(Unit)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { !handles.accountViewModel.signingIn.value }
    }

    @Test
    fun terms_link_click_shows_legal_dialog_with_terms_content() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_terms_link").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("friday_login_legal_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_title)).assertIsDisplayed()

        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_got_it)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("friday_login_legal_dialog").assertDoesNotExist()
    }

    @Test
    fun privacy_link_click_shows_legal_dialog_with_privacy_content() {
        hostScreen()

        composeTestRule.onNodeWithTag("friday_login_privacy_link").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("friday_login_legal_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_title)).assertIsDisplayed()

        composeTestRule.onNodeWithText(str(R.string.onboarding_terms_privacy_dialog_got_it)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("friday_login_legal_dialog").assertDoesNotExist()
    }
}

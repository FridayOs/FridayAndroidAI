package com.dark.tool_neuron.ui.screens.friday

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AccountRepository
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.CloudAccountGateway
import com.dark.tool_neuron.data.CloudAccountGatewayProvider
import com.dark.tool_neuron.data.FridaySession
import com.dark.tool_neuron.data.firebase.FirebaseGoogleAuth
import com.dark.tool_neuron.data.firebase.SignInError
import com.dark.tool_neuron.data.firebase.SignInException
import com.dark.tool_neuron.viewmodel.AccountViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-11 QA rework round 4 (Blocker 1c — production auth-state mapping).
 *
 * Drives the REAL [AccountViewModel.signInWithGoogle] against a fake
 * [CloudAccountGateway] bound through the production [CloudAccountGatewayProvider]
 * test seam (its @VisibleForTesting constructor). This proves AccountViewModel maps
 * gateway outcomes → UI state THROUGH production code — not a hand-driven snackbar or
 * a mirrored state machine:
 *   (i)   gateway suspends            → signingIn == true
 *   (ii)  SignInError.NoCredential    → noGoogleAccount == true
 *   (iii) SignInError.Cancelled       → snackbar emits "cancelled"
 *   (iv)  SignInError.Other           → snackbar emits "generic"
 *   (v)   returns a FridaySession     → account state becomes Authenticated
 *
 * No production auth bypass: the real Credential Manager / Firebase sign-in is never
 * invoked — only the gateway boundary AccountViewModel already depends on is faked.
 * The activity argument is irrelevant to the fake, so a bare ComponentActivity from
 * createAndroidComposeRule supplies it (never used for real sign-in).
 */
@RunWith(AndroidJUnit4::class)
class FridayLoginAuthMappingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var prefs: AppPreferences

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

    /** Fake gateway: the whole point of the seam — no real credential flow. */
    private class FakeGateway(
        private val onSignIn: suspend () -> FridaySession,
    ) : CloudAccountGateway {
        override suspend fun signInWithGoogle(activity: ComponentActivity): FridaySession = onSignIn()
        override suspend fun refresh(current: FridaySession): FridaySession = current
    }

    private fun buildViewModel(gateway: CloudAccountGateway): AccountViewModel {
        val firebaseAuth = FirebaseGoogleAuth(context)
        val accountRepository = AccountRepository(prefs, firebaseAuth)
        // Production provider, test seam constructor — current() returns the fake.
        val provider = CloudAccountGatewayProvider(gateway)
        return AccountViewModel(accountRepository, provider)
    }

    private fun authenticatedSession() = FridaySession(
        jwt = "test-jwt-non-blank",
        userId = "user-1",
        displayName = "Ada Lovelace",
        email = "ada@example.com",
        avatarUrl = "",
        plan = "Free plan",
    )

    /** Runs the REAL production entry point on the main thread. */
    private fun triggerSignIn(vm: AccountViewModel) {
        instrumentation.runOnMainSync { vm.signInWithGoogle(composeTestRule.activity) }
    }

    private fun waitUntil(timeoutMs: Long = 5_000, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!predicate()) {
            if (SystemClock.uptimeMillis() > deadline) {
                throw AssertionError("Condition not met within ${timeoutMs}ms")
            }
            Thread.sleep(20)
        }
    }

    /** Triggers sign-in, waits for the flow to settle, returns the buffered snackbar. */
    private fun triggerAndCollectSnackbar(vm: AccountViewModel): String? {
        triggerSignIn(vm)
        waitUntil { !vm.signingIn.value }
        return runBlocking { withTimeoutOrNull(2_000) { vm.snackbar.first() } }
    }

    @Test
    fun gateway_suspends_keeps_signing_in_true() {
        val gate = CompletableDeferred<Unit>()
        val vm = buildViewModel(FakeGateway { gate.await(); authenticatedSession() })

        triggerSignIn(vm)

        // While the gateway coroutine is still suspended, production keeps the spinner on.
        assertTrue("signingIn must be true while gateway is in-flight", vm.signingIn.value)

        // Release so the coroutine completes cleanly (finally flips signingIn back).
        gate.complete(Unit)
        waitUntil { !vm.signingIn.value }
    }

    @Test
    fun no_credential_error_sets_no_google_account() {
        val vm = buildViewModel(FakeGateway { throw SignInException(SignInError.NoCredential) })

        triggerSignIn(vm)
        waitUntil { !vm.signingIn.value }

        assertTrue("NoCredential must raise the no-Google-account dialog flag", vm.noGoogleAccount.value)
    }

    @Test
    fun cancelled_error_emits_cancelled_snackbar() {
        val vm = buildViewModel(FakeGateway { throw SignInException(SignInError.Cancelled) })

        val message = triggerAndCollectSnackbar(vm)

        assertEquals("cancelled", message)
    }

    @Test
    fun other_error_emits_generic_snackbar() {
        val vm = buildViewModel(
            FakeGateway { throw SignInException(SignInError.Other(RuntimeException("boom"))) },
        )

        val message = triggerAndCollectSnackbar(vm)

        assertEquals("generic", message)
    }

    @Test
    fun unexpected_exception_emits_generic_snackbar() {
        // Non-SignInException path must still map to the generic snackbar (never crash).
        val vm = buildViewModel(FakeGateway { throw IllegalStateException("unexpected") })

        val message = triggerAndCollectSnackbar(vm)

        assertEquals("generic", message)
    }

    @Test
    fun successful_session_persists_authenticated_state() {
        val vm = buildViewModel(FakeGateway { authenticatedSession() })

        triggerSignIn(vm)
        waitUntil { !vm.signingIn.value }
        waitUntil { vm.state.value is AccountState.Authenticated }

        val state = vm.state.value
        assertTrue("A returned session must persist Authenticated state", state is AccountState.Authenticated)
        assertEquals("user-1", (state as AccountState.Authenticated).userId)
    }
}

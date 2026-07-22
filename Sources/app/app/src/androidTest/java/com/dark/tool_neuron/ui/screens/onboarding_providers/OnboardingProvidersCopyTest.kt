package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.viewmodel.OnboardingProvidersViewModel
import com.friday.ai.R
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 — locks the Providers screen's P0-verbatim visible copy (design-spec §4.2,
 * HTML:349-440):
 *  - Title "AI Providers"; hero "Your providers stay on this phone"; chips
 *    "Local-only keys"/"Direct Android calls"/"No prompt sync".
 *  - Configured label "Configured"; available label "Add provider"; Friday row
 *    "Coming later" + "Managed FRIDAY provider".
 *  - Per-provider available-row descs: "Default: <model>" for catalog providers,
 *    "OpenAI-compatible · custom base URL" for OpenClaw/Hermes/Custom.
 *  - Empty state "No AI provider yet"; continue CTA "Continue to Friday".
 *  - Configured row third line "Direct from Android".
 *
 * Hosts the REAL [OnboardingProvidersScreen] through a REAL
 * [OnboardingProvidersViewModel] backed by a REAL on-device
 * [GatewayConfigRepository] — no fakes/mocks. Mirrors GatewayConfigRepositoryTest's
 * HXS setup (BootIntegrity relaxed + disk cleanup). LocalFridayAccent falls back to
 * its static default (SUN).
 *
 * Device-run note: execution needs an emulator/device; compiled against the
 * androidTest source set regardless.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingProvidersCopyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

    private fun str(id: Int) = context.getString(id)
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun setup() {
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
        PolicyEngine.resetForTesting()
        BootIntegrity.setRelaxedForTesting(true)
        HexStorage().close()
        encryptor = HxsEncryptor()
    }

    @After
    fun cleanup() {
        HexStorage().close()
        BootIntegrity.setRelaxedForTesting(false)
        PolicyEngine.resetForTesting()
        File(context.filesDir, "gateway_store_v1").deleteRecursively()
    }

    private fun newRepo(): GatewayConfigRepository =
        GatewayConfigRepository(context, AppKeyStore(context), encryptor)

    private fun hostScreen(repo: GatewayConfigRepository) {
        val vm = OnboardingProvidersViewModel(repo)
        composeTestRule.setContent {
            MaterialTheme {
                OnboardingProvidersScreen(
                    innerPadding = PaddingValues(0.dp),
                    viewModel = vm,
                    onBack = {},
                    onAddProvider = {},
                    onContinueToFriday = {},
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // The screen is a LazyColumn: off-screen items are not composed, so a node below
    // the fold (mid-list OpenClaw row, last Friday row, bottom Continue CTA) must be
    // scrolled into view before it exists in the tree. Scroll the list to the node with
    // the given text, then assert it is displayed. NOTE: performScrollToNode returns the
    // LIST interaction (for chaining), so we must re-query the target node afterwards —
    // the OpenAI-compatible desc matches 3 rows (OpenClaw/Hermes/Custom), so we assert
    // the first matching (now-composed) node rather than onNodeWithText (which throws on
    // multiplicity).
    private fun scrollToAndAssertDisplayed(text: String) {
        composeTestRule
            .onNodeWithTag("onboarding_providers_list")
            .performScrollToNode(hasText(text))
        composeTestRule.onAllNodesWithText(text)[0].assertIsDisplayed()
    }

    @Test
    fun title_hero_and_chips_are_p0_verbatim() {
        hostScreen(newRepo())
        composeTestRule.onNodeWithText(str(R.string.friday_providers_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_hero_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_hero_body)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_chip_local)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_chip_direct)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_chip_no_sync)).assertIsDisplayed()
    }

    @Test
    fun empty_state_shows_p0_verbatim_title_and_available_label() {
        hostScreen(newRepo())
        composeTestRule.onNodeWithText(str(R.string.friday_providers_empty_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_available_label)).assertIsDisplayed()
    }

    @Test
    fun gemini_available_row_shows_default_model_desc() {
        hostScreen(newRepo())
        composeTestRule
            .onNodeWithText(str(R.string.friday_providers_desc_default_model, GatewayProvider.GEMINI.defaultModel))
            .assertIsDisplayed()
    }

    @Test
    fun openclaw_available_row_shows_openai_compatible_desc() {
        hostScreen(newRepo())
        // OpenClaw/Hermes/Custom sit mid-list, below the fold — scroll into view first.
        scrollToAndAssertDisplayed(str(R.string.friday_providers_desc_openai_compatible))
    }

    @Test
    fun friday_row_shows_managed_desc_and_coming_later_chip() {
        hostScreen(newRepo())
        // Friday is the last catalog row — scroll into view before asserting.
        scrollToAndAssertDisplayed(str(R.string.friday_providers_desc_managed_friday))
        scrollToAndAssertDisplayed(str(R.string.friday_providers_coming_soon))
    }

    @Test
    fun configured_state_shows_label_direct_subline_and_continue_cta() {
        val repo = newRepo()
        repo.create(GatewayProvider.OPENAI, "Work", "", "sk-secret-never-rendered", "gpt-4o-mini")
        hostScreen(repo)
        // Guarantee the raw secret never renders anywhere in the composed tree.
        composeTestRule.onNodeWithText("sk-secret-never-rendered", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(str(R.string.friday_providers_configured_label)).assertIsDisplayed()
        // P0 configured-row third line (HTML:392): "Direct from Android" under "label · model".
        scrollToAndAssertDisplayed(str(R.string.friday_providers_direct_from_phone))
        // Footer CTA appears once ≥1 provider is configured (bottom of the list).
        scrollToAndAssertDisplayed(str(R.string.friday_providers_continue))
    }
}

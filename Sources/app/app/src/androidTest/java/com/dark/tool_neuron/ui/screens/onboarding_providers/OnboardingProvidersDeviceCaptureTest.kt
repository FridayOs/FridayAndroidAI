package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.hxs.HexStorage
import com.dark.hxs_encryptor.BootIntegrity
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.hxs_encryptor.PolicyEngine
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.evidence.Fri633CaptureHarness
import com.dark.tool_neuron.evidence.Fri633ThemedHost
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.viewmodel.OnboardingProvidersViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/*
 * FRI-633 phase-09 Codex-QA rework (blocker 2, Fix 2c) — device-capture matrix for
 * OnboardingProvidersScreen (canonical surface #9): EN/VI x light/dark default (empty) grid
 * (4 cells) plus a real provider-selected interaction-state cell (EN/light only, matching the
 * established interaction-state convention).
 *
 * Hosts the REAL [OnboardingProvidersScreen] through a REAL [OnboardingProvidersViewModel]
 * backed by a REAL on-device [GatewayConfigRepository] — same real-VM/real-repo pattern as
 * OnboardingProvidersCopyTest.kt (HXS setup: BootIntegrity relaxed + disk cleanup), with
 * Fri633ThemedHost layered on top (same technique as every other *DeviceCaptureTest) so
 * light/dark/accent/locale are forced deterministically.
 *
 * The __selected cell reuses the SAME repo/VM/composition as the default grid — createComposeRule
 * only accepts one setContent call per test, so __selected is driven by a real
 * GatewayConfigRepository.create() write on the already-composed repo after the grid captures;
 * OnboardingProvidersViewModel.gateways is a StateFlow proxy of the repo, so the write recomposes
 * the already-running screen automatically.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingProvidersDeviceCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var encryptor: HxsEncryptor

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

    @Test
    fun capture_light_dark_en_vi_default_grid_and_selected_state() {
        val darkState = mutableStateOf(false)
        val localeTagState = mutableStateOf("en")
        // Single repo/VM for the whole test (createComposeRule's Activity accepts exactly one
        // setContent call). The __selected cell is driven by writing into this SAME repo after
        // the default-grid captures — OnboardingProvidersViewModel.gateways is a StateFlow
        // sourced straight from the repo, so the real write recomposes the screen with no need
        // for a second composition host.
        val repo = newRepo()
        val vm = OnboardingProvidersViewModel(repo)

        composeTestRule.setContent {
            Fri633ThemedHost(
                dark = darkState.value,
                accent = FridayAccent.SUN,
                languageTag = localeTagState.value,
            ) {
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
                "OnboardingProvidersScreen__${cell.localeLabel}__${cell.themeLabel}__na.png",
            )
        }

        // __selected — return to EN/light, then a real GatewayConfigRepository write on the SAME
        // repo backing the composed VM surfaces the real configured row (StateFlow recompose, no
        // second setContent). The row is clicked by its rendered text (merged-clickable-Row-by-
        // text — no testTag exists on the row itself) to select it, revealing the real
        // CircleCheck/StatusChip selected visuals.
        localeTagState.value = "en"
        darkState.value = false
        composeTestRule.waitForIdle()
        repo.create(GatewayProvider.OPENAI, "Work", "", "sk-secret-never-rendered", "gpt-4o-mini")
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("onboarding_providers_list")
            .performScrollToNode(hasText("Work · gpt-4o-mini"))
        composeTestRule.onNodeWithText("Work · gpt-4o-mini").performClick()
        composeTestRule.waitForIdle()
        Fri633CaptureHarness.save(
            composeTestRule.onRoot(),
            "OnboardingProvidersScreen__EN__light__na__selected.png",
        )
    }
}

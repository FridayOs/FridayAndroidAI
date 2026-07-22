package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.viewmodel.OnboardingProvidersLogic
import com.dark.tool_neuron.viewmodel.ProviderAvailableDesc
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Pure JVM tests for OnboardingProvidersLogic (FRI-582 P7): status-chip
 * string-resource mapping and available-catalog ordering. No Hilt/Context
 * involved, matching the pure-function extraction requirement.
 */
class OnboardingProvidersLogicTest {

    private fun config(
        id: String = "cfg-1",
        provider: GatewayProvider = GatewayProvider.GEMINI,
        status: GatewayStatus = GatewayStatus.NOT_TESTED,
    ) = GatewayConfig(
        id = id,
        provider = provider,
        label = "My Gemini",
        baseUrl = "",
        apiKey = "secret-key-should-never-render",
        model = "",
        createdAt = 0L,
        updatedAt = 0L,
        status = status,
    )

    @Test
    fun `selected config takes priority over raw status`() {
        val cfg = config(id = "cfg-1", status = GatewayStatus.FAILED)
        assertEquals(R.string.friday_providers_status_selected, OnboardingProvidersLogic.statusLabelRes(cfg, activeId = "cfg-1"))
    }

    @Test
    fun `ready status maps to ready label when not selected`() {
        val cfg = config(id = "cfg-1", status = GatewayStatus.READY)
        assertEquals(R.string.friday_providers_status_ready, OnboardingProvidersLogic.statusLabelRes(cfg, activeId = "other-id"))
    }

    @Test
    fun `not tested status maps to not tested label`() {
        val cfg = config(id = "cfg-1", status = GatewayStatus.NOT_TESTED)
        assertEquals(R.string.friday_providers_status_not_tested, OnboardingProvidersLogic.statusLabelRes(cfg, activeId = null))
    }

    @Test
    fun `failed status maps to failed label when not selected`() {
        val cfg = config(id = "cfg-1", status = GatewayStatus.FAILED)
        assertEquals(R.string.friday_providers_status_failed, OnboardingProvidersLogic.statusLabelRes(cfg, activeId = null))
    }

    @Test
    fun `isSelected true only when activeId matches config id`() {
        val cfg = config(id = "cfg-1")
        assertTrue(OnboardingProvidersLogic.isSelected(cfg, activeId = "cfg-1"))
        assertFalse(OnboardingProvidersLogic.isSelected(cfg, activeId = "cfg-2"))
        assertFalse(OnboardingProvidersLogic.isSelected(cfg, activeId = null))
    }

    @Test
    fun `available catalog is exactly 8 entries in required order excluding local`() {
        val expected = listOf(
            GatewayProvider.GEMINI,
            GatewayProvider.OPENAI,
            GatewayProvider.ANTHROPIC,
            GatewayProvider.DEEPSEEK,
            GatewayProvider.OPENCLAW,
            GatewayProvider.HERMES,
            GatewayProvider.CUSTOM,
            GatewayProvider.FRIDAY,
        )
        assertEquals(expected, OnboardingProvidersLogic.availableCatalog())
        assertFalse(OnboardingProvidersLogic.availableCatalog().contains(GatewayProvider.LOCAL))
    }

    // availableDescription (design-spec §4.2, HTML:2045-2048 + fridayDesc 1273/1311):
    //   - catalog providers with a default model -> DefaultModel("<model>")
    //   - urlRequired catalog rows (OpenClaw/Hermes/Custom) -> OpenAiCompatible
    //   - disabled Friday placeholder -> ManagedFriday
    @Test
    fun `available description for catalog providers is DefaultModel with their default model`() {
        assertEquals(
            ProviderAvailableDesc.DefaultModel("gemini-2.0-flash"),
            OnboardingProvidersLogic.availableDescription(GatewayProvider.GEMINI),
        )
        assertEquals(
            ProviderAvailableDesc.DefaultModel("gpt-4o-mini"),
            OnboardingProvidersLogic.availableDescription(GatewayProvider.OPENAI),
        )
        assertEquals(
            ProviderAvailableDesc.DefaultModel("claude-sonnet-4-5"),
            OnboardingProvidersLogic.availableDescription(GatewayProvider.ANTHROPIC),
        )
        assertEquals(
            ProviderAvailableDesc.DefaultModel("deepseek-chat"),
            OnboardingProvidersLogic.availableDescription(GatewayProvider.DEEPSEEK),
        )
    }

    @Test
    fun `available description for urlRequired catalog rows is OpenAiCompatible`() {
        assertEquals(
            ProviderAvailableDesc.OpenAiCompatible,
            OnboardingProvidersLogic.availableDescription(GatewayProvider.OPENCLAW),
        )
        assertEquals(
            ProviderAvailableDesc.OpenAiCompatible,
            OnboardingProvidersLogic.availableDescription(GatewayProvider.HERMES),
        )
        assertEquals(
            ProviderAvailableDesc.OpenAiCompatible,
            OnboardingProvidersLogic.availableDescription(GatewayProvider.CUSTOM),
        )
    }

    @Test
    fun `available description for disabled Friday placeholder is ManagedFriday`() {
        assertEquals(
            ProviderAvailableDesc.ManagedFriday,
            OnboardingProvidersLogic.availableDescription(GatewayProvider.FRIDAY),
        )
    }

    @Test
    fun `available description is never returned for LOCAL because LOCAL is excluded from the catalog`() {
        // LOCAL is not part of the available catalog (filter excludes isLocal), so
        // availableDescription is never called with it in production. Documenting the
        // boundary: if it ever were, urlRequired=false + enabled=true would yield
        // DefaultModel("") — which is why LOCAL is correctly excluded upstream.
        assertEquals(
            ProviderAvailableDesc.DefaultModel(""),
            OnboardingProvidersLogic.availableDescription(GatewayProvider.LOCAL),
        )
        assertFalse(OnboardingProvidersLogic.availableCatalog().contains(GatewayProvider.LOCAL))
    }
}

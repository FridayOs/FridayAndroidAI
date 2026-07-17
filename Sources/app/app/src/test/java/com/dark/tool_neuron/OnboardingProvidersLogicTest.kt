package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.viewmodel.OnboardingProvidersLogic
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
}

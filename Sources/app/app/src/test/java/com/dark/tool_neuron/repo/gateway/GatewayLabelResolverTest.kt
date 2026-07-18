package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * FRI-574 phase 6 — pure resolver, no Android Context. Confirms label precedence
 * (custom label wins over provider displayName), status passthrough, and the
 * deleted/unknown-gateway fallback (label=null, labelRes=R.string.friday_unknown_provider).
 */
class GatewayLabelResolverTest {

    private fun gateway(
        id: String = "gw-1",
        provider: GatewayProvider = GatewayProvider.OPENAI,
        label: String = "",
        status: GatewayStatus = GatewayStatus.READY,
    ) = GatewayConfig(
        id = id,
        provider = provider,
        label = label,
        baseUrl = "",
        apiKey = "",
        model = "",
        createdAt = 0L,
        updatedAt = 0L,
        status = status,
    )

    @Test
    fun resolve_usesProviderDisplayName_whenLabelBlank() {
        val gateways = listOf(gateway(id = "gw-1", provider = GatewayProvider.ANTHROPIC, label = "", status = GatewayStatus.READY))
        val result = GatewayLabelResolver.resolve("gw-1", gateways)
        assertEquals("Anthropic", result.label)
        assertEquals(GatewayStatus.READY, result.status)
        assertNull(result.labelRes)
    }

    @Test
    fun resolve_usesCustomLabel_whenPresent() {
        val gateways = listOf(gateway(id = "gw-1", label = "My Work Key", status = GatewayStatus.FAILED))
        val result = GatewayLabelResolver.resolve("gw-1", gateways)
        assertEquals("My Work Key", result.label)
        assertEquals(GatewayStatus.FAILED, result.status)
    }

    @Test
    fun resolve_notTestedStatus_passesThrough() {
        val gateways = listOf(gateway(id = "gw-1", status = GatewayStatus.NOT_TESTED))
        val result = GatewayLabelResolver.resolve("gw-1", gateways)
        assertEquals(GatewayStatus.NOT_TESTED, result.status)
    }

    @Test
    fun resolve_deletedGateway_fallsBackToUnknownProvider() {
        val gateways = listOf(gateway(id = "gw-1"))
        val result = GatewayLabelResolver.resolve("gw-deleted", gateways)
        assertNull(result.label)
        assertNull(result.status)
        assertEquals(R.string.friday_unknown_provider, result.labelRes)
    }

    @Test
    fun resolve_emptyGatewayList_fallsBackToUnknownProvider() {
        val result = GatewayLabelResolver.resolve("gw-1", emptyList())
        assertNull(result.label)
        assertEquals(R.string.friday_unknown_provider, result.labelRes)
    }
}

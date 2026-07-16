package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayProviderRoleTest {

    @Test
    fun friday_is_disabled_and_roleless() {
        assertFalse(GatewayProvider.FRIDAY.enabled)
        assertTrue(GatewayProvider.FRIDAY.roles.isEmpty())
        assertFalse(GatewayProvider.configurable.contains(GatewayProvider.FRIDAY))
    }

    @Test
    fun brain_role_lists_every_enabled_provider() {
        // Every enabled provider can serve as a brain in this phase.
        val brains = GatewayProvider.forRole(GatewayRole.BRAIN)
        assertTrue(brains.contains(GatewayProvider.OPENAI))
        assertTrue(brains.contains(GatewayProvider.ANTHROPIC))
        assertTrue(brains.contains(GatewayProvider.LOCAL))
        assertFalse(brains.contains(GatewayProvider.FRIDAY))
    }

    @Test
    fun voice_role_excludes_brain_only_providers() {
        val voices = GatewayProvider.forRole(GatewayRole.VOICE)
        assertTrue(voices.contains(GatewayProvider.GEMINI))
        assertTrue(voices.contains(GatewayProvider.LOCAL))
        // OpenAI Realtime voice is deferred (seam kept, no production adapter) → brain-only for now.
        assertFalse(voices.contains(GatewayProvider.OPENAI))
        // Anthropic / DeepSeek / OpenClaw / Hermes / Custom are brain-only.
        assertFalse(voices.contains(GatewayProvider.ANTHROPIC))
        assertFalse(voices.contains(GatewayProvider.DEEPSEEK))
        assertFalse(voices.contains(GatewayProvider.HERMES))
    }

    @Test
    fun local_is_keyless_and_urlless() {
        assertFalse(GatewayProvider.LOCAL.needsKey)
        assertFalse(GatewayProvider.LOCAL.urlRequired)
        assertTrue(GatewayProvider.LOCAL.isLocal)
    }

    @Test
    fun url_required_providers_are_exactly_openclaw_hermes_custom() {
        val urlRequired = GatewayProvider.entries.filter { it.urlRequired }.toSet()
        assertEquals(
            setOf(GatewayProvider.OPENCLAW, GatewayProvider.HERMES, GatewayProvider.CUSTOM),
            urlRequired,
        )
    }

    @Test
    fun cloud_defaults_match_design() {
        assertEquals("gemini-2.0-flash", GatewayProvider.GEMINI.defaultModel)
        assertEquals("gpt-4o-mini", GatewayProvider.OPENAI.defaultModel)
        assertEquals("claude-sonnet-4-5", GatewayProvider.ANTHROPIC.defaultModel)
        assertEquals("deepseek-chat", GatewayProvider.DEEPSEEK.defaultModel)
    }
}

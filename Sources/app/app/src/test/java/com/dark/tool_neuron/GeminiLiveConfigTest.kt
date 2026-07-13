package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveSessionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// GeminiLiveConfig resolution proof: host derives from the user's GatewayConfig, model gets the models/ prefix,
// and the key rides the endpoint (direct-to-provider, never a FRIDAY proxy).
class GeminiLiveConfigTest {

    private fun gateway(baseUrl: String = "", model: String = "", apiKey: String = "sk-live"): GatewayConfig =
        GatewayConfig(
            id = "g1",
            provider = GatewayProvider.GEMINI,
            label = "Gemini",
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            createdAt = 0,
            updatedAt = 0,
        )

    @Test
    fun defaultsResolveToGeminiHostAndLiveModel() {
        val cfg = GeminiLiveConfig.from(gateway())
        assertEquals(GeminiLiveConfig.DEFAULT_HOST, cfg.baseHost)
        assertEquals(GeminiLiveConfig.DEFAULT_MODEL, cfg.model)
    }

    @Test
    fun wireModelGetsModelsPrefix() {
        val cfg = GeminiLiveConfig.from(gateway(model = "gemini-2.0-flash-live-001"))
        assertEquals("models/gemini-2.0-flash-live-001", cfg.wireModel)
    }

    @Test
    fun wireModelKeepsExistingPrefix() {
        val cfg = GeminiLiveConfig.from(gateway(model = "models/custom-live"))
        assertEquals("models/custom-live", cfg.wireModel)
    }

    @Test
    fun customBaseUrlParsesHostPortAndPath() {
        val cfg = GeminiLiveConfig.from(gateway(baseUrl = "https://my.proxy.example:8443/v1beta"))
        assertEquals("my.proxy.example", cfg.baseHost)
        assertEquals(8443, cfg.basePort)
        assertEquals("/v1beta", cfg.basePath)
        assertTrue(LiveSessionEngine.endpointPath(cfg).startsWith("/v1beta/ws/"))
    }

    @Test
    fun blankBaseUrlDefaultsToGeminiHostAndPort443() {
        val cfg = GeminiLiveConfig.from(gateway())
        assertEquals(GeminiLiveConfig.DEFAULT_HOST, cfg.baseHost)
        assertEquals(443, cfg.basePort)
        assertEquals("", cfg.basePath)
    }

    @Test
    fun insecureOrMalformedEndpointFallsBackToSecureDefault() {
        // http/ws would downgrade the socket (key/audio/transcript are TLS-only) — must not be honored.
        val http = GeminiLiveConfig.from(gateway(baseUrl = "http://downgrade.example"))
        assertEquals(GeminiLiveConfig.DEFAULT_HOST, http.baseHost)
        assertEquals(443, http.basePort)
        val junk = GeminiLiveConfig.from(gateway(baseUrl = "not a url"))
        assertEquals(GeminiLiveConfig.DEFAULT_HOST, junk.baseHost)
    }

    @Test
    fun defaultModelIsNotTheShutdownGemini2Model() {
        // gemini-2.0-flash-live-001 was shut down 2025-12-09 — the default must be the current live model.
        assertNotEquals("gemini-2.0-flash-live-001", GeminiLiveConfig.DEFAULT_MODEL)
        assertEquals("gemini-3.1-flash-live-preview", GeminiLiveConfig.DEFAULT_MODEL)
    }

    @Test
    fun endpointPathCarriesBidiAndKey() {
        val cfg = GeminiLiveConfig.from(gateway(apiKey = "sk-live"))
        val path = LiveSessionEngine.endpointPath(cfg)
        assertTrue(path.startsWith("/ws/"))
        assertTrue(path.contains("BidiGenerateContent"))
        assertTrue(path.endsWith("key=sk-live"))
    }

    @Test
    fun keyIsTrimmed() {
        val cfg = GeminiLiveConfig.from(gateway(apiKey = "  sk-live  "))
        assertEquals("sk-live", cfg.apiKey)
    }

    @Test
    fun bargeInDefaultsOn() {
        assertTrue(GeminiLiveConfig.from(gateway()).bargeIn)
        assertFalse(GeminiLiveConfig.from(gateway(), bargeIn = false).bargeIn)
    }
}

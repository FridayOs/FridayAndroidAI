package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayValidationTest {

    @Test
    fun key_required_provider_rejects_blank_key() {
        val r = GatewayValidation.validate(GatewayProvider.OPENAI, apiKey = "  ", model = "gpt-4o-mini", baseUrl = "")
        assertEquals(GatewayValidation.Result.Invalid(GatewayValidation.Reason.MISSING_KEY), r)
    }

    @Test
    fun default_model_satisfies_model_requirement() {
        // Blank model falls back to the provider default, so it is valid.
        assertTrue(GatewayValidation.isValid(GatewayProvider.OPENAI, apiKey = "sk-x", model = "", baseUrl = ""))
    }

    @Test
    fun custom_requires_model_when_provider_has_no_default() {
        val r = GatewayValidation.validate(GatewayProvider.CUSTOM, apiKey = "k", model = "", baseUrl = "https://x.test")
        assertEquals(GatewayValidation.Result.Invalid(GatewayValidation.Reason.MISSING_MODEL), r)
    }

    @Test
    fun url_required_providers_reject_blank_url() {
        listOf(GatewayProvider.OPENCLAW, GatewayProvider.HERMES, GatewayProvider.CUSTOM).forEach { p ->
            val r = GatewayValidation.validate(p, apiKey = "k", model = "m", baseUrl = "")
            assertEquals("$p should require url", GatewayValidation.Result.Invalid(GatewayValidation.Reason.MISSING_URL), r)
        }
    }

    @Test
    fun bad_url_scheme_rejected() {
        val r = GatewayValidation.validate(GatewayProvider.CUSTOM, apiKey = "k", model = "m", baseUrl = "ftp://x")
        assertEquals(GatewayValidation.Result.Invalid(GatewayValidation.Reason.BAD_URL), r)
    }

    @Test
    fun optional_url_when_present_must_be_valid() {
        // Gemini's URL is optional, but a provided malformed one is still rejected.
        val r = GatewayValidation.validate(GatewayProvider.GEMINI, apiKey = "k", model = "m", baseUrl = "notaurl")
        assertEquals(GatewayValidation.Result.Invalid(GatewayValidation.Reason.BAD_URL), r)
    }

    @Test
    fun http_and_https_both_accepted() {
        assertTrue(GatewayValidation.isValid(GatewayProvider.CUSTOM, "k", "m", "http://192.168.1.9:8080"))
        assertTrue(GatewayValidation.isValid(GatewayProvider.CUSTOM, "k", "m", "https://api.example.test/v1"))
    }

    @Test
    fun local_provider_needs_no_key_or_url() {
        assertTrue(GatewayValidation.isValid(GatewayProvider.LOCAL, apiKey = "", model = "", baseUrl = ""))
    }

    @Test
    fun fully_valid_cloud_provider_passes() {
        assertTrue(GatewayValidation.isValid(GatewayProvider.ANTHROPIC, "sk-ant", "claude-sonnet-4-5", ""))
        assertFalse(GatewayValidation.isValid(GatewayProvider.ANTHROPIC, "", "claude-sonnet-4-5", ""))
    }
}

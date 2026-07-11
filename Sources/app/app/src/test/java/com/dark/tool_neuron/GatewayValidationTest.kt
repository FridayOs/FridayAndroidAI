package com.dark.tool_neuron.model.gateway

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

    // URL parser hardening — the old "^https?://.+" regex accepted anything past the scheme.
    @Test
    fun urlParser_rejects_schemeOnly() {
        assertFalse(GatewayValidation.isWellFormedHttpUrl("https://"))
        assertFalse(GatewayValidation.isWellFormedHttpUrl("http://"))
    }

    @Test
    fun urlParser_rejects_missingScheme() {
        assertFalse(GatewayValidation.isWellFormedHttpUrl("api.openai.com"))
    }

    @Test
    fun urlParser_rejects_nonHttpSchemes() {
        assertFalse(GatewayValidation.isWellFormedHttpUrl("ftp://api.example.test"))
        assertFalse(GatewayValidation.isWellFormedHttpUrl("file:///etc/passwd"))
        assertFalse(GatewayValidation.isWellFormedHttpUrl("javascript:alert(1)"))
    }

    @Test
    fun urlParser_rejectsBlankAndWhitespace() {
        assertFalse(GatewayValidation.isWellFormedHttpUrl(""))
        assertFalse(GatewayValidation.isWellFormedHttpUrl("   "))
    }

    @Test
    fun urlParser_acceptsLocalhost() {
        assertTrue(GatewayValidation.isWellFormedHttpUrl("http://localhost:11434"))
    }

    @Test
    fun urlParser_acceptsLiteralIpv4() {
        assertTrue(GatewayValidation.isWellFormedHttpUrl("http://127.0.0.1:8080"))
        assertTrue(GatewayValidation.isWellFormedHttpUrl("https://10.0.0.5"))
    }

    @Test
    fun urlParser_acceptsDottedHost() {
        assertTrue(GatewayValidation.isWellFormedHttpUrl("https://api.example.test/v1"))
        assertTrue(GatewayValidation.isWellFormedHttpUrl("https://generativelanguage.googleapis.com/v1beta"))
    }
}
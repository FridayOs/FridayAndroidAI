package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.GatewayErrorSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayErrorSanitizerTest {

    @Test
    fun blank_input_returns_generic_message() {
        assertEquals("Connection failed", GatewayErrorSanitizer.sanitize(null))
        assertEquals("Connection failed", GatewayErrorSanitizer.sanitize("   "))
    }

    @Test
    fun bearer_token_is_redacted() {
        val out = GatewayErrorSanitizer.sanitize("Authorization: Bearer sk-abc123SECRET rejected")
        assertFalse(out.contains("sk-abc123SECRET"))
        assertTrue(out.contains("***"))
    }

    @Test
    fun gemini_query_key_is_redacted() {
        val out = GatewayErrorSanitizer.sanitize("GET https://host/v1beta/models/x:stream?alt=sse&key=AIzaSyREALKEY failed")
        assertFalse(out.contains("AIzaSyREALKEY"))
        assertTrue(out.contains("key=***"))
    }

    @Test
    fun x_api_key_header_is_redacted() {
        val out = GatewayErrorSanitizer.sanitize("x-api-key: sk-ant-TOPSECRET invalid")
        assertFalse(out.contains("sk-ant-TOPSECRET"))
    }

    @Test
    fun explicit_key_argument_is_scrubbed() {
        val key = "myliteralkey-42"
        val out = GatewayErrorSanitizer.sanitize("provider said $key is bad", key = key)
        assertFalse(out.contains(key))
    }

    @Test
    fun output_is_capped() {
        val long = "x".repeat(1000)
        assertTrue(GatewayErrorSanitizer.sanitize(long).length <= 201)
    }

    @Test
    fun http_status_maps_to_stable_category() {
        assertTrue(GatewayErrorSanitizer.forHttpStatus(401, null).contains("Auth rejected"))
        assertTrue(GatewayErrorSanitizer.forHttpStatus(404, null).contains("not found"))
        assertTrue(GatewayErrorSanitizer.forHttpStatus(429, null).contains("Rate limited"))
        assertTrue(GatewayErrorSanitizer.forHttpStatus(503, null).contains("Provider error"))
    }

    @Test
    fun http_status_body_is_also_redacted() {
        val out = GatewayErrorSanitizer.forHttpStatus(401, "{\"api_key\":\"sk-LEAK\"}")
        assertFalse(out.contains("sk-LEAK"))
    }
}

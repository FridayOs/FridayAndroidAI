package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.GeminiTtsSynthesizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Voice Gateway TTS POSTs the api key direct to the user's Gemini host; a provider error that echoes the
// key must be scrubbed before it could surface. Pure error-path proof, no network (mirrors DirectGatewayClientErrorTest).
class GeminiTtsSynthesizerErrorTest {

    @Test
    fun httpErrorEchoingKey_isSanitized() {
        val sanitized = GeminiTtsSynthesizer.sanitizeError(
            "HTTP 400: {\"error\":\"bad request\"} for key=sk-secret",
            key = "sk-secret",
        )
        assertFalse("sanitized TTS error must not leak the api key", sanitized.contains("sk-secret"))
        assertTrue(sanitized.isNotBlank())
    }

    @Test
    fun nullBodyStillProducesNonBlankMessage() {
        assertTrue(GeminiTtsSynthesizer.sanitizeError(null, key = "sk-secret").isNotBlank())
    }
}

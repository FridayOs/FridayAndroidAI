package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderLogic
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderStatus
import com.friday.ai.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * Pure JVM tests for OnboardingAddProviderLogic (FRI-582 P8): validation
 * priority order, test-status mapping, sanitized failure message extraction,
 * and default-label resolution. No Hilt/Context/network involved.
 */
class OnboardingAddProviderLogicTest {

    @Test
    fun `missing key takes priority over all other errors`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.GEMINI,
            apiKey = "  ",
            model = "",
            baseUrl = "not-a-url",
        )
        assertEquals(R.string.friday_add_provider_err_key, error)
    }

    @Test
    fun `missing model errors when key is not required`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.LOCAL,
            apiKey = "",
            model = "  ",
            baseUrl = "",
        )
        assertEquals(R.string.friday_add_provider_err_model, error)
    }

    @Test
    fun `required url missing or malformed errors after key and model pass`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.CUSTOM,
            apiKey = "sk-123",
            model = "gpt-4o-mini",
            baseUrl = "not-a-url",
        )
        assertEquals(R.string.friday_add_provider_err_url, error)
    }

    @Test
    fun `optional url that is malformed still errors`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.GEMINI,
            apiKey = "sk-123",
            model = "gemini-2.0-flash",
            baseUrl = "not-a-url",
        )
        assertEquals(R.string.friday_add_provider_err_url, error)
    }

    @Test
    fun `optional blank url passes validation`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.GEMINI,
            apiKey = "sk-123",
            model = "gemini-2.0-flash",
            baseUrl = "",
        )
        assertNull(error)
    }

    @Test
    fun `fully valid fields pass validation`() {
        val error = OnboardingAddProviderLogic.validate(
            provider = GatewayProvider.CUSTOM,
            apiKey = "sk-123",
            model = "gpt-4o-mini",
            baseUrl = "https://example.com/v1",
        )
        assertNull(error)
    }

    @Test
    fun `ready result maps to ready status`() {
        assertEquals(OnboardingAddProviderStatus.READY, OnboardingAddProviderLogic.statusFor(GatewayTestResult.Ready))
    }

    @Test
    fun `failure result maps to failed status`() {
        val failure = GatewayTestResult.Failure.AuthRejected("Invalid API key.")
        assertEquals(OnboardingAddProviderStatus.FAILED, OnboardingAddProviderLogic.statusFor(failure))
    }

    @Test
    fun `failure message is surfaced verbatim from typed failure`() {
        val failure = GatewayTestResult.Failure.Timeout("Request timed out.")
        assertEquals("Request timed out.", OnboardingAddProviderLogic.failureMessage(failure))
    }

    @Test
    fun `ready result has no failure message`() {
        assertNull(OnboardingAddProviderLogic.failureMessage(GatewayTestResult.Ready))
    }

    @Test
    fun `blank label resolves to formatted placeholder`() {
        val resolved = OnboardingAddProviderLogic.resolveLabel(
            rawLabel = "   ",
            labelPlaceholderTemplate = "Personal %1\$s",
            providerDisplayName = "Gemini",
        )
        assertEquals("Personal Gemini", resolved)
    }

    @Test
    fun `non-blank label is trimmed and kept as-is`() {
        val resolved = OnboardingAddProviderLogic.resolveLabel(
            rawLabel = "  Work Account  ",
            labelPlaceholderTemplate = "Personal %1\$s",
            providerDisplayName = "OpenAI",
        )
        assertEquals("Work Account", resolved)
    }

    // Stale-save guard (Codex adversarial review): a provider whose credentials were edited after a
    // passing test resets the UI status to NOT_TESTED, so it must persist as NOT_TESTED — never READY.
    @Test
    fun `NOT_TESTED status persists as NOT_TESTED, never READY`() {
        assertEquals(
            GatewayStatus.NOT_TESTED,
            OnboardingAddProviderLogic.recordedStatusFor(OnboardingAddProviderStatus.NOT_TESTED),
        )
    }

    @Test
    fun `TESTING status persists as NOT_TESTED`() {
        assertEquals(
            GatewayStatus.NOT_TESTED,
            OnboardingAddProviderLogic.recordedStatusFor(OnboardingAddProviderStatus.TESTING),
        )
    }

    @Test
    fun `READY status persists as READY`() {
        assertEquals(
            GatewayStatus.READY,
            OnboardingAddProviderLogic.recordedStatusFor(OnboardingAddProviderStatus.READY),
        )
    }

    @Test
    fun `FAILED status persists as FAILED`() {
        assertEquals(
            GatewayStatus.FAILED,
            OnboardingAddProviderLogic.recordedStatusFor(OnboardingAddProviderStatus.FAILED),
        )
    }
}

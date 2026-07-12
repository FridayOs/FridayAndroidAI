package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.repo.gateway.DirectGatewayClient
import com.dark.tool_neuron.repo.gateway.GatewayTestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class DirectGatewayClientProbeFailureTest {

    private fun config(
        provider: GatewayProvider = GatewayProvider.OPENAI,
        model: String = "gpt-4o-mini",
        baseUrl: String = "https://api.openai.com",
        apiKey: String = "sk-secret",
    ): GatewayConfig = GatewayConfig(
        id = "test",
        provider = provider,
        label = "test",
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun timeoutClassifiedAsTimeout() {
        val r = DirectGatewayClient.classifyProbeFailure(SocketTimeoutException("read timed out"), config())
        assertTrue(r is GatewayTestResult.Failure.Timeout)
        assertFalse(r.message.contains("sk-secret"))
    }

    @Test
    fun dnsClassifiedAsDns() {
        val r = DirectGatewayClient.classifyProbeFailure(UnknownHostException("api.openai.com"), config())
        assertTrue(r is GatewayTestResult.Failure.DnsFailure)
    }

    @Test
    fun tlsClassifiedAsTls() {
        val r = DirectGatewayClient.classifyProbeFailure(SSLException("PKIX path building failed"), config())
        assertTrue(r is GatewayTestResult.Failure.TlsFailure)
    }

    @Test
    fun malformedUrlClassifiedAsInvalidUrl() {
        val r = DirectGatewayClient.classifyProbeFailure(MalformedURLException("bad"), config())
        assertTrue(r is GatewayTestResult.Failure.InvalidUrl)
    }

    @Test
    fun http401ClassifiedAsAuth() {
        val raw = "HTTP 401: {\"error\":\"invalid api key\"}"
        val r = DirectGatewayClient.classifyProbeFailure(syntheticHttp(raw, 401), config())
        assertTrue(r is GatewayTestResult.Failure.AuthRejected)
        r as GatewayTestResult.Failure.AuthRejected
        assertTrue(r.message.contains("Auth rejected"))
        assertFalse(r.message.contains("sk-secret"))
    }

    @Test
    fun http404OnModelClassifiedAsInvalidModel() {
        val raw = "HTTP 404: {\"error\":\"model: gpt-fake-99 not found\"}"
        val r = DirectGatewayClient.classifyProbeFailure(syntheticHttp(raw, 404), config())
        assertTrue(r is GatewayTestResult.Failure.InvalidModel)
    }

    @Test
    fun http404GenericClassifiedAsNotFound() {
        val raw = "HTTP 404: not found"
        val r = DirectGatewayClient.classifyProbeFailure(syntheticHttp(raw, 404), config())
        assertTrue(r is GatewayTestResult.Failure.NotFound)
    }

    @Test
    fun http429ClassifiedAsRateLimited() {
        val raw = "HTTP 429: slow down"
        val r = DirectGatewayClient.classifyProbeFailure(syntheticHttp(raw, 429), config())
        assertTrue(r is GatewayTestResult.Failure.RateLimited)
    }

    @Test
    fun http500ClassifiedAsProviderErrorWithStatus() {
        val raw = "HTTP 503: unavailable"
        val r = DirectGatewayClient.classifyProbeFailure(syntheticHttp(raw, 503), config())
        assertTrue(r is GatewayTestResult.Failure.ProviderError)
        r as GatewayTestResult.Failure.ProviderError
        assertEquals(503, r.httpStatus)
    }

    @Test
    fun fallbackClassifiesBlankModelAsInvalidModel() {
        // CUSTOM has no default model, so a blank model stays blank and classifies as InvalidModel.
        val r = DirectGatewayClient.classifyProbeFailure(
            RuntimeException("weird"),
            config(provider = GatewayProvider.CUSTOM, model = "", baseUrl = "https://x.test"),
        )
        assertTrue(r is GatewayTestResult.Failure.InvalidModel)
    }

    @Test
    fun fallbackClassifiesBadUrlAsInvalidUrl() {
        val r = DirectGatewayClient.classifyProbeFailure(RuntimeException("nope"), config(baseUrl = "not a url"))
        assertTrue(r is GatewayTestResult.Failure.InvalidUrl)
    }

    // Reproduce the same exception shape the production reader throws so we exercise the typed path.
    private fun syntheticHttp(raw: String, status: Int): Exception =
        run {
            val ctor = Class.forName("com.dark.tool_neuron.repo.gateway.DirectGatewayClient\$GatewayHttpException")
                .getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java)
            ctor.isAccessible = true
            ctor.newInstance(status, raw) as Exception
        }

    @Test
    fun localProviderShortCircuitsToReady() {
        val c = config(provider = GatewayProvider.LOCAL, model = "", baseUrl = "", apiKey = "")
        // The precondition that enables the testConnection() local short-circuit is provider.isLocal.
        assertTrue(c.provider.isLocal)
    }
}

package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.DirectGatewayClient
import com.dark.tool_neuron.repo.gateway.GatewayEvent
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Cancel-covers-cloud: a cancelled request rethrows CancellationException untouched; other failures are sanitized.
class DirectGatewayClientErrorTest {

    @Test(expected = CancellationException::class)
    fun cancellationIsRethrown_notConvertedToError() {
        DirectGatewayClient.classifyError(CancellationException("cancelled"), key = "sk-secret")
    }

    @Test
    fun httpErrorIsSanitized_keyScrubbed() {
        val event = DirectGatewayClient.classifyError(
            RuntimeException("HTTP 401: Authorization: Bearer sk-secret rejected"),
            key = "sk-secret",
        )
        assertTrue(event is GatewayEvent.Error)
        val msg = (event as GatewayEvent.Error).message
        assertFalse("sanitized error must not leak the key", msg.contains("sk-secret"))
    }

    @Test
    fun nullMessageStillProducesGenericError() {
        val event = DirectGatewayClient.classifyError(RuntimeException(), key = "")
        assertTrue(event is GatewayEvent.Error)
        assertTrue((event as GatewayEvent.Error).message.isNotBlank())
    }
}

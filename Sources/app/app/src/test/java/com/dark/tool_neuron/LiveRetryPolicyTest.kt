package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveRetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Retry taxonomy: only transient failures loop, bounded exponential backoff, hard config/auth errors surface at once.
class LiveRetryPolicyTest {

    @Test
    fun authNeverRetries() {
        assertFalse(LiveRetryPolicy.retryable(LiveErrorKind.AUTH))
    }

    @Test
    fun invalidModelNeverRetries() {
        assertFalse(LiveRetryPolicy.retryable(LiveErrorKind.INVALID_MODEL))
    }

    @Test
    fun permissionNeverRetries() {
        assertFalse(LiveRetryPolicy.retryable(LiveErrorKind.PERMISSION))
    }

    @Test
    fun quotaNeverLoops() {
        // Rate limit is not a fast-retry candidate — looping would hammer the quota.
        assertFalse(LiveRetryPolicy.retryable(LiveErrorKind.QUOTA))
    }

    @Test
    fun networkTimeoutRemoteCloseRetry() {
        assertTrue(LiveRetryPolicy.retryable(LiveErrorKind.NETWORK))
        assertTrue(LiveRetryPolicy.retryable(LiveErrorKind.TIMEOUT))
        assertTrue(LiveRetryPolicy.retryable(LiveErrorKind.REMOTE_CLOSE))
    }

    @Test
    fun backoffIsBoundedAndMonotonic() {
        val b0 = LiveRetryPolicy.backoffMillis(0)!!
        val b1 = LiveRetryPolicy.backoffMillis(1)!!
        val b2 = LiveRetryPolicy.backoffMillis(2)!!
        assertTrue("backoff grows", b1 > b0 && b2 > b1)
    }

    @Test
    fun backoffStopsAfterMaxAttempts() {
        // Past the attempt ceiling the policy returns null — the session gives up instead of retrying forever.
        assertNull(LiveRetryPolicy.backoffMillis(LiveRetryPolicy.MAX_ATTEMPTS))
        assertNull(LiveRetryPolicy.backoffMillis(LiveRetryPolicy.MAX_ATTEMPTS + 5))
    }

    @Test
    fun firstBackoffIsAtLeastBaseDelay() {
        assertTrue(LiveRetryPolicy.backoffMillis(0)!! >= LiveRetryPolicy.BASE_DELAY_MS)
    }
}

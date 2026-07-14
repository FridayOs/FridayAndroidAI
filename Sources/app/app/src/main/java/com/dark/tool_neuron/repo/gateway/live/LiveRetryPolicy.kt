package com.dark.tool_neuron.repo.gateway.live

// Bounded exponential backoff for transient live-session failures; a bad credential/model never spins a reconnect loop.
object LiveRetryPolicy {

    const val MAX_ATTEMPTS = 4
    const val BASE_DELAY_MS = 500L
    private const val MAX_DELAY_MS = 8000L

    fun retryable(kind: LiveErrorKind): Boolean = kind.transient

    // attempt is 0-based: 500ms, 1s, 2s, 4s (capped at 8s). Returns null once the budget is exhausted.
    fun backoffMillis(attempt: Int): Long? {
        if (attempt < 0 || attempt >= MAX_ATTEMPTS) return null
        val shifted = BASE_DELAY_MS shl attempt
        return shifted.coerceAtMost(MAX_DELAY_MS)
    }
}

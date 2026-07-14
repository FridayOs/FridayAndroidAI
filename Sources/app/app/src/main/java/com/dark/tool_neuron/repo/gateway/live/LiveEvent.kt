package com.dark.tool_neuron.repo.gateway.live

// Failure taxonomy: transient NETWORK/TIMEOUT/REMOTE_CLOSE may retry bounded; everything else surfaces immediately.
enum class LiveErrorKind {
    AUTH,
    INVALID_MODEL,
    QUOTA,
    NETWORK,
    TIMEOUT,
    PROTOCOL,
    PERMISSION,
    AUDIO,
    REMOTE_CLOSE;

    val transient: Boolean
        get() = this == NETWORK || this == TIMEOUT || this == REMOTE_CLOSE
}

// One displayable, secret-free event stream for the whole session. Audio deltas carry 24kHz PCM16 bytes in order.
sealed interface LiveEvent {
    data class State(val state: LiveSessionState) : LiveEvent

    // Ordered PCM16 mono 24kHz playback chunk from the model turn.
    data class AudioDelta(val pcm: ByteArray, val seq: Long) : LiveEvent {
        override fun equals(other: Any?): Boolean =
            other is AudioDelta && seq == other.seq && pcm.contentEquals(other.pcm)

        override fun hashCode(): Int = 31 * seq.hashCode() + pcm.contentHashCode()
    }

    data class OutputTranscript(val text: String) : LiveEvent
    data class InputTranscript(val text: String) : LiveEvent

    // Model finished this turn; caller may start the next.
    data object TurnComplete : LiveEvent

    // A client barge-in interrupted the model — drop any queued playback for the current turn.
    data object Interrupted : LiveEvent

    data class Error(val kind: LiveErrorKind, val message: String) : LiveEvent
}

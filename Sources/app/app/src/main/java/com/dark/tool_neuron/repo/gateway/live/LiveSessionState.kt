package com.dark.tool_neuron.repo.gateway.live

// Full session lifecycle the UI can render; ERROR carries a displayable, secret-free reason and never fakes CONNECTED.
enum class LiveSessionState {
    IDLE,
    CONNECTING,
    CONFIGURED,
    LISTENING,
    STREAMING,
    RECONNECTING,
    CLOSING,
    CLOSED,
    ERROR,
}

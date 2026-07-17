package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.event.EventStateStore

// FRI-555 (B4): in-memory EventStateStore test double shared by NonceStoreTest, DedupeStoreTest,
// InboundEventVerifierTest, InboundEventPolicyTest, and EventGatewayTest. Backed by a plain
// MutableMap so constructing a NEW NonceStore/DedupeStore over the SAME FakeEventStateStore
// instance simulates process death/restart while keeping persisted bookkeeping.
class FakeEventStateStore : EventStateStore {
    private val map = mutableMapOf<String, String>()

    override fun read(key: String): String? = map[key]

    override fun write(key: String, value: String) {
        map[key] = value
    }
}

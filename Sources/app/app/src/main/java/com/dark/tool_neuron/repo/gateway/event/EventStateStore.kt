package com.dark.tool_neuron.repo.gateway.event

// FRI-555 (B4): injectable persistence seam for replay/dedupe state. Kept minimal (read/write a
// single opaque string per key) so NonceStore/DedupeStore stay pure-JVM-testable against an
// in-memory fake while production wires the encrypted local vault (PrefsEventStateStore). Never
// holds payload content — only bookkeeping lines (timestamp + nonce/eventId).
interface EventStateStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

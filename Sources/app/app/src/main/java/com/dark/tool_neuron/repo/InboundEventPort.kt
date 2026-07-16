package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.friday.InboundEvent

// Producer-agnostic seam for inbound events. FCM is one producer today; FRI-555 verified pushes and
// local schedulers reuse the same entry point. publish() is safe to call from any thread.
interface InboundEventPort {
    fun publish(event: InboundEvent)
}

package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.data.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555: read-only consent seam. FRI-583 owns the Settings UI and writes this key; EventGateway
// only reads it as a hard gate before any parse/verify work happens. Fail-closed default: if the
// user never opted in (key absent), inbound events from agent runtimes are OFF.
fun interface InboundEventConsent {
    fun inboundEnabled(): Boolean
}

@Singleton
class PrefsInboundEventConsent @Inject constructor(
    private val prefs: AppPreferences,
) : InboundEventConsent {
    override fun inboundEnabled(): Boolean = prefs.getBoolean(KEY_INBOUND_EVENTS_ENABLED, default = false)

    companion object {
        // FRI-583 contract key: boolean toggle for "allow inbound agent events".
        const val KEY_INBOUND_EVENTS_ENABLED = "fri555.inbound_events_enabled"
    }
}

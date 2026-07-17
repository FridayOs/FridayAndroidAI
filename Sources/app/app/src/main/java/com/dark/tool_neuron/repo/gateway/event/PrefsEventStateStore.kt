package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.data.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555 (B4): EventStateStore backed by the encrypted local vault (AppPreferences). Replay/
// dedupe bookkeeping only -- never payload content -- so process death/restart doesn't reopen a
// replay/duplicate window.
@Singleton
class PrefsEventStateStore @Inject constructor(
    private val prefs: AppPreferences,
) : EventStateStore {
    override fun read(key: String): String? = prefs.getString(key).ifEmpty { null }

    override fun write(key: String, value: String) {
        prefs.putString(key, value)
    }
}

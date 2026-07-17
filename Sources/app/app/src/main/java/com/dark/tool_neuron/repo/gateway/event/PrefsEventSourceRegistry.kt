package com.dark.tool_neuron.repo.gateway.event

import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.friday.EventSourceKind
import javax.inject.Inject
import javax.inject.Singleton

// FRI-555: AppPreferences-backed trust registry. Trust material (per-source HMAC key, kind,
// enabled flag) lives only in the encrypted local vault; it is never sent to the backend and
// FRI-583's Settings UI is the intended writer of these keys. An empty/unpopulated registry
// (no source ids recorded) rejects every lookup — fail-closed by construction, no code path
// defaults a source to trusted.
@Singleton
class PrefsEventSourceRegistry @Inject constructor(
    private val prefs: AppPreferences,
) : EventSourceRegistry {

    override fun trustFor(sourceId: String): EventSourceTrust? {
        if (sourceId.isBlank()) return null
        val knownIds = prefs.getString(KEY_SOURCE_IDS)
            .split(ID_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (sourceId !in knownIds) return null

        val kindRaw = prefs.getString(kindKey(sourceId))
        val kind = runCatching { EventSourceKind.valueOf(kindRaw) }.getOrNull() ?: return null
        val hmacKey = prefs.getBytes(hmacKeyKey(sourceId))
        if (hmacKey == null || hmacKey.isEmpty()) return null
        val enabled = prefs.getBoolean(enabledKey(sourceId), default = false)

        return EventSourceTrust(sourceId = sourceId, kind = kind, hmacKey = hmacKey, enabled = enabled)
    }

    companion object {
        // FRI-583 contract: comma-separated list of registered source ids, plus one {kind, hmac
        // key, enabled} triple per id. Kept here (not AppPreferences.kt) since these are gateway-
        // specific keys, not general app settings.
        const val KEY_SOURCE_IDS = "fri555.event_sources"
        private const val ID_DELIMITER = ","

        fun kindKey(sourceId: String) = "fri555.event_source.$sourceId.kind"
        fun hmacKeyKey(sourceId: String) = "fri555.event_source.$sourceId.hmac_key"
        fun enabledKey(sourceId: String) = "fri555.event_source.$sourceId.enabled"
    }
}

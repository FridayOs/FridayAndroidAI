package com.dark.tool_neuron.data.firebase

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CloudPolicy(
    val version: Long,
    val flags: Map<String, Boolean>,
)

// Remote Config feature-flag / policy read. Non-sensitive app policy only —
// never used to gate the local vault, auth, or any security decision (those stay
// native in PolicyEngine). Missing config -> defaults, never a crash.
@Singleton
class FirebasePolicy @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val defaults = mapOf<String, Any>(
        KEY_POLICY_VERSION to 1L,
        KEY_WEB_SEARCH_ENABLED to true,
        KEY_IMAGE_GEN_ENABLED to true,
        KEY_SERVER_MODE_ENABLED to true,
    )

    suspend fun fetch(): CloudPolicy {
        if (!FirebaseCloud.ready(context)) {
            return CloudPolicy(version = 1L, flags = emptyMap())
        }
        val rc = FirebaseRemoteConfig.getInstance()
        rc.setConfigSettingsAsync(
            remoteConfigSettings { minimumFetchIntervalInSeconds = MIN_FETCH_INTERVAL_S },
        )
        rc.setDefaultsAsync(defaults).await()
        runCatching { rc.fetchAndActivate().await() }

        val version = rc.getLong(KEY_POLICY_VERSION)
        val flags = FLAG_KEYS.associateWith { rc.getBoolean(it) }
        return CloudPolicy(version = version, flags = flags)
    }

    suspend fun refreshPolicyVersion(): Long = runCatching { fetch().version }.getOrDefault(1L)

    companion object {
        const val KEY_POLICY_VERSION = "policy_version"
        const val KEY_WEB_SEARCH_ENABLED = "web_search_enabled"
        const val KEY_IMAGE_GEN_ENABLED = "image_gen_enabled"
        const val KEY_SERVER_MODE_ENABLED = "server_mode_enabled"

        private val FLAG_KEYS = listOf(
            KEY_WEB_SEARCH_ENABLED,
            KEY_IMAGE_GEN_ENABLED,
            KEY_SERVER_MODE_ENABLED,
        )

        private const val MIN_FETCH_INTERVAL_S = 3600L
    }
}

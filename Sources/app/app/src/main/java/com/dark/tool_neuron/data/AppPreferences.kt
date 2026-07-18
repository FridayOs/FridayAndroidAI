package com.dark.tool_neuron.data

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {

    private val storage = HexStorage()
    private val basePath: String

    init {
        val dir = context.filesDir.resolve("app_prefs")
        dir.mkdirs()
        basePath = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted app_prefs vault")

        // userKey is NOT wiped: hxs.cpp holds a GlobalRef to this ByteArray for every encrypt/decrypt callback. Zeroing it would make every AEAD op use a zero key.

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_KEY, HexStorage.WIRE_BYTES)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        findRecord(key)?.getBool(TAG_VALUE_BOOL, default) ?: default

    fun putBoolean(key: String, value: Boolean) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBool(TAG_VALUE_BOOL, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBool(TAG_VALUE_BOOL, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun getString(key: String, default: String = ""): String =
        findRecord(key)?.getString(TAG_VALUE_STRING, default) ?: default

    fun putString(key: String, value: String) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putString(TAG_VALUE_STRING, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putString(TAG_VALUE_STRING, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun getBytes(key: String): ByteArray? =
        findRecord(key)?.getBytes(TAG_VALUE_BYTES)

    fun putBytes(key: String, value: ByteArray) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBytes(TAG_VALUE_BYTES, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBytes(TAG_VALUE_BYTES, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun deleteKey(key: String) {
        val existing = findRecord(key) ?: return
        storage.delete(COLLECTION, existing.id)
        storage.flushAll()
    }

    private fun findRecord(key: String): HxsRecord? =
        storage.queryString(COLLECTION, TAG_KEY, key).firstOrNull()

    var onboardingComplete: Boolean
        get() = getBoolean(KEY_ONBOARDING_COMPLETE)
        set(value) = putBoolean(KEY_ONBOARDING_COMPLETE, value)

    var tcAccepted: Boolean
        get() = getBoolean(KEY_TC_ACCEPTED)
        set(value) = putBoolean(KEY_TC_ACCEPTED, value)

    var setupDone: Boolean
        get() = getBoolean(KEY_SETUP_DONE)
        set(value) = putBoolean(KEY_SETUP_DONE, value)

    var securitySetupDone: Boolean
        get() = getBoolean(KEY_SECURITY_SETUP_DONE)
        set(value) = putBoolean(KEY_SECURITY_SETUP_DONE, value)

    var modelSetupDone: Boolean
        get() = getBoolean(KEY_MODEL_SETUP_DONE)
        set(value) = putBoolean(KEY_MODEL_SETUP_DONE, value)

    /** Chosen model-setup path ("gateway"|"local"|"skip"), FRI-582 QA round-2 B1. */
    var modelPath: String?
        get() = getString(KEY_MODEL_PATH).ifBlank { null }
        set(value) {
            if (value.isNullOrBlank()) deleteKey(KEY_MODEL_PATH) else putString(KEY_MODEL_PATH, value)
        }

    /** Chosen local model pack id (only set when [modelPath] == "local"), FRI-582 QA round-2 B1. */
    var modelPack: String?
        get() = getString(KEY_MODEL_PACK).ifBlank { null }
        set(value) {
            if (value.isNullOrBlank()) deleteKey(KEY_MODEL_PACK) else putString(KEY_MODEL_PACK, value)
        }

    var tourDone: Boolean
        get() = getBoolean(KEY_TOUR_DONE)
        set(value) = putBoolean(KEY_TOUR_DONE, value)

    var themeSetupDone: Boolean
        get() = getBoolean(KEY_THEME_SETUP_DONE)
        set(value) = putBoolean(KEY_THEME_SETUP_DONE, value)

    var providerStepDone: Boolean
        get() = getBoolean(KEY_PROVIDER_STEP_DONE)
        set(value) = putBoolean(KEY_PROVIDER_STEP_DONE, value)

    var guideShown: Boolean
        get() = getBoolean(KEY_GUIDE_SHOWN)
        set(value) = putBoolean(KEY_GUIDE_SHOWN, value)

    var rootWarningShown: Boolean
        get() = getBoolean(KEY_ROOT_WARNING_SHOWN)
        set(value) = putBoolean(KEY_ROOT_WARNING_SHOWN, value)

    var serverToken: String
        get() = getString(KEY_SERVER_TOKEN)
        set(value) = putString(KEY_SERVER_TOKEN, value)

    var serverPort: Int
        get() {
            val raw = getString(KEY_SERVER_PORT, DEFAULT_SERVER_PORT.toString()).toIntOrNull()
                ?: return DEFAULT_SERVER_PORT
            return if (raw in 1024..65535) raw else DEFAULT_SERVER_PORT
        }
        set(value) {
            val clamped = value.coerceIn(1024, 65535)
            putString(KEY_SERVER_PORT, clamped.toString())
        }

    var serverBindMode: String
        get() = getString(KEY_SERVER_BIND_MODE, DEFAULT_BIND_MODE)
        set(value) = putString(KEY_SERVER_BIND_MODE, value)

    var serverAutoStart: Boolean
        get() = getBoolean(KEY_SERVER_AUTO_START)
        set(value) = putBoolean(KEY_SERVER_AUTO_START, value)

    var serverConfigured: Boolean
        get() = getBoolean(KEY_SERVER_CONFIGURED)
        set(value) = putBoolean(KEY_SERVER_CONFIGURED, value)

    var serverSelectedModelId: String
        get() = getString(KEY_SERVER_SELECTED_MODEL)
        set(value) = putString(KEY_SERVER_SELECTED_MODEL, value)

    var hfSearchHistory: String
        get() = getString(KEY_HF_SEARCH_HISTORY)
        set(value) = putString(KEY_HF_SEARCH_HISTORY, value)

    var hfTagsCatalogJson: String
        get() = getString(KEY_HF_TAGS_CATALOG)
        set(value) = putString(KEY_HF_TAGS_CATALOG, value)

    var hfTagsCatalogSavedAt: Long
        get() = getString(KEY_HF_TAGS_CATALOG_AT).toLongOrNull() ?: 0L
        set(value) = putString(KEY_HF_TAGS_CATALOG_AT, value.toString())

    var activeTtsModelId: String
        get() = getString(KEY_ACTIVE_TTS_MODEL)
        set(value) = putString(KEY_ACTIVE_TTS_MODEL, value)

    var activeSttModelId: String
        get() = getString(KEY_ACTIVE_STT_MODEL)
        set(value) = putString(KEY_ACTIVE_STT_MODEL, value)

    var ragSmartRerank: Boolean
        get() = getBoolean(KEY_RAG_SMART_RERANK)
        set(value) = putBoolean(KEY_RAG_SMART_RERANK, value)

    var ragMultiQuery: Boolean
        get() = getBoolean(KEY_RAG_MULTI_QUERY)
        set(value) = putBoolean(KEY_RAG_MULTI_QUERY, value)

    var ragDeepResearch: Boolean
        get() = getBoolean(KEY_RAG_DEEP_RESEARCH)
        set(value) = putBoolean(KEY_RAG_DEEP_RESEARCH, value)

    var vlmImageQuality: String
        get() = getString(KEY_VLM_IMAGE_QUALITY, DEFAULT_VLM_IMAGE_QUALITY)
        set(value) = putString(KEY_VLM_IMAGE_QUALITY, value)

    var threadMode: Int
        get() = getString(KEY_THREAD_MODE, DEFAULT_THREAD_MODE.toString()).toIntOrNull()?.coerceIn(0, 2)
            ?: DEFAULT_THREAD_MODE
        set(value) = putString(KEY_THREAD_MODE, value.coerceIn(0, 2).toString())

    var pluginOnnxEp: String
        get() = getString(KEY_PLUGIN_ONNX_EP, DEFAULT_PLUGIN_ONNX_EP)
        set(value) = putString(KEY_PLUGIN_ONNX_EP, value)

    var fridayJwt: String
        get() = getString(KEY_FRIDAY_JWT)
        set(value) = putString(KEY_FRIDAY_JWT, value)

    var fridayUserId: String
        get() = getString(KEY_FRIDAY_USER_ID)
        set(value) = putString(KEY_FRIDAY_USER_ID, value)

    var fridayUserName: String
        get() = getString(KEY_FRIDAY_USER_NAME)
        set(value) = putString(KEY_FRIDAY_USER_NAME, value)

    var fridayUserEmail: String
        get() = getString(KEY_FRIDAY_USER_EMAIL)
        set(value) = putString(KEY_FRIDAY_USER_EMAIL, value)

    var fridayAvatarUrl: String
        get() = getString(KEY_FRIDAY_AVATAR_URL)
        set(value) = putString(KEY_FRIDAY_AVATAR_URL, value)

    var fridayUserPlan: String
        get() = getString(KEY_FRIDAY_USER_PLAN)
        set(value) = putString(KEY_FRIDAY_USER_PLAN, value)

    var fridayApiBaseUrl: String
        get() = getString(KEY_FRIDAY_API_BASE_URL, DEFAULT_FRIDAY_API_BASE_URL)
        set(value) = putString(KEY_FRIDAY_API_BASE_URL, value)

    var fridaySelectedModelId: String
        get() = getString(KEY_FRIDAY_SELECTED_MODEL)
        set(value) = putString(KEY_FRIDAY_SELECTED_MODEL, value)

    var backendMode: BackendMode
        get() = BackendMode.fromId(getString(KEY_BACKEND_MODE, BackendMode.DEFAULT.id))
        set(value) = putString(KEY_BACKEND_MODE, value.id)

    var fridayFcmToken: String
        get() = getString(KEY_FRIDAY_FCM_TOKEN)
        set(value) = putString(KEY_FRIDAY_FCM_TOKEN, value)

    var fridayVoiceAnim: String
        get() = getString(KEY_FRIDAY_VOICE_ANIM, DEFAULT_FRIDAY_VOICE_ANIM)
        set(value) = putString(KEY_FRIDAY_VOICE_ANIM, value)

    var fridayVoiceForegroundContinue: Boolean
        get() = getBoolean(KEY_FRIDAY_VOICE_FOREGROUND_CONTINUE, false)
        set(value) = putBoolean(KEY_FRIDAY_VOICE_FOREGROUND_CONTINUE, value)

    var fridayVoiceBargeIn: Boolean
        get() = getBoolean(KEY_FRIDAY_VOICE_BARGE_IN, true)
        set(value) = putBoolean(KEY_FRIDAY_VOICE_BARGE_IN, value)

    // Random per-install device id for the Firestore device registry. NOT
    // Settings.Secure.ANDROID_ID — the repo forbids OS-attested/global ids for
    // any persisted identity. Lives sealed in the encrypted app_prefs vault.
    fun deviceRegistryId(): String {
        val existing = getString(KEY_FRIDAY_DEVICE_ID)
        if (existing.isNotBlank()) return existing
        val fresh = "dev_" + java.util.UUID.randomUUID().toString().replace("-", "")
        putString(KEY_FRIDAY_DEVICE_ID, fresh)
        return fresh
    }

    fun clearFridayAccount() {
        deleteKey(KEY_FRIDAY_JWT)
        deleteKey(KEY_FRIDAY_USER_ID)
        deleteKey(KEY_FRIDAY_USER_NAME)
        deleteKey(KEY_FRIDAY_USER_EMAIL)
        deleteKey(KEY_FRIDAY_AVATAR_URL)
        deleteKey(KEY_FRIDAY_USER_PLAN)
    }

    fun readAuthState(): AuthState {
        val sealed = getBytes(KEY_AUTH_STATE) ?: return AuthState.DEFAULT
        val plaintext = try {
            encryptor.decrypt(sealed, deriveAuthKey(), AUTH_AAD)
        } catch (_: SecurityException) {
            return AuthState.DEFAULT
        }
        try {
            return AuthState.decode(plaintext)
        } finally {
            encryptor.secureWipe(plaintext)
        }
    }

    fun writeAuthState(state: AuthState) {
        val plaintext = state.encode()
        val sealed = try {
            encryptor.encrypt(plaintext, deriveAuthKey(), AUTH_AAD)
        } finally {
            encryptor.secureWipe(plaintext)
        }
        putBytes(KEY_AUTH_STATE, sealed)
    }

    fun clearAuthState() {
        deleteKey(KEY_AUTH_STATE)
    }

    private fun deriveAuthKey(): ByteArray {
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        return encryptor.deriveKey(ikm = dek, salt = signerHash, info = AUTH_KEY_INFO)
    }

    private fun openOrRebuild(dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(basePath)) {
            if (storage.openEncrypted(basePath, dek, userKey, encryptor)) return true
            File(basePath).deleteRecursively()
            File(basePath).mkdirs()
        }
        return storage.createEncrypted(basePath, dek, userKey, encryptor)
    }

    companion object {
        private const val COLLECTION = "app_prefs"
        private const val TAG_KEY = 1
        private const val TAG_VALUE_BOOL = 2
        private const val TAG_VALUE_STRING = 3
        private const val TAG_VALUE_BYTES = 4

        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_TC_ACCEPTED = "tc_accepted"
        const val KEY_SETUP_DONE = "setup_done"
        const val KEY_SECURITY_SETUP_DONE = "security_setup_done"
        const val KEY_MODEL_SETUP_DONE = "model_setup_done"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_MODEL_PACK = "model_pack"
        const val KEY_TOUR_DONE = "tour_done"
        const val KEY_THEME_SETUP_DONE = "theme_setup_done"
        const val KEY_PROVIDER_STEP_DONE = "provider_step_done"
        const val KEY_GUIDE_SHOWN = "guide_shown"
        const val KEY_ROOT_WARNING_SHOWN = "root_warning_shown"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_SERVER_PORT = "server_port"
        const val KEY_SERVER_BIND_MODE = "server_bind_mode"
        const val KEY_SERVER_AUTO_START = "server_auto_start"
        const val KEY_SERVER_CONFIGURED = "server_configured"
        const val KEY_SERVER_SELECTED_MODEL = "server_selected_model"
        const val KEY_HF_SEARCH_HISTORY = "hf_search_history"
        const val KEY_HF_TAGS_CATALOG = "hf_tags_catalog_v1"
        const val KEY_HF_TAGS_CATALOG_AT = "hf_tags_catalog_v1_at"
        const val KEY_ACTIVE_TTS_MODEL = "active_tts_model"
        const val KEY_ACTIVE_STT_MODEL = "active_stt_model"
        const val KEY_RAG_SMART_RERANK = "rag_smart_rerank"
        const val KEY_RAG_MULTI_QUERY = "rag_multi_query"
        const val KEY_RAG_DEEP_RESEARCH = "rag_deep_research"
        const val KEY_VLM_IMAGE_QUALITY = "vlm_image_quality"
        const val DEFAULT_VLM_IMAGE_QUALITY = "MEDIUM"
        const val KEY_THREAD_MODE = "thread_mode"
        const val DEFAULT_THREAD_MODE = 1
        const val THREAD_MODE_POWER_SAVING = 0
        const val THREAD_MODE_BALANCED = 1
        const val THREAD_MODE_PERFORMANCE = 2

        const val KEY_PLUGIN_ONNX_EP = "plugin_onnx_ep"
        const val PLUGIN_ONNX_EP_CPU = "cpu"
        const val PLUGIN_ONNX_EP_NNAPI = "nnapi"
        const val PLUGIN_ONNX_EP_XNNPACK = "xnnpack"
        const val DEFAULT_PLUGIN_ONNX_EP = PLUGIN_ONNX_EP_CPU

        const val KEY_FRIDAY_JWT = "friday_jwt"
        const val KEY_FRIDAY_USER_ID = "friday_user_id"
        const val KEY_FRIDAY_USER_NAME = "friday_user_name"
        const val KEY_FRIDAY_USER_EMAIL = "friday_user_email"
        const val KEY_FRIDAY_AVATAR_URL = "friday_avatar_url"
        const val KEY_FRIDAY_USER_PLAN = "friday_user_plan"
        const val KEY_FRIDAY_API_BASE_URL = "friday_api_base_url"
        const val KEY_FRIDAY_SELECTED_MODEL = "friday_selected_model"
        const val KEY_FRIDAY_FCM_TOKEN = "friday_fcm_token"
        const val KEY_FRIDAY_DEVICE_ID = "friday_device_id"
        const val KEY_FRIDAY_VOICE_ANIM = "friday_voice_anim"
        const val DEFAULT_FRIDAY_VOICE_ANIM = "orb"
        const val KEY_FRIDAY_VOICE_FOREGROUND_CONTINUE = "friday_voice_foreground_continue"
        const val KEY_FRIDAY_VOICE_BARGE_IN = "friday_voice_barge_in"
        const val KEY_BACKEND_MODE = "backend_mode"
        const val DEFAULT_FRIDAY_API_BASE_URL = "http://localhost:3101"
        const val DEFAULT_SERVER_PORT = 11434
        const val DEFAULT_BIND_MODE = "ALL_INTERFACES"
        private const val KEY_AUTH_STATE = "auth_state_v1"

        const val SECURITY_NONE = "none"
        const val SECURITY_APP_PASSWORD = "app_password"

        private const val USER_KEY_INFO = "tn.app_prefs.user_key.v2"
        private const val AUTH_KEY_INFO = "tn.app_prefs.auth_key.v2"
        private val AUTH_AAD = "tn.auth_state.v1".toByteArray(Charsets.UTF_8)
    }
}

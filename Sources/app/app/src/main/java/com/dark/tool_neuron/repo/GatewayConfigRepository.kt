package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.gateway.GatewayDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Signer-bound HXS vault — keys/endpoints never reach Firebase/FRIDAY. Brain and Voice pointers are independent.
@Singleton
class GatewayConfigRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) : GatewayDirectory {
    private val storage = HexStorage()

    private val _gateways = MutableStateFlow<List<GatewayConfig>>(emptyList())
    override val gateways: StateFlow<List<GatewayConfig>> = _gateways.asStateFlow()

    private val _brainId = MutableStateFlow("")
    val brainId: StateFlow<String> = _brainId.asStateFlow()
    override val brainSelection: StateFlow<String> get() = brainId

    private val _voiceId = MutableStateFlow("")
    val voiceId: StateFlow<String> = _voiceId.asStateFlow()

    // Legacy alias: the drawer selection historically meant "the active brain".
    val selectedId: StateFlow<String> = _brainId.asStateFlow()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted gateway_store vault")

        storage.ensureCollection(COL_GATEWAYS)
        storage.ensureCollection(COL_META)
        storage.addIndex(COL_GATEWAYS, TAG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_META, TAG_META_KEY, HexStorage.WIRE_BYTES)
        refresh()
        _brainId.value = migrateBrainPointer()
        _voiceId.value = readMeta(META_VOICE)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    // Migrate the legacy single "selected_gateway_id" into the brain pointer once.
    private fun migrateBrainPointer(): String {
        val brain = readMeta(META_BRAIN)
        if (brain.isNotBlank()) return brain
        val legacy = readMeta(META_LEGACY_SELECTED)
        if (legacy.isNotBlank()) writeMeta(META_BRAIN, legacy)
        return legacy
    }

    private fun refresh() {
        _gateways.value = storage.getAll(COL_GATEWAYS)
            .map { it.toConfig() }
            .sortedBy { it.createdAt }
    }

    fun getById(id: String): GatewayConfig? = _gateways.value.firstOrNull { it.id == id }

    // Falls back to the first brain-capable gateway when the pointer is stale/blank.
    override fun brainGateway(): GatewayConfig? =
        _gateways.value.firstOrNull { it.id == _brainId.value && it.supportsRole(GatewayRole.BRAIN) }
            ?: _gateways.value.firstOrNull { it.supportsRole(GatewayRole.BRAIN) }

    // Independent of the brain; falls back to the first voice-capable gateway.
    override fun voiceGateway(): GatewayConfig? =
        _gateways.value.firstOrNull { it.id == _voiceId.value && it.supportsRole(GatewayRole.VOICE) }
            ?: _gateways.value.firstOrNull { it.supportsRole(GatewayRole.VOICE) }

    // Pre-role callers asked for "the" selection, which was always the brain.
    fun selected(): GatewayConfig? = brainGateway()

    fun upsert(config: GatewayConfig): GatewayConfig {
        val now = System.currentTimeMillis()
        val existing = storage.queryString(COL_GATEWAYS, TAG_ID, config.id)
        val stored = if (existing.isEmpty()) config.copy(createdAt = now, updatedAt = now)
        else config.copy(updatedAt = now)
        existing.forEach { storage.delete(COL_GATEWAYS, it.id) }
        storage.put(COL_GATEWAYS, stored.toRecord())
        storage.flush(COL_GATEWAYS)
        refresh()
        // Per-role auto-fill so a brain-only provider never hijacks the voice slot.
        if (_brainId.value.isBlank() && stored.supportsRole(GatewayRole.BRAIN)) selectBrain(stored.id)
        if (_voiceId.value.isBlank() && stored.supportsRole(GatewayRole.VOICE)) selectVoice(stored.id)
        return stored
    }

    fun create(
        provider: GatewayProvider,
        label: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        voiceRoute: VoiceRoute = if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD,
    ): GatewayConfig {
        val now = System.currentTimeMillis()
        val config = GatewayConfig(
            id = UUID.randomUUID().toString(),
            provider = provider,
            label = label.ifBlank { provider.displayName },
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            createdAt = now,
            updatedAt = now,
            voiceRoute = voiceRoute,
        )
        return upsert(config)
    }

    // The error is already sanitized by the caller — never a raw key/header.
    fun recordStatus(id: String, status: GatewayStatus, error: String) {
        val current = getById(id) ?: return
        upsert(
            current.copy(
                status = status,
                lastTestedAt = System.currentTimeMillis(),
                statusError = if (status == GatewayStatus.FAILED) error else "",
            )
        )
    }

    fun delete(id: String) {
        storage.queryString(COL_GATEWAYS, TAG_ID, id).forEach { storage.delete(COL_GATEWAYS, it.id) }
        storage.flush(COL_GATEWAYS)
        refresh()
        // Fall back to the next role-capable record, or the no-provider state.
        if (_brainId.value == id) {
            selectBrain(_gateways.value.firstOrNull { it.supportsRole(GatewayRole.BRAIN) }?.id ?: "")
        }
        if (_voiceId.value == id) {
            selectVoice(_gateways.value.firstOrNull { it.supportsRole(GatewayRole.VOICE) }?.id ?: "")
        }
    }

    fun selectBrain(id: String) {
        writeMeta(META_BRAIN, id)
        _brainId.value = id
    }

    fun selectVoice(id: String) {
        writeMeta(META_VOICE, id)
        _voiceId.value = id
    }

    fun select(id: String, role: GatewayRole = GatewayRole.BRAIN) {
        when (role) {
            GatewayRole.BRAIN -> selectBrain(id)
            GatewayRole.VOICE -> selectVoice(id)
        }
    }

    private fun readMeta(key: String): String =
        storage.queryString(COL_META, TAG_META_KEY, key).firstOrNull()?.getString(TAG_META_VALUE).orEmpty()

    private fun writeMeta(key: String, value: String) {
        storage.queryString(COL_META, TAG_META_KEY, key).forEach { storage.delete(COL_META, it.id) }
        storage.put(COL_META, HxsRecord.build {
            putString(TAG_META_KEY, key)
            putString(TAG_META_VALUE, value)
        })
        storage.flush(COL_META)
    }

    private fun GatewayConfig.toRecord(): HxsRecord {
        val g = this
        return HxsRecord.build {
            putString(TAG_ID, g.id)
            putString(TAG_PROVIDER, g.provider.name)
            putString(TAG_LABEL, g.label)
            putString(TAG_BASE_URL, g.baseUrl)
            putString(TAG_API_KEY, g.apiKey)
            putString(TAG_MODEL, g.model)
            putTimestamp(TAG_CREATED_AT, g.createdAt)
            putTimestamp(TAG_UPDATED_AT, g.updatedAt)
            putInt(TAG_STATUS, g.status.ordinal.toLong())
            putTimestamp(TAG_LAST_TESTED_AT, g.lastTestedAt)
            putString(TAG_STATUS_ERROR, g.statusError)
            putInt(TAG_VOICE_ROUTE, g.voiceRoute.ordinal.toLong())
        }
    }

    private fun HxsRecord.toConfig(): GatewayConfig {
        val provider = GatewayProvider.fromId(getString(TAG_PROVIDER))
        return GatewayConfig(
            id = getString(TAG_ID),
            provider = provider,
            label = getString(TAG_LABEL),
            baseUrl = getString(TAG_BASE_URL),
            apiKey = getString(TAG_API_KEY),
            model = getString(TAG_MODEL),
            createdAt = getTimestamp(TAG_CREATED_AT),
            updatedAt = getTimestamp(TAG_UPDATED_AT),
            status = GatewayStatus.entries.getOrElse(getInt(TAG_STATUS).toInt()) { GatewayStatus.NOT_TESTED },
            lastTestedAt = getTimestamp(TAG_LAST_TESTED_AT),
            statusError = getString(TAG_STATUS_ERROR),
            voiceRoute = VoiceRoute.entries.getOrElse(
                getInt(TAG_VOICE_ROUTE, (if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD).ordinal.toLong()).toInt()
            ) { if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD },
        )
    }

    companion object {
        private const val SECURE_DIR = "gateway_store_v1"
        private const val USER_KEY_INFO = "tn.gateways.user_key.v2"

        private const val COL_GATEWAYS = "gateways"
        private const val COL_META = "gateway_meta"

        private const val TAG_ID = 1
        private const val TAG_PROVIDER = 2
        private const val TAG_LABEL = 3
        private const val TAG_BASE_URL = 4
        private const val TAG_API_KEY = 5
        private const val TAG_MODEL = 6
        private const val TAG_CREATED_AT = 7
        private const val TAG_UPDATED_AT = 8
        private const val TAG_STATUS = 9
        private const val TAG_LAST_TESTED_AT = 10
        private const val TAG_STATUS_ERROR = 11
        private const val TAG_VOICE_ROUTE = 12

        private const val TAG_META_KEY = 1
        private const val TAG_META_VALUE = 2
        private const val META_BRAIN = "brain_gateway_id"
        private const val META_VOICE = "voice_gateway_id"
        private const val META_LEGACY_SELECTED = "selected_gateway_id"
    }
}

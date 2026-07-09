package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Gateway endpoints + API keys are the most sensitive part of the M1 data
// boundary: they live in a dedicated signer-bound HXS vault and never reach
// Firebase / FRIDAY API. The selected-gateway pointer rides the same vault so
// the whole gateway state stays local.
@Singleton
class GatewayConfigRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    private val _gateways = MutableStateFlow<List<GatewayConfig>>(emptyList())
    val gateways: StateFlow<List<GatewayConfig>> = _gateways.asStateFlow()

    private val _selectedId = MutableStateFlow("")
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

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
        _selectedId.value = readMeta(META_SELECTED)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    private fun refresh() {
        _gateways.value = storage.getAll(COL_GATEWAYS)
            .map { it.toConfig() }
            .sortedBy { it.createdAt }
    }

    fun selected(): GatewayConfig? {
        val id = _selectedId.value
        return _gateways.value.firstOrNull { it.id == id } ?: _gateways.value.firstOrNull()
    }

    fun getById(id: String): GatewayConfig? = _gateways.value.firstOrNull { it.id == id }

    fun upsert(config: GatewayConfig): GatewayConfig {
        val now = System.currentTimeMillis()
        val existing = storage.queryString(COL_GATEWAYS, TAG_ID, config.id)
        val stored = if (existing.isEmpty()) config.copy(createdAt = now, updatedAt = now)
        else config.copy(updatedAt = now)
        existing.forEach { storage.delete(COL_GATEWAYS, it.id) }
        storage.put(COL_GATEWAYS, stored.toRecord())
        storage.flush(COL_GATEWAYS)
        refresh()
        if (_selectedId.value.isBlank()) select(stored.id)
        return stored
    }

    fun create(provider: GatewayProvider, label: String, baseUrl: String, apiKey: String, model: String): GatewayConfig {
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
        )
        return upsert(config)
    }

    fun delete(id: String) {
        storage.queryString(COL_GATEWAYS, TAG_ID, id).forEach { storage.delete(COL_GATEWAYS, it.id) }
        storage.flush(COL_GATEWAYS)
        refresh()
        if (_selectedId.value == id) select(_gateways.value.firstOrNull()?.id ?: "")
    }

    fun select(id: String) {
        writeMeta(META_SELECTED, id)
        _selectedId.value = id
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
        }
    }

    private fun HxsRecord.toConfig(): GatewayConfig = GatewayConfig(
        id = getString(TAG_ID),
        provider = GatewayProvider.fromId(getString(TAG_PROVIDER)),
        label = getString(TAG_LABEL),
        baseUrl = getString(TAG_BASE_URL),
        apiKey = getString(TAG_API_KEY),
        model = getString(TAG_MODEL),
        createdAt = getTimestamp(TAG_CREATED_AT),
        updatedAt = getTimestamp(TAG_UPDATED_AT),
    )

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

        private const val TAG_META_KEY = 1
        private const val TAG_META_VALUE = 2
        private const val META_SELECTED = "selected_gateway_id"
    }
}

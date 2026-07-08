package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.DownloadHistoryEntry
import com.dark.tool_neuron.model.DownloadHistoryStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    private val _history = MutableStateFlow<List<DownloadHistoryEntry>>(emptyList())
    val history: StateFlow<List<DownloadHistoryEntry>> = _history.asStateFlow()

    init {
        val secureDir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val secureBase = secureDir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(secureBase, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted download_history vault")

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_ID, HexStorage.WIRE_BYTES)

        refresh()
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    fun insert(entry: DownloadHistoryEntry) {
        storage.queryString(COLLECTION, TAG_ID, entry.id).forEach {
            storage.delete(COLLECTION, it.id)
        }
        storage.put(COLLECTION, entry.toRecord())
        capLocked()
        storage.flush(COLLECTION)
        refresh()
    }

    fun clearAll() {
        storage.getAll(COLLECTION).forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
        refresh()
    }

    private fun capLocked() {
        val all = storage.getAll(COLLECTION)
            .map { it.id to it.getTimestamp(TAG_COMPLETED_AT) }
            .sortedByDescending { it.second }
        if (all.size <= MAX_ENTRIES) return
        all.drop(MAX_ENTRIES).forEach { (recordId, _) ->
            storage.delete(COLLECTION, recordId)
        }
    }

    private fun refresh() {
        _history.value = storage.getAll(COLLECTION)
            .map { it.toEntry() }
            .sortedByDescending { it.completedAt }
    }

    private fun DownloadHistoryEntry.toRecord(): HxsRecord {
        val e = this
        return HxsRecord.build {
            putString(TAG_ID, e.id)
            putString(TAG_NAME, e.displayName)
            putString(TAG_TYPE, e.type)
            putInt(TAG_STATUS, e.status.ordinal.toLong())
            putTimestamp(TAG_TOTAL_BYTES, e.totalBytes)
            putTimestamp(TAG_COMPLETED_AT, e.completedAt)
            putString(TAG_ERROR, e.error ?: "")
        }
    }

    private fun HxsRecord.toEntry(): DownloadHistoryEntry {
        val errStr = getString(TAG_ERROR)
        return DownloadHistoryEntry(
            id = getString(TAG_ID),
            displayName = getString(TAG_NAME),
            type = getString(TAG_TYPE),
            status = DownloadHistoryStatus.entries
                .getOrNull(getInt(TAG_STATUS).toInt())
                ?: DownloadHistoryStatus.COMPLETED,
            totalBytes = getTimestamp(TAG_TOTAL_BYTES),
            completedAt = getTimestamp(TAG_COMPLETED_AT),
            error = errStr.ifBlank { null },
        )
    }

    companion object {
        private const val COLLECTION = "download_history"
        private const val SECURE_DIR = "download_history_v1"
        private const val USER_KEY_INFO = "tn.download_history.user_key.v2"

        private const val MAX_ENTRIES = 50

        private const val TAG_ID = 1
        private const val TAG_NAME = 2
        private const val TAG_TYPE = 3
        private const val TAG_STATUS = 4
        private const val TAG_TOTAL_BYTES = 5
        private const val TAG_COMPLETED_AT = 6
        private const val TAG_ERROR = 7
    }
}

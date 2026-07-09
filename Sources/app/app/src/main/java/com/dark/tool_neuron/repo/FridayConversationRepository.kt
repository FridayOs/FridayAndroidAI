package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.friday.FridayConversation
import com.dark.tool_neuron.model.friday.FridayTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Friday gateway chat + voice transcripts. HXS-only, sealed under the DEK +
// signer-bound user-key like every other vault. Nothing here ever reaches
// Firebase / FRIDAY API — transcript is on the local-only side of the M1
// data boundary. Survives process restart so the History screen shows it.
@Singleton
class FridayConversationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    private val _conversations = MutableStateFlow<List<FridayConversation>>(emptyList())
    val conversations: StateFlow<List<FridayConversation>> = _conversations.asStateFlow()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open encrypted friday_store vault")

        storage.ensureCollection(COL_CONVOS)
        storage.ensureCollection(COL_TURNS)
        storage.addIndex(COL_CONVOS, TAG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_TURNS, TAG_TURN_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COL_TURNS, TAG_TURN_CONVO_ID, HexStorage.WIRE_BYTES)
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

    fun refresh() {
        _conversations.value = storage.getAll(COL_CONVOS)
            .map { it.toConversation() }
            .sortedByDescending { it.updatedAt }
    }

    fun createConversation(gatewayId: String): FridayConversation {
        val now = System.currentTimeMillis()
        val convo = FridayConversation(
            id = UUID.randomUUID().toString(),
            title = "New conversation",
            gatewayId = gatewayId,
            createdAt = now,
            updatedAt = now,
        )
        storage.put(COL_CONVOS, convo.toRecord())
        storage.flush(COL_CONVOS)
        refresh()
        return convo
    }

    fun getConversation(id: String): FridayConversation? =
        storage.queryString(COL_CONVOS, TAG_ID, id).firstOrNull()?.toConversation()

    fun getTurns(conversationId: String): List<FridayTurn> =
        storage.queryString(COL_TURNS, TAG_TURN_CONVO_ID, conversationId)
            .map { it.toTurn() }
            .sortedBy { it.timestamp }

    fun addTurn(turn: FridayTurn) {
        storage.put(COL_TURNS, turn.toRecord())
        storage.flush(COL_TURNS)
        val convo = getConversation(turn.conversationId) ?: return
        val title = if (convo.messageCount == 0 && turn.role == "user") titleFrom(turn.content) else convo.title
        updateConversation(convo.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messageCount = convo.messageCount + 1,
        ))
    }

    fun updateTurn(turn: FridayTurn) {
        storage.queryString(COL_TURNS, TAG_TURN_ID, turn.id).forEach { storage.delete(COL_TURNS, it.id) }
        storage.put(COL_TURNS, turn.toRecord())
        storage.flush(COL_TURNS)
    }

    fun deleteConversation(id: String) {
        storage.queryString(COL_CONVOS, TAG_ID, id).forEach { storage.delete(COL_CONVOS, it.id) }
        storage.queryString(COL_TURNS, TAG_TURN_CONVO_ID, id).forEach { storage.delete(COL_TURNS, it.id) }
        storage.flushAll()
        refresh()
    }

    private fun updateConversation(convo: FridayConversation) {
        storage.queryString(COL_CONVOS, TAG_ID, convo.id).forEach { storage.delete(COL_CONVOS, it.id) }
        storage.put(COL_CONVOS, convo.toRecord())
        storage.flush(COL_CONVOS)
        refresh()
    }

    private fun titleFrom(firstMessage: String): String {
        val clean = firstMessage.trim().replace(Regex("\\s+"), " ")
        return if (clean.length > 40) clean.take(40) + "…" else clean.ifBlank { "New conversation" }
    }

    private fun FridayConversation.toRecord(): HxsRecord {
        val c = this
        return HxsRecord.build {
            putString(TAG_ID, c.id)
            putString(TAG_TITLE, c.title)
            putString(TAG_GATEWAY_ID, c.gatewayId)
            putTimestamp(TAG_CREATED_AT, c.createdAt)
            putTimestamp(TAG_UPDATED_AT, c.updatedAt)
            putTimestamp(TAG_MESSAGE_COUNT, c.messageCount.toLong())
        }
    }

    private fun HxsRecord.toConversation(): FridayConversation = FridayConversation(
        id = getString(TAG_ID),
        title = getString(TAG_TITLE),
        gatewayId = getString(TAG_GATEWAY_ID),
        createdAt = getTimestamp(TAG_CREATED_AT),
        updatedAt = getTimestamp(TAG_UPDATED_AT),
        messageCount = getTimestamp(TAG_MESSAGE_COUNT).toInt(),
    )

    private fun FridayTurn.toRecord(): HxsRecord {
        val t = this
        return HxsRecord.build {
            putString(TAG_TURN_ID, t.id)
            putString(TAG_TURN_CONVO_ID, t.conversationId)
            putString(TAG_TURN_ROLE, t.role)
            putString(TAG_TURN_CONTENT, t.content)
            putTimestamp(TAG_TURN_TIMESTAMP, t.timestamp)
            putBool(TAG_TURN_VIA_VOICE, t.viaVoice)
        }
    }

    private fun HxsRecord.toTurn(): FridayTurn = FridayTurn(
        id = getString(TAG_TURN_ID),
        conversationId = getString(TAG_TURN_CONVO_ID),
        role = getString(TAG_TURN_ROLE),
        content = getString(TAG_TURN_CONTENT),
        timestamp = getTimestamp(TAG_TURN_TIMESTAMP),
        viaVoice = getBool(TAG_TURN_VIA_VOICE),
    )

    companion object {
        private const val SECURE_DIR = "friday_store_v1"
        private const val USER_KEY_INFO = "tn.friday_convos.user_key.v2"

        private const val COL_CONVOS = "friday_conversations"
        private const val COL_TURNS = "friday_turns"

        private const val TAG_ID = 1
        private const val TAG_TITLE = 2
        private const val TAG_GATEWAY_ID = 3
        private const val TAG_CREATED_AT = 4
        private const val TAG_UPDATED_AT = 5
        private const val TAG_MESSAGE_COUNT = 6

        private const val TAG_TURN_ID = 1
        private const val TAG_TURN_CONVO_ID = 2
        private const val TAG_TURN_ROLE = 3
        private const val TAG_TURN_CONTENT = 4
        private const val TAG_TURN_TIMESTAMP = 5
        private const val TAG_TURN_VIA_VOICE = 6
    }
}

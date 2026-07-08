package com.dark.tool_neuron.service.inference

import android.util.Log
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tn_security.TnEvent
import com.dark.tn_security.TnSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TnSink that persists every event into an hxs collection. The collection is
 * encrypted/keyed the same way as other ToolNeuron hxs stores — opened in
 * plaintext mode by default for now (Phase 5 minimum); a follow-up wires it
 * up to the SecurityManager-derived keys.
 *
 * Schema (tags):
 * ```
 * 1: timestampMs (varint)    — primary order
 * 2: kind        (varint)    — 0=Log, 1=Error, 2=Cancel, 3=Crash
 * 3: level       (varint)
 * 4: module      (varint)
 * 5: code        (varint)
 * 6: stage       (varint)
 * 7: tag         (bytes/utf8)
 * 8: opId        (bytes/utf8)
 * 9: file        (bytes/utf8)
 * 10: line       (varint)
 * 11: func       (bytes/utf8)
 * 12: message    (bytes/utf8)
 * 13: suggestion (bytes/utf8)
 * 14: tid        (varint)
 * 15: signal     (varint)
 * 16: signalName (bytes/utf8)
 * 17: pid        (varint)
 * 18: crashPath  (bytes/utf8)
 * ```
 */
class HxsTnSink(filesDir: File) : TnSink {

    private val basePath = File(filesDir, "tn_security/events").absolutePath
        .also { File(it).mkdirs() }

    val storage = HexStorage()
    private val opened = AtomicBoolean(false)

    init { INSTANCE = this }

    private fun ensureOpen(): Boolean {
        if (opened.get()) return true
        return try {
            val ok = if (storage.exists(basePath)) {
                storage.openPlaintext(basePath)
            } else {
                storage.createPlaintext(basePath)
            }
            if (!ok) {
                Log.e(TAG, "open/create failed at $basePath")
                return false
            }
            storage.ensureCollection(COLLECTION)
            // Indexes for the CrashReportActivity to filter quickly.
            storage.addIndex(COLLECTION, TAG_TIMESTAMP, HexStorage.WIRE_VARINT)
            storage.addIndex(COLLECTION, TAG_KIND,      HexStorage.WIRE_VARINT)
            storage.addIndex(COLLECTION, TAG_MODULE,    HexStorage.WIRE_VARINT)
            storage.addIndex(COLLECTION, TAG_LEVEL,     HexStorage.WIRE_VARINT)
            storage.addIndex(COLLECTION, TAG_OP_ID,     HexStorage.WIRE_BYTES)
            opened.set(true)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "ensureOpen failed", t)
            false
        }
    }

    override fun onEvent(event: TnEvent) {
        if (!ensureOpen()) return
        val rec = encode(event)
        try { storage.put(COLLECTION, rec) }
        catch (t: Throwable) { Log.e(TAG, "put failed", t) }
    }

    private fun encode(event: TnEvent): HxsRecord {
        val b = HxsRecord.build { }
        // HxsRecord.build returns a finalised record. Re-create via mutable
        // builder pattern: we call build with a lambda below.
        return HxsRecord.build {
            putInt(TAG_TIMESTAMP, event.timestampMs)
            putInt(TAG_MODULE, event.module.value.toLong())
            event.opId?.let { putString(TAG_OP_ID, it) }
            putInt(TAG_TID, event.tid.toLong())

            when (event) {
                is TnEvent.Log -> {
                    putInt(TAG_KIND, KIND_LOG.toLong())
                    putInt(TAG_LEVEL, event.level.value.toLong())
                    event.tag?.let  { putString(TAG_TAG, it) }
                    event.file?.let { putString(TAG_FILE, it) }
                    if (event.line > 0) putInt(TAG_LINE, event.line.toLong())
                    event.func?.let { putString(TAG_FUNC, it) }
                    putString(TAG_MESSAGE, event.message)
                }
                is TnEvent.Error -> {
                    putInt(TAG_KIND, KIND_ERROR.toLong())
                    putInt(TAG_LEVEL, 4L)
                    putInt(TAG_CODE,  event.code.value.toLong())
                    putInt(TAG_STAGE, event.stage.value.toLong())
                    event.file?.let { putString(TAG_FILE, it) }
                    if (event.line > 0) putInt(TAG_LINE, event.line.toLong())
                    event.func?.let       { putString(TAG_FUNC, it) }
                    putString(TAG_MESSAGE, event.message)
                    event.suggestion?.let { putString(TAG_SUGGESTION, it) }
                }
                is TnEvent.Cancellation -> {
                    putInt(TAG_KIND, KIND_CANCEL.toLong())
                    putInt(TAG_LEVEL, 2L)
                    event.reason?.let { putString(TAG_MESSAGE, it) }
                }
                is TnEvent.Crash -> {
                    putInt(TAG_KIND, KIND_CRASH.toLong())
                    putInt(TAG_LEVEL, 5L)
                    putInt(TAG_SIGNAL, event.signal.toLong())
                    putString(TAG_SIGNAL_NAME, event.signalName)
                    putInt(TAG_PID, event.pid.toLong())
                    event.faultAddr?.let   { putString(TAG_MESSAGE, "fault_addr=$it") }
                    putString(TAG_CRASH_PATH, event.crashFilePath)
                    if (event.ring.isNotEmpty()) {
                        putString(TAG_RING_JSON, encodeRingJson(event.ring))
                    }
                }
            }
        }
    }

    companion object {
        const val COLLECTION    = "tn_security_events"
        const val TAG_TIMESTAMP  = 1
        const val TAG_KIND       = 2
        const val TAG_LEVEL      = 3
        const val TAG_MODULE     = 4
        const val TAG_CODE       = 5
        const val TAG_STAGE      = 6
        const val TAG_TAG        = 7
        const val TAG_OP_ID      = 8
        const val TAG_FILE       = 9
        const val TAG_LINE       = 10
        const val TAG_FUNC       = 11
        const val TAG_MESSAGE    = 12
        const val TAG_SUGGESTION = 13
        const val TAG_TID        = 14
        const val TAG_SIGNAL     = 15
        const val TAG_SIGNAL_NAME = 16
        const val TAG_PID        = 17
        const val TAG_CRASH_PATH = 18
        const val TAG_RING_JSON  = 19

        const val KIND_LOG    = 0
        const val KIND_ERROR  = 1
        const val KIND_CANCEL = 2
        const val KIND_CRASH  = 3

        private const val TAG = "HxsTnSink"

        /** Set in the sink's init block. Activity reads from it for the report screen. */
        @Volatile var INSTANCE: HxsTnSink? = null
            private set
    }

    /** Force-open and return the underlying storage, or null if open failed. */
    fun open(): HexStorage? = if (ensureOpen()) storage else null

    private fun encodeRingJson(ring: List<TnEvent.Log>): String {
        val arr = JSONArray()
        for (e in ring) {
            arr.put(JSONObject().apply {
                put("ts",   e.timestampMs)
                put("lvl",  e.level.value)
                put("mod",  e.module.value)
                put("tid",  e.tid)
                e.opId?.let  { put("op",   it) }
                e.tag?.let   { put("tag",  it) }
                e.file?.let  { put("file", it) }
                if (e.line > 0) put("line", e.line)
                e.func?.let  { put("func", it) }
                put("msg",  e.message)
            })
        }
        return arr.toString()
    }
}

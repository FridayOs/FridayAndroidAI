package com.dark.tool_neuron.ui.screens.crash_report

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.service.inference.HxsTnSink
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.ToolNeuronTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated screen for inspecting recent errors + crashes. Reads the
 * `tn_security_events` hxs collection populated by [HxsTnSink], filters to
 * errors and crashes, and renders each as a tab. Export writes a JSON
 * bundle to a SAF-picked location for bug reports.
 */
@AndroidEntryPoint
class CrashReportActivity : ComponentActivity() {

    @Inject lateinit var themeController: ThemeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode by themeController.mode.collectAsStateWithLifecycle()
            val palette by themeController.palette.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeController.Mode.SYSTEM -> systemDark
                ThemeController.Mode.LIGHT -> false
                ThemeController.Mode.DARK -> true
            }
            ToolNeuronTheme(darkTheme = darkTheme, palette = palette) {
                CrashReportScreen(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportScreen(onClose: () -> Unit) {
    val activity = LocalActivity.current
    val records = remember { mutableStateOf<List<CrashRecord>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        records.value = loadRecentErrorsAndCrashes()
    }

    val saver = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null && activity != null) {
            writeBundle(activity, uri, records.value)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash report (${records.value.size})") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(TnIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = records.value.isNotEmpty(),
                        onClick = {
                            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                            saver.launch("tn_security_bundle_$ts.json")
                        },
                    ) {
                        Icon(TnIcons.Download, contentDescription = "Export bundle")
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (records.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No errors or crashes recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selected.coerceIn(0, records.value.size - 1),
                    edgePadding = 12.dp,
                ) {
                    records.value.forEachIndexed { i, r ->
                        Tab(
                            selected = i == selected,
                            onClick = { selected = i },
                            text = {
                                Text(
                                    text = r.tabLabel(),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
                val cur = records.value.getOrNull(selected) ?: records.value.first()
                CrashRecordBody(cur)
            }
        }
    }
}

@Composable
private fun CrashRecordBody(r: CrashRecord) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KvRow("Module",     r.moduleSlug)
        KvRow("Kind",       if (r.kind == HxsTnSink.KIND_CRASH) "Crash" else "Error")
        KvRow("Code",       r.codeName())
        KvRow("Stage",      r.stageName())
        KvRow("Timestamp",  r.timestampHuman())
        if (r.opId.isNotBlank())       KvRow("Op ID",    r.opId)
        if (r.tid != 0)                KvRow("Thread",   r.tid.toString())
        if (r.signal != 0)             KvRow("Signal",   "${r.signalName} (${r.signal})")
        if (r.pid != 0)                KvRow("PID",      r.pid.toString())
        if (r.file.isNotBlank())       KvRow("Source",   "${r.file}:${r.line}")
        if (r.func.isNotBlank())       KvRow("Function", r.func)
        if (r.crashFilePath.isNotBlank()) KvRow("Crash file", r.crashFilePath)

        Text("Message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text = r.message.ifBlank { "(no message)" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }

        if (r.suggestion.isNotBlank()) {
            Text("Suggested fix", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(r.suggestion, style = MaterialTheme.typography.bodyMedium)
        }

        if (r.ring.isNotEmpty()) {
            LogSection(
                title = "Pre-crash ring buffer (${r.ring.size})",
                subtitle = "Last events captured by the native handler before the process died.",
                lines = r.ring,
            )
        }

        if (r.recentLogs.isNotEmpty()) {
            LogSection(
                title = "Logs in the 60s before (${r.recentLogs.size})",
                subtitle = "All persisted log lines from any module immediately preceding this event.",
                lines = r.recentLogs,
            )
        }
    }
}

@Composable
private fun LogSection(title: String, subtitle: String, lines: List<RingLine>) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            lines.forEach { LogLineRow(it) }
        }
    }
}

@Composable
private fun LogLineRow(l: RingLine) {
    val time = remember(l.timestampMs) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(l.timestampMs))
    }
    val levelStr = when (l.level) {
        0 -> "T"; 1 -> "D"; 2 -> "I"; 3 -> "W"; 4 -> "E"; 5 -> "F"; else -> "?"
    }
    val color = when (l.level) {
        4, 5 -> MaterialTheme.colorScheme.error
        3    -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val prefix = buildString {
        append(time); append(' ')
        append(levelStr); append(' ')
        append('['); append(l.moduleSlug); append(']')
        if (l.opId.isNotBlank()) { append(" op="); append(l.opId) }
        if (l.tag.isNotBlank())  { append(' '); append(l.tag); append(':') }
        append(' ')
    }
    Text(
        text = prefix + l.message,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun KvRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ──────────────────────────────────────────────────────────────────────────
//  Data
// ──────────────────────────────────────────────────────────────────────────

private data class RingLine(
    val timestampMs: Long,
    val level: Int,
    val moduleSlug: String,
    val tid: Int,
    val opId: String,
    val tag: String,
    val file: String,
    val line: Int,
    val func: String,
    val message: String,
)

private data class CrashRecord(
    val timestampMs: Long,
    val kind: Int,
    val level: Int,
    val module: Int,
    val moduleSlug: String,
    val code: Int,
    val stage: Int,
    val opId: String,
    val tid: Int,
    val file: String,
    val line: Int,
    val func: String,
    val message: String,
    val suggestion: String,
    val signal: Int,
    val signalName: String,
    val pid: Int,
    val crashFilePath: String,
    val ring: List<RingLine>,
    val recentLogs: List<RingLine>,
) {
    fun tabLabel(): String {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))
        return if (kind == HxsTnSink.KIND_CRASH) "$signalName · $moduleSlug" else "$moduleSlug · $time"
    }
    fun timestampHuman(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
    fun codeName(): String = com.dark.tn_security.TnCode.fromInt(code).name
    fun stageName(): String = com.dark.tn_security.TnStage.fromInt(stage).name
}

private fun loadRecentErrorsAndCrashes(): List<CrashRecord> {
    val hxs = HxsTnSink.INSTANCE?.open() ?: return emptyList()
    return try {
        val errors = hxs.queryInt(HxsTnSink.COLLECTION, HxsTnSink.TAG_KIND, HxsTnSink.KIND_ERROR.toLong())
        val crashes = hxs.queryInt(HxsTnSink.COLLECTION, HxsTnSink.TAG_KIND, HxsTnSink.KIND_CRASH.toLong())
        (errors + crashes)
            .map { rec ->
                val base = rec.toCrashRecord()
                // Pull last 60s of LOG events before this event — the
                // "pre-crash logs" the user wants in every report.
                val recent = queryRecentLogs(hxs, base.timestampMs)
                base.copy(recentLogs = recent)
            }
            .sortedByDescending { it.timestampMs }
            .take(64)
    } catch (_: Throwable) { emptyList() }
}

private fun queryRecentLogs(hxs: HexStorage, beforeMs: Long): List<RingLine> {
    if (beforeMs <= 0) return emptyList()
    return try {
        hxs.queryRange(
            HxsTnSink.COLLECTION,
            HxsTnSink.TAG_TIMESTAMP,
            beforeMs - 60_000L,
            beforeMs,
        )
            .filter { it.getInt(HxsTnSink.TAG_KIND, -1L).toInt() == HxsTnSink.KIND_LOG }
            .map { it.toRingLine() }
            .sortedBy { it.timestampMs }
            .takeLast(80)
    } catch (_: Throwable) { emptyList() }
}

private fun HxsRecord.toRingLine(): RingLine {
    val mod = getInt(HxsTnSink.TAG_MODULE, 0L).toInt()
    return RingLine(
        timestampMs = getInt(HxsTnSink.TAG_TIMESTAMP, 0L),
        level       = getInt(HxsTnSink.TAG_LEVEL, 0L).toInt(),
        moduleSlug  = com.dark.tn_security.TnModule.fromInt(mod).slug,
        tid         = getInt(HxsTnSink.TAG_TID, 0L).toInt(),
        opId        = getString(HxsTnSink.TAG_OP_ID, ""),
        tag         = getString(HxsTnSink.TAG_TAG, ""),
        file        = getString(HxsTnSink.TAG_FILE, ""),
        line        = getInt(HxsTnSink.TAG_LINE, 0L).toInt(),
        func        = getString(HxsTnSink.TAG_FUNC, ""),
        message     = getString(HxsTnSink.TAG_MESSAGE, ""),
    )
}

private fun decodeRingJson(raw: String): List<RingLine> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        val out = ArrayList<RingLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mod = o.optInt("mod", 0)
            out += RingLine(
                timestampMs = o.optLong("ts"),
                level       = o.optInt("lvl"),
                moduleSlug  = com.dark.tn_security.TnModule.fromInt(mod).slug,
                tid         = o.optInt("tid"),
                opId        = o.optString("op"),
                tag         = o.optString("tag"),
                file        = o.optString("file"),
                line        = o.optInt("line"),
                func        = o.optString("func"),
                message     = o.optString("msg"),
            )
        }
        out
    } catch (_: Throwable) { emptyList() }
}

private fun HxsRecord.toCrashRecord(): CrashRecord {
    val mod = getInt(HxsTnSink.TAG_MODULE, 0L).toInt()
    val ringRaw = getString(HxsTnSink.TAG_RING_JSON, "")
    return CrashRecord(
        timestampMs   = getInt(HxsTnSink.TAG_TIMESTAMP, 0L),
        kind          = getInt(HxsTnSink.TAG_KIND, 0L).toInt(),
        level         = getInt(HxsTnSink.TAG_LEVEL, 0L).toInt(),
        module        = mod,
        moduleSlug    = com.dark.tn_security.TnModule.fromInt(mod).slug,
        code          = getInt(HxsTnSink.TAG_CODE, 0L).toInt(),
        stage         = getInt(HxsTnSink.TAG_STAGE, 0L).toInt(),
        opId          = getString(HxsTnSink.TAG_OP_ID, ""),
        tid           = getInt(HxsTnSink.TAG_TID, 0L).toInt(),
        file          = getString(HxsTnSink.TAG_FILE, ""),
        line          = getInt(HxsTnSink.TAG_LINE, 0L).toInt(),
        func          = getString(HxsTnSink.TAG_FUNC, ""),
        message       = getString(HxsTnSink.TAG_MESSAGE, ""),
        suggestion    = getString(HxsTnSink.TAG_SUGGESTION, ""),
        signal        = getInt(HxsTnSink.TAG_SIGNAL, 0L).toInt(),
        signalName    = getString(HxsTnSink.TAG_SIGNAL_NAME, ""),
        pid           = getInt(HxsTnSink.TAG_PID, 0L).toInt(),
        crashFilePath = getString(HxsTnSink.TAG_CRASH_PATH, ""),
        ring          = decodeRingJson(ringRaw),
        recentLogs    = emptyList(),
    )
}

private fun writeBundle(activity: Activity, uri: Uri, records: List<CrashRecord>) {
    val arr = JSONArray()
    for (r in records) {
        arr.put(JSONObject().apply {
            put("timestamp_ms", r.timestampMs)
            put("kind",         r.kind)
            put("module",       r.module)
            put("module_slug",  r.moduleSlug)
            put("code",         r.code)
            put("code_name",    r.codeName())
            put("stage",        r.stage)
            put("stage_name",   r.stageName())
            put("op_id",        r.opId)
            put("tid",          r.tid)
            put("pid",          r.pid)
            put("signal",       r.signal)
            put("signal_name",  r.signalName)
            put("file",         r.file)
            put("line",         r.line)
            put("func",         r.func)
            put("message",      r.message)
            put("suggestion",   r.suggestion)
            put("crash_file",   r.crashFilePath)
            put("ring",         logLinesToJson(r.ring))
            put("recent_logs",  logLinesToJson(r.recentLogs))
        })
    }
    val payload = JSONObject().apply {
        put("schema",     "tn_security/1")
        put("exported_at", System.currentTimeMillis())
        put("records",    arr)
    }.toString(2)
    try {
        activity.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { it.write(payload) }
        }
    } catch (_: Throwable) {}
}

private fun logLinesToJson(lines: List<RingLine>): JSONArray {
    val arr = JSONArray()
    for (l in lines) {
        arr.put(JSONObject().apply {
            put("ts",     l.timestampMs)
            put("lvl",    l.level)
            put("mod",    l.moduleSlug)
            put("tid",    l.tid)
            if (l.opId.isNotBlank()) put("op",   l.opId)
            if (l.tag.isNotBlank())  put("tag",  l.tag)
            if (l.file.isNotBlank()) put("file", l.file)
            if (l.line > 0)          put("line", l.line)
            if (l.func.isNotBlank()) put("func", l.func)
            put("msg",    l.message)
        })
    }
    return arr
}

// Minimal Activity locator — Compose runtime versions vary in this repo so we
// resolve via the Context chain instead of LocalActivity (which only exists in
// newer activity-compose releases).
private object LocalActivity {
    val current: Activity?
        @Composable get() {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var c: android.content.Context? = ctx
            while (c is android.content.ContextWrapper) {
                if (c is Activity) return c
                c = c.baseContext
            }
            return null
        }
}

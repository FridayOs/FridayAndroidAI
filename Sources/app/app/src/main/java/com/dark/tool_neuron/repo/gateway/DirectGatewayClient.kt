package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GatewayEvent {
    data class Delta(val text: String) : GatewayEvent
    data class Done(val fullText: String) : GatewayEvent
    data class Error(val message: String) : GatewayEvent
}

data class GatewayTurn(val role: String, val content: String)

// Success is never faked — Ready only on a real 2xx; Failed carries a sanitized summary.
sealed interface GatewayTestResult {
    data object Ready : GatewayTestResult
    data class Failed(val error: String) : GatewayTestResult
}

// User-owned gateways connect directly from Android — no FRIDAY proxy; key/prompt/response stay on-device.
@Singleton
class DirectGatewayClient @Inject constructor() {

    fun stream(config: GatewayConfig, history: List<GatewayTurn>): Flow<GatewayEvent> = flow {
        val builder = StringBuilder()
        try {
            when (config.provider.wireFormat) {
                GatewayWireFormat.OPENAI -> streamOpenAi(config, history, builder) { emit(it) }
                GatewayWireFormat.ANTHROPIC -> streamAnthropic(config, history, builder) { emit(it) }
                GatewayWireFormat.GEMINI -> streamGemini(config, history, builder) { emit(it) }
            }
            emit(GatewayEvent.Done(builder.toString()))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            emit(classifyError(t, config.apiKey))
        }
    }.flowOn(Dispatchers.IO)

    // Ready only on a real 2xx — never faked. Honours cancellation so leaving the screen aborts it.
    suspend fun testConnection(config: GatewayConfig): GatewayTestResult = withContext(Dispatchers.IO) {
        // Local gateways run on-device — no cloud endpoint to probe.
        if (config.provider.isLocal) return@withContext GatewayTestResult.Ready
        val probe = listOf(GatewayTurn("user", PROBE_PROMPT))
        try {
            when (config.provider.wireFormat) {
                GatewayWireFormat.OPENAI -> probeOpenAi(config, probe)
                GatewayWireFormat.ANTHROPIC -> probeAnthropic(config, probe)
                GatewayWireFormat.GEMINI -> probeGemini(config, probe)
            }
            GatewayTestResult.Ready
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Scrub the literal key in case the provider echoed it in the error body.
            GatewayTestResult.Failed(GatewayErrorSanitizer.sanitize(t.message, config.apiKey))
        }
    }

    // Probe caps output tiny and drains one event to exercise auth + model + endpoint for real.
    private suspend fun probeOpenAi(config: GatewayConfig, history: List<GatewayTurn>) {
        val messages = JSONArray()
        history.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject()
            .put("model", config.displayModel)
            .put("stream", true)
            .put("max_tokens", PROBE_MAX_TOKENS)
            .put("messages", messages)
            .toString()
        val conn = open("${config.effectiveBaseUrl}/v1/chat/completions") {
            readTimeout = PROBE_READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        writeBody(conn, payload)
        drainProbe(conn)
    }

    private suspend fun probeAnthropic(config: GatewayConfig, history: List<GatewayTurn>) {
        val messages = JSONArray()
        history.forEach { messages.put(JSONObject().put("role", "user").put("content", it.content)) }
        val payload = JSONObject()
            .put("model", config.displayModel)
            .put("stream", true)
            .put("max_tokens", PROBE_MAX_TOKENS)
            .put("messages", messages)
            .toString()
        val conn = open("${config.effectiveBaseUrl}/v1/messages") {
            readTimeout = PROBE_READ_TIMEOUT_MS
            setRequestProperty("x-api-key", config.apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        }
        writeBody(conn, payload)
        drainProbe(conn)
    }

    private suspend fun probeGemini(config: GatewayConfig, history: List<GatewayTurn>) {
        val contents = JSONArray()
        history.forEach {
            contents.put(
                JSONObject().put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.content)))
            )
        }
        val payload = JSONObject().put("contents", contents).toString()
        val model = config.displayModel
        val url = "${config.effectiveBaseUrl}/v1beta/models/$model:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val conn = open(url) { readTimeout = PROBE_READ_TIMEOUT_MS }
        writeBody(conn, payload)
        drainProbe(conn)
    }

    // A 2xx with no event still means the endpoint is reachable and authorized.
    private suspend fun drainProbe(conn: HttpURLConnection) {
        readSse(conn) { false }
    }

    private suspend inline fun streamOpenAi(
        config: GatewayConfig,
        history: List<GatewayTurn>,
        builder: StringBuilder,
        emit: (GatewayEvent) -> Unit,
    ) {
        val messages = JSONArray()
        history.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val payload = JSONObject()
            .put("model", config.displayModel)
            .put("stream", true)
            .put("messages", messages)
            .toString()
        val conn = open("${config.effectiveBaseUrl}/v1/chat/completions") {
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        writeBody(conn, payload)
        readSse(conn) { data ->
            if (data == "[DONE]") return@readSse false
            val delta = runCatching {
                JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("delta")?.optString("content").orEmpty()
            }.getOrDefault("")
            if (delta.isNotEmpty()) {
                builder.append(delta)
                emit(GatewayEvent.Delta(delta))
            }
            true
        }
    }

    private suspend inline fun streamAnthropic(
        config: GatewayConfig,
        history: List<GatewayTurn>,
        builder: StringBuilder,
        emit: (GatewayEvent) -> Unit,
    ) {
        val system = history.filter { it.role == "system" }.joinToString("\n") { it.content }
        val messages = JSONArray()
        history.filter { it.role != "system" }.forEach {
            val role = if (it.role == "assistant") "assistant" else "user"
            messages.put(JSONObject().put("role", role).put("content", it.content))
        }
        val payload = JSONObject()
            .put("model", config.displayModel)
            .put("stream", true)
            .put("max_tokens", MAX_TOKENS)
            .apply { if (system.isNotBlank()) put("system", system) }
            .put("messages", messages)
            .toString()
        val conn = open("${config.effectiveBaseUrl}/v1/messages") {
            setRequestProperty("x-api-key", config.apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        }
        writeBody(conn, payload)
        readSse(conn) { data ->
            val obj = runCatching { JSONObject(data) }.getOrNull() ?: return@readSse true
            when (obj.optString("type")) {
                "content_block_delta" -> {
                    val delta = obj.optJSONObject("delta")?.optString("text").orEmpty()
                    if (delta.isNotEmpty()) {
                        builder.append(delta)
                        emit(GatewayEvent.Delta(delta))
                    }
                }
                "message_stop" -> return@readSse false
            }
            true
        }
    }

    private suspend inline fun streamGemini(
        config: GatewayConfig,
        history: List<GatewayTurn>,
        builder: StringBuilder,
        emit: (GatewayEvent) -> Unit,
    ) {
        val contents = JSONArray()
        val systemParts = StringBuilder()
        history.forEach {
            if (it.role == "system") {
                systemParts.append(it.content).append('\n')
                return@forEach
            }
            val role = if (it.role == "assistant") "model" else "user"
            contents.put(
                JSONObject().put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", it.content)))
            )
        }
        val payload = JSONObject()
            .put("contents", contents)
            .apply {
                if (systemParts.isNotBlank()) put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemParts.toString().trim())))
                )
            }
            .toString()
        val model = config.displayModel
        val url = "${config.effectiveBaseUrl}/v1beta/models/$model:streamGenerateContent?alt=sse&key=${config.apiKey}"
        val conn = open(url) {}
        writeBody(conn, payload)
        readSse(conn) { data ->
            val obj = runCatching { JSONObject(data) }.getOrNull() ?: return@readSse true
            val delta = obj.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text").orEmpty()
            if (delta.isNotEmpty()) {
                builder.append(delta)
                emit(GatewayEvent.Delta(delta))
            }
            true
        }
    }

    private inline fun open(url: String, extra: HttpURLConnection.() -> Unit): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            extra()
        }

    private fun writeBody(conn: HttpURLConnection, body: String) {
        val out: OutputStream = conn.outputStream
        out.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }

    // SSE reader: accumulate `data:` lines per event, dispatch on blank line; visitor returns false to stop.
    private suspend inline fun readSse(conn: HttpURLConnection, visit: (String) -> Boolean) {
        val status = conn.responseCode
        if (status !in 200..299) {
            val err = conn.errorStream?.use {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
            }.orEmpty().take(ERROR_BODY_CAP)
            conn.disconnect()
            throw GatewayHttpException("HTTP $status${if (err.isNotBlank()) ": $err" else ""}")
        }
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val data = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                val l = line!!
                when {
                    l.startsWith("data:") -> data.append(l.substring(5).trim())
                    l.isBlank() -> {
                        if (data.isNotEmpty()) {
                            val keepGoing = visit(data.toString())
                            data.clear()
                            if (!keepGoing) break
                        }
                    }
                }
            }
            if (data.isNotEmpty()) visit(data.toString())
        } finally {
            conn.disconnect()
        }
    }

    private class GatewayHttpException(message: String) : Exception(message)

    companion object {
        // Cancellation rethrows (control signal, not an error); everything else is sanitized with the key.
        fun classifyError(t: Throwable, key: String): GatewayEvent {
            if (t is CancellationException) throw t
            return GatewayEvent.Error(GatewayErrorSanitizer.sanitize(t.message, key))
        }

        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 120000
        private const val MAX_TOKENS = 4096
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ERROR_BODY_CAP = 500
        private const val PROBE_MAX_TOKENS = 1
        private const val PROBE_PROMPT = "ping"
        private const val PROBE_READ_TIMEOUT_MS = 20000
    }
}

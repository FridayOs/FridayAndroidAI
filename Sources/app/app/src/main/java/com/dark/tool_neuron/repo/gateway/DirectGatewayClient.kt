package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayWireFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

// User-owned gateways connect DIRECTLY from Android. No FRIDAY API proxy, no
// Firebase. Endpoint, key, model and every prompt/response byte stay on-device.
// Three wire formats cover every supported provider: OpenAI-compatible
// (OpenAI/DeepSeek/Custom/OpenClaw/Hermes), Anthropic messages, Gemini
// generateContent. All stream token deltas over a chunked HTTP read.
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
        } catch (t: Throwable) {
            emit(GatewayEvent.Error(t.message ?: "Gateway request failed"))
        }
    }.flowOn(Dispatchers.IO)

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

    // Reads a text/event-stream: accumulates `data:` lines per event (blank line
    // = dispatch). The visitor returns false to stop early. Non-2xx surfaces the
    // provider error body verbatim so the user sees what the endpoint said.
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
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 120000
        private const val MAX_TOKENS = 4096
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val ERROR_BODY_CAP = 500
    }
}

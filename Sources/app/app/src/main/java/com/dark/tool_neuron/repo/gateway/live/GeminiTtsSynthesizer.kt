package com.dark.tool_neuron.repo.gateway.live

import android.util.Base64
import com.dark.tool_neuron.repo.gateway.GatewayErrorSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

// Vocalizes the Brain Gateway's answer via Gemini TTS (generateContent) — the documented "exact recitation"
// endpoint. POSTs DIRECT to the user's Gemini host over TLS (no FRIDAY proxy); the key stays on-device and any
// error is sanitized before it could surface. Returns base64-decoded PCM16 mono 24kHz (LiveAudioSink format).
@Singleton
class GeminiTtsSynthesizer @Inject constructor() : LiveVoiceSynthesizer {

    override suspend fun synthesize(config: GeminiLiveConfig, text: String): ByteArray? =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext null
            val payload = buildPayload(text, config.voice)
            // Direct to the user's own Gemini host — same base as the Live socket, never a proxy.
            val url = "https://${config.baseHost}/v1beta/models/${GeminiLiveConfig.DEFAULT_TTS_MODEL}:generateContent?key=${config.apiKey}"
            var conn: HttpURLConnection? = null
            try {
                conn = open(url)
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = conn.responseCode
                if (status !in 200..299) {
                    val err = conn.errorStream?.use {
                        BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText()
                    }.orEmpty().take(ERROR_BODY_CAP)
                    // Sanitize so a provider echo of the key never leaks; null lets the VM surface the miss honestly.
                    throw TtsException(sanitizeError("HTTP $status: $err", config.apiKey))
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).readText()
                decodePcm(body)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Never leak the key on any transport/parse failure — a null return degrades gracefully.
                null
            } finally {
                conn?.disconnect()
            }
        }

    private fun buildPayload(text: String, voice: String): String {
        val voiceConfig = JSONObject().put(
            "prebuiltVoiceConfig", JSONObject().put("voiceName", voice)
        )
        val speechConfig = JSONObject().put("voiceConfig", voiceConfig)
        val generationConfig = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", speechConfig)
        val contents = JSONArray().put(
            JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))
        )
        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
            .toString()
    }

    // candidates[0].content.parts[0].inlineData.data = base64 PCM16 mono 24kHz.
    private fun decodePcm(body: String): ByteArray? {
        val data = runCatching {
            JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optJSONObject("inlineData")?.optString("data").orEmpty()
        }.getOrDefault("")
        if (data.isBlank()) return null
        return runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json")
        }

    private class TtsException(message: String) : Exception(message)

    companion object {
        // Pure so the error path is unit-testable off-device (mirrors DirectGatewayClientErrorTest): no key leak.
        internal fun sanitizeError(raw: String?, key: String): String = GatewayErrorSanitizer.sanitize(raw, key)

        private const val CONNECT_TIMEOUT_MS = 15000
        // Single-chunk generateContent (no streaming) can take a few seconds for a short answer.
        private const val READ_TIMEOUT_MS = 60000
        private const val ERROR_BODY_CAP = 500
    }
}

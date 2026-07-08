package com.dark.tool_neuron.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class FridaySession(
    val jwt: String,
    val userId: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String,
    val plan: String,
)

private data class FridayHttpResponse(
    val status: Int,
    val body: String,
)

private class FridayHttpException(message: String) : IOException(message)

@Singleton
class FridayApi @Inject constructor(
    private val prefs: AppPreferences,
) {
    private val baseUrl: String
        get() = prefs.fridayApiBaseUrl.trimEnd('/')

    // Legacy/reference backend mode. Firebase is the default backend mode,
    // but friday_api remains available while Firebase Auth/Firestore wiring lands.
    suspend fun exchangeGoogle(idToken: String): FridaySession {
        require(idToken.isNotBlank()) { "empty idToken" }
        val payload = JSONObject().put("idToken", idToken).toString()
        val response = httpPost(
            url = "$baseUrl/v1/auth/google",
            body = payload,
            contentType = "application/json",
            accept = "application/json",
            timeoutMs = 6000,
            bearer = null,
        )
        if (response.status !in 200..299) {
            throw FridayHttpException("POST /v1/auth/google failed: ${response.status}")
        }
        val json = JSONObject(response.body)
        val token = json.optString("token").takeIf { it.isNotBlank() }
            ?: throw FridayHttpException("POST /v1/auth/google returned no token")
        val user = json.optJSONObject("user")
            ?: throw FridayHttpException("POST /v1/auth/google returned no user")
        val userId = user.optString("id").ifBlank { "usr_unknown" }
        return FridaySession(
            jwt = token,
            userId = userId,
            displayName = user.optString("displayName").ifBlank { "Friday User" },
            email = user.optString("email").ifBlank { "$userId@friday.local" },
            avatarUrl = user.optString("avatarUrl"),
            plan = user.optString("plan").ifBlank { "Free plan" },
        )
    }

    suspend fun refreshFromBackend(current: FridaySession): FridaySession {
        val response = httpGet(
            url = "$baseUrl/v1/me",
            timeoutMs = 4000,
            bearer = current.jwt,
        )
        if (response.status == 401 || response.status == 403) {
            // Backend signature mismatch or expired token — drop to caller so
            // account state falls back to Unauthenticated on next read.
            throw FridayHttpException("GET /v1/me rejected: ${response.status}")
        }
        if (response.status !in 200..299) return current
        val json = runCatching { JSONObject(response.body) }.getOrNull() ?: return current
        return current.copy(
            userId = json.optString("id", current.userId),
            displayName = json.optString("displayName", current.displayName),
            email = json.optString("email", current.email),
            avatarUrl = json.optString("avatarUrl", current.avatarUrl),
            plan = json.optString("plan", current.plan),
        )
    }

    private suspend fun httpPost(
        url: String,
        body: String,
        contentType: String,
        accept: String,
        timeoutMs: Int,
        bearer: String?,
    ): FridayHttpResponse = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "FRIDAY-AI/Android")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            FridayHttpResponse(status = status, body = text)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun httpGet(
        url: String,
        timeoutMs: Int,
        bearer: String?,
    ): FridayHttpResponse = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FRIDAY-AI/Android")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            FridayHttpResponse(status = status, body = text)
        } finally {
            conn.disconnect()
        }
    }
}
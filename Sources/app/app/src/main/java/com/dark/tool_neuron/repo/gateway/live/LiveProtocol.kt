package com.dark.tool_neuron.repo.gateway.live

import com.dark.tool_neuron.repo.gateway.live.LiveJson.JsonValue

// Pure Gemini Live wire codec (no Android, no org.json) so setup/audio/end framing and server-frame parsing are unit-testable off-device.
object LiveProtocol {

    const val INPUT_MIME = "audio/pcm;rate=16000"
    const val OUTPUT_SAMPLE_RATE = 24000
    const val INPUT_SAMPLE_RATE = 16000

    // B1: native-audio Gemini can't be forced into pure-STT mode, but a terse instruction shrinks the
    // wasted own-answer generation the engine discards anyway. The Brain Gateway owns the real answer.
    const val BRAIN_OWNS_ANSWER_INSTRUCTION = "You transcribe only; do not answer."

    // First frame, sent once. responseModalities=AUDIO; manual activity control when barge-in is off.
    fun setupFrame(config: GeminiLiveConfig): String {
        val voiceConfig = LiveJson.obj(
            "prebuiltVoiceConfig" to LiveJson.obj("voiceName" to LiveJson.str(config.voice))
        )
        val speechConfig = LiveJson.obj(
            "voiceConfig" to voiceConfig,
            "languageCode" to LiveJson.str(config.locale),
        )
        val generationConfig = LiveJson.obj(
            "responseModalities" to LiveJson.arr(LiveJson.str("AUDIO")),
            "speechConfig" to speechConfig,
        )
        val setup = LinkedHashMap<String, JsonValue>()
        setup["model"] = LiveJson.str(config.wireModel)
        setup["generationConfig"] = generationConfig
        setup["outputAudioTranscription"] = LiveJson.obj()
        setup["inputAudioTranscription"] = LiveJson.obj()
        // Brain owns the answer => nudge Gemini toward transcription-only; responseModalities stays AUDIO
        // (native-audio requirement) and the engine discards Gemini's own generated audio/text regardless.
        if (config.brainOwnsAnswer) {
            setup["systemInstruction"] = LiveJson.obj(
                "parts" to LiveJson.arr(
                    LiveJson.obj("text" to LiveJson.str(BRAIN_OWNS_ANSWER_INSTRUCTION))
                )
            )
        }
        // Barge-in off => disable Gemini's automatic VAD so the client owns turn boundaries.
        if (!config.bargeIn) {
            setup["realtimeInputConfig"] = LiveJson.obj(
                "automaticActivityDetection" to LiveJson.obj("disabled" to LiveJson.bool(true))
            )
        }
        return LiveJson.write(LiveJson.obj("setup" to JsonValue.Obj(setup)))
    }

    // realtimeInput.audio — base64 PCM16 16kHz mono chunk.
    fun audioFrame(base64Pcm: String): String = LiveJson.write(
        LiveJson.obj(
            "realtimeInput" to LiveJson.obj(
                "audio" to LiveJson.obj(
                    "mimeType" to LiveJson.str(INPUT_MIME),
                    "data" to LiveJson.str(base64Pcm),
                )
            )
        )
    )

    // Signals the end of the user's audio turn (automatic activity detection path).
    fun audioStreamEndFrame(): String = LiveJson.write(
        LiveJson.obj("realtimeInput" to LiveJson.obj("audioStreamEnd" to LiveJson.bool(true)))
    )

    fun activityStartFrame(): String = LiveJson.write(
        LiveJson.obj("realtimeInput" to LiveJson.obj("activityStart" to LiveJson.obj()))
    )

    fun activityEndFrame(): String = LiveJson.write(
        LiveJson.obj("realtimeInput" to LiveJson.obj("activityEnd" to LiveJson.obj()))
    )

    sealed interface ServerFrame {
        data object SetupComplete : ServerFrame
        data class Audio(val base64Pcm: String) : ServerFrame
        data class OutputText(val text: String) : ServerFrame
        data class InputText(val text: String) : ServerFrame
        data object TurnComplete : ServerFrame
        data object Interrupted : ServerFrame
        data class GoAway(val timeLeft: String) : ServerFrame
        data class Failure(val kind: LiveErrorKind, val message: String) : ServerFrame
        data object Unknown : ServerFrame
    }

    // Parse one server text frame into typed frames the session engine acts on. Malformed input => Unknown, never a crash.
    fun parseServerFrame(text: String): List<ServerFrame> {
        val root = runCatching { LiveJson.parse(text) as? JsonValue.Obj }.getOrNull()
            ?: return listOf(ServerFrame.Unknown)

        root.obj("error")?.let { err ->
            val code = (err.entries["code"] as? JsonValue.Num)?.value?.toInt() ?: 0
            val msg = err.str("message").orEmpty()
            return listOf(ServerFrame.Failure(classifyServerError(code, msg), msg))
        }

        if (root.has("setupComplete")) return listOf(ServerFrame.SetupComplete)
        if (root.has("goAway")) {
            val left = root.obj("goAway")?.str("timeLeft").orEmpty()
            return listOf(ServerFrame.GoAway(left))
        }

        val content = root.obj("serverContent") ?: return listOf(ServerFrame.Unknown)
        val out = ArrayList<ServerFrame>()

        if (content.bool("interrupted") == true) out += ServerFrame.Interrupted

        content.obj("outputTranscription")?.str("text")?.takeIf { it.isNotEmpty() }
            ?.let { out += ServerFrame.OutputText(it) }
        content.obj("inputTranscription")?.str("text")?.takeIf { it.isNotEmpty() }
            ?.let { out += ServerFrame.InputText(it) }

        content.obj("modelTurn")?.arr("parts")?.items?.forEach { part ->
            val obj = part as? JsonValue.Obj ?: return@forEach
            obj.obj("inlineData")?.str("data")?.takeIf { it.isNotEmpty() }
                ?.let { out += ServerFrame.Audio(it) }
            obj.str("text")?.takeIf { it.isNotEmpty() }
                ?.let { out += ServerFrame.OutputText(it) }
        }

        if (content.bool("turnComplete") == true || content.bool("generationComplete") == true) {
            out += ServerFrame.TurnComplete
        }

        return out.ifEmpty { listOf(ServerFrame.Unknown) }
    }

    // Map a Gemini error payload onto the retry taxonomy: auth/model/config never loop; quota/transient may.
    fun classifyServerError(code: Int, message: String): LiveErrorKind {
        val m = message.lowercase()
        return when {
            code == 401 || code == 403 || m.contains("api key") || m.contains("unauthenticated") || m.contains("permission") -> LiveErrorKind.AUTH
            code == 404 || m.contains("model") && m.contains("not found") -> LiveErrorKind.INVALID_MODEL
            code == 429 || m.contains("quota") || m.contains("rate limit") || m.contains("resource_exhausted") -> LiveErrorKind.QUOTA
            code in 500..599 -> LiveErrorKind.REMOTE_CLOSE
            else -> LiveErrorKind.PROTOCOL
        }
    }
}

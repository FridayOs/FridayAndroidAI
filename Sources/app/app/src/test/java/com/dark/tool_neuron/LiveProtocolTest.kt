package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveErrorKind
import com.dark.tool_neuron.repo.gateway.live.LiveJson
import com.dark.tool_neuron.repo.gateway.live.LiveProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure Gemini Live codec proof off-device: setup/audio/end framing, server-frame parsing + ordering, error taxonomy.
class LiveProtocolTest {

    private fun config(bargeIn: Boolean = true, brainOwnsAnswer: Boolean = true) = GeminiLiveConfig(
        apiKey = "sk-secret",
        model = "gemini-2.0-flash-live-001",
        voice = "Aoede",
        locale = "en-US",
        bargeIn = bargeIn,
        baseHost = GeminiLiveConfig.DEFAULT_HOST,
        brainOwnsAnswer = brainOwnsAnswer,
    )

    @Test
    fun setupFrame_carriesModelVoiceAndAudioModality() {
        val root = LiveJson.parse(LiveProtocol.setupFrame(config())) as LiveJson.JsonValue.Obj
        val setup = root.obj("setup")!!
        assertEquals("models/gemini-2.0-flash-live-001", setup.str("model"))
        val gen = setup.obj("generationConfig")!!
        val modalities = gen.arr("responseModalities")!!.items
        assertEquals(1, modalities.size)
        assertEquals("AUDIO", (modalities[0] as LiveJson.JsonValue.Str).value)
        val voice = gen.obj("speechConfig")!!.obj("voiceConfig")!!.obj("prebuiltVoiceConfig")!!
        assertEquals("Aoede", voice.str("voiceName"))
    }

    // B1: when the Brain Gateway owns the answer, Gemini Live is STT-only — a systemInstruction tells it not to answer.
    @Test
    fun setupFrame_brainOwnsAnswerCarriesTranscribeOnlySystemInstruction() {
        val root = LiveJson.parse(LiveProtocol.setupFrame(config(brainOwnsAnswer = true))) as LiveJson.JsonValue.Obj
        val setup = root.obj("setup")!!
        val text = setup.obj("systemInstruction")!!.arr("parts")!!.items
            .filterIsInstance<LiveJson.JsonValue.Obj>().first().str("text")
        assertEquals(LiveProtocol.BRAIN_OWNS_ANSWER_INSTRUCTION, text)
        // Audio modality stays — the Live socket is still an audio transport; we simply discard its answer audio.
        assertEquals("AUDIO", (setup.obj("generationConfig")!!.arr("responseModalities")!!.items[0] as LiveJson.JsonValue.Str).value)
    }

    @Test
    fun setupFrame_brainOwnsAnswerOffOmitsSystemInstruction() {
        val root = LiveJson.parse(LiveProtocol.setupFrame(config(brainOwnsAnswer = false))) as LiveJson.JsonValue.Obj
        assertFalse(root.obj("setup")!!.has("systemInstruction"))
    }

    @Test
    fun setupFrame_bargeInOnOmitsManualActivityDetection() {
        val root = LiveJson.parse(LiveProtocol.setupFrame(config(bargeIn = true))) as LiveJson.JsonValue.Obj
        assertFalse(root.obj("setup")!!.has("realtimeInputConfig"))
    }

    @Test
    fun setupFrame_bargeInOffDisablesAutomaticActivityDetection() {
        val root = LiveJson.parse(LiveProtocol.setupFrame(config(bargeIn = false))) as LiveJson.JsonValue.Obj
        val disabled = root.obj("setup")!!.obj("realtimeInputConfig")!!
            .obj("automaticActivityDetection")!!.bool("disabled")
        assertEquals(true, disabled)
    }

    @Test
    fun audioFrame_usesPcm16kMimeAndBase64Payload() {
        val root = LiveJson.parse(LiveProtocol.audioFrame("QUJD")) as LiveJson.JsonValue.Obj
        val audio = root.obj("realtimeInput")!!.obj("audio")!!
        assertEquals("audio/pcm;rate=16000", audio.str("mimeType"))
        assertEquals("QUJD", audio.str("data"))
    }

    @Test
    fun audioStreamEndFrame_flagsTrue() {
        val root = LiveJson.parse(LiveProtocol.audioStreamEndFrame()) as LiveJson.JsonValue.Obj
        assertEquals(true, root.obj("realtimeInput")!!.bool("audioStreamEnd"))
    }

    @Test
    fun parse_setupComplete() {
        val frames = LiveProtocol.parseServerFrame("""{"setupComplete":{}}""")
        assertEquals(listOf(LiveProtocol.ServerFrame.SetupComplete), frames)
    }

    @Test
    fun parse_modelAudioPart_yieldsAudioFrame() {
        val json = """{"serverContent":{"modelTurn":{"role":"model","parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"QUJD"}}]}}}"""
        val frames = LiveProtocol.parseServerFrame(json)
        assertEquals(1, frames.size)
        assertEquals(LiveProtocol.ServerFrame.Audio("QUJD"), frames[0])
    }

    @Test
    fun parse_transcriptionFrames() {
        val out = LiveProtocol.parseServerFrame("""{"serverContent":{"outputTranscription":{"text":"hi"}}}""")
        assertEquals(LiveProtocol.ServerFrame.OutputText("hi"), out[0])
        val inp = LiveProtocol.parseServerFrame("""{"serverContent":{"inputTranscription":{"text":"hello"}}}""")
        assertEquals(LiveProtocol.ServerFrame.InputText("hello"), inp[0])
    }

    @Test
    fun parse_interruptedBeforeAnyAudio() {
        val frames = LiveProtocol.parseServerFrame("""{"serverContent":{"interrupted":true}}""")
        assertTrue(frames.contains(LiveProtocol.ServerFrame.Interrupted))
    }

    // Interrupted must be surfaced before turnComplete so the player flushes queued audio, then ends the turn.
    @Test
    fun parse_interruptOrdering_flushBeforeTurnComplete() {
        val json = """{"serverContent":{"interrupted":true,"turnComplete":true}}"""
        val frames = LiveProtocol.parseServerFrame(json)
        val interruptIdx = frames.indexOf(LiveProtocol.ServerFrame.Interrupted)
        val turnIdx = frames.indexOf(LiveProtocol.ServerFrame.TurnComplete)
        assertTrue(interruptIdx >= 0 && turnIdx >= 0)
        assertTrue("interrupt must precede turnComplete", interruptIdx < turnIdx)
    }

    @Test
    fun parse_turnComplete() {
        val frames = LiveProtocol.parseServerFrame("""{"serverContent":{"turnComplete":true}}""")
        assertTrue(frames.contains(LiveProtocol.ServerFrame.TurnComplete))
    }

    @Test
    fun parse_generationCompleteAlsoEndsTurn() {
        val frames = LiveProtocol.parseServerFrame("""{"serverContent":{"generationComplete":true}}""")
        assertTrue(frames.contains(LiveProtocol.ServerFrame.TurnComplete))
    }

    @Test
    fun parse_goAwayIsRemoteClose() {
        val frames = LiveProtocol.parseServerFrame("""{"goAway":{"timeLeft":"5s"}}""")
        assertTrue(frames[0] is LiveProtocol.ServerFrame.GoAway)
    }

    @Test
    fun parse_multipleAudioPartsPreserveOrder() {
        val json = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"AAA"}},{"inlineData":{"data":"BBB"}}]}}}"""
        val frames = LiveProtocol.parseServerFrame(json)
        assertEquals(LiveProtocol.ServerFrame.Audio("AAA"), frames[0])
        assertEquals(LiveProtocol.ServerFrame.Audio("BBB"), frames[1])
    }

    @Test
    fun parse_malformedJsonIsUnknown_neverCrashes() {
        val frames = LiveProtocol.parseServerFrame("not json at all {{{")
        assertEquals(listOf(LiveProtocol.ServerFrame.Unknown), frames)
    }

    @Test
    fun parse_errorPayloadClassifiesAuth() {
        val json = """{"error":{"code":401,"message":"API key not valid"}}"""
        val frames = LiveProtocol.parseServerFrame(json)
        val failure = frames[0] as LiveProtocol.ServerFrame.Failure
        assertEquals(LiveErrorKind.AUTH, failure.kind)
    }

    @Test
    fun classifyServerError_taxonomy() {
        assertEquals(LiveErrorKind.AUTH, LiveProtocol.classifyServerError(403, "permission denied"))
        assertEquals(LiveErrorKind.INVALID_MODEL, LiveProtocol.classifyServerError(404, "model not found"))
        assertEquals(LiveErrorKind.QUOTA, LiveProtocol.classifyServerError(429, "quota exceeded"))
        assertEquals(LiveErrorKind.REMOTE_CLOSE, LiveProtocol.classifyServerError(503, "unavailable"))
        assertEquals(LiveErrorKind.PROTOCOL, LiveProtocol.classifyServerError(0, "weird"))
    }
}

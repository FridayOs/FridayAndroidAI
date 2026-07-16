package com.dark.tool_neuron

import com.dark.tool_neuron.repo.gateway.GatewayEvent
import com.dark.tool_neuron.repo.gateway.GatewayTurn
import com.dark.tool_neuron.repo.gateway.live.BrainCorrelation
import com.dark.tool_neuron.repo.gateway.live.GeminiLiveConfig
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSink
import com.dark.tool_neuron.repo.gateway.live.LiveAudioSource
import com.dark.tool_neuron.repo.gateway.live.LiveBrainGateway
import com.dark.tool_neuron.repo.gateway.live.LiveCloudBridge
import com.dark.tool_neuron.repo.gateway.live.LiveEvent
import com.dark.tool_neuron.repo.gateway.live.LiveSessionEngine
import com.dark.tool_neuron.repo.gateway.live.LiveTransport
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceSession
import com.dark.tool_neuron.repo.gateway.live.LiveVoiceSynthesizer
import com.dark.tool_neuron.repo.gateway.live.SpeakOutcome
import com.dark.tool_neuron.repo.gateway.live.SynthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// B1 proof: when the Brain Gateway owns the answer, Gemini Live's OWN generated audio is discarded (never
// enqueued into the player), and the Brain's text answer is the ONLY thing vocalized — synthesized via the
// Voice Gateway (LiveVoiceSynthesizer) and enqueued into the same sink. Off-device, no Android audio/network.
@OptIn(ExperimentalCoroutinesApi::class)
class LiveVoiceBrainAnswerContractTest {

    private class RecordingSink : LiveAudioSink {
        val enqueued = mutableListOf<ByteArray>()
        val played = mutableListOf<ByteArray>()
        private var gen = 0L
        override fun start(onError: (Throwable) -> Unit) {}
        override fun currentGeneration(): Long = gen
        override fun enqueue(pcm: ByteArray, gen: Long) { enqueued += pcm }
        // Brain-owns-answer path plays TTS PCM to completion in the same sink; record it distinctly.
        override suspend fun playToCompletion(pcm: ByteArray, gen: Long): Boolean { played += pcm; return true }
        override fun flush() { gen++ }
        override fun stop() {}
    }

    private class NoopSource : LiveAudioSource {
        override fun hasPermission(): Boolean = true
        override fun start(scope: CoroutineScope, onChunk: (ByteArray) -> Unit, onError: (Throwable) -> Unit): Boolean = true
        override fun stop() {}
    }

    // Maps text -> its UTF-8 bytes so the test can assert exactly which Brain text reached the sink.
    private class FakeSynthesizer : LiveVoiceSynthesizer {
        var lastText: String? = null
        var result: (String) -> SynthResult = { SynthResult.Audio(it.toByteArray(Charsets.UTF_8)) }
        override suspend fun synthesize(config: GeminiLiveConfig, text: String): SynthResult {
            lastText = text
            return result(text)
        }
    }

    private class FakeBrainGateway : LiveBrainGateway {
        override fun brainTurn(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> =
            kotlinx.coroutines.flow.flow { emit(GatewayEvent.Done("")) }
        override fun brainContinue(correlation: BrainCorrelation, history: List<GatewayTurn>): Flow<GatewayEvent> =
            brainTurn(correlation, history)
        override fun brainCancel(correlation: BrainCorrelation) {}
        override fun brainConfirm(correlation: BrainCorrelation): Boolean = true
        override fun brainAwaitingConfirmation(): Boolean = false
    }

    private class FakeTransport(private val frames: List<LiveTransport.Incoming>) : LiveTransport {
        override fun open(host: String, port: Int, path: String, onReady: () -> Unit): Flow<LiveTransport.Incoming> = callbackFlow {
            onReady()
            frames.forEach { trySend(it) }
            close()
            awaitClose { }
        }
        override fun sendText(text: String) {}
        override fun close() {}
    }

    private fun config(brainOwnsAnswer: Boolean) = GeminiLiveConfig(
        apiKey = "sk-secret", model = "gemini-2.0-flash-live-001", voice = "Aoede",
        locale = "en-US", bargeIn = true, baseHost = GeminiLiveConfig.DEFAULT_HOST,
        brainOwnsAnswer = brainOwnsAnswer,
    )

    // Gemini emits its own audio part; brain-owns-answer mode must NOT play it (no AudioDelta, nothing enqueued).
    // Input transcription still flows through, proving the engine processes frames and specifically drops the audio.
    @Test
    fun geminiOwnAudio_isDiscarded_whenBrainOwnsAnswer() = runTest {
        val sink = RecordingSink()
        val engine = LiveSessionEngine(
            transportFactory = {
                FakeTransport(
                    listOf(
                        LiveTransport.Incoming.Text("""{"setupComplete":{}}"""),
                        LiveTransport.Incoming.Text("""{"serverContent":{"modelTurn":{"role":"model","parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"QUJD"}}]}}}"""),
                        LiveTransport.Incoming.Text("""{"serverContent":{"inputTranscription":{"text":"hi"}}}"""),
                        LiveTransport.Incoming.Closed(1000, ""),
                    ),
                )
            },
            capture = NoopSource(),
            player = sink,
        )

        val events = engine.run(config(brainOwnsAnswer = true)).toList()

        assertTrue("input transcription must flow through", events.any { it is LiveEvent.InputTranscript && it.text == "hi" })
        assertTrue("Gemini's own answer audio must NOT be emitted", events.none { it is LiveEvent.AudioDelta })
        assertTrue("Gemini's own answer audio must NOT be enqueued for playback", sink.enqueued.isEmpty())
    }

    // The Brain answer text is vocalized via the Voice Gateway synthesizer and enqueued into the same sink.
    @Test
    fun brainAnswerText_isSynthesizedAndEnqueued() = runTest {
        val sink = RecordingSink()
        val synth = FakeSynthesizer()
        val session = LiveVoiceSession(
            engine = LiveSessionEngine({ FakeTransport(emptyList()) }, NoopSource(), sink),
            config = config(brainOwnsAnswer = true),
            sessionId = "s1",
            cloudBridge = LiveCloudBridge(FakeBrainGateway(), "s1"),
            synthesizer = synth,
            sink = sink,
        )

        val outcome = session.speak("HELLO")

        assertEquals(SpeakOutcome.Completed, outcome)
        assertEquals("HELLO", synth.lastText)
        assertEquals(1, sink.played.size)
        assertEquals("HELLO", String(sink.played.first(), Charsets.UTF_8))
    }

    // An empty synthesis (blank text / provider miss) plays nothing — the turn degrades honestly, no silence-as-audio.
    @Test
    fun emptySynthesis_playsNothing() = runTest {
        val sink = RecordingSink()
        val synth = FakeSynthesizer().apply { result = { SynthResult.Empty } }
        val session = LiveVoiceSession(
            engine = LiveSessionEngine({ FakeTransport(emptyList()) }, NoopSource(), sink),
            config = config(brainOwnsAnswer = true),
            sessionId = "s1",
            cloudBridge = LiveCloudBridge(FakeBrainGateway(), "s1"),
            synthesizer = synth,
            sink = sink,
        )

        val outcome = session.speak("")

        assertEquals(SpeakOutcome.Empty, outcome)
        assertTrue(sink.played.isEmpty())
    }

    // A provider failure surfaces as a typed Failed outcome (sanitized) — never faked as success, never silent.
    @Test
    fun failedSynthesis_returnsFailedOutcome_playsNothing() = runTest {
        val sink = RecordingSink()
        val synth = FakeSynthesizer().apply { result = { SynthResult.Failed("HTTP 429") } }
        val session = LiveVoiceSession(
            engine = LiveSessionEngine({ FakeTransport(emptyList()) }, NoopSource(), sink),
            config = config(brainOwnsAnswer = true),
            sessionId = "s1",
            cloudBridge = LiveCloudBridge(FakeBrainGateway(), "s1"),
            synthesizer = synth,
            sink = sink,
        )

        val outcome = session.speak("boom")

        assertTrue(outcome is SpeakOutcome.Failed)
        assertEquals("HTTP 429", (outcome as SpeakOutcome.Failed).message)
        assertTrue(sink.played.isEmpty())
    }
}

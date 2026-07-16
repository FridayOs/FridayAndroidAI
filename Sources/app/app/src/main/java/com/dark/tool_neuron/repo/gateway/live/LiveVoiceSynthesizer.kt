package com.dark.tool_neuron.repo.gateway.live

// Text -> PCM seam that vocalizes the Brain Gateway's answer through the Voice Gateway. Pure interface so the
// B1 contract test proves "the audible payload derives from Brain output" off-device with a fake impl + fake sink.
//
// Returns PCM16 mono 24kHz bytes (identical to LiveAudioSink playback format) or null on failure/empty text.
// The config carries the direct-to-host credentials (apiKey/baseHost) + the chosen voice/locale — never a proxy.
interface LiveVoiceSynthesizer {
    suspend fun synthesize(config: GeminiLiveConfig, text: String): ByteArray?
}

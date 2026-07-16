package com.dark.tool_neuron.repo.gateway.live

// Text -> PCM seam that vocalizes the Brain Gateway's answer through the Voice Gateway. Pure interface so the
// B1 contract test proves "the audible payload derives from Brain output" off-device with a fake impl + fake sink.
//
// Returns a typed SynthResult so the VM can distinguish a real failure (surface as Error) from empty text
// (benign) — the old ByteArray? swallowed provider failures as null (indistinguishable from empty). PCM is
// PCM16 mono 24kHz (identical to LiveAudioSink playback format). The config carries the direct-to-host
// credentials (apiKey/baseHost) + chosen voice/locale — never a proxy.
interface LiveVoiceSynthesizer {
    suspend fun synthesize(config: GeminiLiveConfig, text: String): SynthResult
}

// Empty = nothing to speak (blank text / benign). Failed carries a sanitized, key-free message for the UI.
sealed interface SynthResult {
    data class Audio(val pcm: ByteArray) : SynthResult
    data object Empty : SynthResult
    data class Failed(val message: String) : SynthResult
}

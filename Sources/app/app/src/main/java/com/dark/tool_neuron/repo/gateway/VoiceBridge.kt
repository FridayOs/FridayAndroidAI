package com.dark.tool_neuron.repo.gateway

// Voice layer ↔ Brain Gateway contract. The Voice Gateway handles audio; the
// Brain Gateway handles thinking. This file is the only place where the two
// meet — the router and the voice VM call into it instead of talking to each
// other or the providers directly.
//
// Cloud voice: the active Voice Gateway streams realtime audio to its own
// realtime endpoint. The transcript is then bridged to the active Brain Gateway
// via brain_turn — the brain never sees audio, only the text transcript.
//
// Local voice: on-device STT produces the transcript, brain_turn runs against
// the active Brain Gateway (cloud or local), and on-device TTS speaks the
// reply. The brain still does the thinking; the audio side is on this phone.

sealed interface VoiceTurn {
    data class Transcript(val text: String, val fromCloud: Boolean) : VoiceTurn
    data class Error(val message: String) : VoiceTurn
}

// Cloud voice path — does not exist on this phone. The realtime-audio endpoints
// (OpenAI Realtime, Gemini Live) require a separate bidirectional socket which is
// out of scope for M2-02; cloud voice is exposed as Unavailable until that
// socket ships. Until then, the design only requires that the router routes
// voice picks (CLOUD) to a path that requires the realtime socket — i.e. an
// explicit Unavailable the screen can surface, not a silent fall-through to
// local STT.
//
// This is the contract the M2-02 role router ships: voice gateway selection is
// independent of brain selection, voice picks a route, and the user-visible
// state is honest about whether realtime cloud audio is supported yet.
sealed interface CloudVoiceCapability {
    data object Unavailable : CloudVoiceCapability
}
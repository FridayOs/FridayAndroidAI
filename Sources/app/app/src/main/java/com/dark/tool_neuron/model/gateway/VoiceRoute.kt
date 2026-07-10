package com.dark.tool_neuron.model.gateway

// How a Voice Gateway handles audio. CLOUD streams audio to a provider that does
// realtime speech itself; LOCAL uses on-device STT/TTS. Either way the reasoning
// is bridged to the active Brain Gateway — audio handling and thinking are split.
enum class VoiceRoute { CLOUD, LOCAL }

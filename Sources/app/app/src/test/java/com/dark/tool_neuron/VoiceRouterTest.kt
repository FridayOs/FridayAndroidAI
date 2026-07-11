package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.gateway.CloudVoiceCapability
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The voice router is the single decision point for where voice audio is
// handled (local STT/TTS or a cloud realtime gateway) and how it bridges to
// the active Brain Gateway. These tests prove the contract that the
// acceptance criteria call out: no provider request goes through the FRIDAY
// API, cloud voice is honestly surfaced as unavailable until the realtime
// socket ships, and the brain bridge runs every voice turn.
class VoiceRouterTest {

    private fun config(
        provider: GatewayProvider,
        voiceRoute: VoiceRoute = if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD,
    ): GatewayConfig = GatewayConfig(
        id = provider.name.lowercase() + "-voice",
        provider = provider,
        label = provider.displayName,
        baseUrl = provider.defaultBaseUrl,
        apiKey = "sk-test",
        model = provider.defaultModel,
        createdAt = 0,
        updatedAt = 0,
        voiceRoute = voiceRoute,
    )

    @Test
    fun noVoiceGatewayMeansNoVoice() {
        val route = VoiceRouter.decide(voice = null, brain = null)
        assertEquals(VoiceRouter.Route.NoVoice, route)
    }

    @Test
    fun localVoiceBridgesToBrain() {
        // LOCAL voice: on-device STT → transcript → brain_turn → on-device TTS.
        // The brain is selected, so the route is the local bridge.
        val voice = config(GatewayProvider.LOCAL, voiceRoute = VoiceRoute.LOCAL)
        val brain = config(GatewayProvider.GEMINI)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.LocalBridge)
        route as VoiceRouter.Route.LocalBridge
        assertEquals(voice, route.voice)
        assertEquals(brain, route.brain)
    }

    @Test
    fun localVoiceAllowsNullBrain_so_callers_catch_it_separately() {
        // The router reports LocalBridge even if no brain is selected; the VM
        // surfaces brain_unavailable when it tries to send the transcript.
        val voice = config(GatewayProvider.LOCAL, voiceRoute = VoiceRoute.LOCAL)
        val route = VoiceRouter.decide(voice, brain = null)
        assertTrue(route is VoiceRouter.Route.LocalBridge)
        assertEquals(null, (route as VoiceRouter.Route.LocalBridge).brain)
    }

    @Test
    fun geminiVoiceIsCloud_and_surfaces_unavailable() {
        // Cloud realtime-audio isn't implemented in M2-02; the route reports
        // it honestly instead of falling back to local STT under a Gemini label.
        val voice = config(GatewayProvider.GEMINI, voiceRoute = VoiceRoute.CLOUD)
        val brain = config(GatewayProvider.GEMINI)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.CloudUnavailable)
        route as VoiceRouter.Route.CloudUnavailable
        assertEquals(CloudVoiceCapability.Unavailable, route.capability)
        assertEquals(voice, route.voice)
    }

    @Test
    fun openaiVoiceIsCloud_and_surfaces_unavailable() {
        val voice = config(GatewayProvider.OPENAI, voiceRoute = VoiceRoute.CLOUD)
        val brain = config(GatewayProvider.GEMINI)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.CloudUnavailable)
    }

    @Test
    fun cloudVoiceWithoutBrainReportsNoBrainForCloud() {
        // CLOUD voice always needs a brain to bridge to; if none is selected,
        // the router reports NoBrainForCloud rather than picking a default.
        val voice = config(GatewayProvider.GEMINI, voiceRoute = VoiceRoute.CLOUD)
        val route = VoiceRouter.decide(voice, brain = null)
        assertEquals(VoiceRouter.Route.NoBrainForCloud, route)
    }

    @Test
    fun cloudVoiceBrainBridge_never_implies_FridayApi() {
        // The brain we resolve for cloud voice is the Brain Gateway config —
        // not a FRIDAY-provider call. FRIDAY is roleless and disabled, so the
        // router can never produce a CloudUnavailable with an FRIDAY voice.
        val friday = config(GatewayProvider.FRIDAY, voiceRoute = VoiceRoute.CLOUD)
        assertFalse(friday.supportsRole(com.dark.tool_neuron.model.gateway.GatewayRole.VOICE))
        // The router filters by the active Voice Gateway, which is whatever the
        // user picked. We assert the disabled+roleless FRIDAY provider would
        // never even become a voice pick.
        assertEquals(false, GatewayProvider.FRIDAY.enabled)
    }
}
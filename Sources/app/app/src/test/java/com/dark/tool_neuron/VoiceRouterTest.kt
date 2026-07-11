package com.dark.tool_neuron

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.gateway.GatewayRoleRouter
import com.dark.tool_neuron.repo.gateway.VoiceRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(VoiceRouter.Route.NoVoice, VoiceRouter.decide(voice = null, brain = null))
    }

    @Test
    fun anyVoiceWithoutBrainReportsNoBrain() {
        val voice = config(GatewayProvider.LOCAL)
        assertEquals(VoiceRouter.Route.NoBrain, VoiceRouter.decide(voice, brain = null))
    }

    @Test
    fun localVoiceBridgesToBrain() {
        val voice = config(GatewayProvider.LOCAL, voiceRoute = VoiceRoute.LOCAL)
        val brain = config(GatewayProvider.GEMINI)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.LocalBridge)
        route as VoiceRouter.Route.LocalBridge
        assertEquals(voice, route.voice)
        assertEquals(brain, route.brain)
    }

    @Test
    fun geminiVoiceIsCloudBridgeToBrain() {
        val voice = config(GatewayProvider.GEMINI, voiceRoute = VoiceRoute.CLOUD)
        val brain = config(GatewayProvider.OPENAI)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.CloudBridge)
        route as VoiceRouter.Route.CloudBridge
        assertEquals(voice, route.voice)
        assertEquals(brain, route.brain)
    }

    @Test
    fun openaiVoiceIsCloudBridgeToBrain() {
        val voice = config(GatewayProvider.OPENAI, voiceRoute = VoiceRoute.CLOUD)
        val brain = config(GatewayProvider.ANTHROPIC)
        val route = VoiceRouter.decide(voice, brain)
        assertTrue(route is VoiceRouter.Route.CloudBridge)
        assertEquals(brain, (route as VoiceRouter.Route.CloudBridge).brain)
    }

    // Every voice mode bridges to the active Brain Gateway, and that brain resolves to a
    // direct Cloud/Local brain route — never a FRIDAY-API call.
    @Test
    fun everyVoiceModeBridgesToBrain_neverFridayApi() {
        val brain = config(GatewayProvider.OPENAI)
        listOf(
            config(GatewayProvider.GEMINI, VoiceRoute.CLOUD),
            config(GatewayProvider.OPENAI, VoiceRoute.CLOUD),
            config(GatewayProvider.LOCAL, VoiceRoute.LOCAL),
        ).forEach { voice ->
            val route = VoiceRouter.decide(voice, brain)
            val bridged = when (route) {
                is VoiceRouter.Route.LocalBridge -> route.brain
                is VoiceRouter.Route.CloudBridge -> route.brain
                else -> error("expected a bridge route for $voice")
            }
            assertEquals("voice ${voice.provider} must bridge to the active brain", brain, bridged)
            val brainRoute = GatewayRoleRouter.decideBrain(bridged, localModel = null)
            assertTrue(
                "bridged brain must be a direct cloud route, never FRIDAY-API",
                brainRoute is GatewayRoleRouter.BrainRoute.Cloud,
            )
        }
    }

    @Test
    fun fridayCannotBeAVoiceGateway() {
        assertFalse(GatewayProvider.FRIDAY.enabled)
        assertFalse(GatewayProvider.FRIDAY.supportsRole(GatewayRole.VOICE))
    }
}

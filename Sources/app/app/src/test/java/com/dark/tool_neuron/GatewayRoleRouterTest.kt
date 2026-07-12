package com.dark.tool_neuron

import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.gateway.GatewayRoleRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayRoleRouterTest {

    private fun config(
        provider: GatewayProvider,
        voiceRoute: VoiceRoute = if (provider.isLocal) VoiceRoute.LOCAL else VoiceRoute.CLOUD,
    ): GatewayConfig = GatewayConfig(
        id = provider.name.lowercase() + "-id",
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
    fun noBrainMeansNone() {
        assertEquals(GatewayRoleRouter.BrainRoute.None, GatewayRoleRouter.decideBrain(null, null))
    }

    @Test
    fun cloudBrainAlwaysGoesToCloud() {
        val brain = config(GatewayProvider.OPENAI)
        val route = GatewayRoleRouter.decideBrain(brain, null)
        assertTrue("cloud brain must bypass local", route is GatewayRoleRouter.BrainRoute.Cloud)
        assertEquals(brain, (route as GatewayRoleRouter.BrainRoute.Cloud).config)
    }

    @Test
    fun anthropicBrainDoesNotRouteThroughFridayApi() {
        // Anthropic brain must classify as Cloud (direct Android → provider), never FRIDAY-API.
        val route = GatewayRoleRouter.decideBrain(config(GatewayProvider.ANTHROPIC), null)
        assertTrue(route is GatewayRoleRouter.BrainRoute.Cloud)
        assertFalse(
            "FRIDAY provider must never be a valid cloud brain",
            GatewayProvider.FRIDAY.supportsRole(GatewayRole.BRAIN),
        )
    }

    @Test
    fun localBrainWithNoInstalledModelIsUnavailable() {
        val brain = config(GatewayProvider.LOCAL)
        val route = GatewayRoleRouter.decideBrain(brain, localModel = null)
        assertTrue(route is GatewayRoleRouter.BrainRoute.Unavailable)
        assertEquals(
            GatewayRoleRouter.BrainRoute.Reason.NO_LOCAL_MODEL,
            (route as GatewayRoleRouter.BrainRoute.Unavailable).reason,
        )
    }

    @Test
    fun localBrainWithModelRoutesToLocal() {
        val brain = config(GatewayProvider.LOCAL)
        val model = ModelInfo(
            id = "gguf-1",
            name = "Test",
            path = "/sdcard/test.gguf",
            pathType = PathType.FILE,
        )
        val route = GatewayRoleRouter.decideBrain(brain, model)
        assertTrue(route is GatewayRoleRouter.BrainRoute.Local)
        assertEquals(model, (route as GatewayRoleRouter.BrainRoute.Local).model)
    }

    @Test
    fun localBrainDoesNotRequireApiKeyValidation() {
        // LOCAL provider is keyless by design — even an empty apiKey is fine.
        val brain = config(GatewayProvider.LOCAL).copy(apiKey = "")
        val model = ModelInfo(
            id = "gguf-1", name = "Test", path = "/x", pathType = PathType.FILE,
        )
        val route = GatewayRoleRouter.decideBrain(brain, model)
        assertTrue(route is GatewayRoleRouter.BrainRoute.Local)
    }

    @Test
    fun fridayProviderIsRolelessAndCannotBecomeBrain() {
        val friday = config(GatewayProvider.FRIDAY)
        assertFalse(friday.supportsRole(GatewayRole.BRAIN))
        // Roleless FRIDAY config never classifies — excluded by supportsRole upstream.
        val route = GatewayRoleRouter.decideBrain(friday, null)
        assertEquals(GatewayRoleRouter.BrainRoute.None, route)
    }
}
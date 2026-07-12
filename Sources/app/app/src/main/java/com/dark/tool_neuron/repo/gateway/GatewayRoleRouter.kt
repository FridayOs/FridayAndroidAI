package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import com.dark.tool_neuron.viewmodel.home_vm.ModelLoadState
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayRoleRouter @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
    private val client: DirectGatewayClient,
    private val modelRepo: ModelRepository,
    private val modelSession: ModelSessionManager,
) : BrainBridge, ChatBrain {
    sealed interface BrainRoute {
        data class Cloud(val config: GatewayConfig) : BrainRoute
        data class Local(val config: GatewayConfig, val model: ModelInfo) : BrainRoute
        data class Unavailable(val reason: Reason) : BrainRoute
        data object None : BrainRoute

        enum class Reason { NO_BRAIN, NO_LOCAL_MODEL }
    }

    private val confirmationGate = ConfirmationGate()
    val awaitingConfirmation: StateFlow<Boolean> = confirmationGate.awaiting

    fun hasBrain(): Boolean = gatewayRepo.brainGateway() != null

    override fun brainGateway(): GatewayConfig? = gatewayRepo.brainGateway()

    override fun brainUnavailableMessage(): String = when (val route = resolveBrain()) {
        is BrainRoute.Local, is BrainRoute.Cloud -> ""
        is BrainRoute.Unavailable -> unavailableMessage(route.reason)
        BrainRoute.None -> ERR_NO_BRAIN
    }

    fun voiceRouteMode(): VoiceRoute? = gatewayRepo.voiceGateway()?.voiceRoute

    fun voiceGateway(): GatewayConfig? = gatewayRepo.voiceGateway()

    // Resolved per turn so a mid-turn selection change never disturbs a running stream.
    fun resolveBrain(): BrainRoute = decideBrain(gatewayRepo.brainGateway(), firstLocalModel())

    override fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = flow {
        when (val route = resolveBrain()) {
            is BrainRoute.Cloud -> emitAll(client.stream(route.config, history))
            is BrainRoute.Local -> emitLocal(route.model, history)
            is BrainRoute.Unavailable -> emit(GatewayEvent.Error(unavailableMessage(route.reason)))
            BrainRoute.None -> emit(GatewayEvent.Error(unavailableMessage(BrainRoute.Reason.NO_BRAIN)))
        }
    }

    // brain_continue re-runs over the existing history without injecting a new user message.
    override fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brainTurn(history)

    // Cancelling the collecting Job stops cloud; also stop the engine and fail any parked confirmation.
    override fun brainCancel() {
        confirmationGate.cancel()
        modelSession.stopGeneration()
    }

    // Arm a confirmation gate a turn awaits before an action; brainConfirm/brainCancel resolve the latch.
    fun requireConfirmation(): Deferred<Boolean> = confirmationGate.arm()

    // Confirm the parked action. Returns false when nothing is awaiting confirmation.
    override fun brainConfirm(): Boolean = confirmationGate.confirm()

    override fun brainAwaitingConfirmation(): Boolean = confirmationGate.awaiting.value

    private suspend fun FlowCollector<GatewayEvent>.emitLocal(model: ModelInfo, history: List<GatewayTurn>) {
        val loaded = ensureLocalLoaded(model)
        if (!loaded) {
            emit(GatewayEvent.Error(unavailableMessage(BrainRoute.Reason.NO_LOCAL_MODEL)))
            return
        }
        val builder = StringBuilder()
        var done = false
        val messagesJson = buildLocalMessagesJson(history)
        InferenceClient.generateMultiTurn(messagesJson, modelSession.maxTokens.value).collect { event ->
            when (event) {
                is InferenceEvent.Token -> {
                    builder.append(event.text)
                    emit(GatewayEvent.Delta(event.text))
                }
                is InferenceEvent.Error -> { done = true; emit(GatewayEvent.Error(event.message)) }
                is InferenceEvent.Done -> { done = true; emit(GatewayEvent.Done(builder.toString())) }
                else -> {}
            }
        }
        if (!done) emit(GatewayEvent.Done(builder.toString()))
    }

    private suspend fun ensureLocalLoaded(model: ModelInfo): Boolean {
        val state = modelSession.loadState.value
        if (state is ModelLoadState.Active && state.modelId == model.id) return true
        modelSession.load(model)
        return modelSession.loadState.value.let { it is ModelLoadState.Active && it.modelId == model.id }
    }

    private fun firstLocalModel(): ModelInfo? {
        val models = modelRepo.models.value.filter { it.providerType == ProviderType.GGUF }
        return models.firstOrNull { it.isActive } ?: models.firstOrNull()
    }

    private fun buildLocalMessagesJson(history: List<GatewayTurn>): String {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        return arr.toString()
    }

    private fun unavailableMessage(reason: BrainRoute.Reason): String = when (reason) {
        BrainRoute.Reason.NO_BRAIN -> ERR_NO_BRAIN
        BrainRoute.Reason.NO_LOCAL_MODEL -> ERR_NO_LOCAL_MODEL
    }

    companion object {
        const val ERR_NO_BRAIN = "No Brain Gateway selected"
        const val ERR_NO_LOCAL_MODEL = "No local model installed for the Local brain"

        // Pure so it is unit-testable off-device; a roleless config (FRIDAY) never becomes a route.
        fun decideBrain(brain: GatewayConfig?, localModel: ModelInfo?): BrainRoute {
            if (brain == null) return BrainRoute.None
            if (!brain.supportsRole(GatewayRole.BRAIN)) return BrainRoute.None
            if (!brain.provider.isLocal) return BrainRoute.Cloud(brain)
            return localModel?.let { BrainRoute.Local(brain, it) }
                ?: BrainRoute.Unavailable(BrainRoute.Reason.NO_LOCAL_MODEL)
        }
    }
}

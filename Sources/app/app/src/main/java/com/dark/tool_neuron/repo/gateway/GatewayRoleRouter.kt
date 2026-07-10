package com.dark.tool_neuron.repo.gateway

import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.VoiceRoute
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
import com.dark.tool_neuron.viewmodel.home_vm.ModelLoadState
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// The role router is the single decision point for where a turn goes. It keeps
// the active Brain Gateway (chat, reasoning, tools, actions) independent of the
// active Voice Gateway (audio). Provider/model changes take effect from the next
// resolved turn — a running turn already captured its route and is untouched.
//
// The brain bridge (brain_turn / brain_continue / brain_cancel / brain_confirm)
// is how the voice layer reaches the brain: cloud or local voice both bridge the
// transcript here rather than answering audio on their own.
//
// Cloud brains stream through DirectGatewayClient (direct from Android, no FRIDAY
// proxy). Local brains stream through the on-device InferenceClient using the
// installed GGUF model — no cloud key required. Both are normalised to
// GatewayEvent so callers see one event type.
@Singleton
class GatewayRoleRouter @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
    private val client: DirectGatewayClient,
    private val modelRepo: ModelRepository,
    private val modelSession: ModelSessionManager,
) {
    sealed interface BrainRoute {
        data class Cloud(val config: GatewayConfig) : BrainRoute
        data class Local(val config: GatewayConfig, val model: ModelInfo) : BrainRoute
        // A brain is selected but its route can't run yet (local provider with no
        // installed model). The reason is a stable key the UI can localise.
        data class Unavailable(val reason: Reason) : BrainRoute
        data object None : BrainRoute

        enum class Reason { NO_BRAIN, NO_LOCAL_MODEL }
    }

    fun hasBrain(): Boolean = gatewayRepo.brainGateway() != null

    // The active Brain Gateway config, or null when none is selectable.
    fun brainGateway(): GatewayConfig? = gatewayRepo.brainGateway()

    // A sanitized, ready-to-show reason the brain can't run right now.
    fun brainUnavailableMessage(): String = when (val route = resolveBrain()) {
        is BrainRoute.Local, is BrainRoute.Cloud -> ""
        is BrainRoute.Unavailable -> unavailableMessage(route.reason)
        BrainRoute.None -> ERR_NO_BRAIN
    }

    // The Voice Gateway's route mode, or null when no voice gateway is selected.
    fun voiceRouteMode(): VoiceRoute? = gatewayRepo.voiceGateway()?.voiceRoute

    fun voiceGateway(): GatewayConfig? = gatewayRepo.voiceGateway()

    // Resolve the active brain once. Callers resolve per turn, so a mid-turn
    // selection change never disturbs an in-flight stream.
    fun resolveBrain(): BrainRoute {
        val brain = gatewayRepo.brainGateway() ?: return BrainRoute.Unavailable(BrainRoute.Reason.NO_BRAIN)
        if (!brain.provider.isLocal) return BrainRoute.Cloud(brain)
        val model = firstLocalModel() ?: return BrainRoute.Unavailable(BrainRoute.Reason.NO_LOCAL_MODEL)
        return BrainRoute.Local(brain, model)
    }

    // brain_turn: run one brain turn for the given history. This is the bridge
    // entry point voice uses after producing a transcript, and what chat uses on
    // send. Emits Delta*/Done, or a single Error.
    fun brainTurn(history: List<GatewayTurn>): Flow<GatewayEvent> = flow {
        when (val route = resolveBrain()) {
            is BrainRoute.Cloud -> emitAll(client.stream(route.config, history))
            is BrainRoute.Local -> emitLocal(route.model, history)
            is BrainRoute.Unavailable -> emit(GatewayEvent.Error(unavailableMessage(route.reason)))
            BrainRoute.None -> emit(GatewayEvent.Error(unavailableMessage(BrainRoute.Reason.NO_BRAIN)))
        }
    }

    // brain_continue: continue reasoning with the accumulated history. In this
    // phase continuation is a fresh turn over the same conversation — the history
    // list already carries prior turns, so the semantics match a follow-up turn.
    fun brainContinue(history: List<GatewayTurn>): Flow<GatewayEvent> = brainTurn(history)

    // brain_cancel: stop an in-flight brain turn. Cloud turns cancel by cancelling
    // the collecting coroutine; local turns also need the engine told to stop.
    fun brainCancel() {
        modelSession.stopGeneration()
    }

    // brain_confirm: acknowledge a pending brain action. Tool/action execution is
    // out of scope for this phase, so there is nothing to commit yet — the hook
    // exists so the bridge contract is complete and callers have a stable name.
    fun brainConfirm() { /* no pending-action queue in this phase */ }

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
        // If the engine flow completed without a terminal Done/Error (older paths),
        // still close out the turn so the caller isn't left thinking.
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
    }
}

package com.dark.tool_neuron.viewmodel

import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayRole
import com.dark.tool_neuron.repo.GatewayConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Narrow read seam so FridayVoiceViewModel never depends on the concrete HXS-backed repository
// (mirrors VoicePrefsPort) — keeps the VM's dual-constructor test-friendly pattern intact.
interface VoiceGatewayStatePort {
    val voiceGateway: StateFlow<GatewayConfig?>
    val brainConfigured: StateFlow<Boolean>
}

// App-lifetime singleton adapter: the combine runs for the process lifetime, same scope shape as
// other Hilt singletons reacting to repository StateFlows (no per-ViewModel scope to hook into here).
class GatewayConfigVoiceGatewayStatePort @Inject constructor(
    repo: GatewayConfigRepository,
) : VoiceGatewayStatePort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val voiceGateway: StateFlow<GatewayConfig?> =
        combine(repo.gateways, repo.voiceId) { list, id ->
            list.firstOrNull { it.id == id && it.supportsRole(GatewayRole.VOICE) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override val brainConfigured: StateFlow<Boolean> =
        combine(repo.gateways, repo.brainId) { list, id ->
            list.any { it.id == id && it.supportsRole(GatewayRole.BRAIN) }
        }.stateIn(scope, SharingStarted.Eagerly, false)
}

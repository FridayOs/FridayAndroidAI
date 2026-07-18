package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.repo.GatewayConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/*
 * VM for FridayProviderSelectorSheet (FRI-574 Phase 2). Reads
 * GatewayConfigRepository directly (same source of truth as
 * OnboardingProvidersViewModel) so the sheet never sees stale state.
 *
 * Toast: emits the raw provider display name over a Channel, mirroring
 * AccountViewModel.snackbar — this VM does not localize/format the final
 * string (not @Composable). The host screen (Phase 4) formats it via
 * stringResource(R.string.friday_provider_switched_toast, name) and renders
 * it with whatever snackbar/toast mechanism that screen already uses.
 */
@HiltViewModel
class FridayProviderSelectorViewModel @Inject constructor(
    private val gatewayRepo: GatewayConfigRepository,
) : ViewModel() {

    val gateways: StateFlow<List<GatewayConfig>> = gatewayRepo.gateways
    val brainId: StateFlow<String> = gatewayRepo.brainId

    private val _providerSwitched = Channel<String>(capacity = Channel.BUFFERED)
    val providerSwitched = _providerSwitched.receiveAsFlow()

    fun select(id: String) {
        if (id == brainId.value) return
        val target = gateways.value.find { it.id == id } ?: return
        gatewayRepo.selectBrain(id)
        _providerSwitched.trySend(target.provider.displayName)
    }
}

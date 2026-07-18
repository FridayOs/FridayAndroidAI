package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.repo.ActiveConversationStore
import com.dark.tool_neuron.repo.DefaultActiveConversationStore
import com.dark.tool_neuron.repo.FridayConvoStore
import com.dark.tool_neuron.repo.GatewayConfigRepository
import com.dark.tool_neuron.repo.context.LocaleSource
import com.dark.tool_neuron.repo.gateway.GatewayLabel
import com.dark.tool_neuron.repo.gateway.GatewayLabelResolver
import com.dark.tool_neuron.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/*
 * Sidebar "Recent conversations" row model (FRI-574 phase 3, design spec §2).
 */
data class RecentConvoUi(
    val id: String,
    val title: String,
    val relativeTime: String,
    val providerLabel: String?,
    val providerLabelRes: Int?,
    val isActive: Boolean,
)

private const val INITIAL_PAGE = 6
private const val PAGE_STEP = 4

/*
 * Client-side windowing over FridayConvoStore.conversations (FRI-574 phase 3). No
 * backend pagination endpoint exists — the store already holds the full, HXS-loaded,
 * updatedAt-desc list, so this VM only slices a growing prefix (6, then +4 per
 * loadMore()) and joins per-row provider label (GatewayLabelResolver, phase 1) +
 * active-conversation flag (ActiveConversationStore, phase 1) so the composable stays
 * a pure renderer. Also exposes `providerHint` (current brain gateway's resolved
 * label) for the sidebar footer's Settings row.
 */
@HiltViewModel
class FridayRecentConversationsViewModel @Inject constructor(
    private val convoStore: FridayConvoStore,
    private val gatewayRepo: GatewayConfigRepository,
    private val localeSource: LocaleSource,
    // Trailing default mirrors FridayChatViewModel/FridayVoiceViewModel's convention:
    // keeps any non-Hilt (test) call sites compiling; Hilt supplies the bound
    // singleton explicitly via GatewayModule.bindActiveConversationStore at runtime.
    private val activeConversationStore: ActiveConversationStore = DefaultActiveConversationStore(),
) : ViewModel() {

    private val _pageSize = MutableStateFlow(INITIAL_PAGE)

    val windowed: StateFlow<List<RecentConvoUi>> = combine(
        convoStore.conversations,
        gatewayRepo.gateways,
        activeConversationStore.activeConversationId,
        _pageSize,
        localeSource.selected,
    ) { conversations, gateways, activeId, pageSize, language ->
        val tag = if (language == AppLanguage.SYSTEM) Locale.getDefault().language else language.uiTag
        val now = System.currentTimeMillis()
        conversations.take(pageSize).map { convo ->
            val label = GatewayLabelResolver.resolve(convo.gatewayId, gateways)
            RecentConvoUi(
                id = convo.id,
                title = convo.title.ifBlank { convo.id },
                relativeTime = RelativeTimeFormatter.format(convo.updatedAt, now, tag),
                providerLabel = label.label,
                providerLabelRes = label.labelRes,
                isActive = convo.id == activeId,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val canLoadMore: StateFlow<Boolean> = combine(
        convoStore.conversations,
        _pageSize,
    ) { conversations, pageSize -> conversations.size > pageSize }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isEmpty: StateFlow<Boolean> = convoStore.conversations
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, convoStore.conversations.value.isEmpty())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val providerHint: StateFlow<GatewayLabel> = combine(
        gatewayRepo.gateways,
        gatewayRepo.brainId,
    ) { gateways, brainId -> GatewayLabelResolver.resolve(brainId, gateways) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            GatewayLabelResolver.resolve(gatewayRepo.brainId.value, gatewayRepo.gateways.value),
        )

    /** Grows the visible window by [PAGE_STEP]; no-ops while already loading or exhausted. */
    fun loadMore() {
        if (_isLoading.value || !canLoadMore.value) return
        val target = _pageSize.value + PAGE_STEP
        _isLoading.value = true
        viewModelScope.launch {
            _pageSize.value = target
            // Wait for the real recombination to reach the new window (or the source
            // list's true size, if it is shorter than target) before clearing the flag —
            // tied to genuine StateFlow propagation, never a fabricated delay.
            val expected = minOf(target, convoStore.conversations.value.size)
            windowed.first { it.size >= expected }
            _isLoading.value = false
        }
    }
}

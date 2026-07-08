package com.dark.tool_neuron.repo

import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.DownloadHistoryEntry
import com.dark.tool_neuron.model.DownloadHistoryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadLabel(val displayName: String, val type: String) {
    companion object {
        fun fromUrl(url: String): DownloadLabel {
            val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "Download" }
            return DownloadLabel(name, "unknown")
        }
    }
}

@Singleton
class DownloadCoordinator @Inject constructor(
    private val history: DownloadHistoryRepository,
) {
    private val labels = ConcurrentHashMap<Int, DownloadLabel>()
    private val recordedTerminals = ConcurrentHashMap.newKeySet<Int>()

    private val _labelsFlow = MutableStateFlow<Map<Int, DownloadLabel>>(emptyMap())
    val labelsFlow: StateFlow<Map<Int, DownloadLabel>> = _labelsFlow.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            HxdManager.tasks.collect { tasks ->
                _activeCount.value = tasks.count {
                    it.status == HxdStatus.QUEUED ||
                        it.status == HxdStatus.CONNECTING ||
                        it.status == HxdStatus.DOWNLOADING ||
                        it.status == HxdStatus.PAUSED
                }
                tasks.forEach { state ->
                    val terminal = state.status == HxdStatus.COMPLETED ||
                        state.status == HxdStatus.FAILED ||
                        state.status == HxdStatus.CANCELLED
                    if (terminal && recordedTerminals.add(state.id)) {
                        val label = labels.remove(state.id) ?: DownloadLabel.fromUrl(state.url)
                        publishLabels()
                        history.insert(
                            DownloadHistoryEntry(
                                id = UUID.randomUUID().toString(),
                                displayName = label.displayName,
                                type = label.type,
                                status = state.status.toHistoryStatus(),
                                totalBytes = state.totalBytes.coerceAtLeast(0L),
                                completedAt = System.currentTimeMillis(),
                                error = state.error,
                            )
                        )
                    }
                }
            }
        }
    }

    fun registerLabel(hxdId: Int, displayName: String, type: String) {
        labels[hxdId] = DownloadLabel(displayName, type)
        publishLabels()
    }

    fun labelFor(hxdId: Int): DownloadLabel? = labels[hxdId]

    private fun publishLabels() {
        _labelsFlow.value = HashMap(labels)
    }

    private fun HxdStatus.toHistoryStatus(): DownloadHistoryStatus = when (this) {
        HxdStatus.COMPLETED -> DownloadHistoryStatus.COMPLETED
        HxdStatus.CANCELLED -> DownloadHistoryStatus.CANCELLED
        else -> DownloadHistoryStatus.FAILED
    }
}

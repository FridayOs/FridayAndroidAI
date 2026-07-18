package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatComposer
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatEmptyState
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatHeader
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatMessageList
import com.dark.tool_neuron.ui.screens.friday.components.FridayChatStopRow
import com.dark.tool_neuron.viewmodel.FridayChatViewModel

@Composable
fun FridayChatScreen(
    innerPadding: PaddingValues,
    onOpenMenu: () -> Unit,
    onToVoice: () -> Unit,
    // Dedicated provider-selector callback (FRI-582 QA round-2 B3), distinct
    // from the hamburger onOpenMenu (-> History). Drives the no-provider CTA
    // and the no-ready-provider send interception below; defaults to
    // onOpenMenu only to avoid breaking older call sites.
    onOpenProviderSelector: () -> Unit = onOpenMenu,
    conversationId: String? = null,
    viewModel: FridayChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val thinking by viewModel.thinking.collectAsStateWithLifecycle()
    // FRI-574 phase 7 (B1): inFlight stays true the entire send→Done/Error window, so the
    // Stop row + composer gate can't be released by the first Delta and a second concurrent
    // send can't slip through mid-stream.
    val inFlight by viewModel.inFlight.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val hasGateway by viewModel.hasGateway.collectAsStateWithLifecycle()
    val hasReadyGateway by viewModel.hasReadyGateway.collectAsStateWithLifecycle()
    val hasBrainSelected by viewModel.hasBrainSelected.collectAsStateWithLifecycle()
    val brainLabel by viewModel.brainLabel.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // FRI-574 B2 re-review: newChat() (header) and requestNewChat() (sidebar, routed through
    // newChat()) both bump composerResetSignal. Clear the local draft whenever it changes so a
    // "New conversation" from EITHER surface empties the composer — the VM can't reach this
    // screen-local input directly. drop(1) skips the initial emission so a fresh screen keeps
    // any restored/typed draft.
    val composerResetSignal by viewModel.composerResetSignal.collectAsStateWithLifecycle()
    LaunchedEffect(composerResetSignal) {
        if (composerResetSignal > 0) input = ""
    }

    LaunchedEffect(conversationId) {
        if (conversationId != null) viewModel.open(conversationId)
    }

    // No brain selected: intercept before the VM ever sees the send call and route to the
    // provider selector instead. A selected-but-FAILED/NOT_TESTED brain is allowed to send —
    // it surfaces the real error banner + retry, never a fake reply.
    fun handleSend() {
        val text = input
        if (text.isBlank() || inFlight) return
        if (!hasBrainSelected) {
            onOpenProviderSelector()
            return
        }
        input = ""
        viewModel.send(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        FridayChatHeader(
            onOpenMenu = onOpenMenu,
            onProviderClick = onOpenProviderSelector,
            onNewChat = viewModel::newChat,
            hasGateway = hasGateway,
            hasReadyGateway = hasReadyGateway,
            label = brainLabel,
            hasBrainSelected = hasBrainSelected,
        )

        if (messages.isEmpty() && !thinking) {
            FridayChatEmptyState(
                hasProvider = hasBrainSelected,
                onAddProvider = onOpenProviderSelector,
                onSelectProvider = onOpenProviderSelector,
                modifier = Modifier.weight(1f),
            )
        } else {
            FridayChatMessageList(
                messages = messages,
                thinking = thinking,
                listState = listState,
                modifier = Modifier.weight(1f),
            )
        }

        if (inFlight) {
            FridayChatStopRow(onStop = viewModel::cancel)
        }

        error?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::regenerate) {
                    Text(stringResource(R.string.friday_retry))
                }
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.friday_event_dismiss))
                }
            }
        }

        FridayChatComposer(
            value = input,
            onValueChange = { input = it },
            onSend = ::handleSend,
            onMicClick = onToVoice,
            thinking = inFlight,
        )
    }
}

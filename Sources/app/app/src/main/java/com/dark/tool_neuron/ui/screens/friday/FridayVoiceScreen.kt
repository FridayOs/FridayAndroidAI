package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.ui.screens.friday.components.ConfirmationCard
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceAnimation
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceControls
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceHeader
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceRing
import com.dark.tool_neuron.ui.screens.friday.components.FridayThinkingRow
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceSelectorPrompt
import com.dark.tool_neuron.ui.screens.friday.components.InboundEventCard
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.FridayEventsViewModel
import com.dark.tool_neuron.viewmodel.FridayVoiceViewModel
import com.dark.tool_neuron.viewmodel.VoiceUiState

@Composable
fun FridayVoiceScreen(
    innerPadding: PaddingValues,
    onOpenMenu: () -> Unit,
    onToChat: () -> Unit,
    viewModel: FridayVoiceViewModel = hiltViewModel(),
    eventsViewModel: FridayEventsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val question by viewModel.question.collectAsStateWithLifecycle()
    val answer by viewModel.answer.collectAsStateWithLifecycle()
    val selectorRequest by viewModel.selectorRequest.collectAsStateWithLifecycle()
    val voiceAnimation by viewModel.voiceAnimation.collectAsStateWithLifecycle()
    val voiceGateway by viewModel.voiceGateway.collectAsStateWithLifecycle()
    val brainConfigured by viewModel.brainConfigured.collectAsStateWithLifecycle()
    val pendingAssist by viewModel.pendingAssistInvocation.collectAsStateWithLifecycle()
    val activeInboundEvent by eventsViewModel.activeEvent.collectAsStateWithLifecycle()
    val awaitingConfirmation by viewModel.awaitingConfirmationState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Assistant invocation (long-press home) reaches this screen only after the auth/lock chain has
    // resolved onto the voice route; consume the one-shot flag and open the same turn a mic tap does.
    // Assist goes through the same permission gate — mic-FGS on Android 14+ needs while-in-use RECORD_AUDIO.
    LaunchedEffect(pendingAssist, micGranted) {
        if (pendingAssist && viewModel.consumeAssistInvocation()) {
            if (micGranted) viewModel.onAssistantInvocation() else viewModel.onMicNeedsPermission()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        viewModel.onPermissionResult(granted)
    }
    // WHY: single launch path avoids the double-invocation bug of firing the launcher directly from onClick
    LaunchedEffect(viewModel) {
        viewModel.permissionRequest.collect { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    val active = uiState == VoiceUiState.Listening || uiState == VoiceUiState.Speaking
    val ringColor = when (uiState) {
        VoiceUiState.Listening -> FridayPalette.Primary
        VoiceUiState.Speaking -> FridayPalette.SpeakingGlow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when (uiState) {
        VoiceUiState.Idle, VoiceUiState.Cancelling -> stringResource(R.string.friday_voice_idle_title)
        VoiceUiState.RequestingPermission, VoiceUiState.Connecting -> stringResource(R.string.friday_voice_connecting)
        VoiceUiState.Listening -> stringResource(R.string.friday_voice_listening_title)
        VoiceUiState.Thinking -> stringResource(R.string.friday_voice_thinking_title)
        VoiceUiState.Speaking -> stringResource(R.string.friday_voice_speaking_title)
        VoiceUiState.Done -> stringResource(R.string.friday_voice_done_title)
        is VoiceUiState.Error -> stringResource(R.string.friday_voice_idle_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        FridayVoiceHeader(
            onOpenMenu = onOpenMenu,
            onToChat = onToChat,
            brainConfigured = brainConfigured,
            gatewayLabel = voiceGateway?.label,
            ready = voiceGateway?.status == GatewayStatus.READY,
            onProviderClick = onOpenMenu,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier.fillMaxWidth().size(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            FridayVoiceRing(active = active, color = ringColor, modifier = Modifier.size(280.dp))
            FridayVoiceAnimation(animation = voiceAnimation, active = active)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState is VoiceUiState.Thinking) {
                FridayThinkingRow(modifier = Modifier.padding(bottom = 14.dp))
            }
            if (question.isNotEmpty()) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            if (answer.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = answer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (question.isEmpty() && uiState == VoiceUiState.Idle) {
                Text(
                    text = stringResource(R.string.friday_voice_tap_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        activeInboundEvent?.let { event ->
            InboundEventCard(
                event = event,
                onDismiss = eventsViewModel::dismiss,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        if (awaitingConfirmation) {
            ConfirmationCard(
                onConfirm = viewModel::confirm,
                onCancel = viewModel::reset,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        (uiState as? VoiceUiState.Error)?.message?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { viewModel.reset() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        selectorRequest?.let {
            FridayVoiceSelectorPrompt(
                onAddProvider = { viewModel.clearSelectorRequest(); onOpenMenu() },
                onSelectProvider = { viewModel.clearSelectorRequest(); onOpenMenu() },
            )
        }

        FridayVoiceControls(
            uiState = uiState,
            onToChat = onToChat,
            onMicClick = { if (!micGranted) viewModel.onMicNeedsPermission() else viewModel.onMicTap() },
            onReset = viewModel::reset,
        )
    }
}

package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.friday.components.FridayOrb
import com.dark.tool_neuron.ui.screens.friday.components.FridayWaveform
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.FridayVoiceViewModel
import com.dark.tool_neuron.viewmodel.VoiceMode

@Composable
fun FridayVoiceScreen(
    innerPadding: PaddingValues,
    onOpenMenu: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: FridayVoiceViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val question by viewModel.question.collectAsStateWithLifecycle()
    val answer by viewModel.answer.collectAsStateWithLifecycle()

    val active = mode == VoiceMode.LISTENING || mode == VoiceMode.SPEAKING
    val barColor = when (mode) {
        VoiceMode.LISTENING -> FridayPalette.Primary
        VoiceMode.SPEAKING -> FridayPalette.SpeakingGlow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val glow = if (mode == VoiceMode.SPEAKING) FridayPalette.SpeakingGlow else FridayPalette.Primary
    val title = when (mode) {
        VoiceMode.IDLE -> stringResource(R.string.friday_voice_idle_title)
        VoiceMode.LISTENING -> stringResource(R.string.friday_voice_listening_title)
        VoiceMode.THINKING -> stringResource(R.string.friday_voice_thinking_title)
        VoiceMode.SPEAKING -> stringResource(R.string.friday_voice_speaking_title)
        VoiceMode.DONE -> stringResource(R.string.friday_voice_done_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TopIconButton(TnIcons.Menu, onOpenMenu)
            TopIconButton(TnIcons.MessageCircle, onOpenHistory)
        }

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
            modifier = Modifier
                .fillMaxWidth()
                .size(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            FridayWaveform(active = active, color = barColor, modifier = Modifier.size(280.dp))
            FridayOrb(active = active, glow = glow)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
            if (question.isEmpty() && mode == VoiceMode.IDLE) {
                Text(
                    text = stringResource(R.string.friday_voice_idle_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashedCircleButton(TnIcons.Edit, onClick = onOpenMenu)
            Spacer(Modifier.size(40.dp))
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(FridayPalette.Primary, CircleShape)
                    .clickable { viewModel.tapMic() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (mode == VoiceMode.LISTENING) TnIcons.PlayerStop else TnIcons.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = FridayPalette.OnPrimary,
                )
            }
            Spacer(Modifier.size(40.dp))
            DashedCircleButton(TnIcons.X, onClick = viewModel::reset)
        }
    }
}

@Composable
private fun TopIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DashedCircleButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

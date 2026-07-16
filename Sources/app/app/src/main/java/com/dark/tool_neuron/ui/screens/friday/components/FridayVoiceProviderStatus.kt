package com.dark.tool_neuron.ui.screens.friday.components

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val ReadyGreen = Color(0xFF30A46C) // WHY: no green token in FridayPalette for gateway-ready status

@Composable
fun FridayVoiceProviderStatusPill(
    brainConfigured: Boolean,
    gatewayLabel: String?,
    ready: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (brainConfigured && ready) ReadyGreen else MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.size(8.dp))
        val label = if (brainConfigured) {
            gatewayLabel.orEmpty().ifBlank { stringResource(R.string.friday_voice_select_provider) }
        } else {
            stringResource(R.string.friday_voice_add_provider)
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun FridayVoiceSelectorPrompt(onAddProvider: () -> Unit, onSelectProvider: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.friday_voice_no_provider_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.friday_voice_no_provider_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onSelectProvider) { Text(stringResource(R.string.friday_voice_select_provider)) }
            TextButton(onClick = onAddProvider) { Text(stringResource(R.string.friday_voice_add_provider)) }
        }
    }
}

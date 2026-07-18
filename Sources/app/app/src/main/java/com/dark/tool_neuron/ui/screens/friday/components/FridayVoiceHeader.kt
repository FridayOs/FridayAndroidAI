package com.dark.tool_neuron.ui.screens.friday.components

import com.friday.ai.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons

// WHY: no green token in FridayPalette for gateway-ready status
private val ReadyGreen = Color(0xFF30A46C)

@Composable
fun FridayVoiceHeader(
    onOpenMenu: () -> Unit,
    onToChat: () -> Unit,
    brainConfigured: Boolean,
    gatewayLabel: String?,
    ready: Boolean,
    onProviderClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FridayHeaderIconButton(TnIcons.Menu, stringResource(R.string.friday_cd_menu), onOpenMenu)
        val label = if (brainConfigured) {
            gatewayLabel.orEmpty().ifBlank { stringResource(R.string.friday_voice_select_provider) }
        } else {
            stringResource(R.string.friday_voice_add_provider)
        }
        val dotColor = if (brainConfigured && ready) ReadyGreen else MaterialTheme.colorScheme.onSurfaceVariant
        FridayProviderPill(
            label = label,
            dotColor = dotColor,
            onClick = onProviderClick,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentDescription = stringResource(R.string.friday_cd_provider),
        )
        FridayHeaderIconButton(TnIcons.MessageCircle, stringResource(R.string.friday_cd_to_chat), onToChat)
    }
}

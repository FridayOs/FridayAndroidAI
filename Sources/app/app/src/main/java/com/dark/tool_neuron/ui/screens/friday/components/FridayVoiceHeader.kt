package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons

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
        HeaderIconButton(TnIcons.Menu, onOpenMenu)
        FridayVoiceProviderStatusPill(
            brainConfigured = brainConfigured,
            gatewayLabel = gatewayLabel,
            ready = ready,
            onClick = onProviderClick,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        HeaderIconButton(TnIcons.MessageCircle, onToChat)
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, onClick: () -> Unit) {
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

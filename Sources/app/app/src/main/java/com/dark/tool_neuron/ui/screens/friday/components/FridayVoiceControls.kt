package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.VoiceUiState

@Composable
fun FridayVoiceControls(
    uiState: VoiceUiState,
    onEditClick: () -> Unit,
    onMicClick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashedCircleButton(TnIcons.Edit, onClick = onEditClick)
        Spacer(Modifier.size(40.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(FridayPalette.Primary, CircleShape)
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (uiState == VoiceUiState.Listening) TnIcons.PlayerStop else TnIcons.Mic,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = FridayPalette.OnPrimary,
            )
        }
        Spacer(Modifier.size(40.dp))
        DashedCircleButton(TnIcons.X, onClick = onReset)
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

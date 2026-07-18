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

// WHY: no green token in FridayPalette for gateway-ready status (mirrors FridayVoiceHeader.kt)
private val ReadyGreen = Color(0xFF30A46C)

/*
 * Chat header (design-spec §1 & §4 — markup-identical to Voice's header/pill,
 * only the trailing icon differs: message-circle on Voice vs pencil/new-chat
 * here). Pill label prefers the selected brain's "<label> · <model>" string;
 * falls back to "Set provider" when no brain is selected yet.
 */
@Composable
fun FridayChatHeader(
    onOpenMenu: () -> Unit,
    onProviderClick: () -> Unit,
    onNewChat: () -> Unit,
    hasGateway: Boolean,
    hasReadyGateway: Boolean,
    label: String?,
    hasBrainSelected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FridayHeaderIconButton(TnIcons.Menu, onOpenMenu)
        FridayProviderPill(
            label = label ?: stringResource(R.string.friday_pill_set_provider),
            dotColor = if (hasReadyGateway) ReadyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onProviderClick,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        FridayHeaderIconButton(TnIcons.Edit, onNewChat)
    }
}

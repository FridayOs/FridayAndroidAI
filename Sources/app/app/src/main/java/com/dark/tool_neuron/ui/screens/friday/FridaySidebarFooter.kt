package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.repo.gateway.GatewayLabel
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.util.FridayPalette

/*
 * Sidebar footer (FRI-574 phase 3, design spec §2): Settings row (with current-brain
 * provider hint at right), red Sign out row, and version text. Sign-out and settings
 * navigation are hoisted to the caller; this composable never calls AccountRepository
 * or navigates directly beyond invoking the passed-in callbacks.
 */
@Composable
fun FridaySidebarFooter(
    providerHint: GatewayLabel,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val hintText = providerHint.label
        ?: providerHint.labelRes?.let { stringResource(it) }
        ?: stringResource(R.string.friday_unknown_provider)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(TnIcons.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.friday_settings),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable(onClick = onSignOut),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.friday_sign_out),
                color = FridayPalette.Danger,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.friday_version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

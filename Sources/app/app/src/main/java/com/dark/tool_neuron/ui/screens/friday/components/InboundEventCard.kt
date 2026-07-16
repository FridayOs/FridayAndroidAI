package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.friday.InboundEvent
import com.dark.tool_neuron.ui.util.FridayPalette
import com.friday.ai.R

// In-app surface for an inbound event while the app is foreground. Confirmation rendering
// (Confirm/Cancel) is separate — see ConfirmationCard — because actionable confirmation only comes
// from the brain-armed gate today (push CONFIRMATIONs are downgraded before they get here, FRI-555).
@Composable
fun InboundEventCard(
    event: InboundEvent,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (event.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                event.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.friday_event_dismiss))
            }
        }
    }
}

// Brain-armed confirmation gate: Confirm resolves the parked action, Cancel cancels the turn.
// The user tap is the only path that resolves the gate — never rendered from an unverified push.
@Composable
fun ConfirmationCard(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.friday_event_awaiting_confirmation),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.friday_event_cancel), color = FridayPalette.Danger)
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.friday_event_confirm), color = FridayPalette.Primary)
            }
        }
    }
}

package com.dark.tool_neuron.ui.screens.friday.components

import com.friday.ai.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * FRI-574 B4 (re-review): the "Stop generating" affordance shown while a chat turn is
 * in-flight. Extracted to a stateless composable so it (a) carries a localized
 * accessible name + Role.Button distinct from its short visible "Stop" label, and
 * (b) is hostable in the accessibility regression suite without Hilt/VM. Only rendered
 * by FridayChatScreen while inFlight is true (turn start → Done/Error/cancel).
 */
@Composable
fun FridayChatStopRow(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        val stopLabel = stringResource(R.string.friday_cd_stop)
        TextButton(
            onClick = onStop,
            modifier = Modifier.semantics {
                contentDescription = stopLabel
                role = Role.Button
            },
        ) {
            Text(stringResource(R.string.friday_voice_notif_stop))
        }
    }
}

package com.dark.tool_neuron.ui.screens.friday.components

import com.friday.ai.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalFridayAccent

/*
 * Empty-conversation greeting (design-spec §1). hasProvider shows Friday's
 * standard hello copy; noProvider shows the "choose a brain" prompt with
 * Add/Select CTAs — both funnel into the same onOpenProviderSelector sheet,
 * mirroring FridayVoiceSelectorPrompt's existing pattern.
 */
@Composable
fun FridayChatEmptyState(
    hasProvider: Boolean,
    onAddProvider: () -> Unit,
    onSelectProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val accent = LocalFridayAccent.current
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(accent.soft),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides accent.color) {
                androidx.compose.material3.Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(
                if (hasProvider) R.string.friday_chat_hello_title else R.string.friday_voice_no_provider_title,
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("friday_chat_empty_state"),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                if (hasProvider) R.string.friday_chat_hello_body else R.string.friday_voice_no_provider_body,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hasProvider) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSelectProvider, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.friday_voice_select_provider))
                }
                Button(onClick = onAddProvider, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.friday_add_provider))
                }
            }
        }
    }
}

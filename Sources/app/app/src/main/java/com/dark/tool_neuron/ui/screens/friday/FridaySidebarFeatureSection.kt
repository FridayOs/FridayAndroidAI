package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.util.FridayPalette

/*
 * Sidebar "feature section" (FRI-574 phase 3, design spec §2): Live voice + New
 * conversation are live actions; Skills + MCP are disabled previews (alpha .55 +
 * "coming soon" badge) that surface onComingSoon() — reuses the caller's toast
 * mechanism (a local SnackbarHost) rather than inventing a new one. Navigation is
 * fully hoisted: this composable never navigates directly.
 */
@Composable
fun FridaySidebarFeatureSection(
    onOpenVoice: () -> Unit,
    onNewConversation: () -> Unit,
    onComingSoon: () -> Unit,
) {
    Column {
        DrawerSectionLabel(stringResource(R.string.friday_feature_section))
        Spacer(Modifier.height(10.dp))
        FeatureRow(
            icon = TnIcons.Mic,
            label = stringResource(R.string.friday_live_voice),
            accent = true,
            onClick = onOpenVoice,
        )
        FeatureRow(
            icon = TnIcons.Plus,
            label = stringResource(R.string.friday_new_conversation),
            onClick = onNewConversation,
        )
        FeatureRow(
            icon = TnIcons.Sparkles,
            label = stringResource(R.string.friday_skills),
            disabled = true,
            onClick = onComingSoon,
        )
        FeatureRow(
            icon = TnIcons.Puzzle,
            label = stringResource(R.string.friday_mcp),
            disabled = true,
            onClick = onComingSoon,
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    disabled: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (accent) FridayPalette.Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .alpha(if (disabled) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
        if (disabled) {
            Text(
                text = stringResource(R.string.friday_coming_soon),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.dark.tool_neuron.ui.screens.language_selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily

/*
 * Single selectable language card (design-spec §2, HTML lines 64-104).
 * Extracted from LanguageSelectionScreen.kt to keep files under the 200-line
 * limit; behavior and callers unchanged.
 */
@Composable
internal fun LanguageCard(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalFridayAccent.current
    val bg = if (selected) accent.soft else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) accent.color else MaterialTheme.colorScheme.outlineVariant
    val checkBorder = if (selected) accent.color else MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) accent.color else Color.Transparent)
                .border(1.5.dp, checkBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = null,
                    tint = accent.onColor,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

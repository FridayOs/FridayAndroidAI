package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily

/*
 * Accent picker for the onboarding Theme step (design-spec §6, HTML
 * `accentItems` lines ~230-286: 30px circular swatch, box-shadow glow
 * (`0 0 14px {{ac.glow}}`) when selected, checkmark overlay when selected,
 * name label 13.5px/600). Fixed 4-entry FridayAccent.entries list — no
 * ViewModel access here, purely presentational + callback.
 */
@Composable
fun ThemeAccentRow(
    selected: FridayAccent,
    onSelect: (FridayAccent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FridayAccent.entries.forEach { accent ->
            ThemeAccentItem(
                accent = accent,
                isSelected = accent == selected,
                onClick = { onSelect(accent) },
            )
        }
    }
}

@Composable
private fun ThemeAccentItem(
    accent: FridayAccent,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentSwatch(accent = accent, isSelected = isSelected)
        Spacer(Modifier.size(14.dp))
        Text(
            text = stringResource(accent.displayNameRes),
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = TnIcons.Check,
                contentDescription = null,
                tint = accent.color,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun AccentSwatch(accent: FridayAccent, isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = accent.soft,
                        spotColor = accent.soft,
                    )
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(accent.color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

package com.dark.tool_neuron.ui.screens.onboarding_providers.add

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Sticky footer for the Add Provider screen (FRI-582 P8, design-spec §8):
 * "Save only" (outline, weight 1) + "Save & use" (filled accent, weight ~1.4).
 */
@Composable
fun AddProviderFooter(
    enabled: Boolean,
    onSaveOnly: () -> Unit,
    onSaveAndUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.spacingSm),
    ) {
        OutlinedButton(
            onClick = onSaveOnly,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.friday_add_provider_save_only),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        Button(
            onClick = onSaveAndUse,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.color,
                contentColor = accent.onColor,
            ),
            modifier = Modifier.weight(1.4f),
        ) {
            Text(
                text = stringResource(R.string.friday_add_provider_save_use),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

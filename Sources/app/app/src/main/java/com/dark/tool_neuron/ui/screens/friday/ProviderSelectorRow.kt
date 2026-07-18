package com.dark.tool_neuron.ui.screens.friday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.ui.components.GatewayStatusChip
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding_providers.ProviderAvatar
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * A single provider/model row in FridayProviderSelectorSheet (design-spec §3
 * selectorRows). Reuses ProviderAvatar (logo or gradient-initial fallback,
 * onboarding_providers package) and GatewayStatusChip for the true
 * READY/FAILED/NOT_TESTED status. Selected rows get accentSoft background +
 * accent border, plus a trailing checkmark (mirrors ProviderConfiguredRow's
 * existing selected-check pattern) — the chip keeps showing real connection
 * status rather than being replaced by a "Selected" label.
 */
@Composable
fun ProviderSelectorRow(
    config: GatewayConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCornerRadius))
            .background(if (isSelected) accent.soft else MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isSelected) accent.color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(dimens.cardCornerRadius),
            )
            .clickable(onClick = onClick)
            .padding(dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(provider = config.provider)
        Spacer(modifier = Modifier.width(dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = config.provider.displayName,
                    fontFamily = groteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                GatewayStatusChip(status = config.status)
            }
            Text(
                text = "${config.label} · ${config.displayModel}",
                fontFamily = jakartaFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (isSelected) {
            Icon(
                imageVector = TnIcons.CircleCheck,
                contentDescription = stringResource(R.string.friday_status_selected),
                tint = accent.color,
            )
        }
    }
}

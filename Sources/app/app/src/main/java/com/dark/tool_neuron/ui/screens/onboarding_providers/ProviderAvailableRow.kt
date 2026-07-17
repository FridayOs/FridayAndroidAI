package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingDialog
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * A single available-provider row on the Providers screen catalog list
 * (design-spec §8). Enabled providers show an "Add" action; Friday (the one
 * disabled placeholder in the 8-entry catalog) shows a "coming soon" badge
 * and opens an explanatory dialog on tap instead.
 */
@Composable
fun ProviderAvailableRow(
    provider: GatewayProvider,
    description: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    var showComingSoonDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (!provider.enabled) Modifier.clickable { showComingSoonDialog = true } else Modifier,
            )
            .padding(dimens.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderAvatar(provider = provider)
        Spacer(modifier = Modifier.width(dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                fontFamily = groteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = description,
                fontFamily = jakartaFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (provider.enabled) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.chipCornerRadius))
                    .background(accent.color)
                    .clickable { onAdd() }
                    .padding(horizontal = dimens.chipHorizontalPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = TnIcons.Plus,
                    contentDescription = null,
                    tint = accent.onColor,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(
                    text = stringResource(R.string.friday_providers_add),
                    fontFamily = jakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = accent.onColor,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.friday_providers_coming_soon),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.chipCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = dimens.chipHorizontalPadding, vertical = 6.dp),
            )
        }
    }

    if (showComingSoonDialog) {
        OnboardingDialog(
            title = stringResource(R.string.friday_providers_friday_dialog_title),
            body = stringResource(R.string.friday_providers_friday_dialog_body),
            onDismissRequest = { showComingSoonDialog = false },
            confirmLabel = stringResource(R.string.friday_providers_got_it),
            onConfirm = { showComingSoonDialog = false },
        )
    }
}

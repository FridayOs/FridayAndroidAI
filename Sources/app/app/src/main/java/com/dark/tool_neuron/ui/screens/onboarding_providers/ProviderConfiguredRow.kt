package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingDialog
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingDialogActionKind
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingToast
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * A single configured-provider row on the Providers screen (design-spec §8).
 * Never renders config.apiKey — subtext is "label · model" only, per the
 * security note in GatewayConfig.kt.
 */
@Composable
fun ProviderConfiguredRow(
    config: GatewayConfig,
    statusLabelRes: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeletedToast by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onSelect() }
            .padding(dimens.cardPadding),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        ProviderAvatar(provider = config.provider)
        Spacer(modifier = Modifier.width(dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = config.provider.displayName,
                    fontFamily = groteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                StatusChip(text = stringResource(statusLabelRes), selected = isSelected)
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
                contentDescription = stringResource(R.string.friday_providers_status_selected),
                tint = accent.color,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
                imageVector = TnIcons.Trash,
                contentDescription = stringResource(R.string.friday_providers_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteDialog) {
        OnboardingDialog(
            title = stringResource(R.string.friday_providers_delete_dialog_title),
            body = stringResource(R.string.friday_providers_delete_dialog_body),
            onDismissRequest = { showDeleteDialog = false },
            confirmLabel = stringResource(R.string.friday_providers_delete),
            onConfirm = {
                showDeleteDialog = false
                showDeletedToast = true
                onDelete()
            },
            confirmKind = OnboardingDialogActionKind.DANGER,
            dismissLabel = stringResource(R.string.friday_providers_cancel),
            onDismiss = { showDeleteDialog = false },
        )
    }

    if (showDeletedToast) {
        OnboardingToast(
            message = stringResource(R.string.friday_providers_deleted_toast),
            onDismiss = { showDeletedToast = false },
        )
    }
}

@Composable
private fun StatusChip(text: String, selected: Boolean) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    Text(
        text = text,
        fontFamily = jakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = if (selected) accent.onColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.color else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = dimens.chipHorizontalPadding, vertical = 3.dp),
    )
}

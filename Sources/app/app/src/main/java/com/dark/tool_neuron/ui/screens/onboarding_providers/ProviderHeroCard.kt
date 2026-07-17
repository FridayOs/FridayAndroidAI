package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingDialog
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Privacy hero card for the Providers screen (design-spec §8): icon + title/body
 * + 3 info chips (local storage / direct-from-phone / no server sync). Tapping
 * the card opens a "how privacy works" dialog, mirroring the Terms screen's
 * privacy-details affordance.
 */
@Composable
fun ProviderHeroCard(modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.soft)
            .clickable { showDialog = true }
            .padding(dimens.cardPadding),
    ) {
        Icon(
            imageVector = TnIcons.ShieldCheck,
            contentDescription = null,
            tint = accent.color,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = stringResource(R.string.friday_providers_hero_title),
            fontFamily = groteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
        )
        Text(
            text = stringResource(R.string.friday_providers_hero_body),
            fontFamily = jakartaFamily,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = dimens.spacingSm),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.chipSpacing)) {
            HeroChip(stringResource(R.string.friday_providers_chip_local))
            HeroChip(stringResource(R.string.friday_providers_chip_direct))
            HeroChip(stringResource(R.string.friday_providers_chip_no_sync))
        }
    }

    if (showDialog) {
        OnboardingDialog(
            title = stringResource(R.string.friday_providers_privacy_dialog_title),
            body = stringResource(R.string.friday_providers_privacy_dialog_body),
            onDismissRequest = { showDialog = false },
            confirmLabel = stringResource(R.string.friday_providers_got_it),
            onConfirm = { showDialog = false },
        )
    }
}

@Composable
private fun HeroChip(label: String) {
    val dimens = LocalDimens.current
    Text(
        text = label,
        fontFamily = jakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.chipCornerRadius))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = dimens.chipHorizontalPadding, vertical = 6.dp),
    )
}

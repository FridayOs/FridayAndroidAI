package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.ui.screens.friday.components.FRIDAY_AVATAR_ASSET
import com.dark.tool_neuron.ui.screens.friday.components.rememberAssetBitmap
import com.dark.tool_neuron.ui.util.FridayPalette
import androidx.compose.ui.unit.dp

/*
 * Sidebar header (FRI-574 phase 3, design spec §2): 44dp avatar, fixed "FRIDAY AI"
 * brand title (reuses R.string.app_name — no new key needed), and account email
 * bound to AccountRepository.state (Unauthenticated falls back to
 * R.string.friday_brand_name, matching the pre-existing header's fallback string).
 */
@Composable
fun FridayDrawerHeader(account: AccountState) {
    val avatar = rememberAssetBitmap(FRIDAY_AVATAR_ASSET)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(2.dp, FridayPalette.Primary, CircleShape),
            )
        }
        Spacer(Modifier.size(11.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = (account as? AccountState.Authenticated)?.email?.ifBlank { null }
                    ?: stringResource(R.string.friday_brand_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

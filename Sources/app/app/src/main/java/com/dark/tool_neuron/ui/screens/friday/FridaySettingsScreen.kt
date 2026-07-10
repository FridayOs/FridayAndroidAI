package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.AccountState
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.AccountViewModel

@Composable
fun FridaySettingsScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onLanguageClick: () -> Unit = {},
    accountViewModel: AccountViewModel = hiltViewModel(),
) {
    val account by accountViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TnIcons.ArrowLeft, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Text(
                text = stringResource(R.string.friday_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = (account as? AccountState.Authenticated)?.displayName
                ?: stringResource(R.string.friday_brand_name),
            subtitle = (account as? AccountState.Authenticated)?.email ?: "",
            icon = TnIcons.Person,
        )
        Spacer(Modifier.height(10.dp))
        SectionCard(
            title = stringResource(R.string.friday_settings_section_gateway_title),
            subtitle = stringResource(R.string.friday_settings_section_gateway_subtitle),
            icon = TnIcons.Server,
        )
        Spacer(Modifier.height(10.dp))
        SectionCard(
            title = stringResource(R.string.friday_settings_section_voice_title),
            subtitle = stringResource(R.string.friday_settings_section_voice_subtitle),
            icon = TnIcons.Mic,
        )
        Spacer(Modifier.height(10.dp))
        SectionCard(
            title = stringResource(R.string.friday_settings_section_theme_title),
            subtitle = stringResource(R.string.friday_settings_section_theme_subtitle),
            icon = TnIcons.Sparkles,
        )
        Spacer(Modifier.height(10.dp))
        SectionCard(
            title = stringResource(R.string.friday_settings_section_language_title),
            subtitle = stringResource(R.string.friday_settings_section_language_subtitle),
            icon = TnIcons.Globe,
            onClick = onLanguageClick,
        )

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable {
                    accountViewModel.signOut()
                    onBack()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.friday_sign_out),
                color = FridayPalette.Danger,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(TnIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
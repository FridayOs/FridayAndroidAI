package com.dark.tool_neuron.ui.screens.language_selection

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.LanguageViewModel
import com.friday.ai.R

@Composable
fun LanguageSelectionScreen(
    innerPadding: PaddingValues,
    onContinue: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.friday_language_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(dimens.spacingSm))
            Text(
                text = stringResource(R.string.friday_language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(dimens.spacingLg))
            AppLanguage.entries.forEach { language ->
                LanguageRow(
                    language = language,
                    selected = language == selected,
                    onClick = { viewModel.choose(language) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(dimens.spacingSm))
            }
        }
        Button(
            onClick = {
                viewModel.confirm()
                onContinue()
            },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 200.dp),
            enabled = selected.uiTag.isNotEmpty() || selected == AppLanguage.SYSTEM,
        ) {
            Text(text = stringResource(R.string.friday_language_continue))
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.iconLg)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TnIcons.Globe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
        Spacer(Modifier.size(dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelFor(language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
            Text(
                text = stringResource(R.string.friday_language_subtitle_row, language.uiTag.ifEmpty { "system" }),
                style = MaterialTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.7f),
            )
        }
        if (selected) {
            Icon(
                imageVector = TnIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimens.iconMd),
            )
        }
    }
}

@Composable
private fun labelFor(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.friday_language_system)
    AppLanguage.EN -> stringResource(R.string.friday_language_english)
    AppLanguage.VI -> stringResource(R.string.friday_language_vietnamese)
}
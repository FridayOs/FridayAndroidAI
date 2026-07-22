package com.dark.tool_neuron.ui.screens.setup_screen

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding.components.OnboardingStepIndicator
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.SetupThemeViewModel

/*
 * Theme step re-skin (FRI-582 P5, design-spec §6, HTML lines 230-286):
 * step dots -> title/sub -> 3 mode cards (icon + label only) -> accent
 * row (ThemeAccentRow.kt) -> live preview (ThemePreviewCard.kt). Onboarding
 * always runs on the FRIDAY base palette; accent is the only variable here.
 *
 * Back nav (FRI-582 QA blocker fixes, design `goBack` HTML:1499-1501): explicit
 * chevron tap navigates back to Protect Friday (SetupScreen) via onBack, wired
 * in TNavigation.kt through OnboardingBackNav. No continue CTA lives on this
 * screen (accent/theme picks apply live); forward nav stays elsewhere.
 */
@Composable
fun SetupThemeScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SetupThemeViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val accent by viewModel.accent.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.ensureFridayBase() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        ActionButton(
            onClickListener = onBack,
            icon = TnIcons.ArrowLeft,
            contentDescription = stringResource(R.string.friday_onboarding_back_content_description),
            modifier = Modifier.padding(start = dimens.screenPadding, top = 10.dp),
        )
        OnboardingStepIndicator(
            current = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
        ) {
            Text(
                text = stringResource(R.string.friday_setup_theme_title),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
            )
            Text(
                text = stringResource(R.string.friday_setup_theme_subtitle),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(dimens.spacingXl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                ThemeModeCard(
                    label = stringResource(R.string.friday_setup_theme_mode_system_title),
                    icon = TnIcons.Phone,
                    selected = mode == ThemeController.Mode.SYSTEM,
                    onClick = { viewModel.selectMode(ThemeController.Mode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                ThemeModeCard(
                    label = stringResource(R.string.friday_setup_theme_mode_light_title),
                    icon = TnIcons.Sun,
                    selected = mode == ThemeController.Mode.LIGHT,
                    onClick = { viewModel.selectMode(ThemeController.Mode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemeModeCard(
                    label = stringResource(R.string.friday_setup_theme_mode_dark_title),
                    icon = TnIcons.Moon,
                    selected = mode == ThemeController.Mode.DARK,
                    onClick = { viewModel.selectMode(ThemeController.Mode.DARK) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(dimens.spacingXl))

            Text(
                text = stringResource(R.string.friday_setup_theme_palette_label).uppercase(),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(dimens.spacingSm))
            ThemeAccentRow(
                selected = accent,
                onSelect = { viewModel.selectAccent(it) },
            )

            Spacer(Modifier.height(dimens.spacingLg))

            ThemePreviewCard(accent = accent)

            Spacer(Modifier.height(dimens.spacingXl))
        }
    }
}

@Composable
private fun ThemeModeCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalFridayAccent.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.soft else MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent.color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

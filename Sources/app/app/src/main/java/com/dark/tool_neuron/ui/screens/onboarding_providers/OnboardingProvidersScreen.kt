package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.OnboardingProvidersLogic
import com.dark.tool_neuron.viewmodel.OnboardingProvidersViewModel
import com.dark.tool_neuron.viewmodel.ProviderAvailableDesc
import com.friday.ai.R

/*
 * Providers screen (FRI-582 P7, design-spec §8): header -> privacy hero card ->
 * configured providers (or empty state) -> available catalog (8 entries,
 * GEMINI..CUSTOM then FRIDAY) -> gated "Continue to Friday" CTA. Reuses
 * GatewayConfigRepository/GatewayDirectory exclusively via
 * OnboardingProvidersViewModel — no new storage collection here.
 */
@Composable
fun OnboardingProvidersScreen(
    innerPadding: PaddingValues,
    viewModel: OnboardingProvidersViewModel,
    onBack: () -> Unit,
    onAddProvider: (String) -> Unit,
    onContinueToFriday: () -> Unit,
) {
    val dimens = LocalDimens.current
    val gateways by viewModel.gateways.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("onboarding_providers_list")
            .padding(innerPadding),
        contentPadding = PaddingValues(
            horizontal = dimens.screenPadding,
            vertical = dimens.spacingLg,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = TnIcons.ArrowLeft, contentDescription = stringResource(R.string.friday_providers_cancel))
                }
                Text(
                    text = stringResource(R.string.friday_providers_title),
                    fontFamily = groteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        item {
            ProviderHeroCard(modifier = Modifier.padding(top = dimens.spacingSm))
        }
        item {
            Text(
                text = stringResource(R.string.friday_providers_configured_label),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = dimens.spacingLg, bottom = 4.dp),
            )
        }
        if (gateways.isEmpty()) {
            item { EmptyConfiguredState() }
        } else {
            items(gateways, key = GatewayConfig::id) { config ->
                ProviderConfiguredRow(
                    config = config,
                    statusLabelRes = viewModel.statusLabelRes(config),
                    isSelected = viewModel.isSelected(config),
                    onSelect = { viewModel.select(config.id) },
                    onDelete = { viewModel.delete(config.id) },
                )
            }
        }
        item {
            Text(
                text = stringResource(R.string.friday_providers_available_label),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = dimens.spacingLg, bottom = 4.dp),
            )
        }
        items(viewModel.availableCatalog, key = GatewayProvider::name) { provider ->
            // Per-provider desc (design-spec §4.2, HTML:2045-2048): "Default: <model>"
            // for catalog providers, "OpenAI-compatible · custom base URL" for
            // urlRequired rows (OpenClaw/Hermes/Custom), "Managed FRIDAY provider"
            // for the disabled Friday placeholder. Resolved via the pure
            // OnboardingProvidersLogic.availableDescription seam.
            ProviderAvailableRow(
                provider = provider,
                description = availableDescriptionString(provider),
                onAdd = { onAddProvider(provider.name) },
            )
        }
        if (gateways.isNotEmpty()) {
            item {
                val accent = LocalFridayAccent.current
                Button(
                    onClick = onContinueToFriday,
                    colors = ButtonDefaults.buttonColors(containerColor = accent.color, contentColor = accent.onColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.spacingLg),
                ) {
                    Text(
                        text = stringResource(R.string.friday_providers_continue),
                        fontFamily = jakartaFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun availableDescriptionString(provider: GatewayProvider): String = when (val desc = OnboardingProvidersLogic.availableDescription(provider)) {
    is ProviderAvailableDesc.DefaultModel -> stringResource(R.string.friday_providers_desc_default_model, desc.model)
    ProviderAvailableDesc.OpenAiCompatible -> stringResource(R.string.friday_providers_desc_openai_compatible)
    ProviderAvailableDesc.ManagedFriday -> stringResource(R.string.friday_providers_desc_managed_friday)
}

@Composable
private fun EmptyConfiguredState() {
    val dimens = LocalDimens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(vertical = dimens.spacingLg, horizontal = dimens.cardPadding),
    ) {
        Text(
            text = stringResource(R.string.friday_providers_empty_title),
            fontFamily = groteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.friday_providers_empty_body),
            fontFamily = jakartaFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

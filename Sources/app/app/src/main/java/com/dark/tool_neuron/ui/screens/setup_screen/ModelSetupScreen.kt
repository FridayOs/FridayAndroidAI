package com.dark.tool_neuron.ui.screens.setup_screen

import com.friday.ai.R

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding.components.OnboardingStepIndicator
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily

/*
 * Model Setup re-skin (FRI-582 P6, design-spec §7, HTML lines 295-345 +
 * `pathDefs`/`PACKS` arrays lines 1206-1210/1495-1536): step dots -> title/sub
 * -> 3 path cards (gateway/local/skip) -> conditional 3 pack cards (local path
 * only) -> primary CTA (label depends on path) + secondary "skip for now"
 * text CTA. Design pack ids map to real PackCatalog pack constants. Card tap
 * selects a pack only (design `pickPack`); the primary CTA (design
 * `modelContinue`) confirms the selection and enqueues the real download
 * pipeline via onLocalPackConfirmed — see wiring in TNavigation.kt.
 *
 * onOpenStore/onLocalImport are kept in the signature for call-site
 * compatibility (TNavigation.kt) but unused here: this screen's design has no
 * browse/import affordance. That functionality remains reachable from the
 * Model Store screen outside onboarding.
 */
private enum class ModelSetupPath { GATEWAY, LOCAL, SKIP }

@Composable
fun ModelSetupScreen(
    innerPadding: PaddingValues,
    onLocalPackConfirmed: (packId: String) -> Unit,
    onOpenStore: () -> Unit,
    onLocalImport: (uri: Uri, name: String, size: Long, type: ProviderType) -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onChooseGateway: () -> Unit = {},
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    var selectedPath by remember { mutableStateOf<ModelSetupPath?>(null) }
    var selectedPackId by remember { mutableStateOf<String?>(null) }

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
            current = 5,
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
                text = stringResource(R.string.friday_model_setup_title),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
                letterSpacing = (-0.3).sp,
            )
            Text(
                text = stringResource(R.string.friday_model_setup_subtitle),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(dimens.spacingXl)

            ModelPathCard(
                icon = TnIcons.Server,
                title = stringResource(R.string.friday_model_setup_path_gateway_title),
                body = stringResource(R.string.friday_model_setup_path_gateway_body),
                recommended = true,
                recommendedLabel = stringResource(R.string.friday_model_setup_recommended_badge),
                selected = selectedPath == ModelSetupPath.GATEWAY,
                onClick = { selectedPath = ModelSetupPath.GATEWAY },
            )
            Spacer(dimens.spacingSm)
            ModelPathCard(
                icon = TnIcons.Cpu,
                title = stringResource(R.string.friday_model_setup_path_local_title),
                body = stringResource(R.string.friday_model_setup_path_local_body),
                recommended = false,
                recommendedLabel = "",
                selected = selectedPath == ModelSetupPath.LOCAL,
                onClick = { selectedPath = ModelSetupPath.LOCAL },
                modifier = Modifier.testTag("model_path_local"),
            )
            Spacer(dimens.spacingSm)
            ModelPathCard(
                icon = TnIcons.Clock,
                title = stringResource(R.string.friday_model_setup_path_skip_title),
                body = stringResource(R.string.friday_model_setup_path_skip_body),
                recommended = false,
                recommendedLabel = "",
                selected = selectedPath == ModelSetupPath.SKIP,
                onClick = { selectedPath = ModelSetupPath.SKIP },
            )

            if (selectedPath == ModelSetupPath.LOCAL) {
                Spacer(dimens.spacingLg)
                ModelSetupPackList(
                    spacingSm = dimens.spacingSm,
                    selectedPackId = selectedPackId,
                    onSelect = { selectedPackId = it },
                )
            }

            Spacer(dimens.spacingXl)

            val ctaLabel = when (selectedPath) {
                ModelSetupPath.GATEWAY -> stringResource(R.string.friday_model_setup_cta_gateway)
                ModelSetupPath.LOCAL -> stringResource(R.string.friday_model_setup_cta_local)
                else -> stringResource(R.string.friday_model_setup_continue)
            }
            val ctaEnabled = selectedPath == ModelSetupPath.GATEWAY ||
                selectedPath == ModelSetupPath.SKIP ||
                (selectedPath == ModelSetupPath.LOCAL && selectedPackId != null)

            Text(
                text = ctaLabel,
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                color = if (ctaEnabled) accent.onColor else accent.onColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (ctaEnabled) accent.color else accent.color.copy(alpha = 0.5f))
                    .testTag("model_setup_cta")
                    .semantics {
                        role = Role.Button
                        if (!ctaEnabled) disabled()
                    }
                    .clickable(enabled = ctaEnabled) {
                        when (selectedPath) {
                            ModelSetupPath.GATEWAY -> onChooseGateway()
                            ModelSetupPath.SKIP -> onSkip()
                            ModelSetupPath.LOCAL -> selectedPackId?.let(onLocalPackConfirmed)
                            else -> Unit
                        }
                    }
                    .padding(vertical = 17.dp),
            )

            Spacer(dimens.spacingSm)

            Text(
                text = stringResource(R.string.friday_model_setup_skip_now),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clickable(onClick = onSkip)
                    .padding(vertical = 12.dp),
            )

            Spacer(dimens.spacingXl)
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}

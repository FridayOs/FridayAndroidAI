package com.dark.tool_neuron.ui.screens.setup_screen

import com.friday.ai.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily
import kotlinx.coroutines.launch

/*
 * Primary CTA + inline pack-unavailable error, extracted from
 * ModelSetupScreen.kt (FRI-582 QA round-2 B1 follow-up, keeps the screen file
 * under the repo's ~200-line guidance). Owns the local-pack confirm click ->
 * suspend await -> fail-closed error UI loop; ModelSetupScreen only hoists
 * the path/pack selection state.
 */
@Composable
fun ModelSetupCta(
    selectedPath: ModelSetupPath?,
    selectedPackId: String?,
    accent: FridayAccent,
    onChooseGateway: () -> Unit,
    onSkip: () -> Unit,
    onLocalPackConfirmed: suspend (packId: String) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var packUnavailable by remember { mutableStateOf(false) }

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
                    ModelSetupPath.LOCAL -> selectedPackId?.let { packId ->
                        packUnavailable = false
                        scope.launch {
                            if (!onLocalPackConfirmed(packId)) packUnavailable = true
                        }
                    }
                    else -> Unit
                }
            }
            .padding(vertical = 17.dp),
    )

    if (packUnavailable) {
        Text(
            text = stringResource(R.string.friday_model_setup_pack_unavailable),
            fontFamily = jakartaFamily,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("model_setup_pack_unavailable"),
        )
    }
}

package com.dark.tool_neuron.ui.screens.onboarding_providers.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderStatus
import com.friday.ai.R

// Design-spec §8 semantic tints: ready (green) / fail (red, shared with AddProviderFields' inline error).
private val ReadyColor = Color(0xFF30A46C)
private val FailColor = Color(0xFFE5484D)

/*
 * Test-connection result card for the Add Provider screen (FRI-582 P8).
 * Renders only for READY/FAILED status — callers gate visibility on status.
 * The failed-state body is the sanitized DirectGatewayClient failure.message
 * (never the raw statusError) — there is no static _test_fail_body string.
 */
@Composable
fun AddProviderTestResultCard(
    status: OnboardingAddProviderStatus,
    failureMessage: String?,
    modifier: Modifier = Modifier,
) {
    if (status != OnboardingAddProviderStatus.READY && status != OnboardingAddProviderStatus.FAILED) return

    val dimens = LocalDimens.current
    val ready = status == OnboardingAddProviderStatus.READY
    val tint = if (ready) ReadyColor else FailColor
    val title = stringResource(
        if (ready) R.string.friday_add_provider_test_ready_title else R.string.friday_add_provider_test_fail_title,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCornerRadius))
            .background(tint.copy(alpha = 0.12f))
            .padding(dimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ready) TnIcons.CircleCheck else TnIcons.AlertTriangle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(text = title, fontFamily = groteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = tint)
        }
        val body = if (ready) stringResource(R.string.friday_add_provider_test_ready_body) else failureMessage.orEmpty()
        if (body.isNotEmpty()) {
            Text(
                text = body,
                fontFamily = jakartaFamily,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

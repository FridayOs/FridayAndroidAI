package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.gateway.GatewayStatus
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Reusable gateway status chip (design-spec §3 chipFor()): READY green,
 * FAILED red, NOT_TESTED muted+surface2. Shared by the provider selector
 * sheet (FRI-574 Phase 2) and the left sidebar drawer (Phase 3).
 */
@Composable
fun GatewayStatusChip(status: GatewayStatus, modifier: Modifier = Modifier) {
    val dimens = LocalDimens.current
    val (labelRes, color, background) = when (status) {
        GatewayStatus.READY -> Triple(R.string.friday_status_ready, Color(0xFF30A46C), Color(0x2430A46C))
        GatewayStatus.FAILED -> Triple(R.string.friday_status_failed, Color(0xFFE5484D), Color(0x1FE5484D))
        GatewayStatus.NOT_TESTED -> Triple(
            R.string.friday_status_not_tested,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    Text(
        text = stringResource(labelRes),
        fontFamily = jakartaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = dimens.chipHorizontalPadding, vertical = 3.dp),
    )
}

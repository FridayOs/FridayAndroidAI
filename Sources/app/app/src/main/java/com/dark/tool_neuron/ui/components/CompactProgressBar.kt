package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion

/**
 * Single-line "Compacting…" banner. Layout:
 *
 *     ✦ Compacting · 1m13s · ↑83.3k · 56%  ▰▰▰▰▰▰▰▱▱▱▱▱▱▱▱
 *
 * The fill bar flexes to fill the row's remaining width so the banner
 * doesn't wrap on narrow screens. Pass [visible]=false to collapse.
 */
@Composable
fun CompactProgressBar(
    visible: Boolean,
    elapsedMs: Long,
    tokensIn: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
        exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
        modifier = modifier,
    ) {
        Surface(
            shape = tnShapes.lg,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.spacingXs),
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = dimens.spacingMd,
                    vertical = dimens.spacingSm,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconSm),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Compacting",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "· ${formatElapsed(elapsedMs)} · ↑${formatTokens(tokensIn)} · ${(fraction.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                )
                LinearFillBar(
                    fraction = fraction,
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                )
            }
        }
    }
}

@Composable
private fun LinearFillBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val shape = LocalTnShapes.current.full
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val fill = MaterialTheme.colorScheme.primary
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(f)
                .clip(shape)
                .background(fill),
        )
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = ms / 1000L
    val mins = totalSec / 60
    val secs = totalSec % 60
    return if (mins > 0) "${mins}m${secs}s" else "${secs}s"
}

private fun formatTokens(n: Int): String = when {
    n < 1_000     -> n.toString()
    n < 10_000    -> "%.1fk".format(n / 1000.0)
    n < 1_000_000 -> "${n / 1000}k"
    else          -> "%.1fM".format(n / 1_000_000.0)
}

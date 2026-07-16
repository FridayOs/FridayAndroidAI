package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private const val BAR_COUNT = 56
private val RING_RADIUS = 98.dp
private val BAR_LENGTH = 21.dp
private val BAR_WIDTH = 3.2.dp

@Composable
fun FridayVoiceRing(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voiceRing")
    // WHY: gate infinite animation so idle state never spins in the background
    val phase by if (active) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "voiceRingPhase",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val alpha by animateFloatAsState(targetValue = if (active) 1f else 0.25f, label = "voiceRingAlpha")

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radiusPx = RING_RADIUS.toPx()
        val lengthPx = BAR_LENGTH.toPx()
        val widthPx = BAR_WIDTH.toPx()
        for (i in 0 until BAR_COUNT) {
            val angle = (i.toFloat() / BAR_COUNT) * 2f * Math.PI.toFloat()
            val factor = if (active) 0.6f + 0.4f * sin(phase + i * 0.35f) else 0.6f
            val len = lengthPx * factor
            val dirX = cos(angle)
            val dirY = sin(angle)
            val start = Offset(cx + dirX * radiusPx, cy + dirY * radiusPx)
            val end = Offset(cx + dirX * (radiusPx + len), cy + dirY * (radiusPx + len))
            drawLine(
                color = color.copy(alpha = alpha),
                start = start,
                end = end,
                strokeWidth = widthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

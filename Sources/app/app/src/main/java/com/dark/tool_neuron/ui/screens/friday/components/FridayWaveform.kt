package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin

private const val BAR_COUNT = 44

@Composable
fun FridayWaveform(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase",
    )
    val opacity = if (active) 1f else 0.16f
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringRadius = size.minDimension * 0.37f
        val baseLen = size.minDimension * 0.05f
        val maxLen = size.minDimension * 0.09f
        val barWidth = size.minDimension * 0.011f
        for (i in 0 until BAR_COUNT) {
            val angle = (i.toFloat() / BAR_COUNT) * 2f * Math.PI.toFloat()
            val wobble = if (active) (0.5f + 0.5f * sin(phase + i * 0.5f)) else 0.28f
            val len = baseLen + (maxLen - baseLen) * wobble
            val dirX = cos(angle)
            val dirY = sin(angle)
            val start = Offset(cx + dirX * ringRadius, cy + dirY * ringRadius)
            val end = Offset(cx + dirX * (ringRadius + len), cy + dirY * (ringRadius + len))
            drawLine(
                color = color.copy(alpha = opacity),
                start = start,
                end = end,
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

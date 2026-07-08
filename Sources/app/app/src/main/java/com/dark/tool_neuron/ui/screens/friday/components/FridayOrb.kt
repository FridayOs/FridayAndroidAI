package com.dark.tool_neuron.ui.screens.friday.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.util.FridayPalette

@Composable
fun FridayOrb(
    active: Boolean,
    glow: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    Box(modifier = modifier.size(188.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(188.dp)) {
            val radius = (size.minDimension / 2f) * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = radius * 1.6f,
                ),
                radius = radius * 1.6f,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        FridayPalette.Primary,
                        FridayPalette.PrimaryPressed,
                        Color(0xFF1A1A1A),
                    ),
                    center = Offset(size.width * 0.38f, size.height * 0.34f),
                    radius = radius,
                ),
                radius = radius,
            )
        }
    }
}

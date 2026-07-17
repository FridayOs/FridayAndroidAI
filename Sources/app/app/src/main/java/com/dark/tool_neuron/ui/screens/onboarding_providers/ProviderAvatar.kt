package com.dark.tool_neuron.ui.screens.onboarding_providers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.gateway.GatewayProvider
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.friday.ai.R

/*
 * Shared avatar for the Providers screen (configured + available rows):
 * bundled logo PNG when one exists for the provider, else a gradient circle
 * with the provider's initial letter (e.g. Custom, which has no bundled logo).
 */
private fun logoResFor(provider: GatewayProvider): Int? = when (provider) {
    GatewayProvider.GEMINI -> R.drawable.logo_gemini
    GatewayProvider.OPENAI -> R.drawable.logo_openai
    GatewayProvider.ANTHROPIC -> R.drawable.logo_anthropic
    GatewayProvider.DEEPSEEK -> R.drawable.logo_deepseek
    GatewayProvider.OPENCLAW -> R.drawable.logo_openclaw
    GatewayProvider.HERMES -> R.drawable.logo_hermes
    GatewayProvider.FRIDAY -> R.drawable.logo_friday
    else -> null
}

@Composable
fun ProviderAvatar(provider: GatewayProvider, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val logoRes = logoResFor(provider)
    if (logoRes != null) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = provider.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        val accent = LocalFridayAccent.current
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(accent.color, accent.color.copy(alpha = 0.6f))),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = provider.displayName.take(1).uppercase(),
                fontFamily = groteskFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = accent.onColor,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

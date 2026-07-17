package com.dark.tool_neuron.ui.screens.onboarding_providers.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.jakartaFamily
import kotlinx.coroutines.delay

private const val AUTO_DISMISS_MS = 2400L

/*
 * Shared transient toast for the onboarding-providers flow (FRI-582 P8,
 * design-spec §8) — theme-inverted pill, auto-dismisses after 2.4s. Used for
 * "Saved"/"Friday now uses X · Y" (Add Provider) and delete confirmation
 * (Providers list) so the two flows share one visual language.
 */
@Composable
fun OnboardingToast(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(message) {
        delay(AUTO_DISMISS_MS)
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = message,
                fontFamily = jakartaFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

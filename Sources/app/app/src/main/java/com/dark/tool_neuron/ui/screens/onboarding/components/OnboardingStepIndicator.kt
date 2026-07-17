package com.dark.tool_neuron.ui.screens.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.LocalFridayAccent

/*
 * Shared 6-dot step indicator (design-spec §0 "Step indicator (stepDots, 6 dots)",
 * HTML `renderVals()` line 1976 + markup lines 68-71). Used identically across the
 * 6 linear onboarding screens after Intro: Language(0), Terms(1), Tour(2), Lock(3),
 * ThemeSetup(4), ModelSetup(5). Intro itself is pre-step-indicator.
 *
 * Reused by later onboarding phases (Terms/Tour/Lock/Theme/ModelSetup) — do not
 * duplicate this logic per-screen.
 */
@Composable
fun OnboardingStepIndicator(
    current: Int,
    total: Int = 6,
    modifier: Modifier = Modifier,
) {
    val accent = LocalFridayAccent.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val border = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (index in 0 until total) {
            val isActive = index == current
            val width = if (isActive) 22.dp else 6.dp
            val color = when {
                isActive -> accent.color
                index < current -> muted
                else -> border
            }
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color),
            )
        }
    }
}

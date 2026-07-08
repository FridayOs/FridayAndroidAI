package com.dark.tool_neuron.service.island

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.continuities.G2ContinuityProfile

private val IslandContinuity = G2Continuity(
    profile = G2ContinuityProfile.RoundedRectangle.copy(
        extendedFraction = 0.6,
        arcFraction = 0.4,
        bezierCurvatureScale = 1.2,
        arcCurvatureScale = 1.2,
    ),
    capsuleProfile = G2ContinuityProfile.Capsule.copy(
        extendedFraction = 0.55,
        arcFraction = 0.3,
    ),
)

fun islandShape(cornerRadius: Dp): Shape =
    ContinuousRoundedRectangle(cornerRadius, continuity = IslandContinuity)

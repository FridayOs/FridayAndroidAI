package com.dark.tool_neuron.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.friday.ai.R

/*
 * The 4 fixed onboarding/Friday-palette accents. Neutrals (bg/surface/outline)
 * stay constant across all 4 — only primary/onPrimary/soft-halo swap. Mirrors
 * the HTML prototype's `ACCENTS` array (design-spec §0, lines 1189-1194).
 * Names live in strings.xml (EN+VI) — resolve via stringResource(displayNameRes).
 */
enum class FridayAccent(
    val id: String,
    @StringRes val displayNameRes: Int,
    val color: Color,
    val onColor: Color,
    val soft: Color,
) {
    SUN(
        id = "sun",
        displayNameRes = R.string.friday_accent_sun,
        color = Color(0xFFFFE658),
        onColor = Color(0xFF1A1A1A),
        soft = Color(0x38FFE658),
    ),
    EMBER(
        id = "ember",
        displayNameRes = R.string.friday_accent_ember,
        color = Color(0xFFE51A3F),
        onColor = Color(0xFFFFFFFF),
        soft = Color(0x29E51A3F),
    ),
    VIOLET(
        id = "violet",
        displayNameRes = R.string.friday_accent_violet,
        color = Color(0xFF8B5CF6),
        onColor = Color(0xFFFFFFFF),
        soft = Color(0x2E8B5CF6),
    ),
    MINT(
        id = "mint",
        displayNameRes = R.string.friday_accent_mint,
        color = Color(0xFF2BC79B),
        onColor = Color(0xFF0E2A21),
        soft = Color(0x2E2BC79B),
    );

    companion object {
        fun fromId(id: String): FridayAccent = entries.firstOrNull { it.id == id } ?: SUN
    }
}

val LocalFridayAccent = staticCompositionLocalOf { FridayAccent.SUN }

package com.dark.tool_neuron.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.friday.ai.R

/*
 * Figtree — rounded geometric sans for all app UI chrome.
 * Registered across the full weight range so M3 roles can use
 * weight contrast (300–800) for hierarchy instead of relying on size alone.
 */
@OptIn(ExperimentalTextApi::class)
val FigtreeFontFamily = FontFamily(
    Font(
        resId = R.font.figtree,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        resId = R.font.figtree,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.figtree,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.figtree,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.figtree,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.figtree,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    ),
    Font(
        resId = R.font.figtree_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.figtree_italic,
        weight = FontWeight.Medium,
        style = FontStyle.Italic,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
)

/*
 * Poppins — canonical typography for the Friday Login screen (FRI-633 QA
 * round-5 blocker). Static per-weight TTFs (not a variable font), so each
 * weight maps directly to its own file — no [FontVariation] settings needed.
 */
val PoppinsFontFamily = FontFamily(
    Font(R.font.poppins_regular, weight = FontWeight.Normal),
    Font(R.font.poppins_medium, weight = FontWeight.Medium),
    Font(R.font.poppins_semibold, weight = FontWeight.SemiBold),
    Font(R.font.poppins_bold, weight = FontWeight.Bold),
)

/*
 * Maple Mono — variable monospace for AI chat responses, model output,
 * model name badges, and technical stats (tokens, speed, context length).
 * Keeps a clear visual split between UI chrome and AI voice.
 */
@OptIn(ExperimentalTextApi::class)
val MapleMonoFontFamily = FontFamily(
    Font(
        resId = R.font.maple_mono,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.maple_mono,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.maple_mono,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
)

val maple = MapleMonoFontFamily

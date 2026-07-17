package com.dark.tool_neuron.ui.screens.intro_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceAnimation
import com.dark.tool_neuron.ui.screens.friday.components.VoiceAnimation
import com.dark.tool_neuron.ui.screens.friday.components.AssetGif
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R
import kotlinx.coroutines.delay

/*
 * Intro splash (design-spec §1, HTML lines 48-62). Full-bleed centered column:
 * orb glow -> orb video (reuses FridayVoiceAnimation's VoiceAnimation.ORB path,
 * same TextureView+MediaPlayer pattern already used by the Friday voice screen)
 * -> "FRIDAY AI" title (brand, literal) -> tagline -> pill badge -> bottom
 * loading.gif (reuses AssetGif, same ImageDecoder/AnimatedImageDrawable path).
 *
 * Auto-advances at 2600ms via `onFinish`; tap anywhere skips immediately. The
 * `finished` guard makes the skip idempotent against the auto-advance timer
 * without needing an explicit Job handle — the LaunchedEffect's delay keeps
 * running in the background (harmless, disposed on navigation) but its
 * `onFinish` call is suppressed once `finished` is set.
 */
@Composable
fun IntroScreen(
    innerPadding: PaddingValues,
    onFinish: () -> Unit = {},
) {
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(INTRO_AUTO_ADVANCE_MS)
        if (!finished) {
            finished = true
            onFinish()
        }
    }

    fun skip() {
        if (finished) return
        finished = true
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { skip() },
            )
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        IntroBrandColumn()

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp),
        ) {
            AssetGif(
                assetName = "loading.gif",
                active = true,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}

@Composable
private fun IntroBrandColumn() {
    val accent = LocalFridayAccent.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.soft, accent.soft.copy(alpha = 0f)),
                        ),
                        shape = CircleShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape),
            ) {
                FridayVoiceAnimation(
                    animation = VoiceAnimation.ORB,
                    active = true,
                    modifier = Modifier.size(150.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.onboarding_intro_title),
            fontFamily = groteskFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.padding(top = 30.dp),
        )

        Text(
            text = stringResource(R.string.onboarding_intro_tagline),
            fontFamily = jakartaFamily,
            fontSize = 14.sp,
            lineHeight = 21.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .widthIn(max = 270.dp),
        )

        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(99.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_intro_pill),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val INTRO_AUTO_ADVANCE_MS = 2600L

package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.screens.friday.components.FridayVoiceAnimation
import com.dark.tool_neuron.ui.screens.friday.components.VoiceAnimation
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Live theme preview for the onboarding Theme step (design-spec §6, HTML
 * lines ~270-286): surface card with an uppercase label, a mock chat turn
 * (orb avatar + incoming/outgoing bubble pair) recolored instantly by the
 * currently selected accent. Uses the shared FridayVoiceAnimation ORB path
 * (already used by IntroScreen) for the 30dp avatar.
 */
@Composable
fun ThemePreviewCard(accent: FridayAccent, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.friday_setup_theme_preview_title),
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(12.dp))

        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
            ) {
                FridayVoiceAnimation(
                    animation = VoiceAnimation.ORB,
                    active = true,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.friday_setup_theme_preview_message),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 15.dp,
                            bottomEnd = 15.dp,
                            bottomStart = 15.dp,
                        ),
                    )
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }

        Spacer(Modifier.size(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        ) {
            Text(
                text = stringResource(R.string.friday_setup_theme_preview_reply),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = accent.onColor,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 15.dp,
                            topEnd = 4.dp,
                            bottomEnd = 15.dp,
                            bottomStart = 15.dp,
                        ),
                    )
                    .background(accent.color)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }
}

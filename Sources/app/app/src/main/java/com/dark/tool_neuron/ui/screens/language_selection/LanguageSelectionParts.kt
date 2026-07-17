package com.dark.tool_neuron.ui.screens.language_selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Small stateless building blocks for LanguageSelectionScreen.kt (design-spec
 * §2, HTML lines 64-104): the globe icon badge and the confirm CTA button.
 * Extracted to keep files under the 200-line limit; behavior unchanged.
 */
@Composable
internal fun LanguageIconBadge() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TnIcons.Globe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
internal fun LanguageCta(selected: AppLanguage, onClick: () -> Unit) {
    val accent = LocalFridayAccent.current
    val label = when (selected) {
        AppLanguage.EN -> stringResource(R.string.onboarding_lang_cta_en)
        AppLanguage.VI -> stringResource(R.string.onboarding_lang_cta_vi)
        AppLanguage.SYSTEM -> stringResource(R.string.onboarding_lang_cta_prompt)
    }
    Button(
        onClick = onClick,
        enabled = selected != AppLanguage.SYSTEM,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.color,
            contentColor = accent.onColor,
            disabledContainerColor = accent.color.copy(alpha = 0.4f),
            disabledContentColor = accent.onColor.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = label,
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
        )
    }
}

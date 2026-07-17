package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.jakartaFamily

@Composable
fun TermsConditionsBottomBar(
    @StringRes buttonLabel: Int,
    onAccept: () -> Unit,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Button(
            onClick = onAccept,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.color,
                contentColor = accent.onColor,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(
                    horizontal = dimens.screenPadding,
                    vertical = dimens.spacingMd,
                ),
        ) {
            Text(
                text = stringResource(buttonLabel),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
            )
        }
    }
}

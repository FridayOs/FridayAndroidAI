package com.dark.tool_neuron.ui.screens.onboarding_providers.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

// Design-spec §8 semantic error tint (shared with AddProviderTestResultCard's fail state).
internal val AddProviderErrorColor = Color(0xFFE5484D)

/*
 * Field stack for the Add Provider screen (FRI-582 P8, design-spec §8):
 * Label -> conditional masked API key -> Model -> Base URL (inline if
 * provider.urlRequired, else tucked behind a collapsible "Advanced" section).
 * API key is always rendered masked via the shared PasswordTextField —
 * never plaintext, never logged (security note in the phase spec).
 */
@Composable
fun AddProviderFields(
    label: String,
    onLabelChange: (String) -> Unit,
    needsKey: Boolean,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    keyVisible: Boolean,
    onToggleKeyVisible: () -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    urlRequired: Boolean,
    advancedOpen: Boolean,
    onToggleAdvanced: () -> Unit,
    errorRes: Int?,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(R.string.friday_add_provider_field_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsKey) {
            PasswordTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = stringResource(R.string.friday_add_provider_field_key),
                modifier = Modifier.fillMaxWidth(),
                showPasswordState = keyVisible,
                onToggleVisibility = onToggleKeyVisible,
            )
        }

        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text(stringResource(R.string.friday_add_provider_field_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (urlRequired) {
            BaseUrlField(baseUrl = baseUrl, onBaseUrlChange = onBaseUrlChange)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.friday_add_provider_advanced),
                    fontFamily = jakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                IconButton(onClick = onToggleAdvanced) {
                    Icon(
                        imageVector = if (advancedOpen) TnIcons.ChevronUp else TnIcons.ChevronDown,
                        contentDescription = stringResource(R.string.friday_add_provider_advanced),
                    )
                }
            }
            if (advancedOpen) {
                BaseUrlField(baseUrl = baseUrl, onBaseUrlChange = onBaseUrlChange)
            }
        }

        if (errorRes != null) {
            Text(
                text = stringResource(errorRes),
                fontFamily = jakartaFamily,
                fontSize = 12.sp,
                color = AddProviderErrorColor,
            )
        }
    }
}

@Composable
private fun BaseUrlField(baseUrl: String, onBaseUrlChange: (String) -> Unit) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text(stringResource(R.string.friday_add_provider_field_base_url)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

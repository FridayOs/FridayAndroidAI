package com.dark.tool_neuron.ui.screens.onboarding_providers.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily

/*
 * Shared confirmation/info dialog for the onboarding-providers flow (FRI-582
 * P8, design-spec §8). Consolidates the inline AlertDialogs previously
 * duplicated across ProviderAvailableRow (friday "coming soon") and
 * ProviderConfiguredRow (delete confirmation) — same text/behavior, one body.
 */
enum class OnboardingDialogActionKind { GHOST, DANGER, PRIMARY }

@Composable
fun OnboardingDialog(
    title: String,
    body: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmKind: OnboardingDialogActionKind = OnboardingDialogActionKind.GHOST,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val accent = LocalFridayAccent.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = body,
                fontFamily = jakartaFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    fontFamily = jakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = when (confirmKind) {
                        OnboardingDialogActionKind.DANGER -> MaterialTheme.colorScheme.error
                        OnboardingDialogActionKind.PRIMARY -> accent.color
                        OnboardingDialogActionKind.GHOST -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        dismissButton = if (dismissLabel != null && onDismiss != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = dismissLabel,
                        fontFamily = jakartaFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else null,
    )
}

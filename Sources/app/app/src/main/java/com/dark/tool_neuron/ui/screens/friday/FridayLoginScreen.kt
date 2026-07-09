package com.dark.tool_neuron.ui.screens.friday

import com.friday.ai.R

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.friday.components.FRIDAY_LOGO_ASSET
import com.dark.tool_neuron.ui.screens.friday.components.rememberAssetBitmap
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayThemeViewModel

@Composable
fun FridayLoginScreen(
    innerPadding: PaddingValues,
    accountViewModel: AccountViewModel = hiltViewModel(),
    themeViewModel: FridayThemeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val signingIn by accountViewModel.signingIn.collectAsStateWithLifecycle()
    val noGoogleAccount by accountViewModel.noGoogleAccount.collectAsStateWithLifecycle()
    val mode by themeViewModel.mode.collectAsStateWithLifecycle()
    val logo = rememberAssetBitmap(FRIDAY_LOGO_ASSET)

    val snackbarHostState = remember { SnackbarHostState() }
    val cancelledMsg = stringResource(R.string.friday_login_error_cancelled)
    val genericMsg = stringResource(R.string.friday_login_error_generic)
    LaunchedEffect(accountViewModel) {
        accountViewModel.snackbar.collect { kind ->
            val msg = when (kind) {
                "cancelled" -> cancelledMsg
                else -> genericMsg
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (logo != null) {
                    Image(bitmap = logo, contentDescription = null, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.size(9.dp))
                Text(
                    text = stringResource(R.string.friday_brand_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { themeViewModel.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (mode == ThemeController.Mode.DARK) TnIcons.Sun else TnIcons.Moon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.friday_login_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.friday_or_use_account_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))
                GoogleButton(
                    loading = signingIn,
                    onClick = { accountViewModel.signInWithGoogle(context as ComponentActivity) },
                )
            }

            Spacer(Modifier.weight(1f))

            TermsAndPrivacyFooter()

            Spacer(Modifier.height(20.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }

    if (noGoogleAccount) {
        NoGoogleAccountDialog(
            onConfirm = {
                accountViewModel.dismissNoGoogleAccount()
                openSystemAddAccount(context)
            },
            onDismiss = { accountViewModel.dismissNoGoogleAccount() },
        )
    }
}

@Composable
private fun NoGoogleAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.friday_login_error_no_credential),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.friday_login_error_no_credential_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .background(FridayPalette.Primary, RoundedCornerShape(12.dp))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.friday_login_action_open_settings),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = FridayPalette.OnPrimary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.friday_login_action_close),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun TermsAndPrivacyFooter() {
    val prefix = stringResource(R.string.friday_login_terms_prefix)
    val terms = stringResource(R.string.friday_login_terms_link)
    val and = stringResource(R.string.friday_login_terms_and)
    val privacy = stringResource(R.string.friday_login_privacy_link)
    Text(
        text = "$prefix $terms $and $privacy",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Launch the system flow that lets the user add a Google account. We never
 * auto-open Settings on a sign-in error — the dialog asks the user to tap
 * the primary button first. Falls back through less-specific intents if the
 * add-account intent isn't available on this device.
 */
private fun openSystemAddAccount(context: android.content.Context) {
    val addAccount = android.content.Intent(android.provider.Settings.ACTION_ADD_ACCOUNT).apply {
        putExtra(android.provider.Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
    }
    runCatching { context.startActivity(addAccount) }
        .recoverCatching {
            val syncSettings = android.content.Intent(android.provider.Settings.ACTION_SYNC_SETTINGS)
            runCatching { context.startActivity(syncSettings) }
                .recoverCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                }
        }
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(FridayPalette.Primary, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = FridayPalette.OnPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, FridayPalette.OnPrimary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "G",
                        color = Color(0xFF4285F4),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.friday_continue_with_google),
                    color = FridayPalette.OnPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
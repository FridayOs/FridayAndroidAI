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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val error by accountViewModel.error.collectAsStateWithLifecycle()
    val mode by themeViewModel.mode.collectAsStateWithLifecycle()
    val logo = rememberAssetBitmap(FRIDAY_LOGO_ASSET)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

        Spacer(Modifier.height(34.dp))
        Text(
            text = stringResource(R.string.friday_login_heading),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.friday_or_use_account_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.friday_name_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(TnIcons.Person, contentDescription = null, modifier = Modifier.size(19.dp)) },
            shape = RoundedCornerShape(15.dp),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.friday_password_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(TnIcons.Lock, contentDescription = null, modifier = Modifier.size(19.dp)) },
            shape = RoundedCornerShape(15.dp),
        )

        Spacer(Modifier.height(26.dp))
        GoogleButton(
            loading = signingIn,
            onClick = { accountViewModel.signInWithGoogle(context as ComponentActivity) },
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(28.dp))
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

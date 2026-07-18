package com.dark.tool_neuron.ui.screens.setup_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.PinAction
import com.dark.tool_neuron.ui.components.PinActionRow
import com.dark.tool_neuron.ui.components.PinDotRow
import com.dark.tool_neuron.ui.components.PinNumberPad
import com.dark.tool_neuron.ui.components.SecureScreen
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.friday.ai.R
import kotlinx.coroutines.delay

@Composable
fun SetupPasswordScreen(
    innerPadding: PaddingValues,
    password: String,
    isConfirmStep: Boolean,
    errorRes: Int?,
    errorArg: Int?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    SecureScreen {
        SetupPasswordScreenContent(innerPadding, password, isConfirmStep, errorRes, errorArg, onDigit, onDelete, onClear, onSubmit, onBack)
    }
}

@Composable
private fun SetupPasswordScreenContent(
    innerPadding: PaddingValues,
    password: String,
    isConfirmStep: Boolean,
    errorRes: Int?,
    errorArg: Int?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = TnIcons.OAuth,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(dimens.spacingMd))

                Text(
                    text = stringResource(
                        if (isConfirmStep) R.string.friday_setup_password_confirm_title
                        else R.string.friday_setup_password_create_title
                    ),
                    fontFamily = groteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.3).sp,
                )

                Spacer(Modifier.height(dimens.spacingXs))

                Text(
                    text = stringResource(
                        if (isConfirmStep) R.string.friday_setup_password_confirm_subtitle
                        else R.string.friday_setup_password_create_subtitle
                    ),
                    fontFamily = jakartaFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinDotRow(length = password.length)

                Spacer(Modifier.height(dimens.spacingSm))

                val errorAlpha by animateFloatAsState(
                    targetValue = if (errorRes != null) 1f else 0f,
                    animationSpec = Motion.state(),
                    label = "setupErrorAlpha"
                )
                val errorText = errorRes?.let { resId ->
                    if (errorArg != null) stringResource(resId, errorArg) else stringResource(resId)
                } ?: ""
                Text(
                    text = errorText,
                    fontFamily = jakartaFamily,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(20.dp).alpha(errorAlpha)
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingLg))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 2 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PinNumberPad(onDigit = onDigit, onDelete = onDelete)

                Spacer(Modifier.height(dimens.spacingMd))

                PinActionRow(
                    actions = listOf(
                        PinAction(stringResource(R.string.friday_setup_password_action_back), onBack),
                        PinAction(stringResource(R.string.friday_setup_password_action_clear), onClear),
                        PinAction(
                            label = stringResource(
                                if (isConfirmStep) R.string.friday_setup_password_action_confirm
                                else R.string.friday_setup_password_action_next
                            ),
                            onClick = onSubmit,
                            enabled = password.length >= SetupViewModel.MIN_PIN_LENGTH,
                            primary = true
                        )
                    )
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

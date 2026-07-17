package com.dark.tool_neuron.ui.screens.setup_screen

import com.friday.ai.R

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding.components.OnboardingStepIndicator
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import kotlinx.coroutines.delay

/*
 * Protect Friday re-skin (FRI-582 P5, design-spec §5, HTML lines 187-234):
 * step dots -> title/sub -> lock cards (icon + title + sub + detail +
 * checkmark selection). Tapping a card selects it AND advances (existing
 * SetupViewModel/SecurityManager contract, unchanged) — layout/tokens only.
 */
@Composable
fun SetupScreen(
    innerPadding: PaddingValues,
    selectedMode: String?,
    onModeSelected: (String) -> Unit
) {
    val dimens = LocalDimens.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(80); visible = true }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        OnboardingStepIndicator(
            current = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 }
            ) {
                Column {
                    Icon(
                        imageVector = TnIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(dimens.spacingLg))

                    Text(
                        text = stringResource(R.string.friday_setup_protect_title),
                        fontFamily = groteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.3).sp,
                    )

                    Spacer(Modifier.height(dimens.spacingXs))

                    Text(
                        text = stringResource(R.string.friday_setup_protect_subtitle),
                        fontFamily = jakartaFamily,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(dimens.spacingXl))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 }
            ) {
                Column {
                    SecurityOption(
                        icon = TnIcons.Lock,
                        title = stringResource(R.string.friday_setup_app_password_title),
                        subtitle = stringResource(R.string.friday_setup_app_password_subtitle),
                        detail = stringResource(R.string.friday_setup_app_password_detail),
                        selected = selectedMode == "app_password",
                        onClick = { onModeSelected("app_password") }
                    )

                    Spacer(Modifier.height(dimens.spacingSm))

                    SecurityOption(
                        icon = TnIcons.Eye,
                        title = stringResource(R.string.friday_setup_no_lock_title),
                        subtitle = stringResource(R.string.friday_setup_no_lock_subtitle),
                        detail = stringResource(R.string.friday_setup_no_lock_detail),
                        selected = selectedMode == "none",
                        onClick = { onModeSelected("none") }
                    )

                    Spacer(Modifier.height(dimens.spacingMd))

                    Text(
                        text = stringResource(R.string.friday_setup_change_later),
                        fontFamily = jakartaFamily,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.color else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "optionBorder"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) accent.soft else MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "optionBg"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) accent.soft else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = jakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    text = subtitle,
                    fontFamily = jakartaFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = detail,
                    fontFamily = jakartaFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (selected) {
                Spacer(Modifier.width(dimens.spacingSm))
                Icon(
                    imageVector = TnIcons.Check,
                    contentDescription = null,
                    tint = accent.color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

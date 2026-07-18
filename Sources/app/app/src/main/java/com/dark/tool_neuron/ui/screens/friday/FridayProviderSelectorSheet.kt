package com.dark.tool_neuron.ui.screens.friday

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.model.gateway.GatewayConfig
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Canonical post-onboarding provider/model selector (design-spec §3).
 * Stateless — hosts hoist providers/activeId/callbacks so Phase 4 can mount
 * this over both Voice and Chat without duplicating state. Scrim fdDim .22s,
 * sheet fdSheet .3s cubic-bezier(.2,.8,.25,1), radius 24 24 0 0, max-height
 * 78%. Back press + outside-tap both call onDismiss.
 */
private val sheetEasing = CubicBezierEasing(0.2f, 0.8f, 0.25f, 1f)

@Composable
fun FridayProviderSelectorSheet(
    visible: Boolean,
    providers: List<GatewayConfig>,
    activeId: String,
    onSelect: (String) -> Unit,
    onAddProvider: () -> Unit,
    onManageProviders: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val accent = LocalFridayAccent.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
                    .clickable(onClick = onDismiss),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(300, easing = sheetEasing)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(300, easing = sheetEasing)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.78f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(bottom = dimens.spacingLg),
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = dimens.spacingSm)
                        .width(36.dp)
                        .height(4.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                Column(modifier = Modifier.padding(horizontal = dimens.screenPadding, vertical = dimens.spacingMd)) {
                    Text(
                        text = stringResource(R.string.friday_selector_title),
                        fontFamily = groteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = stringResource(R.string.friday_selector_privacy),
                        fontFamily = jakartaFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                val active = providers.find { it.id == activeId }
                Box(modifier = Modifier.padding(horizontal = dimens.screenPadding)) {
                    if (active != null) {
                        CurrentProviderCard(active)
                    } else {
                        NoProviderCard()
                    }
                }

                Spacer(modifier = Modifier.height(dimens.spacingSm))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(horizontal = dimens.screenPadding),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.spacingXs),
                ) {
                    items(providers, key = { it.id }) { config ->
                        ProviderSelectorRow(
                            config = config,
                            isSelected = config.id == activeId,
                            onClick = { onSelect(config.id) },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingSm),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    Button(
                        onClick = onAddProvider,
                        colors = ButtonDefaults.buttonColors(containerColor = accent.color, contentColor = accent.onColor),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.friday_add_provider), fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onManageProviders,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.friday_manage_providers), fontFamily = jakartaFamily, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentProviderCard(config: GatewayConfig) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(dimens.cardPadding),
    ) {
        Text(
            text = stringResource(R.string.friday_current),
            fontFamily = jakartaFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${config.provider.displayName} · ${config.displayModel}",
            fontFamily = groteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(R.string.friday_direct_from_android),
            fontFamily = jakartaFamily,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun NoProviderCard() {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.cardCornerRadius))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(dimens.cardCornerRadius),
            )
            .padding(dimens.cardPadding),
    ) {
        Text(
            text = stringResource(R.string.friday_no_provider_title),
            fontFamily = groteskFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(R.string.friday_no_provider_body),
            fontFamily = jakartaFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

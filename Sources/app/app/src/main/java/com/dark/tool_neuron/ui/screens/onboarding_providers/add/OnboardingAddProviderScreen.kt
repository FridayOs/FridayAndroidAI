package com.dark.tool_neuron.ui.screens.onboarding_providers.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.friday.components.AssetGif
import com.dark.tool_neuron.ui.screens.onboarding_providers.common.OnboardingToast
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderStatus
import com.dark.tool_neuron.viewmodel.OnboardingAddProviderViewModel
import com.friday.ai.R

/*
 * Add Provider screen (FRI-582 P8, design-spec §8): back+title -> privacy
 * banner -> fields -> test button (+ real DirectGatewayClient probe via VM) ->
 * result card -> sticky Save-only/Save & use footer. Toast is local state; its
 * auto-dismiss (see OnboardingToast) pops back to Providers via onBack.
 */
@Composable
fun OnboardingAddProviderScreen(
    innerPadding: PaddingValues,
    viewModel: OnboardingAddProviderViewModel,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    val label by viewModel.label.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val advancedOpen by viewModel.advancedOpen.collectAsStateWithLifecycle()
    val keyVisible by viewModel.keyVisible.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val errorRes by viewModel.errorRes.collectAsStateWithLifecycle()
    val failureMessage by viewModel.failureMessage.collectAsStateWithLifecycle()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    val savedToast = stringResource(R.string.friday_add_provider_saved_toast)
    val savedUseToastTemplate = stringResource(R.string.friday_add_provider_saved_use_toast)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = TnIcons.ArrowLeft, contentDescription = stringResource(R.string.friday_providers_cancel))
                    }
                    Text(
                        text = stringResource(R.string.friday_add_provider_title_prefix) + viewModel.provider.displayName,
                        fontFamily = groteskFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.friday_add_provider_privacy),
                    fontFamily = jakartaFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimens.spacingSm),
                )
            }
            item {
                AddProviderFields(
                    label = label,
                    onLabelChange = viewModel::onLabelChange,
                    needsKey = viewModel.provider.needsKey,
                    apiKey = apiKey,
                    onApiKeyChange = viewModel::onApiKeyChange,
                    keyVisible = keyVisible,
                    onToggleKeyVisible = viewModel::toggleKeyVisible,
                    model = model,
                    onModelChange = viewModel::onModelChange,
                    baseUrl = baseUrl,
                    onBaseUrlChange = viewModel::onBaseUrlChange,
                    urlRequired = viewModel.provider.urlRequired,
                    advancedOpen = advancedOpen,
                    onToggleAdvanced = viewModel::toggleAdvanced,
                    errorRes = errorRes,
                )
            }
            item {
                TestConnectionButton(status = status, onClick = viewModel::test)
            }
            if (status == OnboardingAddProviderStatus.NOT_TESTED) {
                item {
                    Text(
                        text = stringResource(R.string.friday_add_provider_test_hint),
                        fontFamily = jakartaFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                AddProviderTestResultCard(status = status, failureMessage = failureMessage)
            }
        }

        AddProviderFooter(
            enabled = status != OnboardingAddProviderStatus.TESTING,
            onSaveOnly = {
                viewModel.save(use = false)?.let { toastMessage = savedToast }
            },
            onSaveAndUse = {
                viewModel.save(use = true)?.let { cfg ->
                    toastMessage = String.format(savedUseToastTemplate, cfg.provider.displayName, cfg.model)
                }
            },
            modifier = Modifier.padding(horizontal = dimens.screenPadding, vertical = dimens.spacingSm),
        )
    }

    toastMessage?.let { message ->
        OnboardingToast(message = message, onDismiss = { toastMessage = null; onBack() })
    }
}

@Composable
private fun TestConnectionButton(status: OnboardingAddProviderStatus, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val testing = status == OnboardingAddProviderStatus.TESTING
    OutlinedButton(
        onClick = onClick,
        enabled = !testing,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.chipCornerRadius)),
    ) {
        if (testing) {
            AssetGif(assetName = "loading.gif", active = true, modifier = Modifier.padding(end = 8.dp))
        }
        Text(
            text = stringResource(if (testing) R.string.friday_add_provider_testing else R.string.friday_add_provider_test_connection),
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

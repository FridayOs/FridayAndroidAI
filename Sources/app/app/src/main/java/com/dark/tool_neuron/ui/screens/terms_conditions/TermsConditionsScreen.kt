package com.dark.tool_neuron.ui.screens.terms_conditions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.onboarding.components.OnboardingStepIndicator
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.friday.ai.R

/*
 * Terms & Conditions (design-spec §3, HTML TERMS array lines 1417-1430). Step 1
 * of 6 in the linear onboarding step-indicator (Terms=1). Non-skippable: no skip
 * action, BackHandler consumes back-press. Body content data-driven by TermsItem
 * list below; icon mapping documented per entry (design icon name -> TnIcons;
 * project has no material-icons-extended dependency, so closest available
 * TnIcons entries are used):
 *   lock -> TnIcons.Lock, user -> TnIcons.Person, link -> TnIcons.Server
 *   (gateway/API equivalent), alert -> TnIcons.AlertTriangle, shield ->
 *   TnIcons.Shield, check -> TnIcons.CircleCheck.
 *
 * Real accept-flow wiring (markTermsAccepted + navigation) lives in
 * AppScaffold.kt's onTermsAccepted callback via TermsConditionsBottomBar /
 * AppBottomBar dispatch — onAccept here stays a pass-through per existing
 * architecture (mirrors TNavigation.kt's composable registration).
 *
 * Non-skippable forward path only: BackHandler still consumes the system
 * back gesture (no accidental pop), but the design's `goBack` (HTML:1499-1501)
 * still allows an explicit chevron tap back to Language selection.
 */
private data class TermsItem(val icon: ImageVector, val titleRes: Int, val bodyRes: Int)

private val TERMS_ITEMS = listOf(
    TermsItem(TnIcons.Lock, R.string.onboarding_terms_card1_title, R.string.onboarding_terms_card1_body),
    TermsItem(TnIcons.Person, R.string.onboarding_terms_card2_title, R.string.onboarding_terms_card2_body),
    TermsItem(TnIcons.Server, R.string.onboarding_terms_card3_title, R.string.onboarding_terms_card3_body),
    TermsItem(TnIcons.AlertTriangle, R.string.onboarding_terms_card4_title, R.string.onboarding_terms_card4_body),
    TermsItem(TnIcons.Shield, R.string.onboarding_terms_card5_title, R.string.onboarding_terms_card5_body),
    TermsItem(TnIcons.CircleCheck, R.string.onboarding_terms_card6_title, R.string.onboarding_terms_card6_body),
)

@Composable
fun TermsConditionsScreen(
    innerPadding: PaddingValues,
    onAccept: () -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    var showPrivacyDialog by remember { mutableStateOf(false) }

    // Non-skippable: consume system back-press instead of allowing pop.
    // Explicit chevron tap (below) still navigates back per design `goBack`.
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
    ) {
        ActionButton(
            onClickListener = onBack,
            icon = TnIcons.ArrowLeft,
            contentDescription = stringResource(R.string.friday_onboarding_back_content_description),
            modifier = Modifier.padding(start = dimens.screenPadding, top = 10.dp),
        )
        OnboardingStepIndicator(
            current = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
        ) {
            Text(
                text = stringResource(R.string.onboarding_terms_title),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.onboarding_terms_subtitle),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(TERMS_ITEMS) { item ->
                TermsCard(
                    icon = item.icon,
                    title = stringResource(item.titleRes),
                    body = stringResource(item.bodyRes),
                )
            }
            item {
                val accent = LocalFridayAccent.current
                Text(
                    text = stringResource(R.string.onboarding_terms_privacy_link),
                    fontFamily = jakartaFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = accent.color,
                    textDecoration = TextDecoration.Underline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyDialog = true }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.onboarding_terms_privacy_dialog_title),
                    fontFamily = groteskFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.onboarding_terms_privacy_dialog_body),
                    fontFamily = jakartaFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(
                        text = stringResource(R.string.onboarding_terms_privacy_dialog_got_it),
                        fontFamily = jakartaFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        )
    }
}

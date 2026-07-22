package com.dark.tool_neuron.ui.screens.onboarding_tour

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * Feature Tour (design-spec §4, HTML TOUR array lines 1432-1443). Step 3 of 6
 * in the linear onboarding step-indicator (Tour=2). Self-contained screen
 * registered directly in TNavigation.kt (mirrors LanguageSelectionScreen.kt),
 * no ViewModel needed — the 5 cards are a fixed list.
 *
 * Icon mapping (design icon name -> TnIcons; project has no material-icons-extended
 * dependency and no literal "link"/"sprout" icon, so closest available TnIcons
 * entries are used and documented here):
 *   lock -> TnIcons.Lock, mic -> TnIcons.Mic, link -> TnIcons.Server (gateway/API
 *   equivalent), shield -> TnIcons.ShieldCheck (encrypted-history theme,
 *   differentiated from Terms' TnIcons.Shield), sprout -> TnIcons.Leaf (closest
 *   available growth/plant icon).
 *
 * Back nav (FRI-582 QA blocker fixes, design `goBack` HTML:1499-1501): explicit
 * chevron tap navigates back to Terms & Conditions via onBack (wired in
 * TNavigation.kt through OnboardingBackNav) — no BackHandler override needed
 * here since this screen is skippable-forward-only, not non-skippable.
 */
@Composable
fun FeatureTourScreen(
    innerPadding: PaddingValues,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current

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
            current = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FeatureTourBadge()

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.onboarding_tour_title),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onboarding_tour_subtitle),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FeatureTourCard(
                    icon = TnIcons.Lock,
                    title = stringResource(R.string.onboarding_tour_card1_title),
                    body = stringResource(R.string.onboarding_tour_card1_body),
                )
                FeatureTourCard(
                    icon = TnIcons.Mic,
                    title = stringResource(R.string.onboarding_tour_card2_title),
                    body = stringResource(R.string.onboarding_tour_card2_body),
                )
                FeatureTourCard(
                    icon = TnIcons.Server,
                    title = stringResource(R.string.onboarding_tour_card3_title),
                    body = stringResource(R.string.onboarding_tour_card3_body),
                )
                FeatureTourCard(
                    icon = TnIcons.ShieldCheck,
                    title = stringResource(R.string.onboarding_tour_card4_title),
                    body = stringResource(R.string.onboarding_tour_card4_body),
                )
                FeatureTourCard(
                    icon = TnIcons.Leaf,
                    title = stringResource(R.string.onboarding_tour_card5_title),
                    body = stringResource(R.string.onboarding_tour_card5_body),
                )
            }

            Text(
                text = stringResource(R.string.friday_setup_change_later),
                fontFamily = jakartaFamily,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            )
        }

        Box(modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp)) {
            FeatureTourCta(onClick = onContinue)
        }
    }
}

@Composable
private fun FeatureTourCta(onClick: () -> Unit) {
    val accent = LocalFridayAccent.current
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.color,
            contentColor = accent.onColor,
        ),
    ) {
        Text(
            text = stringResource(R.string.onboarding_tour_continue_cta),
            fontFamily = jakartaFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
        )
    }
}

@Composable
private fun FeatureTourBadge() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.friday_ai_icon),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    }
}

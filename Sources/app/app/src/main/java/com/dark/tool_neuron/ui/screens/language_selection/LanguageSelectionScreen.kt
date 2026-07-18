package com.dark.tool_neuron.ui.screens.language_selection

import android.app.Activity
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.AppLanguage
import com.dark.tool_neuron.ui.screens.onboarding.components.OnboardingStepIndicator
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.groteskFamily
import com.dark.tool_neuron.ui.theme.jakartaFamily
import com.dark.tool_neuron.viewmodel.LanguageViewModel
import com.dark.tool_neuron.viewmodel.OnboardingLocaleTransition
import com.friday.ai.R

/*
 * Language selection (design-spec §2, HTML lines 64-104). Step 1 of 6 in the
 * linear onboarding step-indicator (Language=0). Both languages are shown
 * simultaneously — this screen has not applied a locale yet, so title/subtext/
 * note are rendered as fixed EN+VI dual-language strings (`translatable="false"`),
 * not resolved via the active app locale. Only `en`/`vi` cards are offered
 * (no "system default" card in this design).
 */
@Composable
fun LanguageSelectionScreen(
    innerPadding: PaddingValues,
    onContinue: () -> Unit,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val context = LocalContext.current
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
    ) {
        OnboardingStepIndicator(
            current = 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding, vertical = dimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LanguageIconBadge()

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.onboarding_lang_title_en),
                fontFamily = groteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 23.sp,
                letterSpacing = (-0.3).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.onboarding_lang_title_vi),
                fontFamily = jakartaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                text = stringResource(R.string.onboarding_lang_subtitle_dual),
                fontFamily = jakartaFamily,
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LanguageCard(
                    name = stringResource(R.string.friday_language_english),
                    description = stringResource(R.string.onboarding_lang_card_en_desc),
                    selected = selected == AppLanguage.EN,
                    onClick = { viewModel.choose(AppLanguage.EN) },
                )
                LanguageCard(
                    name = stringResource(R.string.friday_language_vietnamese),
                    description = stringResource(R.string.onboarding_lang_card_vi_desc),
                    selected = selected == AppLanguage.VI,
                    onClick = { viewModel.choose(AppLanguage.VI) },
                )
            }

            Column(
                modifier = Modifier.padding(top = 18.dp, start = 6.dp, end = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_lang_note_en),
                    fontFamily = jakartaFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.onboarding_lang_note_vi),
                    fontFamily = jakartaFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(modifier = Modifier.padding(horizontal = 26.dp, vertical = 10.dp)) {
            LanguageCta(
                selected = selected,
                onClick = {
                    // Activity's config locale (applied at attachBaseContext) is stale until a
                    // recreate re-wraps the base context with the newly-persisted tag. Comparing
                    // against the LIVE Activity Configuration (not the already-updated selected
                    // StateFlow) is what detects the mismatch that needs a recreate.
                    val activityTag = context.resources.configuration.locales[0].language
                    // confirm() MUST persist synchronously (LanguageController.markFirstSelectionDone
                    // → AppPreferences flushAll) so resolveNext() below already reads
                    // languageSelected=true and routes to Terms — and so the post-recreate restore
                    // lands on Terms, not Language. A future async-prefs refactor would break one-tap.
                    viewModel.confirm()
                    val activity = context as? Activity
                    if (OnboardingLocaleTransition.localeChanged(selected.uiTag, activityTag) && activity != null) {
                        // Navigate FIRST so the saved NavController back stack captures the next
                        // route (Terms). recreate() re-runs attachBaseContext → re-wraps with the
                        // new persisted locale; restoreState() then shows THAT destination (Terms)
                        // in the new locale — one tap, no double-Continue.
                        onContinue()
                        activity.recreate()
                    } else {
                        // No locale change, or no Activity to recreate — never leave the CTA dead.
                        onContinue()
                    }
                },
            )
        }
    }
}

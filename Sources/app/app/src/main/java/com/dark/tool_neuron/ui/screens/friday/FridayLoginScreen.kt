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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.friday.components.FRIDAY_LOGO_ASSET
import com.dark.tool_neuron.ui.screens.friday.components.rememberAssetBitmap
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.PoppinsFontFamily
import com.dark.tool_neuron.ui.theme.colorSchemeFor
import com.dark.tool_neuron.ui.util.FridayPalette
import com.dark.tool_neuron.viewmodel.AccountViewModel
import com.dark.tool_neuron.viewmodel.FridayThemeViewModel
import kotlinx.coroutines.launch

/*
 * FRI-633 phase-11 QA rework round 3 (Blocker 1a): behavior-preserving split.
 * The stateful [FridayLoginScreen] wrapper owns the viewmodel collection + the
 * one-shot snackbar LaunchedEffect and delegates all rendering to the stateless
 * [FridayLoginContent] below. Auth state (`signingIn`, `noGoogleAccount`, the
 * snackbar host) is fully injected, so an androidTest can host FridayLoginContent
 * directly and drive loading / no-account / error / cancel deterministically for
 * capture+assert (FridayLoginAuthStateCaptureTest) WITHOUT any production auth
 * bypass — `signInWithGoogle` still runs the real gateway through the wrapper.
 * Zero visual/copy change: every testTag, the footer FlowRow, GoogleCircleButton
 * and NoGoogleAccountDialog are preserved verbatim.
 */
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

    FridayLoginContent(
        innerPadding = innerPadding,
        signingIn = signingIn,
        noGoogleAccount = noGoogleAccount,
        mode = mode,
        logo = logo,
        snackbarHostState = snackbarHostState,
        onGoogleClick = { accountViewModel.signInWithGoogle(context as ComponentActivity) },
        onThemeToggle = { themeViewModel.toggle() },
        onDismissNoGoogle = { accountViewModel.dismissNoGoogleAccount() },
        onAddGoogleAccount = {
            accountViewModel.dismissNoGoogleAccount()
            openSystemAddAccount(context)
        },
    )
}

/**
 * Stateless Friday login surface. Renders purely from the injected auth state
 * (`signingIn`, `noGoogleAccount`, `snackbarHostState`) plus `mode`/`logo`, and
 * routes every external-effect action (Google sign-in, theme toggle, add/dismiss
 * Google account) through callbacks. Pure-UI concerns (name/password fields,
 * password reveal, coming-soon/validation feedback, legal dialogs) stay local —
 * they have no external observers, so keeping them here avoids over-hoisting
 * (KISS/YAGNI) while still leaving every auth state deterministically injectable.
 */
@Composable
fun FridayLoginContent(
    innerPadding: PaddingValues,
    signingIn: Boolean,
    noGoogleAccount: Boolean,
    mode: ThemeController.Mode,
    logo: ImageBitmap?,
    snackbarHostState: SnackbarHostState,
    onGoogleClick: () -> Unit,
    onThemeToggle: () -> Unit,
    onDismissNoGoogle: () -> Unit,
    onAddGoogleAccount: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val comingSoonMsg = stringResource(R.string.friday_login_coming_soon)
    val validationMsg = stringResource(R.string.friday_login_validation_message)
    fun showComingSoon() {
        scope.launch { snackbarHostState.showSnackbar(comingSoonMsg) }
    }
    fun onLoginNowClick() {
        if (name.isBlank() || password.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar(validationMsg) }
        } else {
            showComingSoon()
        }
    }

    // FRI-633 Blocker 1 follow-up: pin Login's neutral surface roles to the
    // canonical FRIDAY palette regardless of the app-wide theme (DYNAMIC by
    // default — see MainActivity's ToolNeuronTheme). The ambient dark/light
    // decision (manual toggle or system dark) is preserved by sampling the
    // inherited background luminance BEFORE overriding the scheme, so the
    // sun/moon toggle above still flips Login between FRIDAY light/dark.
    // The accent axis (FridayPalette.Primary/OnPrimary, hardcoded below) is
    // untouched by this scheme swap — it never reads colorScheme.primary.
    val fridayDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fridayScheme = colorSchemeFor(ColorPalette.FRIDAY, fridayDark, LocalContext.current)

    MaterialTheme(colorScheme = fridayScheme) {
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
                .verticalScroll(rememberScrollState())
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
                    fontFamily = PoppinsFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("friday_login_theme_toggle")
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { onThemeToggle() },
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

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.friday_login_heading),
                    fontFamily = PoppinsFontFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(7.dp))
                Row {
                    Text(
                        text = stringResource(R.string.friday_login_create_account_prompt),
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = stringResource(R.string.friday_login_account_link),
                        fontFamily = PoppinsFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .testTag("friday_login_account_link")
                            .clickable { showComingSoon() },
                    )
                }

                Spacer(Modifier.height(28.dp))

                LoginTextField(
                    label = stringResource(R.string.friday_name_label),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.friday_login_name_placeholder),
                    leadingIcon = TnIcons.Person,
                    testTag = "friday_login_name_input",
                )

                Spacer(Modifier.height(18.dp))

                LoginTextField(
                    label = stringResource(R.string.friday_password_label),
                    value = password,
                    onValueChange = { password = it },
                    placeholder = stringResource(R.string.friday_login_password_placeholder),
                    leadingIcon = TnIcons.Lock,
                    testTag = "friday_login_password_input",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        val showCd = stringResource(R.string.friday_login_password_show_cd)
                        val hideCd = stringResource(R.string.friday_login_password_hide_cd)
                        Icon(
                            imageVector = if (passwordVisible) TnIcons.EyeOff else TnIcons.Eye,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(19.dp)
                                .clickable { passwordVisible = !passwordVisible }
                                .semantics {
                                    contentDescription = if (passwordVisible) hideCd else showCd
                                    role = Role.Button
                                },
                        )
                    },
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.friday_login_forgot_password),
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag("friday_login_forgot_password")
                        .clickable { showComingSoon() },
                )

                Spacer(Modifier.height(26.dp))

                PrimaryLoginButton(
                    text = stringResource(R.string.friday_login_now_button),
                    testTag = "friday_login_now_button",
                    onClick = { onLoginNowClick() },
                )

                Spacer(Modifier.height(13.dp))

                OutlineLoginButton(
                    text = stringResource(R.string.friday_login_register_now_button),
                    testTag = "friday_login_register_now_button",
                    onClick = { showComingSoon() },
                )

                Spacer(Modifier.height(30.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    GoogleCircleButton(
                        loading = signingIn,
                        onClick = onGoogleClick,
                    )
                    Spacer(Modifier.size(18.dp))
                    FacebookCircleButton(onClick = { showComingSoon() })
                }
            }

            Spacer(Modifier.height(28.dp))

            TermsAndPrivacyFooter()

            Spacer(Modifier.height(28.dp))
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
            onConfirm = onAddGoogleAccount,
            onDismiss = onDismissNoGoogle,
        )
    }
    }
}

@Composable
private fun LoginTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    testTag: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = PoppinsFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = PoppinsFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = KeyboardActions.Default,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun PrimaryLoginButton(text: String, testTag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
            .background(FridayPalette.Primary, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = FridayPalette.OnPrimary,
            fontFamily = PoppinsFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlineLoginButton(text: String, testTag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = PoppinsFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GoogleCircleButton(loading: Boolean, onClick: () -> Unit) {
    val cd = stringResource(R.string.friday_continue_with_google)
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("friday_login_google_button")
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = !loading, onClick = onClick)
            .semantics {
                contentDescription = cd
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                // Additive tag (FRI-633 phase-11 Blocker 1b): lets the auth-state
                // capture test assert the loading spinner deterministically. No
                // visual change — same size/color/stroke as before.
                modifier = Modifier
                    .size(20.dp)
                    .testTag("friday_login_google_progress"),
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
            )
        } else {
            // FRI-633 phase-11 blocker 1a: real full-color Google "G" logo (canonical
            // prototype SVG → res/drawable/google_logo.xml), replacing the mono-blue
            // "G" text glyph that broke exact visual parity.
            Image(
                painter = painterResource(R.drawable.google_logo),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FacebookCircleButton(onClick: () -> Unit) {
    val cd = stringResource(R.string.friday_login_facebook_cd)
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag("friday_login_facebook_button")
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = cd
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        // FRI-633 phase-11 blocker 1a: real Facebook brand logo (canonical prototype
        // SVG → res/drawable/facebook_logo.xml), replacing the mono "f" text glyph.
        Image(
            painter = painterResource(R.drawable.facebook_logo),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun NoGoogleAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // FRI-633 device-test fix: AlertDialog's slot content composes in a
    // separate Compose Dialog window, which re-provides LocalContext /
    // LocalConfiguration from the platform (device locale) instead of
    // inheriting whatever the hosting composition overrode them to. That's a
    // no-op in the real app (ambient context here IS already the real
    // activity context), but under Fri633ThemedHost's VI-locale capture
    // override it made this dialog fall back to EN strings. Capturing the
    // ambient values here and re-providing them inside each slot lambda makes
    // stringResource() resolve against the hosting composition's locale in
    // both cases — no string/testTag/structure/callback changes.
    val dialogContext = LocalContext.current
    val dialogConfig = LocalConfiguration.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CompositionLocalProvider(
                LocalContext provides dialogContext,
                LocalConfiguration provides dialogConfig,
            ) {
                Text(
                    text = stringResource(R.string.friday_login_error_no_credential),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            CompositionLocalProvider(
                LocalContext provides dialogContext,
                LocalConfiguration provides dialogConfig,
            ) {
                Text(
                    text = stringResource(R.string.friday_login_error_no_credential_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            CompositionLocalProvider(
                LocalContext provides dialogContext,
                LocalConfiguration provides dialogConfig,
            ) {
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
            }
        },
        dismissButton = {
            CompositionLocalProvider(
                LocalContext provides dialogContext,
                LocalConfiguration provides dialogConfig,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.friday_login_action_close),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

/*
 * Codex QA rework blocker 1 (FRI-633 phase-08): each legal word must be an
 * independently tappable link invoking a REAL honest action — an in-app
 * AlertDialog showing the real terms/privacy content already used by
 * TermsConditionsScreen.kt (onboarding_terms_* strings), not a dead Text.
 * No new strings needed: both dialogs fully reuse existing onboarding_terms_*
 * resources (values + values-vi already have them), so no string-file edits.
 *
 * Codex QA rework round-2 (FRI-633 phase-10, blocker 1a): the previous `Row`
 * never wraps when the 4 Texts overflow one line at narrow widths — Row gives
 * each child only its "fair share" of the remaining width once it runs out of
 * space, so "Privacy Policy" was rendered clipped into a single narrow column
 * (each character on its own line), confirmed in
 * evidence/device/fri633_captures_fix2b/FridayLoginScreen__EN__light__na.png.
 * Swapping to `FlowRow` (same 4 Text children + both testTags unchanged) lets
 * whole Text elements wrap onto additional centered lines instead of clipping
 * within a single line — verified against EN and VI (VI legal copy is longer).
 */
private enum class LegalDialogKind { TERMS, PRIVACY }

@Composable
private fun TermsAndPrivacyFooter() {
    val prefix = stringResource(R.string.friday_login_terms_prefix)
    val terms = stringResource(R.string.friday_login_terms_link)
    val and = stringResource(R.string.friday_login_terms_and)
    val privacy = stringResource(R.string.friday_login_privacy_link)
    var legalDialog by remember { mutableStateOf<LegalDialogKind?>(null) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$prefix ",
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = terms,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .testTag("friday_login_terms_link")
                .clickable { legalDialog = LegalDialogKind.TERMS },
        )
        Text(
            text = " $and ",
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = privacy,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .testTag("friday_login_privacy_link")
                .clickable { legalDialog = LegalDialogKind.PRIVACY },
        )
    }

    legalDialog?.let { kind ->
        LegalInfoDialog(kind = kind, onDismiss = { legalDialog = null })
    }
}

@Composable
private fun LegalInfoDialog(kind: LegalDialogKind, onDismiss: () -> Unit) {
    val termsTitle = stringResource(R.string.onboarding_terms_title)
    val c1t = stringResource(R.string.onboarding_terms_card1_title)
    val c1b = stringResource(R.string.onboarding_terms_card1_body)
    val c2t = stringResource(R.string.onboarding_terms_card2_title)
    val c2b = stringResource(R.string.onboarding_terms_card2_body)
    val c3t = stringResource(R.string.onboarding_terms_card3_title)
    val c3b = stringResource(R.string.onboarding_terms_card3_body)
    val c4t = stringResource(R.string.onboarding_terms_card4_title)
    val c4b = stringResource(R.string.onboarding_terms_card4_body)
    val c5t = stringResource(R.string.onboarding_terms_card5_title)
    val c5b = stringResource(R.string.onboarding_terms_card5_body)
    val c6t = stringResource(R.string.onboarding_terms_card6_title)
    val c6b = stringResource(R.string.onboarding_terms_card6_body)
    val termsBody = "$c1t: $c1b\n\n$c2t: $c2b\n\n$c3t: $c3b\n\n$c4t: $c4b\n\n$c5t: $c5b\n\n$c6t: $c6b"

    val privacyTitle = stringResource(R.string.onboarding_terms_privacy_dialog_title)
    val privacyBody = stringResource(R.string.onboarding_terms_privacy_dialog_body)
    val gotIt = stringResource(R.string.onboarding_terms_privacy_dialog_got_it)

    val title = if (kind == LegalDialogKind.TERMS) termsTitle else privacyTitle
    val body = if (kind == LegalDialogKind.TERMS) termsBody else privacyBody

    AlertDialog(
        modifier = Modifier.testTag("friday_login_legal_dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = gotIt)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
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

package com.dark.tool_neuron.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.data.ThemeController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/*
 * All 15 M3 roles use Figtree. Weight contrast drives hierarchy, not just size:
 *   Display  → Light (300)     large thin text feels premium
 *   Headline → SemiBold (600)  punchy section headers
 *   Title    → Medium (500)    card titles, nav labels
 *   Body     → Regular (400)   reading copy
 *   Label    → SemiBold (600)  buttons, chips — need visual punch
 *
 * MapleMonoFontFamily is not applied here — use it directly on AI response
 * Text composables and technical stat displays.
 */
private val FigtreeTypography: Typography by lazy {
    val base = Typography()
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Light
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Light
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Normal
        ),

        headlineLarge = base.headlineLarge.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Medium
        ),

        titleLarge = base.titleLarge.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Medium
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Medium
        ),

        bodyLarge = base.bodyLarge.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Normal
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Normal
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Normal
        ),

        labelLarge = base.labelLarge.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = FigtreeFontFamily,
            fontWeight = FontWeight.Medium
        ),
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface ThemeControllerEntryPoint {
    fun themeController(): ThemeController
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolNeuronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ColorPalette = ColorPalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp

    val dimens = when {
        screenWidth < 600 -> CompactDimens
        screenWidth < 840 -> MediumDimens
        else -> ExpandedDimens
    }

    // Accent is an orthogonal axis on top of the palette (design-spec §0): the
    // neutrals stay fixed, only primary/onPrimary swap among the 4 fixed
    // accents. Fetched via Hilt entry point (not a param) so every existing
    // ToolNeuronTheme call site picks it up without edits.
    val themeController = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeControllerEntryPoint::class.java
        ).themeController()
    }
    val accent by themeController.accent.collectAsStateWithLifecycle()

    val baseScheme = colorSchemeFor(palette, darkTheme, context)
    val colorScheme = if (palette == ColorPalette.FRIDAY) {
        baseScheme.copy(primary = accent.color, onPrimary = accent.onColor)
    } else {
        baseScheme
    }

    CompositionLocalProvider(
        LocalDimens provides dimens,
        LocalTnShapes provides DefaultTnShapes,
        LocalFridayAccent provides accent
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = FigtreeTypography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

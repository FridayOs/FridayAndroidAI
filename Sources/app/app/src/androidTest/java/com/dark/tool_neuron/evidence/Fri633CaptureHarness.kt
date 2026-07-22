package com.dark.tool_neuron.evidence

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.platform.app.InstrumentationRegistry
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.ui.theme.FridayAccent
import com.dark.tool_neuron.ui.theme.LocalFridayAccent
import com.dark.tool_neuron.ui.theme.colorSchemeFor
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/*
 * FRI-633 phase-07 — shared device-capture harness for instrumented onboarding-screen
 * screenshots. Reused by per-screen *DeviceCaptureTest classes to host each real Compose
 * screen (real VMs/controllers, no fakes, no Hilt test app) under a deterministic
 * light/dark + accent + EN/VI configuration WITHOUT depending on the real Hilt-singleton
 * ThemeController: ToolNeuronTheme (Theme.kt) resolves that singleton via
 * EntryPointAccessors.fromApplication, and a freshly-constructed test-local
 * ThemeController(prefs) instance cannot update that already-initialized in-memory
 * StateFlow, so driving accent through it would be flaky/non-deterministic in a test.
 * Fri633ThemedHost instead reconstructs the exact ColorScheme production uses for the
 * FRIDAY palette via the public colorSchemeFor() function plus the same
 * primary/onPrimary accent-swap ToolNeuronTheme performs, so captures are faithful to
 * what real theming renders for a given accent. androidTest-only; no production code is
 * modified by this file.
 */

/** Builds a Context whose resources are pinned to [languageTag] (e.g. "en"/"vi"). */
fun localizedContext(base: Context, languageTag: String): Context {
    val config = Configuration(base.resources.configuration)
    config.setLocale(Locale.forLanguageTag(languageTag))
    return base.createConfigurationContext(config)
}

/**
 * Wraps [content] with the same ColorScheme (FRIDAY palette + accent swap) and
 * [LocalFridayAccent] that production `ToolNeuronTheme` provides for [dark]/[accent], plus
 * a [languageTag]-pinned `LocalContext`/`LocalConfiguration` so `stringResource()` inside
 * the hosted screen resolves the requested locale's strings.
 */
@Composable
fun Fri633ThemedHost(
    dark: Boolean,
    accent: FridayAccent,
    languageTag: String,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val localized = remember(languageTag) { localizedContext(baseContext, languageTag) }
    val colorScheme = remember(dark, accent, localized) {
        colorSchemeFor(ColorPalette.FRIDAY, dark, localized).copy(
            primary = accent.color,
            onPrimary = accent.onColor,
        )
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalFridayAccent provides accent,
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            content()
        }
    }
}

/**
 * Saves a captured node as `<CAPTURE_SUBDIR>/<filename>` under the app's external files
 * dir on-device; the caller `adb pull`s that directory to `evidence/device/` after the
 * instrumented run completes.
 */
object Fri633CaptureHarness {
    const val CAPTURE_SUBDIR = "fri633_captures"

    fun save(node: SemanticsNodeInteraction, filename: String) {
        save(node.captureToImage().asAndroidBitmap(), filename)
    }

    /*
     * FRI-633 phase-10 QA rework round 2 (Blocker 1b) — public overload so a
     * full-screen Bitmap from UiAutomation.takeScreenshot() (real system-bar-inclusive
     * device pixels, not just the Compose view's content) can be saved through the same
     * on-device convention as the Compose-node capture path above.
     */
    fun save(bitmap: Bitmap, filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), CAPTURE_SUBDIR)
        check(dir.exists() || dir.mkdirs()) { "Failed to create capture dir $dir" }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "PNG compress failed for $filename"
            }
        }
    }
}

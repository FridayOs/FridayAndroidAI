# NavScreens — sealed class routes must survive shrinking so Navigation
# can resolve destinations by route string at runtime.
-keep class com.dark.tool_neuron.model.NavScreens { *; }
-keep class com.dark.tool_neuron.model.NavScreens$* { *; }

# Kotlin serialization — keeps @Serializable class structure intact.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Hilt — generated component classes must not be renamed.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# Prebuilt AARs in libs/ — already minified, expose JNI entry points via specific
# class+method names. R8 must not rename or strip these or the dlsym/JNI lookups
# inside libgguf_lib.so / libai_sherpa.so fail at runtime.
-keep class com.dark.gguf_lib.** { *; }
-keep class com.dark.ai_sherpa.** { *; }
-keep class com.dark.ai_sd.** { *; }
-dontwarn com.dark.gguf_lib.**
-dontwarn com.dark.ai_sherpa.**
-dontwarn com.dark.ai_sd.**

# Local native modules — explicit keeps to satisfy R8 whole-program analysis
-keep class com.dark.hxs.** { *; }
-keep class com.dark.hxs_encryptor.** { *; }
-keep class com.dark.download_manager.** { *; }
-keep class com.dark.native_server.** { *; }
-keep class com.dark.networking.** { *; }

-dontwarn com.dark.hxs.**
-dontwarn com.dark.hxs_encryptor.**
-dontwarn com.dark.download_manager.**
-dontwarn com.dark.native_server.**
-dontwarn com.dark.networking.**

# ============================================================================
# Plugin host runtime contract
# ----------------------------------------------------------------------------
# Plugin .dex files are loaded via DexClassLoader with the host as parent.
# The plugin code references the libraries below by their original
# fully-qualified names. R8 must NOT rename, shrink, or repackage any of
# them — the plugin's bytecode was compiled against these exact names and
# can't be re-linked at install time.
#
# Anything imported by a plugin (see plugins/* sources) lives in here.
# Adding a new library to plugin-api means adding it to this list too.
# ============================================================================

# Plugin SPI — already in plugin-api consumer-rules but kept here for clarity.
-keep class com.dark.plugin_api.** { *; }
-keep interface com.dark.plugin_api.** { *; }

# Kotlin stdlib — Metadata, intrinsics, top-level Kt files (LazyKt, TuplesKt,
# CollectionsKt, StringsKt, ...), coroutine continuations, math, random, reflect.
# Plugin DEX bytecode is full of `kotlin.*` references that the compiler inserts
# implicitly (delegation, destructuring, ranges, etc.) so this stays broad.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin coroutines — SupervisorKt etc. are top-level Kt files referenced
# by plugins for SupervisorJob(), Dispatchers, launch, withContext.
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlinx serialization — Json, Serializable, generated $$serializer pairs.
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# AndroidX core surface that Compose / lifecycle pull in.
-keep class androidx.core.** { *; }
-keep interface androidx.core.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep interface androidx.activity.** { *; }
-keep class androidx.annotation.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.activity.**

# Jetpack Compose — runtime, ui, foundation, material3, animation. Plugins
# author @Composable functions that emit nodes via the host's Composer.
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep interface androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep interface androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep interface androidx.compose.material3.** { *; }
-keep class androidx.compose.material.ripple.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep interface androidx.compose.animation.** { *; }
-dontwarn androidx.compose.**

# ONNX Runtime — plugins request sessions through OnnxApi, which delegates
# to ai.onnxruntime.*. Reflection-driven JNI bindings require keep-everything.
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Apache Commons Compress (used for sherpa-onnx .tar.bz2 voice archives) carries
# soft references to optional integrations (osgi, zstd, brotli, xz) that R8 flags
# as missing. We only use BZip2CompressorInputStream + TarArchiveInputStream.
-dontwarn org.apache.commons.compress.**

# Release-mode log stripping — R8 treats these calls as having no side effects,
# deletes them, and drops the string arguments that would otherwise leak paths,
# keys, or diagnostics into logcat.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Obfuscate everything we own except the JNI surface and plugin contract above.
# -repackageclasses only moves classes that R8 *renamed* — `-keep`d classes
# (including the plugin runtime contract) stay where they are.
-repackageclasses ''
-allowaccessmodification

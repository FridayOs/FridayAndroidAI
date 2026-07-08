import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.dagger.hilt)
}

// google-services processes app/google-services.json into the Firebase config
// resources. The file is local-only + gitignored, so the plugin is applied only
// when it exists; without it the app still compiles and runs on the friday_api
// backend. BuildConfig.FIREBASE_CONFIGURED mirrors this so runtime code can gate
// every Firebase call and surface a clear setup error instead of crashing.
val googleServicesJson = file("google-services.json")
val firebaseConfigured = googleServicesJson.exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tnKeystorePath: String? = localProps.getProperty("TN_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val tnKeystorePassword: String? = localProps.getProperty("TN_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val tnKeyAlias: String? = localProps.getProperty("TN_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val tnKeyPassword: String? = localProps.getProperty("TN_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasReleaseSigning = tnKeystorePath != null &&
    tnKeystorePassword != null &&
    tnKeyAlias != null &&
    tnKeyPassword != null

android {
    namespace = "com.friday.ai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.friday.ai"
        minSdk = 31
        targetSdk = 37
        versionCode = (rootProject.findProperty("tn.versionCode") as String).toInt()
        versionName = rootProject.findProperty("tn.versionName") as String
        ndk {
            // arm64-v8a — all modern Android phones and NPU-capable devices
            // x86_64    — emulator support during development
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(tnKeystorePath!!)
                storePassword = tnKeystorePassword
                keyAlias = tnKeyAlias
                keyPassword = tnKeyPassword
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable = false
            isJniDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/x86_64/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/arm64-v8a/libonnxruntime.so",
                "lib/x86_64/libonnxruntime.so",
                "lib/armeabi-v7a/libonnxruntime.so",
                "lib/arm64-v8a/libonnxruntime4j_jni.so",
                "lib/x86_64/libonnxruntime4j_jni.so",
                "lib/armeabi-v7a/libonnxruntime4j_jni.so",
                "lib/arm64-v8a/libtn_security.so",
                "lib/x86_64/libtn_security.so",
                "lib/armeabi-v7a/libtn_security.so",
            )
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    // Local modules
    implementation(project(":hxs_encryptor"))
    implementation(project(":hxs"))
    implementation(project(":download_manager"))
    implementation(project(":networking"))
    implementation(project(":native-server"))
    implementation(project(":plugin-api"))
    implementation(project(":plugin-exc"))

    // AI inference AARs
    implementation(files("../libs/tn_security-release.aar"))
    implementation(files("../libs/gguf_lib-release.aar"))
    implementation(files("../libs/ai_sherpa-release.aar"))
    implementation(files("../libs/ai_sd-release.aar"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Firebase — cloud metadata backend (auth/profile/device/push/policy only).
    // Always on the classpath so FirebaseAccountGateway compiles; runtime calls
    // are gated on BuildConfig.FIREBASE_CONFIGURED. FirebaseApp auto-init is a
    // no-op without google-services.json, so no crash on a config-less build.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.config)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.compose)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.capsule)

    // Compose BOM — pins all compose library versions together
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.material3)


    // Unit tests
    testImplementation(libs.junit)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug only — removed from release by build type
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

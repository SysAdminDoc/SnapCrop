import java.util.Properties
import org.cyclonedx.model.Component
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.cyclonedx.bom)
    alias(libs.plugins.ksp)
}

// Load signing credentials from gitignored keystore.properties (local builds)
// or environment variables (CI). Never inline secrets in this file.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStorePath = signingValue("storeFile", "SNAPCROP_KEYSTORE_PATH") ?: "snapcrop-release.jks"
val releaseStoreFile = rootProject.file(releaseStorePath)
val hasReleaseKeystore = releaseStoreFile.exists()
        && signingValue("storePassword", "SNAPCROP_KEYSTORE_PASSWORD") != null
        && signingValue("keyPassword", "SNAPCROP_KEY_PASSWORD") != null

android {
    namespace = "com.sysadmindoc.snapcrop"
    compileSdk = 36

    androidResources {
        // Auto-generate locales_config.xml from values-* folders and wire android:localeConfig,
        // so SnapCrop appears under system per-app language settings as soon as a translation
        // (e.g. values-es/) is added. Harmless with a single locale today.
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.sysadmindoc.snapcrop"
        minSdk = 29
        targetSdk = 36
        versionCode = 76
        versionName = "6.28.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "SNAPCROP_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SNAPCROP_KEY_ALIAS") ?: "snapcrop"
                keyPassword = signingValue("keyPassword", "SNAPCROP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sign with the release keystore when available; otherwise fall back to the
            // debug signing config so contributor builds still produce an installable APK.
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // The Compose BOM 2026.05 lint elevated these opinionated checks to error severity. They
        // flag context.getString()/resources reads inside event handlers (toasts, one-shot status),
        // which are runtime-correct. Keep them visible as warnings instead of breaking the build.
        warning += "LocalContextGetResourceValueCall"
        warning += "LocalContextResourcesRead"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.mlkit.objectdetection)
    implementation(libs.mlkit.textrecognition)
    implementation(libs.mlkit.textrecognition.chinese)
    implementation(libs.mlkit.textrecognition.japanese)
    implementation(libs.mlkit.textrecognition.korean)
    implementation(libs.mlkit.textrecognition.devanagari)
    implementation(libs.mlkit.facedetection)
    implementation(libs.mlkit.barcodescanning)
    implementation(libs.mlkit.subjectsegmentation)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.entity.extraction)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.json)
    debugImplementation(libs.androidx.ui.tooling)
}

tasks.cyclonedxDirectBom {
    projectType.set(Component.Type.APPLICATION)
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
    skipConfigs.set(listOf(".*[Tt]est.*", "debug.*", "androidTest.*"))
}

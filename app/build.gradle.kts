plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sysadmindoc.snapcrop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sysadmindoc.snapcrop"
        minSdk = 29
        targetSdk = 35
        versionCode = 49
        versionName = "6.5.6"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../snapcrop-release.jks")
            storePassword = "snapcrop123"
            keyAlias = "snapcrop"
            keyPassword = "snapcrop123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.mlkit.facedetection)
    implementation(libs.mlkit.barcodescanning)
    implementation(libs.mlkit.subjectsegmentation)
    debugImplementation(libs.androidx.ui.tooling)
}

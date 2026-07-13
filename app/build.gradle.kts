import java.util.Properties
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.cyclonedx.model.Component
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.cyclonedx.bom)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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
        versionCode = 121
        versionName = "6.69.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        debug {
            // Keep local instrumentation isolated from an installed production build.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    lint {
        // The Compose BOM 2026.05 lint elevated these opinionated checks to error severity. They
        // flag context.getString()/resources reads inside event handlers (toasts, one-shot status),
        // which are runtime-correct. Keep them visible as warnings instead of breaking the build.
        warning += "LocalContextGetResourceValueCall"
        warning += "LocalContextResourcesRead"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    // Keep the processor argument explicit: AGP 9's built-in Kotlin path does not yet
    // receive the Room plugin's schema location for every KSP task.
    arg("room.schemaLocation", "$projectDir/schemas")
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
    implementation(libs.re2j)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(platform(libs.kotlinx.serialization.bom))
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.json)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.ui.tooling)
}

tasks.cyclonedxDirectBom {
    projectType.set(Component.Type.APPLICATION)
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
    skipConfigs.set(listOf(".*[Tt]est.*", "debug.*", "androidTest.*"))
}

tasks.register("generateReleaseProvenance") {
    group = "distribution"
    description = "Builds stable release artifacts and a machine-readable provenance manifest."
    dependsOn("assembleRelease", "cyclonedxDirectBom", rootProject.tasks.named("verifyWrapperJar"))

    val provenanceDirectory = layout.buildDirectory.dir("outputs/provenance")
    val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val releaseSbom = layout.buildDirectory.file("reports/cyclonedx-direct/bom.json")
    inputs.file(releaseApk)
    inputs.file(releaseSbom)
    outputs.dir(provenanceDirectory)

    doLast {
        val versionName = android.defaultConfig.versionName
            ?: error("versionName is required for release provenance")
        val versionCode = android.defaultConfig.versionCode
        val sourceApk = releaseApk.get().asFile
        val sourceSbom = releaseSbom.get().asFile
        require(sourceApk.isFile) { "Release APK was not produced: $sourceApk" }
        require(sourceSbom.isFile) { "CycloneDX JSON SBOM was not produced: $sourceSbom" }

        val outputDir = provenanceDirectory.get().asFile.apply { mkdirs() }
        val stableApk = outputDir.resolve("SnapCrop-$versionName.apk")
        val stableSbom = outputDir.resolve("SnapCrop-$versionName-sbom.json")
        sourceApk.copyTo(stableApk, overwrite = true)
        sourceSbom.copyTo(stableSbom, overwrite = true)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use { load(it) }
        }
        val sdkDirectory = sequenceOf(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            localProperties.getProperty("sdk.dir")
        ).filterNotNull().firstOrNull()?.let(::File)
            ?: error("Android SDK path was not found in the environment or local.properties")
        val buildToolsDir = sdkDirectory.resolve("build-tools").listFiles()
            ?.filter(File::isDirectory)
            ?.maxByOrNull { dir ->
                dir.name.split('.').joinToString("") { it.padStart(4, '0') }
            }
            ?: error("Android build-tools are required to verify the signing certificate")
        val apksigner = buildToolsDir.resolve(
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                "apksigner.bat"
            } else {
                "apksigner"
            }
        )
        require(apksigner.isFile) { "apksigner was not found: $apksigner" }
        val signerOutput = providers.exec {
            commandLine(apksigner.absolutePath, "verify", "--print-certs", stableApk.absolutePath)
        }.standardOutput.asText.get()
        val certificateDigest = Regex(
            "certificate SHA-256 digest: ([0-9a-fA-F]+)"
        ).find(signerOutput)?.groupValues?.get(1)
            ?: error("apksigner did not report a SHA-256 certificate digest")
        val certificateFingerprint = certificateDigest.chunked(2)
            .joinToString(":").uppercase(Locale.ROOT)

        val sourceCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        val sourceState = providers.exec {
            isIgnoreExitValue = true
            commandLine("git", "status", "--porcelain")
        }.standardOutput.asText.get().trim().let { if (it.isEmpty()) "clean" else "dirty" }
        val command = ".\\gradlew.bat :app:generateReleaseProvenance --console=plain"
        val provenance = outputDir.resolve("SnapCrop-$versionName-provenance.json")
        provenance.writeText(
            """
            {
              "schemaVersion": 1,
              "project": "SnapCrop",
              "versionName": "$versionName",
              "versionCode": $versionCode,
              "apk": "${stableApk.name}",
              "apkSha256": "${sha256(stableApk)}",
              "signingCertificateSha256": "$certificateFingerprint",
              "sbom": "${stableSbom.name}",
              "sourceCommit": "$sourceCommit",
              "sourceState": "$sourceState",
              "buildCommand": "${command.replace("\\", "\\\\")}",
              "generatedAtUtc": "${Instant.now()}"
            }
            """.trimIndent() + "\n"
        )
        logger.lifecycle("Release provenance: ${provenance.absolutePath}")
    }
}

tasks.register("verifyOfficialRelease") {
    group = "distribution"
    description = "Fails closed unless the official APK is production-signed, clean, synchronized, and 16 KB aligned."
    dependsOn("generateReleaseProvenance", "verifyRedactionQuality")

    doLast {
        check(hasReleaseKeystore) {
            "Official release requires keystore.properties or SNAPCROP_KEYSTORE_* production credentials"
        }
        val versionName = android.defaultConfig.versionName ?: error("versionName is required")
        val outputDir = layout.buildDirectory.dir("outputs/provenance").get().asFile
        val apk = outputDir.resolve("SnapCrop-$versionName.apk")
        val sbom = outputDir.resolve("SnapCrop-$versionName-sbom.json")
        val provenance = outputDir.resolve("SnapCrop-$versionName-provenance.json")
        check(apk.isFile && sbom.isFile && provenance.isFile) { "Versioned release artifacts are missing" }
        val json = provenance.readText()
        fun jsonValue(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(json)?.groupValues?.get(1) ?: error("Missing provenance field: $name")
        val expectedCertificate = providers.gradleProperty("snapcrop.releaseCertificateSha256")
            .orNull?.replace(":", "")?.uppercase(Locale.ROOT)
            ?: error("snapcrop.releaseCertificateSha256 must be pinned in gradle.properties")
        val actualCertificate = jsonValue("signingCertificateSha256").replace(":", "").uppercase(Locale.ROOT)
        check(actualCertificate == expectedCertificate) {
            "Signing certificate mismatch: expected $expectedCertificate, got $actualCertificate"
        }
        check(jsonValue("versionName") == versionName) { "Provenance versionName is not synchronized" }
        check(rootProject.version.toString() == versionName) { "Root and app versions are not synchronized" }
        val sbomVersion = Regex("\\\"name\\\"\\s*:\\s*\\\"app\\\"\\s*,\\s*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(sbom.readText())?.groupValues?.get(1)
            ?: error("SBOM application version is missing")
        check(sbomVersion == versionName) { "SBOM and app versions are not synchronized" }
        val allowDirty = providers.gradleProperty("allowDirtyOfficialVerification").orNull == "true"
        check(allowDirty || jsonValue("sourceState") == "clean") {
            "Official releases require a clean Git worktree"
        }

        ZipFile(apk).use { zip ->
            val nativeEntries = zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.toList()
            check(nativeEntries.isNotEmpty()) { "No native libraries found for 16 KB compatibility verification" }
            nativeEntries.forEach { entry ->
                check(entry.method == ZipEntry.STORED) { "Native library is compressed: ${entry.name}" }
                val magic = zip.getInputStream(entry).use { it.readNBytes(4) }
                check(magic.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) {
                    "Native entry is not ELF: ${entry.name}"
                }
            }
        }

        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use { load(it) }
        }
        val sdkDirectory = sequenceOf(
            System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), localProperties.getProperty("sdk.dir")
        ).filterNotNull().firstOrNull()?.let(::File) ?: error("Android SDK path not found")
        val buildToolsDir = sdkDirectory.resolve("build-tools").listFiles()
            ?.filter(File::isDirectory)
            ?.maxByOrNull { dir -> dir.name.split('.').joinToString("") { it.padStart(4, '0') } }
            ?: error("Android build-tools not found")
        val zipalign = buildToolsDir.resolve(if (System.getProperty("os.name").startsWith("Windows", true)) "zipalign.exe" else "zipalign")
        check(zipalign.isFile) { "zipalign not found: $zipalign" }
        providers.exec {
            commandLine(zipalign.absolutePath, "-c", "-P", "16", "-v", "4", apk.absolutePath)
        }.result.get().assertNormalExitValue()
        logger.lifecycle("Official release gate passed: ${apk.absolutePath}")
    }
}

tasks.register("verifyRedactionQuality") {
    group = "verification"
    description = "Runs the fixed synthetic redaction corpus and verifies its release thresholds."
    dependsOn("testDebugUnitTest")

    doLast {
        val report = layout.buildDirectory
            .file("test-results/testDebugUnitTest/TEST-com.sysadmindoc.snapcrop.RedactionQualityGateTest.xml")
            .get().asFile
        check(report.isFile) { "Redaction quality gate test report is missing" }
        val xml = report.readText()
        val tests = Regex("tests=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val failures = Regex("failures=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val errors = Regex("errors=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        check(tests >= 3 && failures == 0 && errors == 0) {
            "Redaction quality gate did not pass: tests=$tests failures=$failures errors=$errors"
        }
        logger.lifecycle("Redaction quality gate passed: ${report.absolutePath}")
    }
}

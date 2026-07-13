import java.util.Properties
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
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
val releaseAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
val releaseAbiSplitsEnabled = providers.gradleProperty("snapcrop.releaseAbiSplits")
    .map { it.toBooleanStrict() }
    .getOrElse(
        gradle.startParameter.taskNames.any { taskName ->
            taskName.contains("assembleRelease", ignoreCase = true) ||
                    taskName.contains("generateReleaseProvenance", ignoreCase = true) ||
                    taskName.contains("ReleaseSize", ignoreCase = true) ||
                    taskName.contains("verifyOfficialRelease", ignoreCase = true)
        }
    )

android {
    namespace = "com.sysadmindoc.snapcrop"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    androidResources {
        // Auto-generate locales_config.xml from values-* folders and wire android:localeConfig,
        // so SnapCrop appears under system per-app language settings as soon as a translation
        // (e.g. values-es/) is added. Harmless with a single locale today.
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.sysadmindoc.snapcrop"
        minSdk = 29
        targetSdk = 37
        versionCode = 141
        versionName = "6.89.0"
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

    splits {
        abi {
            isEnable = releaseAbiSplitsEnabled
            reset()
            include(*releaseAbis.toTypedArray())
            isUniversalApk = true
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
    implementation(libs.play.services.base)
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
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.cyclonedxDirectBom {
    projectType.set(Component.Type.APPLICATION)
    includeConfigs.set(listOf("releaseRuntimeClasspath"))
    skipConfigs.set(listOf(".*[Tt]est.*", "debug.*", "androidTest.*"))
}

val releaseSizeReportFile = layout.buildDirectory.file("reports/release-size.json")
val releaseSizeBaselineFile = rootProject.layout.projectDirectory.file("gradle/release-size-baseline.json")
val releaseSizeArtifactCompressedGrowthLimit = 256L * 1024L
val releaseSizeArtifactUncompressedGrowthLimit = 512L * 1024L

tasks.register("generateReleaseSizeReport") {
    group = "verification"
    description = "Measures compressed and expanded release APK, dependency, and native-library sizes."
    dependsOn("assembleRelease")
    outputs.file(releaseSizeReportFile)
    outputs.upToDateWhen { false }

    doLast {
        val versionName = android.defaultConfig.versionName ?: error("versionName is required")
        val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apks = linkedMapOf(
            "universal" to releaseApkDirectory.resolve("app-universal-release.apk"),
        ).apply {
            releaseAbis.forEach { abi -> put(abi, releaseApkDirectory.resolve("app-$abi-release.apk")) }
        }
        apks.forEach { (abi, apk) -> require(apk.isFile) { "Release APK is missing for $abi: $apk" } }

        fun expandedBytes(file: File): Long = ZipFile(file).use { zip ->
            zip.entries().asSequence().sumOf { entry -> entry.size.coerceAtLeast(0L) }
        }
        fun apkCategories(file: File): Map<String, Map<String, Long>> {
            val totals = linkedMapOf<String, LongArray>()
            ZipFile(file).use { zip ->
                zip.entries().asSequence().filterNot(ZipEntry::isDirectory).forEach { entry ->
                    val category = when {
                        entry.name.endsWith(".dex") -> "dex"
                        entry.name.startsWith("lib/") -> "native"
                        entry.name.startsWith("assets/") -> "assets"
                        entry.name.startsWith("res/") || entry.name == "resources.arsc" -> "resources"
                        entry.name.startsWith("META-INF/") -> "metadata"
                        else -> "other"
                    }
                    val values = totals.getOrPut(category) { longArrayOf(0, 0) }
                    values[0] += entry.compressedSize.coerceAtLeast(0L)
                    values[1] += entry.size.coerceAtLeast(0L)
                }
            }
            return totals.mapValues { (_, values) ->
                linkedMapOf("compressedBytes" to values[0], "uncompressedBytes" to values[1])
            }
        }
        fun nativeRows(file: File): List<Map<String, Any>> = ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .map { entry ->
                    linkedMapOf<String, Any>(
                        "abi" to entry.name.substringAfter("lib/").substringBefore('/'),
                        "name" to entry.name.substringAfterLast('/'),
                        "compressedBytes" to entry.compressedSize.coerceAtLeast(0L),
                        "uncompressedBytes" to entry.size.coerceAtLeast(0L),
                    )
                }
                .sortedWith(compareBy<Map<String, Any>> { it.getValue("abi").toString() }
                    .thenBy { it.getValue("name").toString() })
                .toList()
        }

        val universalNative = nativeRows(apks.getValue("universal"))
        releaseAbis.forEach { abi ->
            val expected = universalNative.filter { it.getValue("abi") == abi }
            val actual = nativeRows(apks.getValue(abi))
            check(actual == expected) { "Native-library payload for $abi does not match the universal APK" }
        }
        val artifactRows = apks.mapValues { (_, apk) ->
            linkedMapOf<String, Any>(
                "compressedBytes" to apk.length(),
                "uncompressedBytes" to expandedBytes(apk),
                "categories" to apkCategories(apk),
            )
        }
        val dependencyRows = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .map { artifact ->
                val id = artifact.moduleVersion.id
                linkedMapOf<String, Any>(
                    "coordinate" to "${id.group}:${id.name}:${id.version}",
                    "artifact" to artifact.file.name,
                    "compressedBytes" to artifact.file.length(),
                    "uncompressedBytes" to expandedBytes(artifact.file),
                )
            }
            .distinctBy { row -> "${row.getValue("coordinate")}|${row.getValue("artifact")}" }
            .sortedWith(compareBy<Map<String, Any>> { it.getValue("coordinate").toString() }
                .thenBy { it.getValue("artifact").toString() })

        val report = linkedMapOf<String, Any>(
            "schemaVersion" to 2,
            "measuredVersion" to versionName,
            "generatedAtUtc" to Instant.now().toString(),
            "artifacts" to artifactRows,
            "dependencies" to dependencyRows,
            "nativeLibraries" to universalNative,
        )
        releaseSizeReportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        }
        logger.lifecycle("Release-size report: ${releaseSizeReportFile.get().asFile.absolutePath}")
    }
}

tasks.register("updateReleaseSizeBaseline") {
    group = "verification"
    description = "Intentionally replaces the release-size baseline; requires -Psnapcrop.sizeBaselineReason."
    dependsOn("generateReleaseSizeReport")

    doLast {
        val reason = providers.gradleProperty("snapcrop.sizeBaselineReason").orNull?.trim().orEmpty()
        require(reason.isNotEmpty()) {
            "Updating the release-size baseline requires -Psnapcrop.sizeBaselineReason=<reason>"
        }
        val measured = JsonSlurper().parse(releaseSizeReportFile.get().asFile) as? Map<*, *>
            ?: error("Release-size report root must be an object")
        val baseline = linkedMapOf<String, Any>(
            "schemaVersion" to 2,
            "baselineVersion" to (android.defaultConfig.versionName ?: error("versionName is required")),
            "rationale" to reason,
            "limits" to linkedMapOf(
                "artifactCompressedGrowthBytes" to releaseSizeArtifactCompressedGrowthLimit,
                "artifactUncompressedGrowthBytes" to releaseSizeArtifactUncompressedGrowthLimit,
                "dependencyCompressedGrowthBytes" to 0,
                "dependencyUncompressedGrowthBytes" to 0,
                "nativeCompressedGrowthBytes" to 0,
                "nativeUncompressedGrowthBytes" to 0,
            ),
            "artifacts" to requireNotNull(measured["artifacts"]),
            "dependencies" to requireNotNull(measured["dependencies"]),
            "nativeLibraries" to requireNotNull(measured["nativeLibraries"]),
        )
        releaseSizeBaselineFile.asFile.writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(baseline)) + "\n"
        )
        logger.lifecycle("Release-size baseline updated: ${releaseSizeBaselineFile.asFile.absolutePath}")
    }
}

tasks.register("verifyReleaseSizeBudget") {
    group = "verification"
    description = "Fails on unexplained release APK, dependency, or native-library size regressions."
    dependsOn("generateReleaseSizeReport")
    inputs.file(releaseSizeBaselineFile)

    doLast {
        val measured = JsonSlurper().parse(releaseSizeReportFile.get().asFile) as? Map<*, *>
            ?: error("Release-size report root must be an object")
        val baseline = JsonSlurper().parse(releaseSizeBaselineFile.asFile) as? Map<*, *>
            ?: error("Release-size baseline root must be an object")
        fun objectField(source: Map<*, *>, name: String): Map<*, *> = source[name] as? Map<*, *>
            ?: error("Release-size $name must be an object")
        fun listField(source: Map<*, *>, name: String): List<Map<*, *>> = (source[name] as? List<*>)
            ?.map { it as? Map<*, *> ?: error("Release-size $name entries must be objects") }
            ?: error("Release-size $name must be an array")
        fun longField(source: Map<*, *>, name: String): Long = source[name]?.toString()?.toLongOrNull()
            ?: error("Release-size $name must be an integer")
        check(longField(baseline, "schemaVersion") == 2L) { "Release-size baseline schema 2 is required" }
        check(baseline["rationale"]?.toString()?.isNotBlank() == true) {
            "Release-size baseline rationale is required"
        }
        val limits = objectField(baseline, "limits")
        val measuredArtifacts = objectField(measured, "artifacts")
        val baselineArtifacts = objectField(baseline, "artifacts")
        val expectedAbis = linkedSetOf("universal") + releaseAbis
        check(measuredArtifacts.keys == expectedAbis && baselineArtifacts.keys == expectedAbis) {
            "Release-size artifact set mismatch: measured=${measuredArtifacts.keys} baseline=${baselineArtifacts.keys}"
        }
        measuredArtifacts.forEach { (abi, measuredValue) ->
            val current = measuredValue as? Map<*, *> ?: error("Measured artifact $abi must be an object")
            val previous = baselineArtifacts[abi] as? Map<*, *> ?: error("Baseline artifact $abi must be an object")
            val compressedDelta = longField(current, "compressedBytes") - longField(previous, "compressedBytes")
            val uncompressedDelta = longField(current, "uncompressedBytes") - longField(previous, "uncompressedBytes")
            check(compressedDelta <= longField(limits, "artifactCompressedGrowthBytes")) {
                "$abi APK compressed size grew by $compressedDelta bytes"
            }
            check(uncompressedDelta <= longField(limits, "artifactUncompressedGrowthBytes")) {
                "$abi APK expanded size grew by $uncompressedDelta bytes"
            }
        }
        fun verifyExactRows(name: String, keys: List<String>, compressedLimit: String, uncompressedLimit: String) {
            fun indexed(source: Map<*, *>): Map<String, Map<*, *>> {
                val rows = listField(source, name)
                val indexed = rows.associateBy { row ->
                    keys.joinToString("|") { key -> row[key]?.toString() ?: error("$name entry is missing $key") }
                }
                check(indexed.size == rows.size) { "$name contains duplicate identity rows" }
                return indexed
            }
            val current = indexed(measured)
            val previous = indexed(baseline)
            check(current.keys == previous.keys) {
                "$name set changed: added=${current.keys - previous.keys}, removed=${previous.keys - current.keys}"
            }
            current.forEach { (key, row) ->
                val old = previous.getValue(key)
                val compressedDelta = longField(row, "compressedBytes") - longField(old, "compressedBytes")
                val uncompressedDelta = longField(row, "uncompressedBytes") - longField(old, "uncompressedBytes")
                check(compressedDelta <= longField(limits, compressedLimit)) {
                    "$name $key compressed size grew by $compressedDelta bytes"
                }
                check(uncompressedDelta <= longField(limits, uncompressedLimit)) {
                    "$name $key expanded size grew by $uncompressedDelta bytes"
                }
            }
        }
        verifyExactRows(
            "dependencies", listOf("coordinate", "artifact"),
            "dependencyCompressedGrowthBytes", "dependencyUncompressedGrowthBytes",
        )
        verifyExactRows(
            "nativeLibraries", listOf("abi", "name"),
            "nativeCompressedGrowthBytes", "nativeUncompressedGrowthBytes",
        )
        logger.lifecycle("Release-size budget passed against ${baseline["baselineVersion"]}")
    }
}

tasks.register("generateReleaseProvenance") {
    group = "distribution"
    description = "Builds stable release artifacts and a machine-readable provenance manifest."
    dependsOn(
        "verifyReleaseSizeBudget",
        "cyclonedxDirectBom",
        rootProject.tasks.named("verifyWrapperJar"),
        rootProject.tasks.named("verifyBuildCacheSecurity"),
    )

    val provenanceDirectory = layout.buildDirectory.dir("outputs/provenance")
    val releaseApkDirectory = layout.buildDirectory.dir("outputs/apk/release")
    val releaseSbom = layout.buildDirectory.file("reports/cyclonedx-direct/bom.json")
    val releaseSizeBaseline = rootProject.layout.projectDirectory.file("gradle/release-size-baseline.json")
    inputs.dir(releaseApkDirectory)
    inputs.file(releaseSbom)
    inputs.file(releaseSizeBaseline)
    outputs.dir(provenanceDirectory)

    doLast {
        val versionName = android.defaultConfig.versionName
            ?: error("versionName is required for release provenance")
        val versionCode = android.defaultConfig.versionCode
        val sourceApks = linkedMapOf(
            "universal" to releaseApkDirectory.get().asFile.resolve("app-universal-release.apk"),
        ).apply {
            releaseAbis.forEach { abi ->
                put(abi, releaseApkDirectory.get().asFile.resolve("app-$abi-release.apk"))
            }
        }
        val sourceSbom = releaseSbom.get().asFile
        val baselineData = JsonSlurper().parse(releaseSizeBaseline.asFile) as Map<*, *>
        val baselineVersion = baselineData["baselineVersion"]?.toString()
            ?: error("Release-size baseline version is missing")
        val baselineSizes = baselineData["artifacts"] as? Map<*, *>
            ?: error("Release-size baseline artifacts are missing")
        val measuredSizeData = JsonSlurper().parse(releaseSizeReportFile.get().asFile) as? Map<*, *>
            ?: error("Release-size report root must be an object")
        val measuredSizes = measuredSizeData["artifacts"] as? Map<*, *>
            ?: error("Release-size report artifacts are missing")
        fun sizeRow(source: Map<*, *>, abi: String): Map<*, *> = source[abi] as? Map<*, *>
            ?: error("Release-size data is missing $abi")
        fun sizeValue(source: Map<*, *>, abi: String, field: String): Long =
            sizeRow(source, abi)[field]?.toString()?.toLongOrNull()
                ?: error("Release-size $field is missing for $abi")
        sourceApks.forEach { (abi, sourceApk) ->
            require(sourceApk.isFile) { "Release APK was not produced for $abi: $sourceApk" }
        }
        require(sourceSbom.isFile) { "CycloneDX JSON SBOM was not produced: $sourceSbom" }

        val outputDir = provenanceDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val stableApks = sourceApks.mapValuesTo(linkedMapOf()) { (abi, sourceApk) ->
            val suffix = if (abi == "universal") "" else "-$abi"
            outputDir.resolve("SnapCrop-$versionName$suffix.apk").also { stableApk ->
                sourceApk.copyTo(stableApk, overwrite = true)
            }
        }
        val stableApk = stableApks.getValue("universal")
        val stableSbom = outputDir.resolve("SnapCrop-$versionName-sbom.json")
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
        fun signingCertificate(file: File): String {
            val signerOutput = providers.exec {
                commandLine(apksigner.absolutePath, "verify", "--print-certs", file.absolutePath)
            }.standardOutput.asText.get()
            val certificateDigest = Regex(
                "certificate SHA-256 digest: ([0-9a-fA-F]+)"
            ).find(signerOutput)?.groupValues?.get(1)
                ?: error("apksigner did not report a SHA-256 certificate digest for ${file.name}")
            return certificateDigest.chunked(2).joinToString(":").uppercase(Locale.ROOT)
        }
        val artifactCertificates = stableApks.mapValues { (_, file) -> signingCertificate(file) }
        val certificateFingerprint = artifactCertificates.getValue("universal")
        require(artifactCertificates.values.toSet() == setOf(certificateFingerprint)) {
            "Release APKs were signed by different certificates"
        }

        val sourceCommit = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()
        val sourceState = providers.exec {
            isIgnoreExitValue = true
            commandLine("git", "status", "--porcelain")
        }.standardOutput.asText.get().trim().let { if (it.isEmpty()) "clean" else "dirty" }
        val command = ".\\gradlew.bat --no-build-cache --no-configuration-cache --system-prop=kotlin.caching.enabled=false --project-prop=kotlin.incremental=false :app:generateReleaseProvenance --console=plain"
        val artifactsJson = stableApks.entries.joinToString(",\n") { (abi, file) ->
            """
                {
                  "abi": "$abi",
                  "kind": "${if (abi == "universal") "universal" else "abi"}",
                  "apk": "${file.name}",
                  "apkSha256": "${sha256(file)}",
                  "sizeBytes": ${file.length()},
                  "sizeDeltaBytes": ${file.length() - sizeValue(baselineSizes, abi, "compressedBytes")},
                  "uncompressedSizeBytes": ${sizeValue(measuredSizes, abi, "uncompressedBytes")},
                  "uncompressedSizeDeltaBytes": ${sizeValue(measuredSizes, abi, "uncompressedBytes") - sizeValue(baselineSizes, abi, "uncompressedBytes")},
                  "versionName": "$versionName",
                  "versionCode": $versionCode,
                  "signingCertificateSha256": "${artifactCertificates.getValue(abi)}",
                  "sbom": "${stableSbom.name}",
                  "sbomSha256": "${sha256(stableSbom)}"
                }
            """.trimIndent()
        }.prependIndent("    ")
        val provenance = outputDir.resolve("SnapCrop-$versionName-provenance.json")
        provenance.writeText(
            """
            {
              "schemaVersion": 4,
              "project": "SnapCrop",
              "versionName": "$versionName",
              "versionCode": $versionCode,
              "apk": "${stableApk.name}",
              "apkSha256": "${sha256(stableApk)}",
              "signingCertificateSha256": "$certificateFingerprint",
              "sbom": "${stableSbom.name}",
              "sbomSha256": "${sha256(stableSbom)}",
              "sourceCommit": "$sourceCommit",
              "sourceState": "$sourceState",
              "buildCommand": "${command.replace("\\", "\\\\")}",
              "generatedAtUtc": "${Instant.now()}",
              "sizeBaselineVersion": "$baselineVersion",
              "mlDelivery": {
                "bundledOcrScripts": ["latin"],
                "playServicesOcrScripts": ["chinese", "japanese", "korean", "devanagari"],
                "optionalOcrApproxApkBytesPerScriptArchitecture": 260000,
                "optionalOcrApproxInstalledBytesPerScript": 4000000,
                "translationApproxInstalledBytesPerLanguage": 30000000
              },
              "artifacts": [
$artifactsJson
              ]
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
        val versionCode = android.defaultConfig.versionCode
        val outputDir = layout.buildDirectory.dir("outputs/provenance").get().asFile
        val stableApks = linkedMapOf(
            "universal" to outputDir.resolve("SnapCrop-$versionName.apk"),
        ).apply {
            releaseAbis.forEach { abi -> put(abi, outputDir.resolve("SnapCrop-$versionName-$abi.apk")) }
        }
        val sbom = outputDir.resolve("SnapCrop-$versionName-sbom.json")
        val provenance = outputDir.resolve("SnapCrop-$versionName-provenance.json")
        val expectedFiles = stableApks.values.mapTo(mutableSetOf()) { it.name }.apply {
            add(sbom.name)
            add(provenance.name)
        }
        val actualFiles = outputDir.listFiles()?.filter(File::isFile)?.mapTo(mutableSetOf()) { it.name }.orEmpty()
        check(actualFiles == expectedFiles) {
            "Official release asset set mismatch: expected $expectedFiles, got $actualFiles"
        }
        check(stableApks.values.all(File::isFile) && sbom.isFile && provenance.isFile) {
            "Versioned release artifacts are missing"
        }

        val provenanceData = JsonSlurper().parse(provenance) as? Map<*, *>
            ?: error("Release provenance root must be an object")
        val sizeBaseline = JsonSlurper().parse(rootProject.file("gradle/release-size-baseline.json")) as? Map<*, *>
            ?: error("Release-size baseline root must be an object")
        val sizeReport = JsonSlurper().parse(releaseSizeReportFile.get().asFile) as? Map<*, *>
            ?: error("Release-size report root must be an object")
        fun field(map: Map<*, *>, name: String): Any = map[name]
            ?: error("Missing provenance field: $name")
        fun normalizeCertificate(value: Any): String = value.toString().replace(":", "").uppercase(Locale.ROOT)
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
        val expectedCertificate = providers.gradleProperty("snapcrop.releaseCertificateSha256")
            .orNull?.replace(":", "")?.uppercase(Locale.ROOT)
            ?: error("snapcrop.releaseCertificateSha256 must be pinned in gradle.properties")
        val actualCertificate = normalizeCertificate(field(provenanceData, "signingCertificateSha256"))
        check(actualCertificate == expectedCertificate) {
            "Signing certificate mismatch: expected $expectedCertificate, got $actualCertificate"
        }
        check(field(provenanceData, "schemaVersion").toString().toInt() == 4) {
            "Official release requires provenance schema 4"
        }
        check(field(provenanceData, "versionName") == versionName) { "Provenance versionName is not synchronized" }
        check(field(provenanceData, "versionCode").toString().toInt() == versionCode) {
            "Provenance versionCode is not synchronized"
        }
        check(rootProject.version.toString() == versionName) { "Root and app versions are not synchronized" }
        val universalApk = stableApks.getValue("universal")
        check(field(provenanceData, "apk") == universalApk.name &&
                field(provenanceData, "apkSha256") == sha256(universalApk)) {
            "Legacy universal APK provenance is not synchronized"
        }
        val sbomHash = sha256(sbom)
        check(field(provenanceData, "sbom") == sbom.name && field(provenanceData, "sbomSha256") == sbomHash) {
            "Provenance SBOM identity or hash is not synchronized"
        }
        val sbomVersion = Regex("\\\"name\\\"\\s*:\\s*\\\"app\\\"\\s*,\\s*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(sbom.readText())?.groupValues?.get(1)
            ?: error("SBOM application version is missing")
        check(sbomVersion == versionName) { "SBOM and app versions are not synchronized" }
        val baselineVersion = field(sizeBaseline, "baselineVersion").toString()
        val baselineSizes = field(sizeBaseline, "artifacts") as? Map<*, *>
            ?: error("Release-size baseline artifacts must be an object")
        val measuredSizes = field(sizeReport, "artifacts") as? Map<*, *>
            ?: error("Release-size report artifacts must be an object")
        check(field(provenanceData, "sizeBaselineVersion") == baselineVersion) {
            "Provenance release-size baseline is not synchronized"
        }
        val mlDelivery = field(provenanceData, "mlDelivery") as? Map<*, *>
            ?: error("Provenance ML delivery report must be an object")
        check(field(mlDelivery, "bundledOcrScripts") == listOf("latin")) {
            "Only Latin OCR may be bundled"
        }
        check(
            field(mlDelivery, "playServicesOcrScripts") ==
                listOf("chinese", "japanese", "korean", "devanagari")
        ) { "Optional OCR delivery report is incomplete" }
        val sbomText = sbom.readText()
        listOf("chinese", "japanese", "korean", "devanagari").forEach { script ->
            check(!sbomText.contains("pkg:maven/com.google.mlkit/text-recognition-$script@")) {
                "Bundled $script OCR dependency returned"
            }
            check(
                sbomText.contains(
                    "pkg:maven/com.google.android.gms/play-services-mlkit-text-recognition-$script@"
                )
            ) {
                "Thin $script OCR dependency is missing"
            }
        }
        val allowDirty = providers.gradleProperty("allowDirtyOfficialVerification").orNull == "true"
        check(allowDirty || field(provenanceData, "sourceState") == "clean") {
            "Official releases require a clean Git worktree"
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
        val windows = System.getProperty("os.name").startsWith("Windows", true)
        val apksigner = buildToolsDir.resolve(if (windows) "apksigner.bat" else "apksigner")
        val zipalign = buildToolsDir.resolve(if (System.getProperty("os.name").startsWith("Windows", true)) "zipalign.exe" else "zipalign")
        val apkanalyzerName = if (windows) "apkanalyzer.bat" else "apkanalyzer"
        val apkanalyzer = sdkDirectory.resolve("cmdline-tools").walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(apkanalyzerName, ignoreCase = windows) }
            ?: error("apkanalyzer not found under ${sdkDirectory.resolve("cmdline-tools")}")
        check(apksigner.isFile) { "apksigner not found: $apksigner" }
        check(zipalign.isFile) { "zipalign not found: $zipalign" }
        fun toolOutput(tool: File, vararg args: String): String = providers.exec {
            commandLine(tool.absolutePath, *args)
        }.standardOutput.asText.get().trim()

        val artifactList = field(provenanceData, "artifacts") as? List<*>
            ?: error("Provenance artifacts must be an array")
        val artifactEntries = artifactList.map { entry ->
            entry as? Map<*, *> ?: error("Every provenance artifact must be an object")
        }
        val artifactsByAbi = artifactEntries.associateBy { field(it, "abi").toString() }
        check(artifactEntries.size == stableApks.size && artifactsByAbi.keys == stableApks.keys) {
            "Provenance ABI set mismatch: expected ${stableApks.keys}, got ${artifactsByAbi.keys}"
        }

        val manifestMinSdks = mutableSetOf<String>()
        val manifestTargetSdks = mutableSetOf<String>()
        stableApks.forEach { (abi, apk) ->
            val artifact = artifactsByAbi.getValue(abi)
            val expectedKind = if (abi == "universal") "universal" else "abi"
            check(field(artifact, "kind") == expectedKind) { "Incorrect artifact kind for $abi" }
            check(field(artifact, "apk") == apk.name) { "Incorrect provenance filename for $abi" }
            check(field(artifact, "apkSha256") == sha256(apk)) { "APK hash mismatch for ${apk.name}" }
            check(field(artifact, "sizeBytes").toString().toLong() == apk.length()) {
                "APK size mismatch for ${apk.name}"
            }
            val baselineArtifact = baselineSizes[abi] as? Map<*, *>
                ?: error("Release-size baseline is missing $abi")
            val measuredArtifact = measuredSizes[abi] as? Map<*, *>
                ?: error("Release-size report is missing $abi")
            val baselineBytes = field(baselineArtifact, "compressedBytes").toString().toLong()
            val deltaBytes = apk.length() - baselineBytes
            check(field(artifact, "sizeDeltaBytes").toString().toLong() == deltaBytes) {
                "APK size delta mismatch for ${apk.name}"
            }
            check(field(measuredArtifact, "compressedBytes").toString().toLong() == apk.length()) {
                "Release-size report compressed bytes mismatch for ${apk.name}"
            }
            val uncompressedBytes = field(measuredArtifact, "uncompressedBytes").toString().toLong()
            val uncompressedDelta = uncompressedBytes -
                field(baselineArtifact, "uncompressedBytes").toString().toLong()
            check(field(artifact, "uncompressedSizeBytes").toString().toLong() == uncompressedBytes &&
                    field(artifact, "uncompressedSizeDeltaBytes").toString().toLong() == uncompressedDelta) {
                "Expanded APK size provenance mismatch for ${apk.name}"
            }
            check(field(artifact, "versionName") == versionName &&
                    field(artifact, "versionCode").toString().toInt() == versionCode) {
                "Provenance version mismatch for ${apk.name}"
            }
            check(normalizeCertificate(field(artifact, "signingCertificateSha256")) == expectedCertificate) {
                "Provenance certificate mismatch for ${apk.name}"
            }
            check(field(artifact, "sbom") == sbom.name && field(artifact, "sbomSha256") == sbomHash) {
                "SBOM binding mismatch for ${apk.name}"
            }

            val signerOutput = toolOutput(apksigner, "verify", "--verbose", "--print-certs", apk.absolutePath)
            val signedCertificate = Regex("certificate SHA-256 digest: ([0-9a-fA-F]+)")
                .find(signerOutput)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
                ?: error("apksigner did not report a certificate for ${apk.name}")
            check(signedCertificate == expectedCertificate) { "APK certificate mismatch for ${apk.name}" }

            check(toolOutput(apkanalyzer, "manifest", "application-id", apk.absolutePath) == android.namespace) {
                "Application ID mismatch for ${apk.name}"
            }
            check(toolOutput(apkanalyzer, "manifest", "version-name", apk.absolutePath) == versionName) {
                "Manifest versionName mismatch for ${apk.name}"
            }
            check(toolOutput(apkanalyzer, "manifest", "version-code", apk.absolutePath) == versionCode.toString()) {
                "Manifest versionCode mismatch for ${apk.name}"
            }
            manifestMinSdks += toolOutput(apkanalyzer, "manifest", "min-sdk", apk.absolutePath)
            manifestTargetSdks += toolOutput(apkanalyzer, "manifest", "target-sdk", apk.absolutePath)

            ZipFile(apk).use { zip ->
                val entryNames = zip.entries().asSequence().map(ZipEntry::getName).toList()
                check(entryNames.any { "/Latn_ctc/" in it }) {
                    "Bundled Latin OCR assets are missing from ${apk.name}"
                }
                val forbiddenOcrTokens = listOf(
                    "/Deva_ctc/", "/Hani_ctc/", "/Jpan_ctc/", "/Kore_ctc/",
                    "gocrdevanagari", "gocrchinese", "gocrjapanese", "gocrkorean",
                )
                check(entryNames.none { name -> forbiddenOcrTokens.any(name::contains) }) {
                    "Bundled optional OCR assets remain in ${apk.name}"
                }
                val nativeEntries = zip.entries().asSequence()
                    .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                    .toList()
                check(nativeEntries.isNotEmpty()) {
                    "No native libraries found for 16 KB compatibility verification in ${apk.name}"
                }
                val nativeAbis = nativeEntries.mapTo(mutableSetOf()) {
                    it.name.substringAfter("lib/").substringBefore('/')
                }
                val expectedAbis = if (abi == "universal") releaseAbis.toSet() else setOf(abi)
                check(nativeAbis == expectedAbis) {
                    "Native ABI set mismatch for ${apk.name}: expected $expectedAbis, got $nativeAbis"
                }
                nativeEntries.forEach { entry ->
                    check(entry.method == ZipEntry.STORED) { "Native library is compressed: ${entry.name}" }
                    val magic = zip.getInputStream(entry).use { it.readNBytes(4) }
                    check(magic.contentEquals(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) {
                        "Native entry is not ELF: ${entry.name}"
                    }
                }
            }

            providers.exec {
                commandLine(zipalign.absolutePath, "-c", "-P", "16", "-v", "4", apk.absolutePath)
            }.result.get().assertNormalExitValue()
        }
        check(manifestMinSdks == setOf("29") && manifestTargetSdks == setOf("37")) {
            "Manifest SDK levels are inconsistent: min=$manifestMinSdks target=$manifestTargetSdks"
        }
        val universalSize = universalApk.length()
        releaseAbis.forEach { abi ->
            val splitSize = stableApks.getValue(abi).length()
            check(splitSize <= universalSize * 0.8) {
                "${stableApks.getValue(abi).name} is not materially smaller than the universal APK"
            }
        }
        logger.lifecycle("Official release gate passed for ${stableApks.size} APKs in ${outputDir.absolutePath}")
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

val screenshotSimilarityReport = layout.buildDirectory
    .file("reports/screenshot-similarity/benchmark.json")

tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    outputs.file(screenshotSimilarityReport)
}

tasks.register("benchmarkScreenshotSimilarity") {
    group = "verification"
    description = "Benchmarks generated screenshot pairs against production dHash, raw dHash, pHash, and bounded SSIM."
    dependsOn("testDebugUnitTest")

    doLast {
        val junitReport = layout.buildDirectory
            .file("test-results/testDebugUnitTest/TEST-com.sysadmindoc.snapcrop.ScreenshotSimilarityBenchmarkTest.xml")
            .get().asFile
        check(junitReport.isFile) { "Screenshot-similarity JUnit report is missing" }
        val xml = junitReport.readText()
        val tests = Regex("tests=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val failures = Regex("failures=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val errors = Regex("errors=\"(\\d+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        check(tests >= 1 && failures == 0 && errors == 0) {
            "Screenshot-similarity benchmark did not pass: tests=$tests failures=$failures errors=$errors"
        }
        val benchmarkReport = screenshotSimilarityReport.get().asFile
        check(benchmarkReport.isFile && benchmarkReport.length() > 0) {
            "Screenshot-similarity JSON report is missing"
        }
        check(JsonSlurper().parse(benchmarkReport) is Map<*, *>) {
            "Screenshot-similarity JSON report must contain an object"
        }
        logger.lifecycle("Screenshot-similarity benchmark passed: ${benchmarkReport.absolutePath}")
    }
}

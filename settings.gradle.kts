data class StableKotlinVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<StableKotlinVersion> {
    override fun compareTo(other: StableKotlinVersion): Int =
        compareValuesBy(this, other, StableKotlinVersion::major, StableKotlinVersion::minor, StableKotlinVersion::patch)
}

fun parseStableKotlinVersion(value: String): StableKotlinVersion {
    val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(value)
        ?: error("Pre-release or malformed Kotlin compiler versions are not allowed: $value")
    return StableKotlinVersion(
        match.groupValues[1].toInt(),
        match.groupValues[2].toInt(),
        match.groupValues[3].toInt(),
    )
}

fun strictGradleProperty(name: String): Boolean {
    val raw = providers.gradleProperty(name).orNull ?: return false
    return raw.toBooleanStrictOrNull()
        ?: error("Gradle property $name must be exactly true or false, got: $raw")
}

fun strictSystemProperty(name: String): Boolean {
    val raw = providers.systemProperty(name).orNull ?: return false
    return raw.toBooleanStrictOrNull()
        ?: error("JVM system property $name must be exactly true or false, got: $raw")
}

val versionCatalogText = file("gradle/libs.versions.toml").readText()
val kotlinVersionText = Regex("(?m)^kotlin\\s*=\\s*\"([^\"]+)\"\\s*$")
    .find(versionCatalogText)?.groupValues?.get(1)
    ?: error("Unable to read the Kotlin compiler version from gradle/libs.versions.toml")
val kotlinVersion = parseStableKotlinVersion(kotlinVersionText)
val cacheDeserializationFixed = kotlinVersion >= StableKotlinVersion(2, 4, 20)
val gradleBuildCacheRequested = gradle.startParameter.isBuildCacheEnabled
val kotlinBuildCacheRequested = strictSystemProperty("kotlin.caching.enabled")
val ambiguousKotlinCacheProjectProperty = strictGradleProperty("kotlin.caching.enabled")
val kotlinIncrementalCacheRequested = strictGradleProperty("kotlin.incremental")

if (!cacheDeserializationFixed) {
    check(!gradleBuildCacheRequested) {
        "CVE-2026-53914: --build-cache/org.gradle.caching is forbidden with Kotlin $kotlinVersionText; use stable Kotlin 2.4.20 or newer"
    }
    check(!kotlinBuildCacheRequested) {
        "CVE-2026-53914: -Dkotlin.caching.enabled=true is forbidden with Kotlin $kotlinVersionText"
    }
    check(!ambiguousKotlinCacheProjectProperty) {
        "CVE-2026-53914: -Pkotlin.caching.enabled=true is forbidden; use the JVM system property only"
    }
    check(!kotlinIncrementalCacheRequested) {
        "CVE-2026-53914: kotlin.incremental=true is forbidden with Kotlin $kotlinVersionText"
    }
}

buildCache.local.isEnabled = cacheDeserializationFixed && gradleBuildCacheRequested
gradle.extensions.extraProperties.set("snapcrop.kotlinVersion", kotlinVersionText)
gradle.extensions.extraProperties.set("snapcrop.cacheDeserializationFixed", cacheDeserializationFixed)
gradle.extensions.extraProperties.set("snapcrop.kotlinBuildCacheRequested", kotlinBuildCacheRequested)
gradle.extensions.extraProperties.set("snapcrop.kotlinIncrementalCacheRequested", kotlinIncrementalCacheRequested)

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SnapCrop"
include(":app")

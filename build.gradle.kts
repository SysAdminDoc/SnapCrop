plugins {
    alias(libs.plugins.cyclonedx.bom) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.room) apply false
}

tasks.register("verifyWrapperJar") {
    group = "verification"
    description = "Verifies the Gradle 9.4.1 wrapper JAR against Gradle's published SHA-256."
    doLast {
        val wrapperJar = layout.projectDirectory.file("gradle/wrapper/gradle-wrapper.jar").asFile
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val actual = wrapperJar.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val expected = "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c"
        check(actual == expected) { "Gradle wrapper JAR checksum mismatch: $actual" }
    }
}

tasks.register("verifyBuildCacheSecurity") {
    group = "verification"
    description = "Verifies the Kotlin build-cache containment policy for CVE-2026-53914."
    doLast {
        val kotlinVersion = gradle.extensions.extraProperties["snapcrop.kotlinVersion"] as String
        val cacheDeserializationFixed =
            gradle.extensions.extraProperties["snapcrop.cacheDeserializationFixed"] as Boolean
        val kotlinBuildCacheRequested =
            gradle.extensions.extraProperties["snapcrop.kotlinBuildCacheRequested"] as Boolean
        val kotlinIncrementalCacheRequested =
            gradle.extensions.extraProperties["snapcrop.kotlinIncrementalCacheRequested"] as Boolean
        check(!gradle.startParameter.isBuildCacheEnabled || cacheDeserializationFixed) {
            "Reusable Gradle build caches require stable Kotlin 2.4.20 or newer"
        }
        check(!kotlinBuildCacheRequested || cacheDeserializationFixed) {
            "Reusable Kotlin build caches require stable Kotlin 2.4.20 or newer"
        }
        check(!kotlinIncrementalCacheRequested || cacheDeserializationFixed) {
            "Kotlin incremental caches require stable Kotlin 2.4.20 or newer"
        }
        logger.lifecycle(
            if (cacheDeserializationFixed) {
                "Build-cache security gate: stable Kotlin $kotlinVersion permits explicit cache opt-in"
            } else {
                "Build-cache security gate: reusable Gradle/Kotlin caches disabled for Kotlin $kotlinVersion"
            }
        )
    }
}

allprojects {
    group = "com.sysadmindoc"
    version = "6.85.0"
}

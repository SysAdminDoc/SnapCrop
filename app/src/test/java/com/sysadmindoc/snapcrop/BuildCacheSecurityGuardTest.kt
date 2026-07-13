package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildCacheSecurityGuardTest {
    @Test
    fun affectedKotlinDefaultsEveryReusableCacheOff() {
        val properties = sourceFile("gradle.properties")
        assertTrue(properties.lineSequence().any { it.trim() == "org.gradle.caching=false" })
        assertTrue(properties.lineSequence().any { it.trim() == "org.gradle.configuration-cache=false" })
        assertTrue(properties.lineSequence().any { it.trim() == "systemProp.kotlin.caching.enabled=false" })
        assertTrue(properties.lineSequence().any { it.trim() == "kotlin.incremental=false" })
    }

    @Test
    fun settingsGateRejectsOptInsUntilAStablePatchedCompiler() {
        val settings = sourceFile("settings.gradle.kts")
        assertTrue(settings.contains("StableKotlinVersion(2, 4, 20)"))
        assertTrue(settings.contains("parseStableKotlinVersion(kotlinVersionText)"))
        assertTrue(settings.contains("gradle.startParameter.isBuildCacheEnabled"))
        assertTrue(settings.contains("strictSystemProperty(\"kotlin.caching.enabled\")"))
        assertTrue(settings.contains("strictGradleProperty(\"kotlin.caching.enabled\")"))
        assertTrue(settings.contains("strictGradleProperty(\"kotlin.incremental\")"))
        assertTrue(settings.contains("CVE-2026-53914"))
    }

    @Test
    fun officialReleaseDependsOnTheCacheSecurityGate() {
        val appBuild = sourceFile("app/build.gradle.kts")
        assertTrue(appBuild.contains("rootProject.tasks.named(\"verifyBuildCacheSecurity\")"))
    }

    private fun sourceFile(path: String): String {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.isFile) return candidate.readText()
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }
}

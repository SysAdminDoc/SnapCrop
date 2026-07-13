package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentationTrustContractTest {
    @Test
    fun readmeLocalLinksResolveAndSecurityDisclosureUsesPublishedSurfaces() {
        val root = repositoryRoot()
        val readme = File(root, "README.md").readText()
        val linkTargets = Regex("(?<!!)\\[[^]]+]\\(([^)]+)\\)")
            .findAll(readme)
            .map { it.groupValues[1].substringBefore('#') }
            .filter(String::isNotBlank)
            .toList()

        linkTargets.filterNot { it.startsWith("https://") }.forEach { target ->
            assertFalse("README links must not use insecure HTTP: $target", target.startsWith("http://"))
            assertTrue("Missing README link target: $target", File(root, target).isFile)
        }
        assertFalse(readme.contains("[SECURITY.md](SECURITY.md)"))
        assertTrue(readme.contains("https://github.com/SysAdminDoc/SnapCrop/security/advisories/new"))
        assertTrue(readme.contains("https://developers.google.com/ml-kit/terms"))
        assertTrue(readme.contains("https://developers.google.com/ml-kit/android-data-disclosure"))
    }

    @Test
    fun publicVersionAndFeatureCountsMatchSource() {
        val root = repositoryRoot()
        val readme = File(root, "README.md").readText()
        val changelog = File(root, "CHANGELOG.md").readText()
        val appBuild = File(root, "app/build.gradle.kts").readText()
        val rootBuild = File(root, "build.gradle.kts").readText()
        val models = File(root, "app/src/main/java/com/sysadmindoc/snapcrop/EditorModels.kt").readText()

        val appVersion = Regex("versionName = \"([^\"]+)\"").find(appBuild)!!.groupValues[1]
        val rootVersion = Regex("version = \"([^\"]+)\"").find(rootBuild)!!.groupValues[1]
        val latestHistoryVersion = Regex("v([0-9]+\\.[0-9]+\\.[0-9]+)").find(changelog)!!.groupValues[1]
        assertEquals(appVersion, rootVersion)
        assertEquals(appVersion, latestHistoryVersion)

        assertTrue(readme.contains("Powerful Editor — ${simpleEnumCount(models, "EditMode")} Modes"))
        assertTrue(readme.contains("Draw — ${constructorEnumCount(models, "DrawTool")} Tools"))
        assertTrue(readme.contains("${constructorEnumCount(models, "AspectRatio")} aspect ratios"))
        val filterEffects = constructorEnumCount(models, "ImageFilter") - 1
        assertTrue(readme.contains("Adjust — 13 Sliders + $filterEffects Filters"))
    }

    @Test
    fun disclosureCoversEveryDeclaredSensitivePermissionAndNetworkPath() {
        val root = repositoryRoot()
        val readme = File(root, "README.md").readText()
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

        listOf(
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO",
            "POST_NOTIFICATIONS",
            "SYSTEM_ALERT_WINDOW",
            "ACCESS_LOCAL_NETWORK",
            "INTERNET",
        ).forEach { permission -> assertTrue(manifest.contains("android.permission.$permission")) }
        listOf(
            "Photos/images",
            "Notifications",
            "display-over-apps",
            "Accessibility",
            "Local network access",
            "api.github.com",
            "Static Web Capture",
            "Network exports",
            "performance/utilization",
            "input images, recognized text, and inference results",
        ).forEach { disclosure -> assertTrue("Missing disclosure: $disclosure", readme.contains(disclosure)) }
    }

    private fun simpleEnumCount(source: String, name: String): Int =
        enumBody(source, name).split(',').count { it.trim().isNotEmpty() }

    private fun constructorEnumCount(source: String, name: String): Int =
        Regex("\\b[A-Z][A-Z0-9_]*\\(").findAll(enumBody(source, name)).count()

    private fun enumBody(source: String, name: String): String =
        Regex("internal enum class $name[^\\{]*\\{([^}]*)}", RegexOption.DOT_MATCHES_ALL)
            .find(source)?.groupValues?.get(1) ?: error("Unable to locate enum $name")

    private fun repositoryRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            if (File(current, "README.md").isFile && File(current, "app/build.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate repository root")
    }
}

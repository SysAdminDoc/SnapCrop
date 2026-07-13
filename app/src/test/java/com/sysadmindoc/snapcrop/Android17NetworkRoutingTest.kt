package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Android17NetworkRoutingTest {
    @Test
    fun buildAndManifestTargetApi37WithOnlyTheScopedLanPermission() {
        val build = sourceFile("app/build.gradle.kts")
        val manifest = sourceFile("app/src/main/AndroidManifest.xml")

        assertTrue(build.contains("compileSdk = 37"))
        assertTrue(build.contains("targetSdk = 37"))
        assertTrue(manifest.contains("android.permission.ACCESS_LOCAL_NETWORK"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(manifest.contains("android.permission.NEARBY_WIFI_DEVICES"))
    }

    @Test
    fun reportUploadAssessesBeforeOpeningTheSourceAndPublicImgurSkipsLanAssessment() {
        val main = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt")
        val client = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/NetworkExportClient.kt")
        val adapter = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/AndroidLocalNetworkAccess.kt")

        val assess = main.indexOf("AndroidLocalNetworkAccess.assess(this, settings)")
        val upload = main.indexOf("NetworkExportClient.uploadReportPdf(")
        assertTrue(assess >= 0)
        assertTrue(upload > assess)
        assertTrue(client.contains("failureReason = NetworkExportFailureReason.LOCAL_NETWORK_PERMISSION"))
        assertTrue(client.contains("localNetworkEndpoint = false"))
        assertTrue(adapter.contains("if (settings.target == NetworkExportTarget.IMGUR)"))
    }

    @Test
    fun deniedOrRevokedPermissionHasAutomaticSettingsRecoveryWithoutBlockingLocalSave() {
        val main = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/MainActivity.kt")
        val settings = sourceFile("app/src/main/java/com/sysadmindoc/snapcrop/SettingsActivity.kt")

        assertTrue(main.contains("LocalNetworkPermissionOutcome.OPEN_SETTINGS"))
        assertTrue(main.contains("openLocalNetworkPermissionSettings()"))
        assertTrue(main.contains("NetworkExportFailureReason.LOCAL_NETWORK_PERMISSION"))
        assertTrue(settings.contains("AndroidLocalNetworkAccess.PREF_PERMISSION_REQUESTED"))
        assertTrue(settings.contains("Settings.ACTION_APPLICATION_DETAILS_SETTINGS"))
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

package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseArtifactContractTest {
    @Test
    fun officialDistributionPublishesAndValidatesTheUniversalAndFourAbiApks() {
        val build = sourceFile("app/build.gradle.kts")

        listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").forEach { abi ->
            assertTrue(build.contains("\"$abi\""))
        }
        assertTrue(build.contains("SnapCrop-\$versionName\$suffix.apk"))
        assertTrue(build.contains("actualFiles == expectedFiles"))
        assertTrue(build.contains("artifactsByAbi.keys == stableApks.keys"))
        assertTrue(build.contains("manifest\", \"version-code"))
        assertTrue(build.contains("nativeAbis == expectedAbis"))
        assertTrue(build.contains("splitSize <= universalSize * 0.8"))
        assertTrue(build.contains("\"schemaVersion\": 4"))
        assertTrue(build.contains("sizeDeltaBytes"))
        assertTrue(build.contains("uncompressedSizeDeltaBytes"))
        assertTrue(build.contains("verifyReleaseSizeBudget"))
        assertTrue(build.contains("snapcrop.sizeBaselineReason"))
        assertTrue(build.contains("Bundled optional OCR assets remain"))
        assertTrue(
            build.contains("pkg:maven/com.google.android.gms/play-services-mlkit-text-recognition-")
        )
        assertTrue(build.contains("pkg:maven/com.google.mlkit/text-recognition-"))
        assertTrue(sourceFile("gradle/release-size-baseline.json").contains("\"schemaVersion\": 2"))
    }

    @Test
    fun debugBuildsDoNotEnableReleaseAbiSplitsByDefault() {
        val build = sourceFile("app/build.gradle.kts")

        assertTrue(build.contains("isEnable = releaseAbiSplitsEnabled"))
        assertTrue(build.contains("taskName.contains(\"assembleRelease\""))
        assertTrue(build.contains("taskName.contains(\"verifyOfficialRelease\""))
        assertTrue(build.contains("taskName.contains(\"ReleaseSize\""))
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

package com.sysadmindoc.snapcrop

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSizeBudgetContractTest {
    @Test
    fun releaseGraphOmitsNavigationComposeButRetainsNavigationEventVerification() {
        val build = sourceFile("app/build.gradle.kts")
        val catalog = sourceFile("gradle/libs.versions.toml")
        val verification = sourceFile("gradle/verification-metadata.xml")

        assertFalse(build.contains("libs.androidx.navigation.compose"))
        assertFalse(catalog.contains("navigationCompose"))
        assertFalse(catalog.contains("androidx-navigation-compose"))
        assertFalse(verification.contains("group=\"androidx.navigation\""))
        assertTrue(verification.contains("group=\"androidx.navigationevent\""))
    }

    @Test
    fun releaseSizeBaselineRecordsBoundedArtifactsDependenciesAndNativeLibraries() {
        val baseline = JSONObject(sourceFile("gradle/release-size-baseline.json"))

        assertEquals(2, baseline.getInt("schemaVersion"))
        assertTrue(baseline.getString("baselineVersion").isNotBlank())
        assertTrue(baseline.getString("rationale").isNotBlank())

        val limits = baseline.getJSONObject("limits")
        assertEquals(
            setOf(
                "artifactCompressedGrowthBytes",
                "artifactUncompressedGrowthBytes",
                "dependencyCompressedGrowthBytes",
                "dependencyUncompressedGrowthBytes",
                "nativeCompressedGrowthBytes",
                "nativeUncompressedGrowthBytes",
            ),
            limits.keysSet(),
        )
        assertEquals(262_144L, limits.getLong("artifactCompressedGrowthBytes"))
        assertEquals(524_288L, limits.getLong("artifactUncompressedGrowthBytes"))
        listOf(
            "dependencyCompressedGrowthBytes",
            "dependencyUncompressedGrowthBytes",
            "nativeCompressedGrowthBytes",
            "nativeUncompressedGrowthBytes",
        ).forEach { key -> assertEquals(0L, limits.getLong(key)) }

        val artifacts = baseline.getJSONObject("artifacts")
        assertEquals(
            setOf("universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            artifacts.keysSet(),
        )
        artifacts.keysSet().forEach { key -> assertSizeEntry(artifacts.getJSONObject(key)) }

        val dependencies = baseline.getJSONArray("dependencies")
        assertTrue(dependencies.length() > 0)
        val dependencyKeys = dependencies.objects().map { dependency ->
            val coordinate = dependency.getString("coordinate")
            assertFalse(coordinate.startsWith("androidx.navigation:"))
            assertTrue(dependency.getString("artifact").isNotBlank())
            assertSizeEntry(dependency)
            "$coordinate|${dependency.getString("artifact")}"
        }
        assertEquals(dependencyKeys.sorted(), dependencyKeys)
        assertEquals(dependencyKeys.toSet().size, dependencyKeys.size)

        val nativeLibraries = baseline.getJSONArray("nativeLibraries")
        assertTrue(nativeLibraries.length() > 0)
        val nativeKeys = nativeLibraries.objects().map { library ->
            val abi = library.getString("abi")
            assertTrue(library.getString("name").endsWith(".so"))
            assertSizeEntry(library)
            "$abi|${library.getString("name")}"
        }
        assertEquals(
            setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            nativeLibraries.objects().mapTo(mutableSetOf()) { it.getString("abi") },
        )
        assertEquals(
            nativeKeys.sortedWith(compareBy<String> { it.substringBefore('|') }.thenBy { it.substringAfter('|') }),
            nativeKeys,
        )
        assertEquals(nativeKeys.toSet().size, nativeKeys.size)
    }

    private fun assertSizeEntry(entry: JSONObject) {
        assertTrue(entry.getLong("compressedBytes") > 0L)
        assertTrue(entry.getLong("uncompressedBytes") > 0L)
    }

    private fun JSONArray.objects(): List<JSONObject> =
        List(length()) { index -> getJSONObject(index) }

    private fun JSONObject.keysSet(): Set<String> = keys().asSequence().toSet()

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

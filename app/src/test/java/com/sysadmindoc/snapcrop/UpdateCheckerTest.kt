package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun newerVersionsDetected() {
        assertTrue(UpdateChecker.isNewer("6.27.0", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("6.26.1", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("7.0.0", "6.26.0"))
        assertTrue(UpdateChecker.isNewer("6.26.10", "6.26.2"))
    }

    @Test
    fun sameOrOlderNotNewer() {
        assertFalse(UpdateChecker.isNewer("6.26.0", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.25.0", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.26.0", "6.26.1"))
        assertFalse(UpdateChecker.isNewer("5.9.0", "6.26.0"))
    }

    @Test
    fun nonNumericSuffixIgnored() {
        // "6.27.0-beta" parses as [6,27,0] and is newer than 6.26.0.
        assertTrue(UpdateChecker.isNewer("6.27.0-beta", "6.26.0"))
        assertFalse(UpdateChecker.isNewer("6.26.0-rc1", "6.26.0"))
    }

    @Test
    fun latestReleaseSelectsExactVersionedApkAndGitHubDigest() {
        val digest = "ab".repeat(32)
        val result = UpdateChecker.parseLatestRelease(
            """
            {
              "tag_name":"v6.61.0",
              "html_url":"https://github.com/SysAdminDoc/SnapCrop/releases/tag/v6.61.0",
              "body":"Release notes",
              "assets":[
                {"name":"other.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/other.apk"},
                {"name":"SnapCrop-6.61.0-arm64-v8a.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-arm64-v8a.apk"},
                {"name":"SnapCrop-6.61.0-armeabi-v7a.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-armeabi-v7a.apk"},
                {"name":"SnapCrop-6.61.0-x86.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-x86.apk"},
                {"name":"SnapCrop-6.61.0-x86_64.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-x86_64.apk"},
                {"name":"SnapCrop-6.61.0.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0.apk","digest":"sha256:$digest"}
              ]
            }
            """.trimIndent(),
            "6.60.0",
        ) as UpdateChecker.Result.Available

        assertEquals("https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0.apk", result.info.downloadUrl)
        assertEquals(digest, result.info.apkSha256)
    }

    @Test
    fun splitOnlyReleaseFallsBackToTrustedReleasePage() {
        val result = UpdateChecker.parseLatestRelease(
            """
            {
              "tag_name":"v6.61.0",
              "html_url":"https://github.com/SysAdminDoc/SnapCrop/releases/tag/v6.61.0",
              "assets":[
                {"name":"SnapCrop-6.61.0-arm64-v8a.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-arm64-v8a.apk"},
                {"name":"SnapCrop-6.61.0-x86_64.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-6.61.0-x86_64.apk"}
              ]
            }
            """.trimIndent(),
            "6.60.0",
        ) as UpdateChecker.Result.Available

        assertEquals("https://github.com/SysAdminDoc/SnapCrop/releases/tag/v6.61.0", result.info.downloadUrl)
        assertNull(result.info.apkUrl)
        assertNull(result.info.apkSha256)
    }

    @Test
    fun untrustedOrWrongNamedAssetsFallBackToReleasePageWithoutChecksum() {
        val result = UpdateChecker.parseLatestRelease(
            """
            {
              "tag_name":"v6.61.0",
              "html_url":"https://evil.example/release",
              "assets":[
                {"name":"SnapCrop-6.61.0.apk","state":"uploaded","browser_download_url":"https://evil.example/SnapCrop-6.61.0.apk","digest":"sha256:${"cd".repeat(32)}"},
                {"name":"SnapCrop-latest.apk","state":"uploaded","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v6.61.0/SnapCrop-latest.apk"}
              ]
            }
            """.trimIndent(),
            "6.60.0",
        ) as UpdateChecker.Result.Available

        assertEquals(UpdateChecker.FALLBACK_URL, result.info.downloadUrl)
        assertNull(result.info.apkUrl)
        assertNull(result.info.apkSha256)
    }

    @Test
    fun malformedDigestIsNotPresentedAsAValidChecksum() {
        val result = UpdateChecker.parseLatestRelease(
            """{"tag_name":"v7.0.0","assets":[{"name":"SnapCrop-7.0.0.apk","browser_download_url":"https://github.com/SysAdminDoc/SnapCrop/releases/download/v7.0.0/SnapCrop-7.0.0.apk","digest":"sha256:not-a-digest"}]}""",
            "6.60.0",
        ) as UpdateChecker.Result.Available

        assertTrue(result.info.downloadUrl.endsWith("/SnapCrop-7.0.0.apk"))
        assertNull(result.info.apkSha256)
    }
}

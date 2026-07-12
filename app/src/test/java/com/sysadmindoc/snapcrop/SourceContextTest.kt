package com.sysadmindoc.snapcrop

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceContextTest {
    @Test
    fun webCaptureUrlWinsSharedAndReferrerWhilePackageSurvives() {
        val intent = Intent()
            .putExtra(WebCaptureActivity.EXTRA_SOURCE_URL, "https://web.example/page")
            .putExtra(Intent.EXTRA_TEXT, "https://shared.example/page")
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("https://referrer.example/page"))
            .putExtra(ExplicitSourceContext.EXTRA_PACKAGE_NAME, "Com.Example.Browser")

        val context = ExplicitSourceContext.fromIntent(intent)

        assertEquals("https://web.example/page", context?.url)
        assertEquals("com.example.browser", context?.packageName)
    }

    @Test
    fun sharedUrlWinsReferrerWhenWebSignalIsAbsent() {
        val intent = Intent()
            .putExtra(Intent.EXTRA_TEXT, "http://shared.example:80/path?q=1#part")
            .putExtra(Intent.EXTRA_REFERRER_NAME, "https://referrer.example/path")

        val context = ExplicitSourceContext.fromIntent(intent)

        assertEquals("http://shared.example/path?q=1#part", context?.url)
    }

    @Test
    fun typedReferrerWinsNameAndActivityReferrer() {
        val intent = Intent()
            .putExtra(Intent.EXTRA_REFERRER, Uri.parse("https://typed.example/path"))
            .putExtra(Intent.EXTRA_REFERRER_NAME, "https://named.example/path")

        val context = ExplicitSourceContext.fromIntent(
            intent,
            activityReferrer = Uri.parse("https://activity.example/path"),
        )

        assertEquals("https://typed.example/path", context?.url)
    }

    @Test
    fun androidAppReferrerProvidesHigherPriorityPackageHint() {
        val intent = Intent()
            .putExtra(Intent.EXTRA_REFERRER_NAME, "android-app://Com.Example.Referrer")
            .putExtra(ExplicitSourceContext.EXTRA_PACKAGE_NAME, "com.example.fallback")

        val context = ExplicitSourceContext.fromIntent(intent)

        assertEquals("com.example.referrer", context?.packageName)
    }

    @Test
    fun parserIgnoresIntentDataAndOtherImplicitSources() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ignored.example/from-data"))
        intent.clipData = android.content.ClipData.newPlainText("source", "https://ignored.example/clip")

        assertNull(ExplicitSourceContext.fromIntent(intent))
    }

    @Test
    fun urlNormalizationAllowsOnlyCredentialFreeHttpAndHttps() {
        val valid = ExplicitSourceContext(url = " HTTPS://Exämple.com:443/a?q=1#f ").normalizedOrNull()

        assertEquals("https://xn--exmple-cua.com/a?q=1#f", valid?.url)
        assertNull(ExplicitSourceContext(url = "ftp://example.com/file").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "content://example.com/file").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https:///missing-host").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://bad_host.example/path").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://user:secret@example.com/").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://example.com/a b").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://example.com/%0aheader").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://example.com/line\nbreak").normalizedOrNull())
        assertNull(ExplicitSourceContext(url = "https://example.com/${"a".repeat(2_100)}").normalizedOrNull())
    }

    @Test
    fun labelAndPackageAreNormalizedAndBounded() {
        val context = ExplicitSourceContext(
            label = "  Support   runbook  ",
            packageName = " Com.Example_App.Tool ",
        ).normalizedOrNull()

        assertEquals("Support runbook", context?.label)
        assertEquals("com.example_app.tool", context?.packageName)
        assertNull(ExplicitSourceContext(label = "bad\tlabel").normalizedOrNull())
        assertNull(ExplicitSourceContext(packageName = "not a package").normalizedOrNull())
        assertNull(ExplicitSourceContext(label = "x".repeat(161)).normalizedOrNull())
        assertNull(ExplicitSourceContext(packageName = "a".repeat(256)).normalizedOrNull())
    }

    @Test
    fun invalidFieldDoesNotEraseOtherValidExplicitContext() {
        val context = ExplicitSourceContext(
            url = "https://user:secret@example.com",
            label = "Incident 42",
            packageName = "com.example.support",
        ).normalizedOrNull()

        assertNull(context?.url)
        assertEquals("Incident 42", context?.label)
        assertEquals("com.example.support", context?.packageName)
    }

    @Test
    fun userEditsOverrideOrExplicitlyClearParsedFields() {
        val parsed = ExplicitSourceContext(
            url = "https://example.com/original",
            label = "Original",
            packageName = "com.example.original",
        )

        val edited = parsed.mergeUserEdits(
            ExplicitSourceContext(
                url = "https://example.com/edited",
                label = "Edited",
                packageName = "",
            ),
        )

        assertEquals("https://example.com/edited", edited?.url)
        assertEquals("Edited", edited?.label)
        assertNull(edited?.packageName)
    }

    @Test
    fun validHigherPriorityFieldsMergeWithoutDroppingLowerFields() {
        val lower = ExplicitSourceContext(
            url = "https://referrer.example/page",
            packageName = "com.example.app",
        )

        val merged = lower.mergedWith(ExplicitSourceContext(label = "User label"))

        assertEquals("https://referrer.example/page", merged?.url)
        assertEquals("User label", merged?.label)
        assertEquals("com.example.app", merged?.packageName)
    }

    @Test
    fun openAndShareHelpersExposeOnlyTheNormalizedLink() {
        val context = ExplicitSourceContext(
            url = "HTTPS://Example.COM:443/path",
            label = "Runbook",
            packageName = "com.example.app",
        )

        assertEquals(Uri.parse("https://example.com/path"), context.openUri)
        assertEquals("https://example.com/path", context.shareText)
        assertNull(ExplicitSourceContext(label = "Runbook", packageName = "com.example.app").shareText)
        assertNull(ExplicitSourceContext(url = "file:///private").openUri)
    }

    @Test
    fun extraTextMustBeOnlyAUrlRatherThanProseContainingOne() {
        val intent = Intent().putExtra(Intent.EXTRA_TEXT, "See https://example.com/private")

        assertNull(ExplicitSourceContext.fromIntent(intent))
    }

    @Test
    fun packageOnlySignalIsRetained() {
        val context = ExplicitSourceContext.fromIntent(
            Intent().putExtra(ExplicitSourceContext.EXTRA_PACKAGE_NAME, "com.example.capture"),
        )

        assertEquals("com.example.capture", context?.packageName)
        assertTrue(context?.url == null && context?.label == null)
    }
}

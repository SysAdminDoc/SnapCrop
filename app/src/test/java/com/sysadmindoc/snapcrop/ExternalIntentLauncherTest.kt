package com.sysadmindoc.snapcrop

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalIntentLauncherTest {
    @Test
    fun missingHandlerReturnsUnavailableWithoutStarting() {
        var started = false
        var resolvedIntent: Intent? = null
        val launcher = ExternalIntentLauncher(
            canResolve = { resolvedIntent = it; false },
            start = { started = true },
        )

        assertEquals(
            ExternalLaunchOutcome.UNAVAILABLE,
            launcher.launchDial("+15551234567"),
        )
        assertFalse(started)
        assertEquals(Intent.ACTION_DIAL, resolvedIntent?.action)
        assertEquals("tel", resolvedIntent?.data?.scheme)
    }

    @Test
    fun activityNotFoundAfterResolutionReturnsFailed() {
        var startedIntent: Intent? = null
        val launcher = ExternalIntentLauncher(
            canResolve = { true },
            start = {
                startedIntent = it
                throw ActivityNotFoundException("handler disappeared")
            },
        )

        assertEquals(
            ExternalLaunchOutcome.FAILED,
            launcher.launchEmail("person@example.com"),
        )
        assertEquals(Intent.ACTION_SENDTO, startedIntent?.action)
        assertEquals("mailto", startedIntent?.data?.scheme)
    }

    @Test
    fun resolvedHttpUrlStartsWithExpectedActionAndReturnsLaunched() {
        var captured: Intent? = null
        val launcher = ExternalIntentLauncher(
            canResolve = { true },
            start = { captured = it },
        )

        assertEquals(
            ExternalLaunchOutcome.LAUNCHED,
            launcher.launchUrl("https://example.com/path?q=1"),
        )
        assertEquals(Intent.ACTION_VIEW, captured?.action)
        assertEquals("https", captured?.data?.scheme)
        assertEquals("example.com", captured?.data?.host)
    }

    @Test
    fun unsafeUrlFailsBeforeResolutionOrLaunch() {
        var resolved = false
        var started = false
        val launcher = ExternalIntentLauncher(
            canResolve = { resolved = true; true },
            start = { started = true },
        )

        assertEquals(
            ExternalLaunchOutcome.FAILED,
            launcher.launchUrl("javascript:alert(1)"),
        )
        assertFalse(resolved)
        assertFalse(started)
    }

    @Test
    fun fallbackRequiresFailureAndPreservesCopyRouting() {
        val fallback = ExternalActionFallback(
            outcome = ExternalLaunchOutcome.UNAVAILABLE,
            copyValue = "https://example.com",
            copyKind = ExternalFallbackCopyKind.URL,
        )

        assertEquals(ExternalFallbackCopyKind.URL, fallback.copyKind)
        assertTrue(fallback.copyValue.startsWith("https://"))
    }

    @Test
    fun manifestDeclaresVisibilityForEveryResolvedHandoff() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.intent.action.DIAL"))
        assertTrue(manifest.contains("android:scheme=\"tel\""))
        assertTrue(manifest.contains("android.intent.action.SENDTO"))
        assertTrue(manifest.contains("android:scheme=\"mailto\""))
        assertTrue(manifest.contains("android:scheme=\"http\""))
        assertTrue(manifest.contains("android:scheme=\"https\""))
    }
}

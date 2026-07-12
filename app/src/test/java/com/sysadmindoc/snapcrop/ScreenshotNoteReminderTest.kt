package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScreenshotNoteReminderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    @Test
    fun noteNormalizationPreservesUsefulLinesAndRejectsUnsafeBounds() {
        assertEquals("ABC\nnext\tstep", ScreenshotNoteText.normalize("  ＡＢＣ\r\nnext\tstep  "))
        assertTrue(runCatching { ScreenshotNoteText.normalize("bad\u0000value") }.isFailure)
        assertTrue(runCatching { ScreenshotNoteText.normalize("x".repeat(ScreenshotNoteText.MAX_CHARS + 1)) }.isFailure)
    }

    @Test
    fun notesParticipateInSearchWithoutDerivedIndexData() {
        val photo = Photo(
            id = 4,
            uri = Uri.parse("content://media/external/images/media/4"),
            dateAdded = 44,
            name = "Screenshot.png",
            indexText = "",
            noteReminder = ScreenshotNoteReminder(
                uri = Uri.parse("content://media/external/images/media/4"),
                dateAdded = 44,
                note = "Renew the radiology license",
                reminderAt = null,
                reminderToken = null,
                createdAt = 1,
                updatedAt = 1
            )
        )

        assertTrue(photo.matchesGalleryQuery("RADIOLOGY"))
        assertFalse(photo.matchesGalleryQuery("groceries"))
    }

    @Test
    fun reminderIdentityIsStablePrivateAndDateSensitive() {
        val uri = Uri.parse("content://media/external/images/media/42")
        val first = ScreenshotReminderScheduler.workName(uri, 100)
        val same = ScreenshotReminderScheduler.workName(uri, 100)
        val reused = ScreenshotReminderScheduler.workName(uri, 101)

        assertEquals(first, same)
        assertNotEquals(first, reused)
        assertFalse(first.contains("content"))
        assertFalse(first.contains("42"))
        assertEquals(0, ScreenshotReminderScheduler.initialDelay(10, 20))
        assertEquals(15, ScreenshotReminderScheduler.initialDelay(35, 20))
        assertEquals(
            ScreenshotReminderScheduler.notificationId(uri, 100),
            ScreenshotReminderScheduler.notificationId(uri, 100)
        )
    }

    @Test
    fun schedulerReplacesOneIdentityAndCancellationIsExact() {
        val uri = Uri.parse("content://media/external/images/media/7")
        val first = reminder(uri, 77, "one", System.currentTimeMillis() + 3_600_000)
        val replacement = reminder(uri, 77, "two", System.currentTimeMillis() + 7_200_000)

        ScreenshotReminderScheduler.schedule(context, first)
        ScreenshotReminderScheduler.schedule(context, replacement)

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScreenshotReminderScheduler.workName(uri, 77)).get()
        assertEquals(1, work.count { !it.state.isFinished })
        assertTrue(work.single { !it.state.isFinished }.tags.contains(ScreenshotReminderScheduler.TAG))

        ScreenshotReminderScheduler.cancel(context, uri, 77)
        val cancelled = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScreenshotReminderScheduler.workName(uri, 77)).get()
        assertTrue(cancelled.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun deliveryPolicyRejectsStaleWorkAndRetainsPermissionMisses() {
        val uri = Uri.parse("content://media/external/images/media/5")
        val stored = reminder(uri, 55, "current", 1_000)

        assertEquals(
            ReminderDeliveryDisposition.NO_OP,
            reminderDeliveryDisposition(stored, 1_000, "stale", mediaExists = true, notificationsAvailable = true)
        )
        assertEquals(
            ReminderDeliveryDisposition.CLEAR_MISSING,
            reminderDeliveryDisposition(stored, 1_000, "current", mediaExists = false, notificationsAvailable = true)
        )
        assertEquals(
            ReminderDeliveryDisposition.KEEP_DISABLED,
            reminderDeliveryDisposition(stored, 1_000, "current", mediaExists = true, notificationsAvailable = false)
        )
        assertEquals(
            ReminderDeliveryDisposition.DELIVER,
            reminderDeliveryDisposition(stored, 1_000, "current", mediaExists = true, notificationsAvailable = true)
        )
    }

    @Test
    fun reminderIntentAndGalleryResolutionRequireExactMediaIdentity() {
        val uri = Uri.parse("content://media/external/images/media/9")
        val intent = Intent(ScreenshotReminderContract.ACTION_OPEN)
            .putExtra(ScreenshotReminderContract.EXTRA_URI, uri.toString())
            .putExtra(ScreenshotReminderContract.EXTRA_DATE_ADDED, 90L)
        assertEquals(GalleryOpenRequest(uri, 90), ScreenshotReminderContract.parse(intent))
        assertNull(ScreenshotReminderContract.parse(Intent("wrong.action")))
        assertNull(ScreenshotReminderContract.parse(Intent(ScreenshotReminderContract.ACTION_OPEN)
            .putExtra(ScreenshotReminderContract.EXTRA_URI, "file:///private/image.png")
            .putExtra(ScreenshotReminderContract.EXTRA_DATE_ADDED, 90L)))

        val photos = listOf(Photo(id = 9, uri = uri, dateAdded = 91))
        assertEquals(-1, exactGalleryTargetIndex(photos, GalleryOpenRequest(uri, 90)))
        assertEquals(0, exactGalleryTargetIndex(photos, GalleryOpenRequest(uri, 91)))
    }

    private fun reminder(uri: Uri, dateAdded: Long, token: String, at: Long) = ScreenshotNoteReminder(
        uri = uri,
        dateAdded = dateAdded,
        note = "",
        reminderAt = at,
        reminderToken = token,
        createdAt = 1,
        updatedAt = 1
    )
}

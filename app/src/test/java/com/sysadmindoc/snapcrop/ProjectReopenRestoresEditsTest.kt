package com.sysadmindoc.snapcrop

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectReopenRestoresEditsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<CropActivity>? = null

    @After
    fun tearDown() {
        SlowMediaProvider.release.countDown()
        scenario?.close()
    }

    /**
     * Seen on a 16 KB Android 16 emulator: a saved .snapcrop.json reopened with its crop but none
     * of its 13 redactions. The editor showed the source while the source's MediaStore row was
     * still being read, and only then got the project's edits, which it takes once.
     */
    @Test
    fun redactionsComeBackWhenTheEditorShowsBeforeTheSourceRowIsRead() {
        val sourceUri = Uri.parse("content://media/external/images/media/18")
        val projectUri = Uri.parse("content://media/external/file/26")
        val png = sourcePng(360, 800)
        val project = SnapCropProject(
            sourceUri = sourceUri.toString(),
            sourceSha256 = sha256(png),
            sourceWidth = 360,
            sourceHeight = 800,
            cropRect = Rect(10, 20, 350, 700),
            adjustments = FloatArray(25).also { it[1] = 1f; it[2] = 1f },
            redactions = listOf(
                RedactionRegions.manual(Rect(40, 60, 200, 100)),
                RedactionRegions.manual(Rect(40, 140, 260, 180)),
            ),
            drawLayers = emptyList(),
            exportFormat = "png",
            exportMimeType = "image/png",
            exportQuality = 100,
            exportSavePath = "Pictures/SnapCrop",
            deleteOriginal = false,
        )
        val json = SnapCropProjectSidecar.encode(project).toByteArray()

        val resolver = shadowOf(RuntimeEnvironment.getApplication().contentResolver)
        resolver.registerInputStreamSupplier(sourceUri) { ByteArrayInputStream(png) }
        resolver.registerInputStreamSupplier(projectUri) { ByteArrayInputStream(json) }
        SlowMediaProvider.file = File.createTempFile("source", ".png").apply {
            writeBytes(png)
            deleteOnExit()
        }
        Robolectric.setupContentProvider(SlowMediaProvider::class.java, "media")

        scenario = ActivityScenario.launch(
            Intent(Intent.ACTION_VIEW)
                .setClassName(RuntimeEnvironment.getApplication(), CropActivity::class.java.name)
                .setDataAndType(projectUri, SnapCropProjectSidecar.MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )

        // Hold the load inside the source-row read and let the screen draw everything it has.
        waitFor("the source row read") { SlowMediaProvider.entered.count == 0L }
        compose.waitForIdle()
        SlowMediaProvider.release.countDown()

        waitFor("the editor") { compose.onAllNodesWithText("Redact").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithText("Redact").performClick()
        compose.onNodeWithText("Redactions").assertExists()
        compose.onNodeWithText("2/2").assertExists()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(10_000, condition)
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Never reached $what. Screen:\n" + compose.onAllNodes(isRoot()).printToString(), e)
        }
    }

    private fun sourcePng(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        for (y in 300 until 500) for (x in 40 until 320) bitmap.setPixel(x, y, Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Serves the source file, and answers MediaStore row reads only after the test says so. */
    class SlowMediaProvider : ContentProvider() {
        companion object {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            @Volatile var file: File? = null
        }

        override fun onCreate(): Boolean = true

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file ?: throw FileNotFoundException(uri.toString()), ParcelFileDescriptor.MODE_READ_ONLY)

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            return null
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}

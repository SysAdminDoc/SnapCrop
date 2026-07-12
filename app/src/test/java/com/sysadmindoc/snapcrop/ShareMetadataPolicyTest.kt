package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ShareMetadataPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun inspectReportsCategoriesWithoutReturningValues() {
        val source = populatedJpeg("inspect.jpg")

        val summary = ExifTransfer.inspect(ExifInterface(source))

        assertEquals(MetadataCategory.entries.toSet(), summary.categories)
        assertTrue(summary.toString().contains("LOCATION"))
        assertTrue(!summary.toString().contains("Example Camera"))
    }

    @Test
    fun stripAllWritesNoModeledMetadataAndDoesNotMutateSource() {
        assertPolicy(ShareMetadataPolicy.STRIP_ALL)
    }

    @Test
    fun keepSafeWritesOnlyTechnicalMetadataAndDoesNotMutateSource() {
        assertPolicy(ShareMetadataPolicy.KEEP_SAFE)
    }

    @Test
    fun preserveWritesEveryDetectedSupportedTagAndDoesNotMutateSource() {
        assertPolicy(ShareMetadataPolicy.PRESERVE)
    }

    private lateinit var sourceValues: Map<String, String?>

    private fun assertPolicy(policy: ShareMetadataPolicy) {
        val source = populatedJpeg("source-${policy.name}.jpg")
        val destination = emptyJpeg("destination-${policy.name}.jpg")
        val sourceExif = ExifInterface(source)
        sourceValues = allModeledTags().associateWith(sourceExif::getAttribute)
        val emptyValues = allModeledTags().associateWith(ExifInterface(destination)::getAttribute)

        val destinationExif = ExifInterface(destination)
        ExifTransfer.copyAttributes(sourceExif, destinationExif, policy)
        destinationExif.saveAttributes()

        val reopened = ExifInterface(destination)
        val sourceAfter = ExifInterface(source)
        assertEquals(sourceValues, allModeledTags().associateWith(sourceAfter::getAttribute))
        val eligibleTags = when (policy) {
            ShareMetadataPolicy.STRIP_ALL -> emptySet()
            ShareMetadataPolicy.KEEP_SAFE -> ExifTransfer.TECHNICAL_TAGS.toSet()
            ShareMetadataPolicy.PRESERVE -> allModeledTags().toSet()
        }
        allModeledTags().forEach { tag ->
            val expected = sourceValues[tag].takeIf { tag in eligibleTags && it != null } ?: emptyValues[tag]
            assertEquals("Unexpected destination value for $tag", expected, reopened.getAttribute(tag))
        }
    }

    private fun populatedJpeg(name: String): File = emptyJpeg(name).also { file ->
        ExifInterface(file).apply {
            setLatLong(37.4219983, -122.084)
            setAttribute(ExifInterface.TAG_MAKE, "Example Camera")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:07:12 12:34:56")
            setAttribute(ExifInterface.TAG_ARTIST, "Example Author")
            setAttribute(ExifInterface.TAG_SOFTWARE, "Example Software")
            setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, "Example embedded text")
            setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.008")
            saveAttributes()
        }
    }

    private fun emptyJpeg(name: String): File = temporaryFolder.newFile(name).also { file ->
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output -> assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) }
        bitmap.recycle()
    }

    private fun allModeledTags(): List<String> = (
        ExifTransfer.TECHNICAL_TAGS +
            ExifTransfer.TIME_TAGS +
            ExifTransfer.AUTHOR_TAGS +
            ExifTransfer.LOCATION_TAGS +
            ExifTransfer.DEVICE_TAGS +
            ExifTransfer.SOFTWARE_TAGS +
            ExifTransfer.EMBEDDED_TEXT_TAGS
        ).distinct()
}

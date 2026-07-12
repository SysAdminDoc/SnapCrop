package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ExifTransfer {
    internal val TECHNICAL_TAGS = arrayOf(
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_SCENE_TYPE,
    )

    internal val TIME_TAGS = arrayOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    )

    internal val AUTHOR_TAGS = arrayOf(
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
    )

    internal val LOCATION_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_VERSION_ID,
    )

    internal val DEVICE_TAGS = arrayOf(
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
    )

    internal val SOFTWARE_TAGS = arrayOf(ExifInterface.TAG_SOFTWARE)

    internal val EMBEDDED_TEXT_TAGS = arrayOf(
        ExifInterface.TAG_XMP,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
    )

    private val LEGACY_SAFE_TAGS = TECHNICAL_TAGS + TIME_TAGS + AUTHOR_TAGS + SOFTWARE_TAGS
    private val LEGACY_PRIVACY_TAGS = LOCATION_TAGS + DEVICE_TAGS
    private val ALL_SHARE_TAGS = TECHNICAL_TAGS + TIME_TAGS + AUTHOR_TAGS + LOCATION_TAGS + DEVICE_TAGS + SOFTWARE_TAGS + EMBEDDED_TEXT_TAGS

    fun copyExif(resolver: ContentResolver, sourceUri: Uri, destUri: Uri, strip: Boolean) {
        try {
            val srcExif = resolver.openFileDescriptor(sourceUri, "r")?.use { fd ->
                ExifInterface(fd.fileDescriptor)
            } ?: return

            resolver.openFileDescriptor(destUri, "rw")?.use { fd ->
                val dstExif = ExifInterface(fd.fileDescriptor)
                for (tag in LEGACY_SAFE_TAGS) {
                    srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                }
                if (!strip) {
                    for (tag in LEGACY_PRIVACY_TAGS) {
                        srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                    }
                }
                dstExif.saveAttributes()
            }
        } catch (_: Exception) {}
    }

    fun inspect(resolver: ContentResolver, sourceUri: Uri): MetadataSummary = try {
        resolver.openFileDescriptor(sourceUri, "r")?.use { fd ->
            inspect(ExifInterface(fd.fileDescriptor))
        } ?: MetadataSummary(emptySet(), inspectionFailed = true)
    } catch (_: Exception) {
        MetadataSummary(emptySet(), inspectionFailed = true)
    }

    fun copyForShare(
        resolver: ContentResolver,
        sourceUri: Uri,
        destUri: Uri,
        policy: ShareMetadataPolicy
    ): Boolean = try {
        if (policy == ShareMetadataPolicy.STRIP_ALL) return true
        val source = resolver.openFileDescriptor(sourceUri, "r")?.use { ExifInterface(it.fileDescriptor) }
            ?: return false
        resolver.openFileDescriptor(destUri, "rw")?.use { fd ->
            val destination = ExifInterface(fd.fileDescriptor)
            copyAttributes(source, destination, policy)
            destination.saveAttributes()
        } ?: return false
        true
    } catch (_: Exception) {
        false
    }

    internal fun inspect(exif: ExifInterface): MetadataSummary {
        val categories = buildSet {
            if (hasAny(exif, LOCATION_TAGS)) add(MetadataCategory.LOCATION)
            if (hasAny(exif, DEVICE_TAGS)) add(MetadataCategory.DEVICE)
            if (hasAny(exif, TIME_TAGS)) add(MetadataCategory.TIME)
            if (hasAny(exif, AUTHOR_TAGS)) add(MetadataCategory.AUTHORSHIP)
            if (hasAny(exif, SOFTWARE_TAGS)) add(MetadataCategory.SOFTWARE)
            if (hasAny(exif, EMBEDDED_TEXT_TAGS)) add(MetadataCategory.EMBEDDED_TEXT)
            if (hasAny(exif, TECHNICAL_TAGS)) add(MetadataCategory.TECHNICAL)
        }
        return MetadataSummary(categories)
    }

    internal fun copyAttributes(source: ExifInterface, destination: ExifInterface, policy: ShareMetadataPolicy) {
        val tags = when (policy) {
            ShareMetadataPolicy.STRIP_ALL -> emptyArray()
            ShareMetadataPolicy.KEEP_SAFE -> TECHNICAL_TAGS
            ShareMetadataPolicy.PRESERVE -> ALL_SHARE_TAGS
        }
        tags.forEach { tag -> source.getAttribute(tag)?.let { destination.setAttribute(tag, it) } }
    }

    private fun hasAny(exif: ExifInterface, tags: Array<String>): Boolean =
        tags.any { exif.getAttribute(it) != null }

    fun stripExif(resolver: ContentResolver, uri: Uri) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                val exif = ExifInterface(fd.fileDescriptor)
                for (tag in LEGACY_PRIVACY_TAGS) {
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            }
        } catch (_: Exception) {}
    }
}

enum class ShareMetadataPolicy { STRIP_ALL, KEEP_SAFE, PRESERVE }

enum class MetadataCategory { LOCATION, DEVICE, TIME, AUTHORSHIP, SOFTWARE, EMBEDDED_TEXT, TECHNICAL }

data class MetadataSummary(
    val categories: Set<MetadataCategory>,
    val inspectionFailed: Boolean = false
) {
    val hasMetadata: Boolean get() = categories.isNotEmpty()
}

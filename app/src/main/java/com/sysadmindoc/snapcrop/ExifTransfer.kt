package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

object ExifTransfer {
    private val SAFE_TAGS = arrayOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_SCENE_TYPE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_ARTIST,
    )

    private val PRIVACY_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
    )

    fun copyExif(resolver: ContentResolver, sourceUri: Uri, destUri: Uri, strip: Boolean) {
        try {
            val srcExif = resolver.openFileDescriptor(sourceUri, "r")?.use { fd ->
                ExifInterface(fd.fileDescriptor)
            } ?: return

            resolver.openFileDescriptor(destUri, "rw")?.use { fd ->
                val dstExif = ExifInterface(fd.fileDescriptor)
                for (tag in SAFE_TAGS) {
                    srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                }
                if (!strip) {
                    for (tag in PRIVACY_TAGS) {
                        srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                    }
                }
                dstExif.saveAttributes()
            }
        } catch (_: Exception) {}
    }

    fun stripExif(resolver: ContentResolver, uri: Uri) {
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { fd ->
                val exif = ExifInterface(fd.fileDescriptor)
                for (tag in PRIVACY_TAGS) {
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            }
        } catch (_: Exception) {}
    }
}

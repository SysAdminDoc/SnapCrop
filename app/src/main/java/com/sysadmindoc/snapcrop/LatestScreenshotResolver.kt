package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore

internal data class LatestScreenshot(val id: Long, val uri: Uri)

internal object LatestScreenshotResolver {
    fun find(contentResolver: ContentResolver): LatestScreenshot? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        return runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: continue
                    val path = cursor.getString(2).orEmpty()
                    if (!isScreenshotCandidate(name, path)) continue

                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    val readable = runCatching {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeStream(stream, null, options)
                            options.outWidth > 0 && options.outHeight > 0
                        } == true
                    }.getOrDefault(false)
                    if (readable) return@use LatestScreenshot(id, uri)
                }
                null
            }
        }.getOrNull()
    }

    internal fun isScreenshotCandidate(name: String, path: String): Boolean {
        val normalizedName = name.lowercase()
        val normalizedPath = path.lowercase()
        if (normalizedPath.contains("snapcrop") || normalizedName.startsWith("snapcrop_")) return false
        if (normalizedName.startsWith("reddit_") || normalizedName.startsWith("twitter_")) return false
        return normalizedName.contains("screenshot") ||
            normalizedPath.contains("screenshot") ||
            normalizedPath.endsWith("screenshots/") ||
            normalizedPath.contains("/screenshots/")
    }
}

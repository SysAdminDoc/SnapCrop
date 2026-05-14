package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

object CropSourceHints {
    fun fromIntent(intent: Intent, uri: Uri): List<String> {
        return listOfNotNull(
            uri.toString(),
            uri.authority,
            uri.path,
            intent.getStringExtra("android.intent.extra.PACKAGE_NAME"),
            intent.getStringExtra("android.intent.extra.REFERRER_NAME")
        ).filter { it.isNotBlank() }
    }

    fun fromMedia(resolver: ContentResolver, uri: Uri): List<String> {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME
        )
        return try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use emptyList()
                projection.mapNotNull { column ->
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0) cursor.getString(index) else null
                }.filter { !it.isNullOrBlank() }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun normalize(hints: List<String>): List<String> {
        return hints.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}

package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings

/** Moves replaced source screenshots into a visible, recoverable archive album. */
internal object SourceArchiveManager {
    val ARCHIVE_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/trashed/"

    fun hasPromptFreeAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    fun permissionIntent(): Intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)

    fun moveToArchive(resolver: ContentResolver, uri: Uri): Boolean =
        resolver.update(uri, archiveValues(), null, null) == 1

    internal fun archiveValues(): ContentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.RELATIVE_PATH, ARCHIVE_RELATIVE_PATH)
    }
}

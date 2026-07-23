package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings

/**
 * Media-management access helpers for source removal. When MANAGE_MEDIA is granted the
 * MediaStore trash request used by Save &amp; Replace runs without a per-photo system
 * prompt; otherwise the platform shows its own confirmation.
 */
internal object SourceArchiveManager {
    fun hasPromptFreeAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    fun permissionIntent(): Intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
}

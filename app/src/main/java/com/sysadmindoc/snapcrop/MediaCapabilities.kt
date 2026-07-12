package com.sysadmindoc.snapcrop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess { NONE, SELECTED, FULL }

data class MediaCapabilities(
    val imageAccess: MediaAccess,
    val videoAccess: MediaAccess,
    val notificationAccess: Boolean
) {
    val canMonitorScreenshots: Boolean get() = imageAccess == MediaAccess.FULL
    val canQueryImages: Boolean get() = imageAccess != MediaAccess.NONE
    val canQueryVideos: Boolean get() = videoAccess != MediaAccess.NONE
}

object MediaCapabilityResolver {
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun current(context: Context): MediaCapabilities {
        val grants = buildSet {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS,
                READ_MEDIA_VISUAL_USER_SELECTED
            ).forEach { permission ->
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    add(permission)
                }
            }
        }
        return evaluate(Build.VERSION.SDK_INT, grants)
    }

    fun evaluate(apiLevel: Int, granted: Set<String>): MediaCapabilities {
        if (apiLevel < 33) {
            val media = Manifest.permission.READ_EXTERNAL_STORAGE in granted
            return MediaCapabilities(
                imageAccess = if (media) MediaAccess.FULL else MediaAccess.NONE,
                videoAccess = if (media) MediaAccess.FULL else MediaAccess.NONE,
                notificationAccess = true
            )
        }
        val selectedAccess = apiLevel >= 34 && READ_MEDIA_VISUAL_USER_SELECTED in granted
        val imageAccess = when {
            Manifest.permission.READ_MEDIA_IMAGES in granted -> MediaAccess.FULL
            selectedAccess -> MediaAccess.SELECTED
            else -> MediaAccess.NONE
        }
        val videoAccess = when {
            Manifest.permission.READ_MEDIA_VIDEO in granted -> MediaAccess.FULL
            selectedAccess -> MediaAccess.SELECTED
            else -> MediaAccess.NONE
        }
        return MediaCapabilities(
            imageAccess = imageAccess,
            videoAccess = videoAccess,
            notificationAccess = Manifest.permission.POST_NOTIFICATIONS in granted
        )
    }

    fun imageRequest(apiLevel: Int): Array<String> = when {
        apiLevel >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        apiLevel >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun videoRequest(apiLevel: Int): Array<String> = when {
        apiLevel >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        apiLevel >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun notificationRequest(apiLevel: Int): Array<String> =
        if (apiLevel >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
}

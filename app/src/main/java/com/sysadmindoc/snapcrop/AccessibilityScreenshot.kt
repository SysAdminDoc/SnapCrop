package com.sysadmindoc.snapcrop

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal enum class AccessibilityScreenshotFailure {
    WINDOW_UNAVAILABLE,
    WINDOW_LOST,
    SECURE_WINDOW,
    INVALID_WINDOW,
    THROTTLED,
    ACCESS_REVOKED,
    INVALID_DISPLAY,
    INTERNAL
}

internal sealed interface AccessibilityScreenshotResult {
    data class Success(
        val bitmap: Bitmap,
        val windowScoped: Boolean,
        val windowBounds: AccessibilityWindowBounds?
    ) : AccessibilityScreenshotResult
    data class Failure(val reason: AccessibilityScreenshotFailure) : AccessibilityScreenshotResult
}

internal data class AccessibilityWindowCandidate(
    val id: Int,
    val type: Int,
    val active: Boolean,
    val focused: Boolean,
    val layer: Int
)

internal data class AccessibilityWindowBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal data class AccessibilityWindowTarget(val id: Int, val bounds: AccessibilityWindowBounds)

internal object AccessibilityScreenshotPolicy {
    fun selectActiveWindowId(candidates: List<AccessibilityWindowCandidate>): Int? {
        val ordered = candidates.sortedByDescending(AccessibilityWindowCandidate::layer)
        return ordered.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.active }?.id
    }

    fun failureFromErrorCode(errorCode: Int): AccessibilityScreenshotFailure = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> AccessibilityScreenshotFailure.THROTTLED
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> AccessibilityScreenshotFailure.ACCESS_REVOKED
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> AccessibilityScreenshotFailure.INVALID_DISPLAY
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> AccessibilityScreenshotFailure.INVALID_WINDOW
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> AccessibilityScreenshotFailure.SECURE_WINDOW
        else -> AccessibilityScreenshotFailure.INTERNAL
    }

    fun shouldCropSystemInsets(windowScoped: Boolean): Boolean = !windowScoped

    fun tapFractions(
        clickX: Int,
        clickY: Int,
        bitmapWidth: Int,
        bitmapHeight: Int,
        windowBounds: AccessibilityWindowBounds?
    ): Pair<Float, Float> {
        if (clickX < 0 || clickY < 0) return Float.NaN to Float.NaN
        val x = if (windowBounds != null && windowBounds.width > 0) {
            (clickX - windowBounds.left).toFloat() / windowBounds.width
        } else {
            clickX.toFloat() / bitmapWidth.coerceAtLeast(1)
        }
        val y = if (windowBounds != null && windowBounds.height > 0) {
            (clickY - windowBounds.top).toFloat() / windowBounds.height
        } else {
            clickY.toFloat() / bitmapHeight.coerceAtLeast(1)
        }
        return x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
    }
}

internal fun AccessibilityService.currentActiveWindowTarget(): AccessibilityWindowTarget? {
    if (Build.VERSION.SDK_INT < 34) return null
    val windowSnapshot = windows
    val candidates = windowSnapshot.map { window ->
        AccessibilityWindowCandidate(window.id, window.type, window.isActive, window.isFocused, window.layer)
    }
    val id = AccessibilityScreenshotPolicy.selectActiveWindowId(candidates) ?: return null
    val window = windowSnapshot.firstOrNull { it.id == id } ?: return null
    val bounds = Rect().also(window::getBoundsInScreen)
    return AccessibilityWindowTarget(
        id,
        AccessibilityWindowBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    )
}

@RequiresApi(Build.VERSION_CODES.R)
internal suspend fun AccessibilityService.captureAccessibilityScreenshot(
    preferredWindowId: Int? = null
): AccessibilityScreenshotResult = suspendCancellableCoroutine { continuation ->
    val windowScoped = Build.VERSION.SDK_INT >= 34
    var captureBounds: AccessibilityWindowBounds? = null
    val callback = object : AccessibilityService.TakeScreenshotCallback {
        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
            val bitmap = try {
                val hardwareBuffer = screenshot.hardwareBuffer
                try {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                    val copy = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap?.recycle()
                    copy
                } finally {
                    hardwareBuffer.close()
                }
            } catch (_: Exception) {
                null
            }
            val result = bitmap?.let { AccessibilityScreenshotResult.Success(it, windowScoped, captureBounds) }
                ?: AccessibilityScreenshotResult.Failure(AccessibilityScreenshotFailure.INTERNAL)
            if (continuation.isActive) continuation.resume(result)
            else bitmap?.recycle()
        }

        override fun onFailure(errorCode: Int) {
            if (continuation.isActive) {
                continuation.resume(
                    AccessibilityScreenshotResult.Failure(
                        AccessibilityScreenshotPolicy.failureFromErrorCode(errorCode)
                    )
                )
            }
        }
    }

    if (windowScoped) {
        val target = currentActiveWindowTarget()
        if (target == null) {
            continuation.resume(AccessibilityScreenshotResult.Failure(AccessibilityScreenshotFailure.WINDOW_UNAVAILABLE))
        } else if (preferredWindowId != null && preferredWindowId >= 0 && preferredWindowId != target.id) {
            continuation.resume(AccessibilityScreenshotResult.Failure(AccessibilityScreenshotFailure.WINDOW_LOST))
        } else {
            captureBounds = target.bounds
            takeScreenshotOfWindow(target.id, mainExecutor, callback)
        }
    } else {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, callback)
    }
}

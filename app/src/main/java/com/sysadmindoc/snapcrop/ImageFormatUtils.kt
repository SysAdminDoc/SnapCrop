package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Gainmap
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

internal fun Bitmap.CompressFormat.isWebpFormat(): Boolean =
    name.startsWith("WEBP")

internal fun Bitmap.hasUltraHdrGainmap(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasGainmap()

/**
 * Android 16 (API 36) is the first release whose PNG codec encodes HDR gain maps (the `gmAP`
 * chunk, ISO 21496-1). Before it, only JPEG carries a gain map, so HDR content must fall back
 * to JPEG to stay HDR.
 */
internal fun supportsPngGainmapEncoding(): Boolean = Build.VERSION.SDK_INT >= 36

internal data class ResolvedExportFormat(
    val format: Bitmap.CompressFormat,
    val quality: Int,
    val ext: String,
    val mime: String,
)

/**
 * Chooses an export encoder. When [ultraHdr] is set the result must preserve the gain map:
 * a PNG preference (or a forced-PNG shape crop) stays PNG only where [pngGainmapSupported]
 * (Android 16+); otherwise HDR falls back to JPEG. Non-HDR output honors the user's format,
 * or forced PNG for alpha-bearing shape crops.
 */
internal fun resolveExportFormat(
    userFormat: ExportImageFormat,
    quality: Int,
    forcePng: Boolean,
    ultraHdr: Boolean,
    pngGainmapSupported: Boolean = supportsPngGainmapEncoding(),
): ResolvedExportFormat {
    val png = ResolvedExportFormat(Bitmap.CompressFormat.PNG, 100, "png", "image/png")
    if (ultraHdr) {
        val wantsPng = forcePng || userFormat == ExportImageFormat.PNG
        return if (wantsPng && pngGainmapSupported) png
        else ResolvedExportFormat(Bitmap.CompressFormat.JPEG, quality.coerceAtLeast(90), "jpg", "image/jpeg")
    }
    if (forcePng) return png
    return when (userFormat) {
        ExportImageFormat.WEBP -> {
            @Suppress("DEPRECATION")
            val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                      else Bitmap.CompressFormat.WEBP
            ResolvedExportFormat(fmt, quality, "webp", "image/webp")
        }
        ExportImageFormat.JPEG -> ResolvedExportFormat(Bitmap.CompressFormat.JPEG, quality, "jpg", "image/jpeg")
        ExportImageFormat.PNG -> png
    }
}

/**
 * Reattaches the source gain map after Canvas/pixel edits. [baseTransform] maps source-image
 * coordinates into [target]; gain-map coordinates are scaled independently because the gain map
 * is commonly lower resolution than the SDR base image.
 */
internal fun preserveUltraHdrGainmap(
    source: Bitmap,
    target: Bitmap,
    baseTransform: Matrix = Matrix()
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        !source.hasGainmap() || target.hasGainmap()) return
    UltraHdrApi34.attach(source, target, baseTransform)
}

/** Reattaches an Ultra HDR gain map after a non-affine base-image transform. */
internal fun preserveUltraHdrGainmap(
    source: Bitmap,
    target: Bitmap,
    transformContents: (Bitmap) -> Bitmap,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        !source.hasGainmap() || target.hasGainmap()) return
    UltraHdrApi34.attachTransformed(source, target, transformContents)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object UltraHdrApi34 {
    fun attach(source: Bitmap, target: Bitmap, baseTransform: Matrix) {
        val sourceGainmap = source.gainmap ?: return
        if (source.width == target.width && source.height == target.height && baseTransform.isIdentity) {
            target.gainmap = sourceGainmap
            return
        }

        val contents = sourceGainmap.gainmapContents
        val targetGainWidth = (target.width * contents.width.toFloat() / source.width)
            .roundToInt().coerceAtLeast(1)
        val targetGainHeight = (target.height * contents.height.toFloat() / source.height)
            .roundToInt().coerceAtLeast(1)
        val transformedContents = Bitmap.createBitmap(
            targetGainWidth,
            targetGainHeight,
            Bitmap.Config.ARGB_8888
        )
        val gainTransform = Matrix().apply {
            setScale(source.width.toFloat() / contents.width, source.height.toFloat() / contents.height)
            postConcat(baseTransform)
            postScale(targetGainWidth.toFloat() / target.width, targetGainHeight.toFloat() / target.height)
        }
        Canvas(transformedContents).apply {
            drawColor(Color.GRAY)
            drawBitmap(contents, gainTransform, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        target.gainmap = cloneMetadata(sourceGainmap, transformedContents)
    }

    fun attachTransformed(source: Bitmap, target: Bitmap, transformContents: (Bitmap) -> Bitmap) {
        val sourceGainmap = source.gainmap ?: return
        val transformedContents = transformContents(sourceGainmap.gainmapContents)
        target.gainmap = cloneMetadata(sourceGainmap, transformedContents)
    }

    private fun cloneMetadata(source: Gainmap, contents: Bitmap): Gainmap = Gainmap(contents).apply {
        source.ratioMin.let { setRatioMin(it[0], it[1], it[2]) }
        source.ratioMax.let { setRatioMax(it[0], it[1], it[2]) }
        source.gamma.let { setGamma(it[0], it[1], it[2]) }
        source.epsilonSdr.let { setEpsilonSdr(it[0], it[1], it[2]) }
        source.epsilonHdr.let { setEpsilonHdr(it[0], it[1], it[2]) }
        minDisplayRatioForHdrTransition = source.minDisplayRatioForHdrTransition
        displayRatioForFullHdr = source.displayRatioForFullHdr
        if (Build.VERSION.SDK_INT >= 36) {
            gainmapDirection = source.gainmapDirection
            alternativeImagePrimaries = source.alternativeImagePrimaries
        }
    }
}

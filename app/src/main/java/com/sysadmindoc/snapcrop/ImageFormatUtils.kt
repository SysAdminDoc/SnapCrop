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

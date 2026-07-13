package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

internal enum class SimilarityCorpusLabel { POSITIVE, NEGATIVE, DIAGNOSTIC }

internal enum class SimilarityCorpusCategory(val label: SimilarityCorpusLabel) {
    JPEG_RECOMPRESS(SimilarityCorpusLabel.POSITIVE),
    UNIFORM_RESIZE(SimilarityCorpusLabel.POSITIVE),
    SLIGHT_CROP(SimilarityCorpusLabel.POSITIVE),
    COLOR_SHIFT(SimilarityCorpusLabel.POSITIVE),
    ONE_MESSAGE_CHANGE(SimilarityCorpusLabel.NEGATIVE),
    STICKY_HEADER_CHANGE(SimilarityCorpusLabel.NEGATIVE),
    UNRELATED_SAME_LAYOUT(SimilarityCorpusLabel.NEGATIVE),
    THEME_SHIFT(SimilarityCorpusLabel.DIAGNOSTIC),
}

internal data class SimilarityCorpusPair(
    val id: String,
    val seed: Int,
    val category: SimilarityCorpusCategory,
    val before: Bitmap,
    val after: Bitmap,
) {
    val label: SimilarityCorpusLabel get() = category.label
    val calibration: Boolean get() = seed < ScreenshotSimilarityCorpus.CALIBRATION_SEEDS
}

internal object ScreenshotSimilarityCorpus {
    const val SEED_COUNT = 12
    const val CALIBRATION_SEEDS = SEED_COUNT / 2
    const val WIDTH = 360
    const val HEIGHT = 800
    const val PAIR_COUNT = SEED_COUNT * 8

    fun forEachPair(block: (SimilarityCorpusPair) -> Unit) {
        repeat(SEED_COUNT) { seed ->
            val dark = seed % 2 != 0
            val before = render(seed, dark)
            try {
                SimilarityCorpusCategory.entries.forEach { category ->
                    val after = when (category) {
                        SimilarityCorpusCategory.JPEG_RECOMPRESS -> jpegRoundTrip(before, quality = 70)
                        SimilarityCorpusCategory.UNIFORM_RESIZE -> Bitmap.createScaledBitmap(
                            before,
                            (WIDTH * 0.75f).roundToInt(),
                            (HEIGHT * 0.75f).roundToInt(),
                            true,
                        )
                        SimilarityCorpusCategory.SLIGHT_CROP -> Bitmap.createBitmap(
                            before,
                            2,
                            4,
                            before.width - 4,
                            before.height - 8,
                        )
                        SimilarityCorpusCategory.COLOR_SHIFT -> colorShift(before)
                        SimilarityCorpusCategory.ONE_MESSAGE_CHANGE -> render(seed, dark, changedMessage = true)
                        SimilarityCorpusCategory.STICKY_HEADER_CHANGE -> render(seed, dark, changedHeader = true)
                        SimilarityCorpusCategory.UNRELATED_SAME_LAYOUT -> render(seed + 101, dark)
                        SimilarityCorpusCategory.THEME_SHIFT -> render(seed, !dark)
                    }
                    try {
                        block(
                            SimilarityCorpusPair(
                                id = "seed-${seed.toString().padStart(2, '0')}-${category.name.lowercase()}",
                                seed = seed,
                                category = category,
                                before = before,
                                after = after,
                            )
                        )
                    } finally {
                        after.recycle()
                    }
                }
            } finally {
                before.recycle()
            }
        }
    }

    private fun render(
        seed: Int,
        dark: Boolean,
        changedMessage: Boolean = false,
        changedHeader: Boolean = false,
    ): Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val background = if (dark) Color.rgb(16, 17, 22) else Color.rgb(246, 247, 250)
        val foreground = if (dark) Color.rgb(237, 239, 246) else Color.rgb(28, 31, 40)
        val surface = if (dark) Color.rgb(37, 39, 49) else Color.WHITE
        val subtle = if (dark) Color.rgb(119, 124, 142) else Color.rgb(103, 109, 125)
        val accent = palette(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(background)

        paint.color = if (dark) Color.rgb(8, 9, 12) else Color.rgb(232, 234, 240)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 28f, paint)
        paint.color = if (changedHeader) palette(seed + 47) else accent
        canvas.drawRect(0f, 28f, WIDTH.toFloat(), 108f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(38f, 68f, 20f, paint)
        paint.color = if (changedHeader) Color.rgb(24, 24, 28) else Color.WHITE
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText(if (changedHeader) "Archive ${seed + 9}" else "Thread ${seed + 1}", 72f, 69f, paint)
        paint.textSize = 13f
        paint.isFakeBoldText = false
        canvas.drawText(if (changedHeader) "3 new updates" else "Synthetic screenshot", 72f, 91f, paint)

        repeat(6) { row ->
            val top = 126f + row * 101f
            paint.color = surface
            canvas.drawRoundRect(18f, top, 342f, top + 84f, 16f, 16f, paint)
            val rowSeed = seed * 17 + row * 23
            paint.color = if (changedMessage && row == 2) palette(rowSeed + 89) else palette(rowSeed)
            canvas.drawCircle(48f, top + 28f, 13f, paint)
            paint.color = foreground
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText("Contact ${(rowSeed % 29) + 1}", 72f, top + 27f, paint)
            paint.isFakeBoldText = false
            paint.color = if (changedMessage && row == 2) foreground else subtle
            val firstLine = if (changedMessage && row == 2) 235f else 172f + (rowSeed % 74)
            canvas.drawRoundRect(72f, top + 39f, firstLine, top + 46f, 4f, 4f, paint)
            val secondLine = if (changedMessage && row == 2) 296f else 145f + (rowSeed % 112)
            canvas.drawRoundRect(72f, top + 57f, secondLine, top + 64f, 4f, 4f, paint)
            if (changedMessage && row == 2) {
                paint.color = Color.rgb(255, 183, 77)
                canvas.drawCircle(316f, top + 62f, 8f, paint)
            }
        }

        paint.color = if (dark) Color.rgb(24, 25, 31) else Color.rgb(226, 229, 236)
        canvas.drawRect(0f, 742f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        repeat(4) { index ->
            paint.color = if (index == seed % 4) accent else subtle
            canvas.drawCircle(54f + index * 84f, 770f, if (index == seed % 4) 9f else 7f, paint)
        }
    }

    private fun palette(seed: Int): Int {
        val colors = intArrayOf(
            Color.rgb(137, 180, 250),
            Color.rgb(166, 227, 161),
            Color.rgb(245, 194, 231),
            Color.rgb(250, 179, 135),
            Color.rgb(203, 166, 247),
            Color.rgb(148, 226, 213),
        )
        return colors[Math.floorMod(seed, colors.size)]
    }

    private fun jpegRoundTrip(source: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.JPEG, quality, output))
            output.toByteArray()
        }
        return checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun colorShift(source: Bitmap): Bitmap {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            pixels[index] = Color.argb(
                Color.alpha(color),
                (Color.red(color) + 10).coerceAtMost(255),
                (Color.green(color) + 3).coerceAtMost(255),
                (Color.blue(color) - 5).coerceAtLeast(0),
            )
        }
        return Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
    }
}

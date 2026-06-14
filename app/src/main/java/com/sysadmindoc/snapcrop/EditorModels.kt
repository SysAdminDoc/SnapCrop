package com.sysadmindoc.snapcrop

import android.content.SharedPreferences
import android.graphics.PointF
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class DrawPath(
    val points: List<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val isArrow: Boolean = false,
    val shapeType: String? = null,
    val text: String? = null,
    val filled: Boolean = false,
    val dashed: Boolean = false,
    val visible: Boolean = true,
    val controlPoint: PointF? = null,
    // Post-commit layer transform (move/resize/rotate), pivoted on the layer centroid.
    // Identity (offset 0, scale 1, rotation 0) means no transform is applied.
    val transOffsetX: Float = 0f,
    val transOffsetY: Float = 0f,
    val transScale: Float = 1f,
    val transRotation: Float = 0f
) {
    val hasTransform: Boolean
        get() = transOffsetX != 0f || transOffsetY != 0f || transScale != 1f || transRotation != 0f

    /** Centroid of the layer's points, used as the transform pivot. */
    fun centroid(): PointF {
        if (points.isEmpty()) return PointF(0f, 0f)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return PointF((minX + maxX) / 2f, (minY + maxY) / 2f)
    }

    /** Android matrix for this layer's transform, or null when identity. */
    fun transformMatrix(): android.graphics.Matrix? {
        if (!hasTransform) return null
        val c = centroid()
        return android.graphics.Matrix().apply {
            postScale(transScale, transScale, c.x, c.y)
            postRotate(transRotation, c.x, c.y)
            postTranslate(transOffsetX, transOffsetY)
        }
    }
}

internal enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER
}

/** Snapshot of the editor's restorable state, pulled by the host activity to checkpoint a draft
 *  across process death. */
data class EditorDraft(
    val crop: Rect,
    val pix: List<Rect>,
    val draws: List<DrawPath>,
    val adj: FloatArray
)

internal enum class EditMode { CROP, PIXELATE, DRAW, OCR, ADJUST }

internal enum class DrawTool(val label: String) {
    PEN("Pen"), ARROW("Arrow"), CURVED_ARROW("Curve"), LINE("Line"), MEASURE("Ruler"), RECT("Rect"), CIRCLE("Circle"), TEXT("Text"),
    HIGHLIGHT("Mark"), CALLOUT("#"), SPOTLIGHT("Focus"), MAGNIFIER("Zoom"), EMOJI("Emoji"),
    NEON("Neon"), BLUR("Blur"), ERASER("Erase"), FILL("Fill"), HEAL("Smart")
}

internal val commonEmojis = listOf(
    "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD14", "\uD83D\uDE31",
    "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDD25", "\u2764\uFE0F", "\u2B50",
    "\u2705", "\u274C", "\u26A0\uFE0F", "\uD83D\uDCA1", "\uD83D\uDCCC",
    "\uD83D\uDCF7", "\uD83C\uDFAF", "\uD83D\uDE80", "\uD83D\uDC40", "\uD83C\uDF89"
)

internal val drawColors = listOf(
    0xFFFF0000.toInt() to "Red",
    0xFFFFFF00.toInt() to "Yellow",
    0xFF00FF00.toInt() to "Green",
    0xFF89B4FA.toInt() to "Blue",
    0xFFFFFFFF.toInt() to "White",
    0xFF000000.toInt() to "Black"
)

internal data class DrawStylePreset(
    val name: String,
    val color: Int,
    val strokeWidth: Float,
    val dashed: Boolean,
    val tool: DrawTool = DrawTool.PEN
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("color", color)
        put("strokeWidth", strokeWidth.toDouble())
        put("dashed", dashed)
        put("tool", tool.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): DrawStylePreset = DrawStylePreset(
            name = obj.optString("name", ""),
            color = obj.optInt("color", 0xFFFF0000.toInt()),
            strokeWidth = obj.optDouble("strokeWidth", 6.0).toFloat(),
            dashed = obj.optBoolean("dashed", false),
            tool = try { DrawTool.valueOf(obj.optString("tool", "PEN")) } catch (_: Exception) { DrawTool.PEN }
        )
    }
}

internal object DrawStylePresetStore {
    private const val KEY = "draw_style_presets"
    private const val KEY_DEFAULT = "draw_style_default"

    fun load(prefs: SharedPreferences): List<DrawStylePreset> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { DrawStylePreset.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    fun save(prefs: SharedPreferences, presets: List<DrawStylePreset>) {
        val arr = JSONArray().apply { presets.forEach { put(it.toJson()) } }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun defaultName(prefs: SharedPreferences): String? = prefs.getString(KEY_DEFAULT, null)

    fun setDefault(prefs: SharedPreferences, name: String?) {
        if (name == null) prefs.edit().remove(KEY_DEFAULT).apply()
        else prefs.edit().putString(KEY_DEFAULT, name).apply()
    }
}

internal fun smoothPath(points: List<PointF>): List<PointF> {
    if (points.size < 4) return points
    val reduced = mutableListOf(points.first())
    for (i in 1 until points.size) {
        val prev = reduced.last()
        val cur = points[i]
        val dist = kotlin.math.sqrt(((cur.x - prev.x) * (cur.x - prev.x) + (cur.y - prev.y) * (cur.y - prev.y)).toDouble())
        if (dist > 2.0) reduced.add(cur)
    }
    if (reduced.size < 4) return reduced

    val smooth = mutableListOf<PointF>()
    for (i in 0 until reduced.size - 1) {
        val p0 = reduced[(i - 1).coerceAtLeast(0)]
        val p1 = reduced[i]
        val p2 = reduced[(i + 1).coerceAtMost(reduced.size - 1)]
        val p3 = reduced[(i + 2).coerceAtMost(reduced.size - 1)]
        val steps = 4
        for (s in 0 until steps) {
            val t = s.toFloat() / steps
            val t2 = t * t
            val t3 = t2 * t
            val x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)
            val y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)
            smooth.add(PointF(x, y))
        }
    }
    smooth.add(reduced.last())
    return smooth
}

internal enum class AspectRatio(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_5_4("5:4", 5f / 4f),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_2_1("2:1", 2f / 1f),
    RATIO_3_1("3:1", 3f / 1f),
    RATIO_21_9("21:9", 21f / 9f),
    CIRCLE("Circle", 1f),
    ROUNDED("Rounded", null),
    STAR("Star", 1f),
    HEART("Heart", 1f),
    TRIANGLE("Triangle", 1f),
    HEXAGON("Hexagon", 1f),
    DIAMOND("Diamond", 1f)
}

internal fun aspectRatioForShapeCrop(value: Float): AspectRatio = when (value.roundToInt()) {
    1 -> AspectRatio.CIRCLE
    2 -> AspectRatio.ROUNDED
    3 -> AspectRatio.STAR
    4 -> AspectRatio.HEART
    5 -> AspectRatio.TRIANGLE
    6 -> AspectRatio.HEXAGON
    7 -> AspectRatio.DIAMOND
    else -> AspectRatio.FREE
}

internal fun filterFromOrdinal(value: Float): ImageFilter =
    ImageFilter.entries.getOrElse(value.roundToInt()) { ImageFilter.NONE }

internal fun FloatArray?.adjustValue(index: Int, default: Float): Float =
    this?.getOrNull(index) ?: default

internal data class EditorSnapshot(
    val crop: Rect,
    val bright: Float,
    val contr: Float,
    val sat: Float,
    val warm: Float,
    val vig: Float,
    val sharp: Float,
    val rotAngle: Float,
    val hi: Float,
    val sh: Float,
    val tilt: Float,
    val dn: Float,
    val gradBg: Int,
    val filter: ImageFilter,
    val pixRects: List<Rect>,
    val draws: List<DrawPath>,
    val cR: Float = 0f,
    val cG: Float = 0f,
    val cB: Float = 0f,
    val perspectiveQuad: List<PointF>? = null
)

internal enum class ImageFilter(val label: String) {
    NONE("None"), MONO("Mono"), SEPIA("Sepia"), COOL("Cool"), WARM("Warm"),
    VIVID("Vivid"), MUTED("Muted"), VINTAGE("Vintage"), NOIR("Noir"), FADE("Fade"),
    INVERT("Invert"), POLAROID("Polaroid"), GRAIN("Grain"),
    RED_POP("Red"), BLUE_POP("Blue"), GREEN_POP("Green"),
    GLITCH("Glitch")
}

internal fun getFilterMatrix(filter: ImageFilter): android.graphics.ColorMatrix? {
    return when (filter) {
        ImageFilter.NONE -> null
        ImageFilter.MONO -> android.graphics.ColorMatrix().apply { setSaturation(0f) }
        ImageFilter.SEPIA -> android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 40f,
                0f, 1f, 0f, 0f, 20f,
                0f, 0f, 1f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.COOL -> android.graphics.ColorMatrix(floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.WARM -> android.graphics.ColorMatrix(floatArrayOf(
            1.1f, 0f, 0f, 0f, 15f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.VIVID -> android.graphics.ColorMatrix().apply {
            setSaturation(1.5f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 10f,
                0f, 1.1f, 0f, 0f, 10f,
                0f, 0f, 1.1f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.MUTED -> android.graphics.ColorMatrix().apply {
            setSaturation(0.4f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 15f,
                0f, 1f, 0f, 0f, 15f,
                0f, 0f, 1f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.VINTAGE -> android.graphics.ColorMatrix().apply {
            setSaturation(0.5f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.05f, 0.05f, 0f, 0f, 20f,
                0f, 1f, 0.05f, 0f, 10f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.NOIR -> android.graphics.ColorMatrix().apply {
            setSaturation(0f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.4f, 0f, 0f, 0f, -30f,
                0f, 1.4f, 0f, 0f, -30f,
                0f, 0f, 1.4f, 0f, -30f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.FADE -> android.graphics.ColorMatrix().apply {
            setSaturation(0.6f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 30f,
                0f, 0f, 1f, 0f, 30f,
                0f, 0f, 0f, 0.9f, 0f
            )))
        }
        ImageFilter.INVERT -> android.graphics.ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.POLAROID -> android.graphics.ColorMatrix(floatArrayOf(
            1.438f, -0.062f, -0.062f, 0f, 0f,
            -0.122f, 1.378f, -0.122f, 0f, 0f,
            -0.016f, -0.016f, 1.483f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        ImageFilter.GRAIN -> android.graphics.ColorMatrix().apply {
            setSaturation(0.8f)
            postConcat(android.graphics.ColorMatrix(floatArrayOf(
                1.05f, 0.02f, 0f, 0f, 8f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 0.95f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        ImageFilter.RED_POP, ImageFilter.BLUE_POP, ImageFilter.GREEN_POP, ImageFilter.GLITCH -> null
    }
}

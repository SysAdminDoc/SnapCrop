package com.sysadmindoc.snapcrop

import android.graphics.PointF
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

data class SnapCropProject(
    val sourceUri: String?,
    val sourceSha256: String?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropRect: Rect,
    val adjustments: FloatArray,
    val pixelateRects: List<Rect>,
    val drawLayers: List<DrawPath>,
    val exportFormat: String?,
    val exportMimeType: String?,
    val exportQuality: Int,
    val exportSavePath: String?,
    val deleteOriginal: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SnapCropProject) return false
        return sourceUri == other.sourceUri &&
                sourceSha256 == other.sourceSha256 &&
                sourceWidth == other.sourceWidth &&
                sourceHeight == other.sourceHeight &&
                cropRect == other.cropRect &&
                adjustments.contentEquals(other.adjustments) &&
                pixelateRects == other.pixelateRects &&
                drawLayers == other.drawLayers &&
                exportFormat == other.exportFormat &&
                exportMimeType == other.exportMimeType &&
                exportQuality == other.exportQuality &&
                exportSavePath == other.exportSavePath &&
                deleteOriginal == other.deleteOriginal
    }

    override fun hashCode(): Int {
        var result = sourceUri?.hashCode() ?: 0
        result = 31 * result + (sourceSha256?.hashCode() ?: 0)
        result = 31 * result + sourceWidth
        result = 31 * result + sourceHeight
        result = 31 * result + cropRect.hashCode()
        result = 31 * result + adjustments.contentHashCode()
        result = 31 * result + pixelateRects.hashCode()
        result = 31 * result + drawLayers.hashCode()
        result = 31 * result + (exportFormat?.hashCode() ?: 0)
        result = 31 * result + (exportMimeType?.hashCode() ?: 0)
        result = 31 * result + exportQuality
        result = 31 * result + (exportSavePath?.hashCode() ?: 0)
        result = 31 * result + deleteOriginal.hashCode()
        return result
    }
}

object SnapCropProjectSidecar {
    const val MIME_TYPE = "application/vnd.snapcrop.project+json"
    private const val SCHEMA = "com.sysadmindoc.snapcrop.project"
    private const val VERSION = 1
    private const val ADJUSTMENT_COUNT = 17

    fun looksLikeProject(mimeType: String?, displayName: String?): Boolean {
        val lowerName = displayName?.lowercase().orEmpty()
        val lowerMime = mimeType?.lowercase().orEmpty()
        return lowerMime == MIME_TYPE ||
                lowerMime == "application/json" ||
                lowerMime == "text/json" ||
                lowerName.endsWith(".snapcrop.json")
    }

    fun encode(project: SnapCropProject): String {
        return JSONObject()
            .put("schema", SCHEMA)
            .put("version", VERSION)
            .put("source", JSONObject()
                .putNullable("uri", project.sourceUri)
                .putNullable("sha256", project.sourceSha256)
                .put("width", project.sourceWidth)
                .put("height", project.sourceHeight)
            )
            .put("crop", project.cropRect.toJson())
            .put("adjustments", project.adjustments.toAdjustmentsJson())
            .put("pixelateRects", JSONArray().apply {
                project.pixelateRects.forEach { put(it.toJson()) }
            })
            .put("drawLayers", JSONArray().apply {
                project.drawLayers.forEach { put(it.toJson()) }
            })
            .put("export", JSONObject()
                .putNullable("format", project.exportFormat)
                .putNullable("mimeType", project.exportMimeType)
                .put("quality", project.exportQuality)
                .putNullable("savePath", project.exportSavePath)
                .put("deleteOriginal", project.deleteOriginal)
            )
            .toString(2)
    }

    fun decode(json: String): SnapCropProject {
        val root = JSONObject(json)
        val schema = root.optString("schema", "")
        val version = root.optInt("version", 0)
        require(schema == SCHEMA) { "Unsupported SnapCrop project schema" }
        require(version == VERSION) { "Unsupported SnapCrop project version $version" }

        val source = root.optJSONObject("source") ?: JSONObject()
        val export = root.optJSONObject("export") ?: JSONObject()
        val adjustments = root.optJSONObject("adjustments").toAdjustmentsArray()
        return SnapCropProject(
            sourceUri = source.optNullableString("uri"),
            sourceSha256 = source.optNullableString("sha256"),
            sourceWidth = source.optInt("width", 0),
            sourceHeight = source.optInt("height", 0),
            cropRect = root.getJSONObject("crop").toRect(),
            adjustments = adjustments,
            pixelateRects = root.optJSONArray("pixelateRects").toRectList(),
            drawLayers = root.optJSONArray("drawLayers").toDrawPathList(),
            exportFormat = export.optNullableString("format"),
            exportMimeType = export.optNullableString("mimeType"),
            exportQuality = export.optInt("quality", 100),
            exportSavePath = export.optNullableString("savePath"),
            deleteOriginal = export.optBoolean("deleteOriginal", false)
        )
    }

    private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name)

    private fun Rect.toJson(): JSONObject = JSONObject()
        .put("left", left)
        .put("top", top)
        .put("right", right)
        .put("bottom", bottom)

    private fun JSONObject.toRect(): Rect = Rect(
        optInt("left", 0),
        optInt("top", 0),
        optInt("right", 0),
        optInt("bottom", 0)
    )

    private fun PointF.toJson(): JSONObject = JSONObject()
        .put("x", x.toDouble())
        .put("y", y.toDouble())

    private fun JSONObject.toPointF(): PointF = PointF(
        optDouble("x", 0.0).toFloat(),
        optDouble("y", 0.0).toFloat()
    )

    private fun DrawPath.toJson(): JSONObject = JSONObject()
        .put("color", color)
        .put("strokeWidth", strokeWidth.toDouble())
        .put("isArrow", isArrow)
        .putNullable("shapeType", shapeType)
        .putNullable("text", text)
        .put("filled", filled)
        .put("dashed", dashed)
        .put("visible", visible)
        .put("points", JSONArray().apply {
            points.forEach { put(it.toJson()) }
        })

    private fun JSONObject.toDrawPath(): DrawPath = DrawPath(
        points = optJSONArray("points").toPointList(),
        color = optInt("color", 0xFFFF0000.toInt()),
        strokeWidth = optDouble("strokeWidth", 6.0).toFloat(),
        isArrow = optBoolean("isArrow", false),
        shapeType = optNullableString("shapeType"),
        text = optNullableString("text"),
        filled = optBoolean("filled", false),
        dashed = optBoolean("dashed", false),
        visible = optBoolean("visible", true)
    )

    private fun FloatArray.toAdjustmentsJson(): JSONObject {
        fun value(index: Int, default: Float): Double =
            getOrNull(index)?.toDouble() ?: default.toDouble()
        return JSONObject()
            .put("brightness", value(0, 0f))
            .put("contrast", value(1, 1f))
            .put("saturation", value(2, 1f))
            .put("shapeCrop", value(3, 0f))
            .put("warmth", value(4, 0f))
            .put("vignette", value(5, 0f))
            .put("filterIndex", value(6, 0f))
            .put("sharpen", value(7, 0f))
            .put("rotationAngle", value(8, 0f))
            .put("highlights", value(9, 0f))
            .put("shadows", value(10, 0f))
            .put("tiltShift", value(11, 0f))
            .put("denoise", value(12, 0f))
            .put("gradientBg", value(13, 0f))
            .put("curveR", value(14, 0f))
            .put("curveG", value(15, 0f))
            .put("curveB", value(16, 0f))
    }

    private fun JSONObject?.toAdjustmentsArray(): FloatArray {
        val obj = this ?: JSONObject()
        return FloatArray(ADJUSTMENT_COUNT).also { adj ->
            adj[0] = obj.optDouble("brightness", 0.0).toFloat()
            adj[1] = obj.optDouble("contrast", 1.0).toFloat()
            adj[2] = obj.optDouble("saturation", 1.0).toFloat()
            adj[3] = obj.optDouble("shapeCrop", 0.0).toFloat()
            adj[4] = obj.optDouble("warmth", 0.0).toFloat()
            adj[5] = obj.optDouble("vignette", 0.0).toFloat()
            adj[6] = obj.optDouble("filterIndex", 0.0).toFloat()
            adj[7] = obj.optDouble("sharpen", 0.0).toFloat()
            adj[8] = obj.optDouble("rotationAngle", 0.0).toFloat()
            adj[9] = obj.optDouble("highlights", 0.0).toFloat()
            adj[10] = obj.optDouble("shadows", 0.0).toFloat()
            adj[11] = obj.optDouble("tiltShift", 0.0).toFloat()
            adj[12] = obj.optDouble("denoise", 0.0).toFloat()
            adj[13] = obj.optDouble("gradientBg", 0.0).toFloat()
            adj[14] = obj.optDouble("curveR", 0.0).toFloat()
            adj[15] = obj.optDouble("curveG", 0.0).toFloat()
            adj[16] = obj.optDouble("curveB", 0.0).toFloat()
        }
    }

    private fun JSONArray?.toRectList(): List<Rect> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add((optJSONObject(i) ?: JSONObject()).toRect())
        }
    }

    private fun JSONArray?.toPointList(): List<PointF> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add((optJSONObject(i) ?: JSONObject()).toPointF())
        }
    }

    private fun JSONArray?.toDrawPathList(): List<DrawPath> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add((optJSONObject(i) ?: JSONObject()).toDrawPath())
        }
    }
}

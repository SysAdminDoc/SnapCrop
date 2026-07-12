package com.sysadmindoc.snapcrop

import android.graphics.PointF
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.abs

data class SnapCropProject(
    val sourceUri: String?,
    val sourceSha256: String?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropRect: Rect,
    val adjustments: FloatArray,
    val pixelateRects: List<Rect> = emptyList(),
    val redactions: List<RedactionRegion> = pixelateRects.map { bounds ->
        RedactionRegions.manual(bounds, RedactionStyle.PIXELATE)
    },
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
                redactions == other.redactions &&
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
        result = 31 * result + redactions.hashCode()
        result = 31 * result + drawLayers.hashCode()
        result = 31 * result + (exportFormat?.hashCode() ?: 0)
        result = 31 * result + (exportMimeType?.hashCode() ?: 0)
        result = 31 * result + exportQuality
        result = 31 * result + (exportSavePath?.hashCode() ?: 0)
        result = 31 * result + deleteOriginal.hashCode()
        return result
    }
}

data class ProjectImportLimits(
    val maxJsonBytes: Int = 8 * 1024 * 1024,
    val maxPixelateRects: Int = 512,
    val maxRedactions: Int = 512,
    val maxDrawLayers: Int = 256,
    val maxPointsPerLayer: Int = 16_384,
    val maxTotalPoints: Int = 65_536,
    val maxTextChars: Int = 4_096,
    val maxUriChars: Int = 8_192,
    val maxPathChars: Int = 1_024
)

enum class ProjectImportOrigin { EXTERNAL, INTERNAL_DRAFT }
enum class ProjectRejectReason { TOO_LARGE, MALFORMED, UNSUPPORTED_SCHEMA, UNSUPPORTED_VERSION, MISSING_FINGERPRINT, INVALID_FIELD }

sealed interface ProjectDecodeResult {
    data class Success(val project: SnapCropProject) : ProjectDecodeResult
    data class Rejected(val reason: ProjectRejectReason, val field: String? = null) : ProjectDecodeResult
}

data class SourceIdentity(val sha256: String?, val width: Int, val height: Int)
enum class SourceVerificationPolicy { REQUIRE_FINGERPRINT, ALLOW_MISSING_FINGERPRINT }
enum class SourceMatch { MATCH, HASH_MISMATCH, DIMENSION_MISMATCH, MISSING_FINGERPRINT }

object SnapCropProjectSidecar {
    const val MIME_TYPE = "application/vnd.snapcrop.project+json"
    private const val SCHEMA = "com.sysadmindoc.snapcrop.project"
    private const val VERSION = 2
    private const val ADJUSTMENT_COUNT = 25
    val DEFAULT_LIMITS = ProjectImportLimits()
    private val HASH_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    private val REDACTION_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,80}$")
    private val ALLOWED_SHAPES = setOf(
        "rect", "circle", "text", "highlight", "callout", "spotlight", "magnifier",
        "emoji", "neon", "blur", "line", "measure", "eraser", "fill", "smart_erase", "heal"
    )

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
            .put("redactions", JSONArray().apply {
                project.redactions.forEach { put(it.toJson()) }
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

    fun decode(
        input: InputStream,
        origin: ProjectImportOrigin,
        limits: ProjectImportLimits = DEFAULT_LIMITS
    ): ProjectDecodeResult {
        val bytes = ByteArrayOutputStream(minOf(limits.maxJsonBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limits.maxJsonBytes) return ProjectDecodeResult.Rejected(ProjectRejectReason.TOO_LARGE)
            bytes.write(buffer, 0, read)
        }
        val json = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: Exception) {
            return ProjectDecodeResult.Rejected(ProjectRejectReason.MALFORMED)
        }
        return decodeString(json, origin, limits)
    }

    internal fun decodeString(
        json: String,
        origin: ProjectImportOrigin,
        limits: ProjectImportLimits = DEFAULT_LIMITS
    ): ProjectDecodeResult {
        if (json.toByteArray(Charsets.UTF_8).size > limits.maxJsonBytes) {
            return ProjectDecodeResult.Rejected(ProjectRejectReason.TOO_LARGE)
        }
        return try {
            val project = decodeObject(json)
            validate(project, origin, limits)
        } catch (e: IllegalArgumentException) {
            when {
                e.message == "schema" -> ProjectDecodeResult.Rejected(ProjectRejectReason.UNSUPPORTED_SCHEMA)
                e.message == "version" -> ProjectDecodeResult.Rejected(ProjectRejectReason.UNSUPPORTED_VERSION)
                else -> ProjectDecodeResult.Rejected(ProjectRejectReason.MALFORMED)
            }
        } catch (_: Exception) {
            ProjectDecodeResult.Rejected(ProjectRejectReason.MALFORMED)
        }
    }

    internal fun decode(json: String): SnapCropProject =
        when (val result = decodeString(json, ProjectImportOrigin.INTERNAL_DRAFT)) {
            is ProjectDecodeResult.Success -> result.project
            is ProjectDecodeResult.Rejected -> throw IllegalArgumentException("Rejected project: ${result.reason}")
        }

    private fun decodeObject(json: String): SnapCropProject {
        val root = JSONObject(json)
        val schema = root.optString("schema", "")
        val version = root.optInt("version", 0)
        require(schema == SCHEMA) { "schema" }
        require(version in 1..VERSION) { "version" }

        val source = root.optJSONObject("source") ?: JSONObject()
        val export = root.optJSONObject("export") ?: JSONObject()
        val adjustments = root.optJSONObject("adjustments").toAdjustmentsArray()
        val legacyRects = if (version == 1) root.optJSONArray("pixelateRects").toRectList() else emptyList()
        return SnapCropProject(
            sourceUri = source.optNullableString("uri"),
            sourceSha256 = source.optNullableString("sha256"),
            sourceWidth = source.optInt("width", 0),
            sourceHeight = source.optInt("height", 0),
            cropRect = root.optJSONObject("crop")?.toRect() ?: Rect(0, 0, 0, 0),
            adjustments = adjustments,
            pixelateRects = legacyRects,
            redactions = if (version == 1) {
                legacyRects.map { RedactionRegions.manual(it, RedactionStyle.PIXELATE) }
            } else {
                root.optJSONArray("redactions").toRedactionList()
            },
            drawLayers = root.optJSONArray("drawLayers").toDrawPathList(),
            exportFormat = export.optNullableString("format"),
            exportMimeType = export.optNullableString("mimeType"),
            exportQuality = export.optInt("quality", 100),
            exportSavePath = export.optNullableString("savePath"),
            deleteOriginal = export.optBoolean("deleteOriginal", false)
        )
    }

    fun compareSource(
        project: SnapCropProject,
        actual: SourceIdentity,
        policy: SourceVerificationPolicy
    ): SourceMatch {
        if (project.sourceWidth != actual.width || project.sourceHeight != actual.height) {
            return SourceMatch.DIMENSION_MISMATCH
        }
        val expectedHash = project.sourceSha256
        if (expectedHash == null) {
            return if (policy == SourceVerificationPolicy.ALLOW_MISSING_FINGERPRINT) SourceMatch.MATCH
            else SourceMatch.MISSING_FINGERPRINT
        }
        return if (actual.sha256.equals(expectedHash, ignoreCase = true)) SourceMatch.MATCH
        else SourceMatch.HASH_MISMATCH
    }

    private fun validate(
        project: SnapCropProject,
        origin: ProjectImportOrigin,
        limits: ProjectImportLimits
    ): ProjectDecodeResult {
        fun reject(field: String) = ProjectDecodeResult.Rejected(ProjectRejectReason.INVALID_FIELD, field)
        val width = project.sourceWidth
        val height = project.sourceHeight
        if (width !in 1..4096 || height !in 1..4096 || width.toLong() * height > 16_777_216L) return reject("source.dimensions")
        if ((project.sourceUri?.length ?: 0) > limits.maxUriChars) return reject("source.uri")
        val hash = project.sourceSha256
        if (hash != null && !HASH_PATTERN.matches(hash)) return reject("source.sha256")
        if (origin == ProjectImportOrigin.EXTERNAL && hash == null) {
            return ProjectDecodeResult.Rejected(ProjectRejectReason.MISSING_FINGERPRINT, "source.sha256")
        }
        if (!project.cropRect.isValidInside(width, height)) return reject("crop")
        if (project.pixelateRects.size > limits.maxPixelateRects || project.pixelateRects.any { !it.isValidInside(width, height) }) {
            return reject("pixelateRects")
        }
        if (project.redactions.size > limits.maxRedactions) return reject("redactions")
        if (project.redactions.map { it.id }.toSet().size != project.redactions.size) return reject("redactions.id")
        for (region in project.redactions) {
            if (!REDACTION_ID_PATTERN.matches(region.id)) return reject("redactions.id")
            if (!region.bounds.isValidInside(width, height)) return reject("redactions.bounds")
        }
        if (project.drawLayers.size > limits.maxDrawLayers) return reject("drawLayers")
        var totalPoints = 0
        for (layer in project.drawLayers) {
            if (layer.points.isEmpty() || layer.points.size > limits.maxPointsPerLayer) return reject("drawLayers.points")
            totalPoints += layer.points.size
            if (totalPoints > limits.maxTotalPoints) return reject("drawLayers.totalPoints")
            if (layer.shapeType != null && layer.shapeType !in ALLOWED_SHAPES) return reject("drawLayers.shapeType")
            if ((layer.text?.length ?: 0) > limits.maxTextChars) return reject("drawLayers.text")
            if (!layer.strokeWidth.isFinite() || layer.strokeWidth !in 0f..80f) return reject("drawLayers.strokeWidth")
            if (!layer.transScale.isFinite() || layer.transScale !in 0.2f..5f) return reject("drawLayers.transScale")
            if (!layer.transOffsetX.isFinite() || abs(layer.transOffsetX) > width * 4f ||
                !layer.transOffsetY.isFinite() || abs(layer.transOffsetY) > height * 4f ||
                !layer.transRotation.isFinite() || abs(layer.transRotation) > 3600f) return reject("drawLayers.transform")
            val allPoints = layer.points + listOfNotNull(layer.controlPoint)
            if (allPoints.any { !it.x.isFinite() || !it.y.isFinite() || it.x < -width || it.x > width * 2f || it.y < -height || it.y > height * 2f }) {
                return reject("drawLayers.points")
            }
        }
        if (project.exportQuality !in 1..100 || (project.exportSavePath?.length ?: 0) > limits.maxPathChars) return reject("export")
        val formats = mapOf("png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "webp" to "image/webp")
        if (project.exportFormat != null || project.exportMimeType != null) {
            if (formats[project.exportFormat?.lowercase()] != project.exportMimeType?.lowercase()) return reject("export.format")
        }
        if (!validAdjustments(project.adjustments, width, height)) return reject("adjustments")
        return ProjectDecodeResult.Success(project)
    }

    private fun Rect.isValidInside(width: Int, height: Int): Boolean =
        left >= 0 && top >= 0 && right <= width && bottom <= height && left < right && top < bottom

    private fun validAdjustments(adj: FloatArray, width: Int, height: Int): Boolean {
        if (adj.size < ADJUSTMENT_COUNT || adj.any { !it.isFinite() }) return false
        fun inRange(index: Int, min: Float, max: Float) = adj[index] in min..max
        fun integral(index: Int) = adj[index] == adj[index].toInt().toFloat()
        if (!inRange(0, -100f, 100f) || !inRange(1, 0.5f, 2f) || !inRange(2, 0f, 2f) ||
            !integral(3) || !inRange(3, 0f, 7f) || !inRange(4, -50f, 50f) || !inRange(5, 0f, 1f) ||
            !integral(6) || !inRange(6, 0f, 16f) || !inRange(7, 0f, 2f) || !inRange(8, -45f, 45f) ||
            !inRange(9, -100f, 100f) || !inRange(10, -100f, 100f) || !inRange(11, 0f, 1f) ||
            !inRange(12, 0f, 1f) || !integral(13) || !inRange(13, 0f, 6f) ||
            (14..16).any { !inRange(it, -100f, 100f) }) return false
        val quad = adj.sliceArray(17..24)
        if (quad.all { it == 0f }) return true
        for (i in quad.indices step 2) {
            if (quad[i] !in 0f..width.toFloat() || quad[i + 1] !in 0f..height.toFloat()) return false
        }
        var area = 0f
        for (i in 0 until 4) {
            val next = (i + 1) % 4
            area += quad[i * 2] * quad[next * 2 + 1] - quad[next * 2] * quad[i * 2 + 1]
        }
        return abs(area) > 1f
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

    private fun RedactionRegion.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("bounds", bounds.toJson())
        .put("categories", JSONArray().apply {
            categories.sortedBy(RedactionCategory::name).forEach { put(it.name) }
        })
        .put("source", source.name)
        .put("style", style.name)
        .put("enabled", enabled)

    private fun JSONObject.toRedaction(): RedactionRegion {
        val categoryJson = optJSONArray("categories") ?: throw IllegalArgumentException("redactions.categories")
        val categories = buildSet {
            for (index in 0 until categoryJson.length()) {
                add(RedactionCategory.valueOf(categoryJson.getString(index)))
            }
        }
        return RedactionRegion(
            id = getString("id"),
            bounds = getJSONObject("bounds").toRect(),
            categories = categories,
            source = RedactionSource.valueOf(getString("source")),
            style = RedactionStyle.valueOf(getString("style")),
            enabled = optBoolean("enabled", true)
        )
    }

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
        .apply { controlPoint?.let { put("controlPoint", it.toJson()) } }
        .apply {
            if (hasTransform) {
                put("transOffsetX", transOffsetX.toDouble())
                put("transOffsetY", transOffsetY.toDouble())
                put("transScale", transScale.toDouble())
                put("transRotation", transRotation.toDouble())
            }
        }

    private fun JSONObject.toDrawPath(): DrawPath = DrawPath(
        points = optJSONArray("points").toPointList(),
        color = optInt("color", 0xFFFF0000.toInt()),
        strokeWidth = optDouble("strokeWidth", 6.0).toFloat(),
        isArrow = optBoolean("isArrow", false),
        shapeType = optNullableString("shapeType"),
        text = optNullableString("text"),
        filled = optBoolean("filled", false),
        dashed = optBoolean("dashed", false),
        visible = optBoolean("visible", true),
        controlPoint = optJSONObject("controlPoint")?.let { PointF(it.optDouble("x").toFloat(), it.optDouble("y").toFloat()) },
        transOffsetX = optDouble("transOffsetX", 0.0).toFloat(),
        transOffsetY = optDouble("transOffsetY", 0.0).toFloat(),
        transScale = optDouble("transScale", 1.0).toFloat(),
        transRotation = optDouble("transRotation", 0.0).toFloat()
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
            .put("quadTLx", value(17, 0f))
            .put("quadTLy", value(18, 0f))
            .put("quadTRx", value(19, 0f))
            .put("quadTRy", value(20, 0f))
            .put("quadBRx", value(21, 0f))
            .put("quadBRy", value(22, 0f))
            .put("quadBLx", value(23, 0f))
            .put("quadBLy", value(24, 0f))
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
            adj[17] = obj.optDouble("quadTLx", 0.0).toFloat()
            adj[18] = obj.optDouble("quadTLy", 0.0).toFloat()
            adj[19] = obj.optDouble("quadTRx", 0.0).toFloat()
            adj[20] = obj.optDouble("quadTRy", 0.0).toFloat()
            adj[21] = obj.optDouble("quadBRx", 0.0).toFloat()
            adj[22] = obj.optDouble("quadBRy", 0.0).toFloat()
            adj[23] = obj.optDouble("quadBLx", 0.0).toFloat()
            adj[24] = obj.optDouble("quadBLy", 0.0).toFloat()
        }
    }

    private fun JSONArray?.toRectList(): List<Rect> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add((optJSONObject(i) ?: JSONObject()).toRect())
        }
    }

    private fun JSONArray?.toRedactionList(): List<RedactionRegion> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(getJSONObject(i).toRedaction())
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

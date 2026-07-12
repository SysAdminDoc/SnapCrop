package com.sysadmindoc.snapcrop

import android.graphics.Rect
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class RedactionCategory {
    EMAIL,
    PHONE,
    PAYMENT_CARD,
    IPV4,
    IPV6,
    MAC_ADDRESS,
    IBAN,
    POSTAL_ADDRESS,
    FACE,
    MANUAL
}

enum class RedactionSource {
    MANUAL,
    FACE_DETECTOR,
    OCR_REGEX,
    ENTITY
}

/**
 * Editable source-space redaction geometry.
 *
 * [Rect] is mutable, so construction, reads, and copies all cross a defensive-copy boundary.
 * Callers can safely retain or mutate their own Rect without changing a region or undo snapshot.
 */
class RedactionRegion(
    val id: String,
    bounds: Rect,
    categories: Set<RedactionCategory>,
    val source: RedactionSource,
    val style: RedactionStyle,
    val enabled: Boolean = true
) {
    private val storedBounds = Rect(bounds)
    private val storedCategories = categories.toSet()

    val bounds: Rect
        get() = Rect(storedBounds)

    val categories: Set<RedactionCategory>
        get() = storedCategories

    init {
        require(id.isNotBlank()) { "Redaction id must not be blank" }
        require(storedBounds.left < storedBounds.right && storedBounds.top < storedBounds.bottom) {
            "Redaction bounds must have positive area"
        }
        require(storedCategories.isNotEmpty()) { "Redaction must have at least one category" }
    }

    fun copy(
        id: String = this.id,
        bounds: Rect = this.bounds,
        categories: Set<RedactionCategory> = this.categories,
        source: RedactionSource = this.source,
        style: RedactionStyle = this.style,
        enabled: Boolean = this.enabled
    ): RedactionRegion = RedactionRegion(id, bounds, categories, source, style, enabled)

    override fun equals(other: Any?): Boolean =
        other is RedactionRegion &&
            id == other.id &&
            storedBounds == other.storedBounds &&
            storedCategories == other.storedCategories &&
            source == other.source &&
            style == other.style &&
            enabled == other.enabled

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + storedBounds.hashCode()
        result = 31 * result + storedCategories.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }

    override fun toString(): String =
        "RedactionRegion(id=$id, bounds=$storedBounds, categories=$storedCategories, " +
            "source=$source, style=$style, enabled=$enabled)"
}

object RedactionRegions {
    const val MIN_REGION_SIZE = 4

    internal fun fromSensitiveDetections(
        detections: List<SensitiveTextDetection>,
        style: RedactionStyle = RedactionStyle.SOLID
    ): List<RedactionRegion> = detections
        .groupBy { it.bounds.geometryKey() }
        .entries
        .sortedBy { it.key }
        .map { (_, matches) ->
            val bounds = matches.first().bounds
            val categories = matches.mapTo(linkedSetOf()) { it.category.toRedactionCategory() }
            val source = if (matches.any { it.source == SensitiveTextDetectionSource.REGEX }) {
                RedactionSource.OCR_REGEX
            } else {
                RedactionSource.ENTITY
            }
            RedactionRegion(
                id = stableId("sensitive", bounds),
                bounds = bounds,
                categories = categories,
                source = source,
                style = style
            )
        }

    fun fromFaces(
        faces: List<Rect>,
        style: RedactionStyle = RedactionStyle.SOLID
    ): List<RedactionRegion> = faces
        .asSequence()
        .filter { it.left < it.right && it.top < it.bottom }
        .distinctBy { it.geometryKey() }
        .sortedBy { it.geometryKey() }
        .map { bounds ->
            RedactionRegion(
                id = stableId("face", bounds),
                bounds = bounds,
                categories = setOf(RedactionCategory.FACE),
                source = RedactionSource.FACE_DETECTOR,
                style = style
            )
        }
        .toList()

    fun manual(
        bounds: Rect,
        style: RedactionStyle = RedactionStyle.SOLID
    ): RedactionRegion = RedactionRegion(
        id = stableId("manual", bounds),
        bounds = bounds,
        categories = setOf(RedactionCategory.MANUAL),
        source = RedactionSource.MANUAL,
        style = style
    )

    /**
     * Merges repeated detector results without resetting user-selected style or enabled state.
     * Existing regions win presentation state; matching incoming evidence only enriches categories
     * and records the strongest deterministic source.
     */
    fun merge(
        existing: List<RedactionRegion>,
        incoming: List<RedactionRegion>
    ): List<RedactionRegion> {
        val merged = linkedMapOf<String, RedactionRegion>()
        (existing + incoming).forEach { candidate ->
            val current = merged[candidate.id]
            merged[candidate.id] = if (current == null) {
                candidate.copy()
            } else {
                current.copy(
                    categories = current.categories + candidate.categories,
                    source = conservativeSource(current.source, candidate.source)
                )
            }
        }
        return merged.values.toList()
    }

    /** Toggles all regions carrying [category] as one bulk editor action. */
    fun toggleCategory(
        regions: List<RedactionRegion>,
        category: RedactionCategory
    ): List<RedactionRegion> {
        val matching = regions.filter { category in it.categories }
        if (matching.isEmpty()) return regions.map { it.copy() }
        val enable = matching.none { it.enabled }
        return setCategoryEnabled(regions, category, enable)
    }

    fun setCategoryEnabled(
        regions: List<RedactionRegion>,
        category: RedactionCategory,
        enabled: Boolean
    ): List<RedactionRegion> = regions.map { region ->
        if (category in region.categories) region.copy(enabled = enabled) else region.copy()
    }

    fun move(
        region: RedactionRegion,
        dx: Int,
        dy: Int,
        imageWidth: Int,
        imageHeight: Int,
        minimumSize: Int = MIN_REGION_SIZE
    ): RedactionRegion {
        validateCanvas(imageWidth, imageHeight, minimumSize)
        val original = clampBounds(region.bounds, imageWidth, imageHeight, minimumSize)
        val width = original.width()
        val height = original.height()
        val left = (original.left + dx).coerceIn(0, imageWidth - width)
        val top = (original.top + dy).coerceIn(0, imageHeight - height)
        return region.copy(bounds = Rect(left, top, left + width, top + height))
    }

    fun resize(
        region: RedactionRegion,
        requestedBounds: Rect,
        imageWidth: Int,
        imageHeight: Int,
        minimumSize: Int = MIN_REGION_SIZE
    ): RedactionRegion {
        validateCanvas(imageWidth, imageHeight, minimumSize)
        return region.copy(
            bounds = clampBounds(requestedBounds, imageWidth, imageHeight, minimumSize)
        )
    }

    private fun clampBounds(
        requested: Rect,
        imageWidth: Int,
        imageHeight: Int,
        minimumSize: Int
    ): Rect {
        val rawLeft = minOf(requested.left, requested.right)
        val rawRight = maxOf(requested.left, requested.right)
        val rawTop = minOf(requested.top, requested.bottom)
        val rawBottom = maxOf(requested.top, requested.bottom)

        val left = rawLeft.coerceIn(0, imageWidth - minimumSize)
        val top = rawTop.coerceIn(0, imageHeight - minimumSize)
        val right = rawRight.coerceIn(left + minimumSize, imageWidth)
        val bottom = rawBottom.coerceIn(top + minimumSize, imageHeight)
        return Rect(left, top, right, bottom)
    }

    private fun validateCanvas(imageWidth: Int, imageHeight: Int, minimumSize: Int) {
        require(minimumSize > 0) { "Minimum size must be positive" }
        require(imageWidth >= minimumSize && imageHeight >= minimumSize) {
            "Image must accommodate the minimum redaction size"
        }
    }

    private fun conservativeSource(first: RedactionSource, second: RedactionSource): RedactionSource {
        val precedence = listOf(
            RedactionSource.MANUAL,
            RedactionSource.FACE_DETECTOR,
            RedactionSource.OCR_REGEX,
            RedactionSource.ENTITY
        )
        return if (precedence.indexOf(first) <= precedence.indexOf(second)) first else second
    }

    private fun stableId(kind: String, bounds: Rect): String {
        val input = "$kind:${bounds.geometryKey()}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "red_$digest"
    }

    private fun SensitiveTextCategory.toRedactionCategory(): RedactionCategory = when (this) {
        SensitiveTextCategory.EMAIL -> RedactionCategory.EMAIL
        SensitiveTextCategory.PHONE -> RedactionCategory.PHONE
        SensitiveTextCategory.PAYMENT_CARD -> RedactionCategory.PAYMENT_CARD
        SensitiveTextCategory.IPV4 -> RedactionCategory.IPV4
        SensitiveTextCategory.IPV6 -> RedactionCategory.IPV6
        SensitiveTextCategory.MAC_ADDRESS -> RedactionCategory.MAC_ADDRESS
        SensitiveTextCategory.IBAN -> RedactionCategory.IBAN
        SensitiveTextCategory.POSTAL_ADDRESS -> RedactionCategory.POSTAL_ADDRESS
    }

    private fun Rect.geometryKey(): String = "$left:$top:$right:$bottom"
}

package com.sysadmindoc.snapcrop

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class GalleryMediaType { ALL, IMAGES, VIDEOS, SCREENSHOTS }

enum class GalleryDateRange(internal val windowSeconds: Long?) {
    ALL(null),
    LAST_24_HOURS(24L * 60L * 60L),
    LAST_7_DAYS(7L * 24L * 60L * 60L),
    LAST_30_DAYS(30L * 24L * 60L * 60L),
    LAST_90_DAYS(90L * 24L * 60L * 60L),
    LAST_365_DAYS(365L * 24L * 60L * 60L),
}

enum class GalleryOrientation { ALL, PORTRAIT, LANDSCAPE, SQUARE }

enum class GalleryFavoriteMode { ALL, FAVORITES, NOT_FAVORITES }

enum class GalleryFormat {
    PNG,
    JPEG,
    WEBP,
    GIF,
    HEIC,
    AVIF,
    MP4,
    WEBM,
    THREE_GPP,
    QUICKTIME,
    OTHER,
}

/** Stable source identity used by source/app filter chips. */
val Photo.sourceKey: String
    get() = ownerPackage.normalizedFilterValue().ifEmpty { albumPath.normalizedAlbumPath() }

/** Media format derived from MediaStore MIME data, never a filename guess. */
val Photo.galleryFormat: GalleryFormat
    get() = when (mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)) {
        "image/png" -> GalleryFormat.PNG
        "image/jpeg", "image/jpg" -> GalleryFormat.JPEG
        "image/webp" -> GalleryFormat.WEBP
        "image/gif" -> GalleryFormat.GIF
        "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence" -> GalleryFormat.HEIC
        "image/avif" -> GalleryFormat.AVIF
        "video/mp4" -> GalleryFormat.MP4
        "video/webm" -> GalleryFormat.WEBM
        "video/3gpp", "video/3gpp2" -> GalleryFormat.THREE_GPP
        "video/quicktime" -> GalleryFormat.QUICKTIME
        else -> GalleryFormat.OTHER
    }

data class GalleryFilterState(
    val mediaType: GalleryMediaType = GalleryMediaType.ALL,
    val sourceOrAlbum: String = "",
    val categories: Set<String> = emptySet(),
    val dateRange: GalleryDateRange = GalleryDateRange.ALL,
    val orientation: GalleryOrientation = GalleryOrientation.ALL,
    val minWidth: Int = 0,
    val minHeight: Int = 0,
    val favoriteMode: GalleryFavoriteMode = GalleryFavoriteMode.ALL,
    val formats: Set<GalleryFormat> = emptySet(),
) {
    /** Number of selected filter chips/values rather than the number of model fields. */
    val activeCount: Int
        get() =
            (if (mediaType != GalleryMediaType.ALL) 1 else 0) +
                (if (sourceOrAlbum.normalizedFilterValue().isNotEmpty()) 1 else 0) +
                normalizedCategories().size +
                (if (dateRange != GalleryDateRange.ALL) 1 else 0) +
                (if (orientation != GalleryOrientation.ALL) 1 else 0) +
                (if (minWidth > 0) 1 else 0) +
                (if (minHeight > 0) 1 else 0) +
                (if (favoriteMode != GalleryFavoriteMode.ALL) 1 else 0) +
                formats.size

    fun matches(
        photo: Photo,
        favoriteUris: Set<String>,
        nowEpochSeconds: Long,
    ): Boolean {
        if (!matchesMediaType(photo)) return false

        val source = sourceOrAlbum.normalizedSourceKey()
        if (source.isNotEmpty() && photo.sourceKey != source) return false

        val wantedCategories = normalizedCategories()
        if (wantedCategories.isNotEmpty()) {
            val actualCategories = photo.indexCategories.asSequence()
                .map(String::normalizedFilterValue)
                .filter(String::isNotEmpty)
                .toSet()
            if (wantedCategories.none(actualCategories::contains)) return false
        }

        val window = dateRange.windowSeconds
        if (window != null) {
            val now = nowEpochSeconds.coerceAtLeast(0L)
            val cutoff = (now - window).coerceAtLeast(0L)
            if (photo.dateAdded !in cutoff..now) return false
        }

        if (!matchesOrientation(photo)) return false
        if (minWidth > 0 && photo.width < minWidth) return false
        if (minHeight > 0 && photo.height < minHeight) return false

        val isFavorite = photo.uri.toString() in favoriteUris
        if (favoriteMode == GalleryFavoriteMode.FAVORITES && !isFavorite) return false
        if (favoriteMode == GalleryFavoriteMode.NOT_FAVORITES && isFavorite) return false

        if (formats.isNotEmpty() && photo.galleryFormat !in formats) return false
        return true
    }

    /** Fixed-order, canonical JSON suitable for rememberSaveable and process-death restoration. */
    fun encode(): String {
        val normalizedSource = sourceOrAlbum.normalizedSourceKey().take(MAX_SOURCE_LENGTH)
        val normalizedCategories = normalizedCategories().sorted()
        val normalizedFormats = formats.sortedBy(GalleryFormat::name)
        return buildString {
            append("{\"schema\":\"").append(SCHEMA).append("\",\"version\":").append(VERSION)
            append(",\"mediaType\":").append(JSONObject.quote(mediaType.name))
            append(",\"sourceOrAlbum\":").append(JSONObject.quote(normalizedSource))
            append(",\"categories\":[")
            normalizedCategories.forEachIndexed { index, category ->
                if (index > 0) append(',')
                append(JSONObject.quote(category))
            }
            append(']')
            append(",\"dateRange\":").append(JSONObject.quote(dateRange.name))
            append(",\"orientation\":").append(JSONObject.quote(orientation.name))
            append(",\"minWidth\":").append(minWidth.coerceIn(0, MAX_DIMENSION))
            append(",\"minHeight\":").append(minHeight.coerceIn(0, MAX_DIMENSION))
            append(",\"favoriteMode\":").append(JSONObject.quote(favoriteMode.name))
            append(",\"formats\":[")
            normalizedFormats.forEachIndexed { index, format ->
                if (index > 0) append(',')
                append(JSONObject.quote(format.name))
            }
            append("]}")
        }
    }

    private fun normalizedCategories(): Set<String> = categories.asSequence()
        .map(String::normalizedFilterValue)
        .filter(String::isNotEmpty)
        .map { it.take(MAX_CATEGORY_LENGTH) }
        .distinct()
        .sorted()
        .take(MAX_CATEGORIES)
        .toSet()

    private fun matchesMediaType(photo: Photo): Boolean = when (mediaType) {
        GalleryMediaType.ALL -> true
        GalleryMediaType.IMAGES -> !photo.isVideo
        GalleryMediaType.VIDEOS -> photo.isVideo
        GalleryMediaType.SCREENSHOTS -> !photo.isVideo && photo.isScreenshot
    }

    private fun matchesOrientation(photo: Photo): Boolean = when (orientation) {
        GalleryOrientation.ALL -> true
        GalleryOrientation.PORTRAIT -> photo.width > 0 && photo.height > photo.width
        GalleryOrientation.LANDSCAPE -> photo.height > 0 && photo.width > photo.height
        GalleryOrientation.SQUARE -> photo.width > 0 && photo.width == photo.height
    }

    companion object {
        private const val SCHEMA = "snapcrop.galleryFilters"
        private const val VERSION = 1
        private const val MAX_ENCODED_LENGTH = 16 * 1024
        private const val MAX_SOURCE_LENGTH = 512
        private const val MAX_CATEGORY_LENGTH = 64
        private const val MAX_CATEGORIES = 32
        private const val MAX_DIMENSION = 1_000_000

        fun decode(encoded: String?): GalleryFilterState {
            if (encoded.isNullOrBlank() || encoded.length > MAX_ENCODED_LENGTH) return GalleryFilterState()
            return runCatching {
                val root = JSONObject(encoded)
                require(root.optString("schema") == SCHEMA)
                require(root.optInt("version", -1) == VERSION)
                val categories = root.optJSONArray("categories")?.let { array ->
                    buildSet {
                        for (index in 0 until minOf(array.length(), MAX_CATEGORIES)) {
                            array.optString(index).normalizedFilterValue()
                                .take(MAX_CATEGORY_LENGTH)
                                .takeIf(String::isNotEmpty)
                                ?.let(::add)
                        }
                    }
                }.orEmpty()
                val formats = root.optJSONArray("formats")?.let { array ->
                    buildSet {
                        for (index in 0 until array.length()) {
                            enumValueOrNull<GalleryFormat>(array.optString(index))?.let(::add)
                        }
                    }
                }.orEmpty()
                GalleryFilterState(
                    mediaType = enumValueOrNull<GalleryMediaType>(root.optString("mediaType"))
                        ?: GalleryMediaType.ALL,
                    sourceOrAlbum = root.optString("sourceOrAlbum").normalizedSourceKey()
                        .take(MAX_SOURCE_LENGTH),
                    categories = categories,
                    dateRange = enumValueOrNull<GalleryDateRange>(root.optString("dateRange"))
                        ?: GalleryDateRange.ALL,
                    orientation = enumValueOrNull<GalleryOrientation>(root.optString("orientation"))
                        ?: GalleryOrientation.ALL,
                    minWidth = root.optInt("minWidth", 0).coerceIn(0, MAX_DIMENSION),
                    minHeight = root.optInt("minHeight", 0).coerceIn(0, MAX_DIMENSION),
                    favoriteMode = enumValueOrNull<GalleryFavoriteMode>(root.optString("favoriteMode"))
                        ?: GalleryFavoriteMode.ALL,
                    formats = formats,
                )
            }.getOrDefault(GalleryFilterState())
        }

        private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
            enumValues<T>().firstOrNull { it.name == value }
    }
}

fun applyGalleryFilters(
    photos: List<Photo>,
    state: GalleryFilterState,
    favoriteUris: Set<String>,
    nowEpochSeconds: Long,
): List<Photo> = photos.filter { state.matches(it, favoriteUris, nowEpochSeconds) }

private fun String.normalizedFilterValue(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

private fun String.normalizedAlbumPath(): String = normalizedFilterValue()
    .replace('\\', '/')
    .replace(Regex("/+"), "/")
    .trim('/')

private fun String.normalizedSourceKey(): String {
    val normalized = normalizedFilterValue()
    return if ('/' in normalized || '\\' in normalized) normalized.normalizedAlbumPath() else normalized
}

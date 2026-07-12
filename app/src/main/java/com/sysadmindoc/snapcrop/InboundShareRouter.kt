package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns

internal object InboundShareContract {
    const val EXTRA_URIS = "com.sysadmindoc.snapcrop.extra.INBOUND_URIS"
    const val MAX_ITEMS = 50
    const val MAX_ITEM_BYTES = 64L * 1024L * 1024L
    const val MAX_IMAGE_PIXELS = 48_000_000L

    @Suppress("DEPRECATION")
    fun extractUris(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let(uris::add)
            (intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))?.let(uris::addAll)
            intent.clipData?.let { clip ->
                for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
            }
        }
        intent.data?.let(uris::add)
        return uris.distinctBy(Uri::toString)
    }

    fun validateImages(resolver: ContentResolver, uris: List<Uri>): InboundValidationResult {
        val accepted = mutableListOf<Uri>()
        val rejected = mutableListOf<InboundRejection>()
        uris.forEachIndexed { index, uri ->
            val reason = when {
                index >= MAX_ITEMS -> "selection exceeds $MAX_ITEMS items"
                !uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) -> "unsupported URI scheme"
                declaredSize(resolver, uri)?.let { it > MAX_ITEM_BYTES } == true -> "image exceeds 64 MiB"
                declaredSize(resolver, uri) == null && streamExceedsLimit(resolver, uri) -> "image exceeds 64 MiB"
                else -> validateImagePayload(resolver, uri)
            }
            if (reason == null) accepted.add(uri) else rejected.add(InboundRejection(index, reason))
        }
        return InboundValidationResult(accepted, rejected)
    }

    private fun validateImagePayload(resolver: ContentResolver, uri: Uri): String? {
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        if (mime != null && !mime.startsWith("image/")) return "unsupported media type"
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)
        if (!decoded) return "image is unreadable"
        if (options.outWidth.toLong() * options.outHeight.toLong() > MAX_IMAGE_PIXELS) {
            return "image exceeds 48 megapixels"
        }
        return null
    }

    private fun streamExceedsLimit(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_ITEM_BYTES) return@use true
            }
            false
        } ?: true
    }.getOrDefault(true)

    private fun declaredSize(resolver: ContentResolver, uri: Uri): Long? {
        val descriptorLength = runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { length -> length >= 0 } }
        }.getOrNull()
        if (descriptorLength != null) return descriptorLength
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
            }
        }.getOrNull()
    }
}

internal data class InboundRejection(val itemIndex: Int, val reason: String)
internal data class InboundValidationResult(
    val accepted: List<Uri>,
    val rejected: List<InboundRejection>
)

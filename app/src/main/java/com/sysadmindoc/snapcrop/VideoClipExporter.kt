package com.sysadmindoc.snapcrop

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import java.nio.ByteBuffer

object VideoClipExporter {
    fun durationMs(context: Context, uri: Uri): Long {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        }
    }

    fun frameAt(context: Context, uri: Uri, timeMs: Long): Bitmap? {
        return MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }

    fun trimToGallery(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "SnapCrop_Clip_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SnapCrop")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            resolver.openFileDescriptor(outputUri, "w")?.use { descriptor ->
                trimInto(context, uri, descriptor.fileDescriptor, startMs * 1000L, endMs * 1000L)
            } ?: throw IllegalStateException("Unable to open output video")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(outputUri, values, null, null)
            outputUri
        } catch (_: Exception) {
            try {
                resolver.delete(outputUri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }

    private fun trimInto(
        context: Context,
        uri: Uri,
        output: java.io.FileDescriptor,
        startUs: Long,
        endUs: Long
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            setOrientationHint(context, uri, muxer)

            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = 256 * 1024
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                val outputTrack = muxer.addTrack(format)
                trackMap[track] = outputTrack
                extractor.selectTrack(track)
                maxBufferSize = maxOf(maxBufferSize, safeMaxInputSize(format))
            }

            if (trackMap.isEmpty()) throw IllegalStateException("No audio/video tracks")

            muxer.start()
            muxerStarted = true
            extractor.seekTo(startUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val sampleTrack = extractor.sampleTrackIndex
                if (sampleTrack < 0) break
                val outputTrack = trackMap[sampleTrack]
                if (outputTrack == null) {
                    extractor.advance()
                    continue
                }

                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime > endUs) break

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                info.set(
                    0,
                    sampleSize,
                    (sampleTime - startUs).coerceAtLeast(0L),
                    extractor.sampleFlags
                )
                muxer.writeSampleData(outputTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            extractor.release()
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            muxer?.release()
        }
    }

    private fun safeMaxInputSize(format: MediaFormat): Int {
        return try {
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else {
                256 * 1024
            }
        } catch (_: Exception) {
            256 * 1024
        }
    }

    private fun setOrientationHint(context: Context, uri: Uri, muxer: MediaMuxer) {
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: return
                if (rotation == 90 || rotation == 180 || rotation == 270) {
                    muxer.setOrientationHint(rotation)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun saveFrameToGallery(
        resolver: ContentResolver,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        quality: Int,
        ext: String,
        mime: String,
        savePath: String
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SnapCrop_Frame_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, savePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(format, quality, it) }
                ?: throw IllegalStateException("Unable to open output image")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            null
        }
    }
}

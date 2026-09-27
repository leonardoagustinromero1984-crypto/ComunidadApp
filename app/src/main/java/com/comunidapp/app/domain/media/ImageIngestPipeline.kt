package com.comunidapp.app.domain.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FilePurposePolicy
import java.io.File
import java.io.FileOutputStream

data class ImageIngestResult(
    val uriString: String,
    val mimeType: String,
    val sizeBytes: Long,
    val normalized: Boolean
)

fun interface ImageIngest {
    fun normalize(uriString: String, purpose: FileAssetPurpose): ImageIngestResult

    companion object {
        val NoOp = ImageIngest { uri, _ ->
            ImageIngestResult(uri, "application/octet-stream", 0L, normalized = false)
        }
    }
}

object ImageIngestPolicy {
    const val AVATAR_MAX_EDGE_PX = 1280
    const val SOCIAL_MAX_EDGE_PX = 2560
    const val JPEG_QUALITY = 88
    const val UPSCALE_FORBIDDEN = true
    const val USER_VISIBLE_MEDIA_LIMIT_FOR_NORMAL_PHONE_MEDIA = false

    fun maxEdgePx(purpose: FileAssetPurpose): Int =
        MediaIngestionPolicy.masterEdgePx(purpose)

    fun shouldIngest(purpose: FileAssetPurpose, mime: String?): Boolean {
        if (MediaIngestionPolicy.isVideoMime(mime)) return false
        if (MediaIngestionPolicy.isHeic(mime)) return true
        val spec = FilePurposePolicy.spec(purpose)
        val acceptsImage = spec.allowedMimeTypes.any { it.startsWith("image/") }
        return acceptsImage && (mime.isNullOrBlank() || MediaIngestionPolicy.isImageMime(mime))
    }
}

/**
 * EXIF orientation + downscale + JPEG compress before upload validation.
 * Never upscales. Never loads videos.
 */
class AndroidImageIngest(
    private val contentResolver: ContentResolver,
    private val cacheDir: File
) : ImageIngest {
    override fun normalize(uriString: String, purpose: FileAssetPurpose): ImageIngestResult {
        try {
            return normalizeInternal(uriString, purpose)
        } catch (error: MediaIngestException) {
            throw error
        } catch (error: Throwable) {
            throw MediaIngestException(MediaDiagnostic.DECODE, error)
        }
    }

    private fun normalizeInternal(uriString: String, purpose: FileAssetPurpose): ImageIngestResult {
        if (uriString.isBlank()) throw MediaIngestException(MediaDiagnostic.URI)
        if (ProfileMediaPipeline.shouldSkipReingest(uriString, purpose)) {
            val uri = Uri.parse(uriString)
            val mime = contentResolver.getType(uri) ?: inferredMime(uriString)
            if (MediaIngestionPolicy.isVideoMime(mime) ||
                uriString.contains("leover_video_", ignoreCase = true)
            ) {
                val size = ProfileMediaPipeline.fileLength(uriString)
                return ImageIngestResult(
                    uriString,
                    mime ?: "video/mp4",
                    size,
                    normalized = false
                )
            }
            val size = ProfileMediaPipeline.fileLength(uriString)
            if (size > 0L) {
                return ImageIngestResult(uriString, "image/jpeg", size, normalized = true)
            }
        }
        val uri = Uri.parse(uriString)
        val mime = contentResolver.getType(uri) ?: inferredMime(uriString)
        if (!ImageIngestPolicy.shouldIngest(purpose, mime)) {
            val size = ProfileMediaPipeline.resolvedSizeBytes(uriString, 0L)
            return ImageIngestResult(
                uriString,
                mime ?: "application/octet-stream",
                size,
                normalized = false
            )
        }
        val stream = open(uri) ?: throw MediaIngestException(MediaDiagnostic.URI)
        stream.close()
        val orientation = readOrientation(uri)
        val maxEdge = MediaIngestionPolicy.masterEdgePx(purpose)
        val decoded = decodeBounded(uri, maxEdge)
            ?: throw MediaIngestException(MediaDiagnostic.DECODE)
        val oriented = try {
            applyExif(decoded, orientation)
        } catch (error: Throwable) {
            throw MediaIngestException(MediaDiagnostic.EXIF, error)
        }
        if (oriented != decoded && !decoded.isRecycled) decoded.recycle()
        val scaled = downscale(oriented, maxEdge)
        if (scaled != oriented && !oriented.isRecycled) oriented.recycle()
        val out = File(cacheDir, "leover_ingest_${System.nanoTime()}.jpg")
        writeJpegUnderCap(
            scaled,
            out,
            MediaIngestionPolicy.targetBytes(purpose),
            MediaIngestionPolicy.processedMaxBytes(purpose, "image/jpeg")
        )
        if (!scaled.isRecycled) scaled.recycle()
        return ImageIngestResult(
            uriString = Uri.fromFile(out).toString(),
            mimeType = "image/jpeg",
            sizeBytes = out.length(),
            normalized = true
        )
    }

    /**
     * uCrop output is already cropped and orientation-baked. Do not apply EXIF
     * or crop again. Resize to destination edge and JPEG-encode only.
     */
    fun encodeAlreadyCropped(uriString: String, purpose: FileAssetPurpose): ImageIngestResult {
        if (uriString.isBlank()) throw MediaIngestException(MediaDiagnostic.URI)
        val uri = Uri.parse(uriString)
        val decoded = decodeBounded(uri, MediaIngestionPolicy.masterEdgePx(purpose) * 2)
            ?: throw MediaIngestException(MediaDiagnostic.DECODE)
        val maxEdge = MediaIngestionPolicy.masterEdgePx(purpose)
        val output = if (purpose == FileAssetPurpose.USER_AVATAR ||
            purpose == FileAssetPurpose.USER_COVER
        ) {
            scaleExact(decoded, maxEdge, maxEdge)
        } else {
            downscale(decoded, maxEdge)
        }
        if (output != decoded && !decoded.isRecycled) decoded.recycle()
        val out = File(cacheDir, "leover_ingest_${System.nanoTime()}.jpg")
        writeJpegUnderCap(
            output,
            out,
            MediaIngestionPolicy.targetBytes(purpose),
            MediaIngestionPolicy.processedMaxBytes(purpose, "image/jpeg")
        )
        if (!output.isRecycled) output.recycle()
        return ImageIngestResult(
            uriString = Uri.fromFile(out).toString(),
            mimeType = "image/jpeg",
            sizeBytes = out.length(),
            normalized = true
        )
    }

    private fun scaleExact(source: Bitmap, width: Int, height: Int): Bitmap {
        if (source.width == width && source.height == height) return source
        return Bitmap.createScaledBitmap(source, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
    }

    private fun inferredMime(uriString: String): String? {
        val lower = uriString.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            lower.endsWith(".mp4") || lower.endsWith(".mov") -> "video/mp4"
            else -> null
        }
    }

    private fun readOrientation(uri: Uri): Int = runCatching {
        open(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun decodeBounded(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        while (w / sample > maxEdge * 2 || h / sample > maxEdge * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return open(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun downscale(source: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= maxEdge) return source
        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun writeJpegUnderCap(bitmap: Bitmap, out: File, targetBytes: Long, hardCapBytes: Long) {
        var quality = MediaIngestionPolicy.JPEG_QUALITY
        var lastSize = Long.MAX_VALUE
        while (quality >= MediaIngestionPolicy.JPEG_QUALITY_FLOOR) {
            FileOutputStream(out).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    throw MediaIngestException(MediaDiagnostic.ENCODE)
                }
            }
            lastSize = out.length()
            if (lastSize <= targetBytes) return
            quality -= 4
        }
        if (lastSize > hardCapBytes) {
            out.delete()
            throw MediaIngestException(MediaDiagnostic.SIZE)
        }
    }

    private fun applyExif(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun open(uri: Uri) = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).inputStream() }
        else -> contentResolver.openInputStream(uri)
    }
}

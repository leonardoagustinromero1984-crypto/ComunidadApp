package com.comunidapp.app.domain.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FileValidationRules
import java.io.File

/**
 * Verified video selected from a ContentProvider.
 * Validates via [MediaExtractor] track inspection, then normalizes to a local cache file
 * so downstream upload validation does not re-trust broken provider metadata.
 */
data class VerifiedVideoMedia(
    val uri: Uri,
    val detectedMime: String,
    val filename: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null
)

object VerifiedVideoPipeline {

    fun verify(context: Context, source: Uri): Result<VerifiedVideoMedia> = runCatching {
        val trackMime = detectVideoTrackMime(context, source)
            ?: error(MediaDiagnostic.MIME)
        val containerMime = normalizeContainerMime(trackMime)
        val meta = readMetadata(context, source)
        normalizeToCache(
            context = context,
            source = source,
            containerMime = containerMime,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height
        )
    }

    internal fun detectVideoTrackMime(context: Context, uri: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()
                if (mime.startsWith("video/")) return mime
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    internal fun normalizeContainerMime(trackMime: String): String {
        val mime = trackMime.trim().lowercase()
        return when {
            mime in CONTAINER_MIMES -> mime
            mime.startsWith("video/webm") -> "video/webm"
            mime.startsWith("video/3gpp") || mime == "video/3gp" -> "video/3gpp"
            mime.startsWith("video/quicktime") -> "video/quicktime"
            mime.startsWith("video/x-matroska") -> "video/x-matroska"
            mime.startsWith("video/") -> "video/mp4"
            mime.contains("avc") || mime.contains("hevc") || mime.contains("h264") -> "video/mp4"
            else -> "video/mp4"
        }
    }

    private val CONTAINER_MIMES = setOf(
        "video/mp4",
        "video/quicktime",
        "video/3gpp",
        "video/webm",
        "video/x-matroska"
    )

    internal fun extensionForMime(mime: String): String = when (mime.lowercase()) {
        "video/webm" -> "webm"
        "video/3gpp", "video/3gp" -> "3gp"
        "video/quicktime" -> "mov"
        else -> "mp4"
    }

    private data class Meta(
        val durationMs: Long?,
        val width: Int?,
        val height: Int?
    )

    private fun readMetadata(context: Context, uri: Uri): Meta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Meta(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
            )
        } catch (_: Exception) {
            Meta(null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun normalizeToCache(
        context: Context,
        source: Uri,
        containerMime: String,
        durationMs: Long?,
        width: Int?,
        height: Int?
    ): VerifiedVideoMedia {
        val ext = extensionForMime(containerMime)
        val filename = "leover_video_${System.currentTimeMillis()}.$ext"
        val out = File(context.cacheDir, filename)
        if (source.scheme == "file") {
            val existing = source.path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
            if (existing != null && FileNameSanitizer.extensionOf(existing.name) == ext) {
                return VerifiedVideoMedia(
                    uri = Uri.fromFile(existing),
                    detectedMime = containerMime,
                    filename = existing.name,
                    sizeBytes = existing.length(),
                    durationMs = durationMs,
                    width = width,
                    height = height
                )
            }
        }
        context.contentResolver.openInputStream(source)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error(MediaDiagnostic.MIME)
        if (out.length() <= 0L) error(MediaDiagnostic.MIME)
        if (detectVideoTrackMime(context, Uri.fromFile(out)) == null) {
            out.delete()
            error(MediaDiagnostic.MIME)
        }
        FileValidationRules.inferMimeFromExtension(ext)?.let { inferred ->
            require(inferred == containerMime || containerMime.startsWith("video/")) {
                MediaDiagnostic.MIME
            }
        }
        return VerifiedVideoMedia(
            uri = Uri.fromFile(out),
            detectedMime = containerMime,
            filename = filename,
            sizeBytes = out.length(),
            durationMs = durationMs,
            width = width,
            height = height
        )
    }
}

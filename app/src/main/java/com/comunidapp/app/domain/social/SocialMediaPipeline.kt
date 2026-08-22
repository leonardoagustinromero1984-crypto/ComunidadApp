package com.comunidapp.app.domain.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class PreparedSocialMedia(
    val uri: Uri,
    val mimeType: String,
    val filename: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val transcoded: Boolean = false
)

object SocialMediaPipeline {

    suspend fun prepare(
        context: Context,
        source: Uri,
        expectVideo: Boolean,
        onProgress: (Int) -> Unit = {}
    ): Result<PreparedSocialMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val mime = cr.getType(source)?.lowercase().orEmpty().ifBlank {
                if (expectVideo) VideoExportPolicy.TARGET_VIDEO_MIME else "image/jpeg"
            }
            val isVideo = mime.startsWith("video/") || expectVideo
            onProgress(8)
            if (isVideo) prepareVideo(context, source, mime, onProgress)
            else prepareImage(context, source, mime, onProgress)
        }
    }

    private fun prepareImage(
        context: Context,
        source: Uri,
        mime: String,
        onProgress: (Int) -> Unit
    ): PreparedSocialMedia {
        onProgress(35)
        val ingested = com.comunidapp.app.domain.media.AndroidImageIngest(
            context.contentResolver,
            context.cacheDir
        ).normalize(source.toString(), com.comunidapp.app.domain.files.FileAssetPurpose.POST_MEDIA)
        if (!ingested.normalized) {
            error(com.comunidapp.app.domain.media.MediaDiagnostic.DECODE)
        }
        onProgress(70)
        val file = java.io.File(android.net.Uri.parse(ingested.uriString).path ?: "")
        return PreparedSocialMedia(
            uri = android.net.Uri.parse(ingested.uriString),
            mimeType = ingested.mimeType,
            filename = file.name.ifBlank { "photo.jpg" },
            sizeBytes = ingested.sizeBytes,
            transcoded = true
        )
    }

    private suspend fun prepareVideo(
        context: Context,
        source: Uri,
        mime: String,
        onProgress: (Int) -> Unit
    ): PreparedSocialMedia {
        val meta = readVideoMeta(context, source)
        onProgress(20)
        val codec = meta.mimeType.orEmpty()
        val alreadyOptimized =
            (meta.sizeBytes ?: Long.MAX_VALUE) <= VideoExportPolicy.PROCESSED_HARD_CAP_BYTES &&
                (meta.maxEdge ?: Int.MAX_VALUE) <= VideoExportPolicy.TARGET_MAX_EDGE &&
                (mime.contains("mp4") || codec.contains("mp4", ignoreCase = true)) &&
                (codec.contains("avc", ignoreCase = true) ||
                    codec.contains("h264", ignoreCase = true) ||
                    codec.isBlank())
        if (alreadyOptimized) {
            onProgress(70)
            return PreparedSocialMedia(
                uri = source,
                mimeType = mime.ifBlank { VideoExportPolicy.TARGET_VIDEO_MIME },
                filename = "reel.mp4",
                sizeBytes = meta.sizeBytes ?: 0L,
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                transcoded = false
            )
        }
        val out = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        transcode(context, source, out, onProgress)
        val exported = out.takeIf { it.exists() && it.length() > 0 }
        val use = exported ?: error("VIDEO_TRANSCODE_FAILED")
        if (use.length() > VideoExportPolicy.PROCESSED_HARD_CAP_BYTES) {
            error(com.comunidapp.app.domain.media.MediaDiagnostic.SIZE)
        }
        return PreparedSocialMedia(
            uri = Uri.fromFile(use),
            mimeType = VideoExportPolicy.TARGET_VIDEO_MIME,
            filename = use.name,
            sizeBytes = use.length(),
            durationMs = meta.durationMs,
            transcoded = true
        )
    }

    private suspend fun transcode(
        context: Context,
        source: Uri,
        output: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(85)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) cont.resumeWith(Result.failure(exportException))
                    }
                })
                .build()
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForHeight(VideoExportPolicy.TARGET_MAX_EDGE))
                    )
                )
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()
            transformer.start(composition, output.absolutePath)
            cont.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching { transformer.cancel() }
                }
            }
        }
    }

    private data class VideoMeta(
        val sizeBytes: Long?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val mimeType: String? = null
    ) {
        val maxEdge: Int? = listOfNotNull(width, height).maxOrNull()
    }

    private fun readVideoMeta(context: Context, uri: Uri): VideoMeta {
        val size = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.openFileDescriptor(uri, "r")?.statSize
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.length
            }
        }.getOrNull()?.takeIf { it > 0 }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            VideoMeta(size, duration, w, h, mime)
        } finally {
            retriever.release()
        }
    }

    private fun decodeBounded(context: Context, source: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = com.comunidapp.app.domain.media.MediaIngestionPolicy.sampleSize(
            bounds.outWidth,
            bounds.outHeight,
            maxEdge
        )
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun scaleToMaxEdge(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest.toFloat()
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}

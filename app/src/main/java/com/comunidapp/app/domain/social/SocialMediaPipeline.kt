package com.comunidapp.app.domain.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
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
    val transcoded: Boolean = false,
    val exportDecision: String? = null,
    val bitrateBps: Long? = null
)

object SocialMediaPipeline {

    suspend fun prepare(
        context: Context,
        source: Uri,
        expectVideo: Boolean,
        outputFile: File? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<PreparedSocialMedia> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver
            val displayName = cr.query(
                source,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val ext = displayName?.let { com.comunidapp.app.domain.files.FileNameSanitizer.extensionOf(it) }
            val resolverMime = cr.getType(source)?.lowercase().orEmpty()
            com.comunidapp.app.domain.media.MediaDiagnostic.logStaging(
                "MIME-DIAG-REEL scheme=${source.scheme} ext=$ext resolverMime=$resolverMime expectVideo=$expectVideo"
            )
            onProgress(8)
            if (expectVideo) {
                return@runCatching prepareVideo(
                    context = context,
                    source = source,
                    mime = resolverMime,
                    outputFile = outputFile,
                    onProgress = onProgress
                )
            }
            val mime = when {
                resolverMime.startsWith("video/") -> resolverMime
                resolverMime.isNotBlank() &&
                    !resolverMime.equals("application/octet-stream", ignoreCase = true) -> resolverMime
                ext != null -> {
                    com.comunidapp.app.domain.files.FileValidationRules.inferMimeFromExtension(ext)
                        ?: "image/jpeg"
                }
                else -> "image/jpeg"
            }
            onProgress(8)
            prepareImage(context, source, mime, onProgress)
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
        outputFile: File?,
        onProgress: (Int) -> Unit
    ): PreparedSocialMedia {
        val trackMime = com.comunidapp.app.domain.media.VerifiedVideoPipeline.detectVideoTrackMime(context, source)
            ?: error(com.comunidapp.app.domain.media.MediaDiagnostic.MIME)
        val meta = readVideoMeta(context, source)
        val size = meta.sizeBytes
        if (size != null && size > VideoExportPolicy.RAW_REJECT_BYTES) {
            error(com.comunidapp.app.domain.media.MediaDiagnostic.SIZE)
        }
        val bitrate = VideoExportPolicy.bitrateBps(size, meta.durationMs) ?: meta.bitrateBps
        val codecSignal = listOfNotNull(mime, meta.mimeType, trackMime).joinToString(" ")
        onProgress(20)
        val passthrough = VideoExportPolicy.shouldPassthrough(
            maxEdge = meta.maxEdge,
            bitrateBps = bitrate,
            sizeBytes = size,
            mimeOrCodec = codecSignal,
            durationMs = meta.durationMs
        )
        com.comunidapp.app.domain.media.MediaDiagnostic.logStaging(
            "REEL-EXPORT decision=${if (passthrough) VideoExportPolicy.DECISION_PASSTHROUGH else VideoExportPolicy.DECISION_TRANSCODE} " +
                "size=$size bitrate=$bitrate maxEdge=${meta.maxEdge} codec=$codecSignal"
        )
        if (passthrough) {
            onProgress(70)
            val localSource = source.path
                ?.takeIf { source.scheme.isNullOrBlank() || source.scheme == "file" }
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.length() > 0L }
            val passthroughUri = if (localSource != null) {
                Uri.fromFile(localSource)
            } else if (outputFile != null) {
                copyToPrivate(context, source, outputFile)
                Uri.fromFile(outputFile)
            } else {
                source
            }
            val passthroughSize = localSource?.length()
                ?: outputFile?.takeIf { it.exists() }?.length()
                ?: size
                ?: 0L
            return PreparedSocialMedia(
                uri = passthroughUri,
                mimeType = mime.ifBlank {
                    meta.mimeType?.takeIf { it.startsWith("video/") }
                        ?: VideoExportPolicy.TARGET_VIDEO_MIME
                },
                filename = outputFile?.name ?: "reel.mp4",
                sizeBytes = passthroughSize,
                durationMs = meta.durationMs,
                width = meta.width,
                height = meta.height,
                transcoded = false,
                exportDecision = VideoExportPolicy.DECISION_PASSTHROUGH,
                bitrateBps = bitrate
            )
        }
        val out = outputFile ?: File(context.cacheDir, "leover_export_${System.currentTimeMillis()}.mp4")
        try {
            transcode(context, source, out, meta, onProgress)
        } catch (error: Throwable) {
            out.delete()
            throw error
        }
        val use = out.takeIf { it.exists() && it.length() > 0L } ?: error("VIDEO_TRANSCODE_FAILED")
        if (use.length() > VideoExportPolicy.PROCESSED_HARD_CAP_BYTES) {
            use.delete()
            error(com.comunidapp.app.domain.media.MediaDiagnostic.SIZE)
        }
        val exported = readVideoMeta(context, Uri.fromFile(use))
        onProgress(70)
        return PreparedSocialMedia(
            uri = Uri.fromFile(use),
            mimeType = VideoExportPolicy.TARGET_VIDEO_MIME,
            filename = use.name,
            sizeBytes = use.length(),
            durationMs = exported.durationMs ?: meta.durationMs,
            width = exported.width ?: meta.width,
            height = exported.height ?: meta.height,
            transcoded = true,
            exportDecision = VideoExportPolicy.DECISION_TRANSCODE,
            bitrateBps = VideoExportPolicy.bitrateBps(use.length(), exported.durationMs ?: meta.durationMs)
        )
    }

    private suspend fun transcode(
        context: Context,
        source: Uri,
        output: File,
        meta: VideoMeta,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            val outputEdge = VideoExportPolicy.outputEdge(meta.maxEdge)
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(VideoExportPolicy.targetBitrateBps(outputEdge).toInt())
                        .build()
                )
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(VideoExportPolicy.TARGET_AUDIO_BITRATE_BPS)
                        .build()
                )
                .build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(65)
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
            val videoEffects = if (VideoExportPolicy.needsScale(meta.maxEdge) &&
                meta.width != null && meta.height != null
            ) {
                val (tw, th) = com.comunidapp.app.domain.media.MediaIngestionPolicy.outputSize(
                    meta.width,
                    meta.height,
                    VideoExportPolicy.TARGET_MAX_EDGE
                )
                listOf(Presentation.createForWidthAndHeight(tw, th, Presentation.LAYOUT_SCALE_TO_FIT))
            } else {
                emptyList()
            }
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()
            transformer.start(composition, output.absolutePath)
            cont.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching { transformer.cancel() }
                }
                output.delete()
            }
        }
    }

    private data class VideoMeta(
        val sizeBytes: Long?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val mimeType: String? = null,
        val bitrateBps: Long? = null
    ) {
        val maxEdge: Int? = listOfNotNull(width, height).maxOrNull()
    }

    private fun copyToPrivate(context: Context, source: Uri, dest: File) {
        if (source.scheme == "file" && source.path == dest.absolutePath) return
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("FILE_READ_FAILED")
        if (!dest.exists() || dest.length() <= 0L) error("FILE_READ_FAILED")
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
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
            VideoMeta(size, duration, w, h, mime, bitrate)
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

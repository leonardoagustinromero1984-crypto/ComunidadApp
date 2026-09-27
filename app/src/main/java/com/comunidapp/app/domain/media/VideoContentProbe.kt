package com.comunidapp.app.domain.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Legacy entry point — delegates to [VerifiedVideoPipeline] / [MediaExtractor] track checks.
 */
object VideoContentProbe {

    data class ProbeResult(
        val isVideo: Boolean,
        val mimeType: String? = null,
        val durationMs: Long? = null,
        val width: Int? = null,
        val height: Int? = null
    )

    fun probe(context: Context, uri: Uri): ProbeResult {
        val trackMime = VerifiedVideoPipeline.detectVideoTrackMime(context, uri)
        if (trackMime == null) return ProbeResult(isVideo = false)
        val meta = readMetadata(context, uri)
        return ProbeResult(
            isVideo = true,
            mimeType = VerifiedVideoPipeline.normalizeContainerMime(trackMime),
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height
        )
    }

    internal fun hasVideoTrack(
        durationMs: Long?,
        width: Int?,
        height: Int?,
        mimeType: String?
    ): Boolean {
        if (mimeType?.lowercase()?.startsWith("video/") == true) return true
        if (width != null && width > 0 && height != null && height > 0) return true
        return durationMs != null && durationMs > 0L
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
}

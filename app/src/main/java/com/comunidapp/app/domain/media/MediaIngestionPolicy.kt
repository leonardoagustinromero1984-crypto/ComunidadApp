package com.comunidapp.app.domain.media

import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.ResumableUploadPolicy

/**
 * Single LeoVer media policy. Destinations differ in pixels/quality;
 * infrastructure (inspect → decode → EXIF → resize → compress → upload) is shared.
 *
 * Camera originals are never rejected for being "too heavy" in the UX.
 * Only corrupt files, unsupported formats, and malicious extremes fail.
 * Decode/crop/encode failure never falls back to the original file.
 */
object MediaIngestionPolicy {
    const val NORMAL_PHONE_PHOTO_USER_REJECTION = false
    const val MIB = 1024L * 1024L

    /** Internal safety only — far above any normal phone photo/video. */
    const val MALICIOUS_SOURCE_MAX_BYTES = 200L * MIB
    const val MALICIOUS_PIXEL_EDGE = 16_384

    const val AVATAR_MASTER_EDGE_PX = 1024
    const val PET_MASTER_EDGE_PX = 1920
    const val ORGANIZATION_MASTER_EDGE_PX = 1920
    const val SOCIAL_MASTER_EDGE_PX = 2560
    const val VITACORA_MASTER_EDGE_PX = 2560
    const val JPEG_QUALITY = 88
    const val JPEG_QUALITY_FLOOR = 56

    const val AVATAR_TARGET_BYTES = 2L * MIB
    const val PET_TARGET_BYTES = 4L * MIB
    const val ORGANIZATION_TARGET_BYTES = 4L * MIB
    const val SOCIAL_IMAGE_TARGET_BYTES = 6L * MIB

    /**
     * Processed master caps match STAGING buckets/RPC where they exist:
     * profile-avatars / organization-media = 5 MiB; public-media = 8 MiB.
     * Video hard cap follows the configurable Supabase global limit, not a
     * universal "everything must end at 40 MiB" encode target.
     */
    const val AVATAR_MASTER_MAX_BYTES = 5L * MIB
    const val PET_MASTER_MAX_BYTES = 8L * MIB
    const val ORGANIZATION_MASTER_MAX_BYTES = 5L * MIB
    const val POST_MASTER_MAX_BYTES = 8L * MIB
    const val SOCIAL_IMAGE_HARD_CAP_BYTES = 8L * MIB
    const val STORY_MASTER_MAX_BYTES = SOCIAL_IMAGE_HARD_CAP_BYTES
    const val REEL_MASTER_MAX_BYTES = SOCIAL_IMAGE_HARD_CAP_BYTES
    const val VITACORA_MASTER_MAX_BYTES = SOCIAL_IMAGE_HARD_CAP_BYTES
    const val SUPABASE_GLOBAL_LIMIT_BYTES = 50L * MIB
    const val VIDEO_PROCESSED_HARD_CAP_BYTES = SUPABASE_GLOBAL_LIMIT_BYTES
    const val VIDEO_MASTER_MAX_BYTES = VIDEO_PROCESSED_HARD_CAP_BYTES
    const val VIDEO_RAW_REJECT_BYTES = MALICIOUS_SOURCE_MAX_BYTES
    const val VIDEO_TARGET_EDGE_PX = 1080
    const val VIDEO_FIXED_40MB_POLICY = false

    const val STANDARD_UPLOAD_THRESHOLD_BYTES = ResumableUploadPolicy.STANDARD_THRESHOLD_BYTES

    const val SUPABASE_GLOBAL_FILE_LIMIT = "50MiB"
    const val PROFILE_AVATARS_BUCKET_LIMIT_BYTES = 5L * MIB
    const val ORGANIZATION_MEDIA_BUCKET_LIMIT_BYTES = 5L * MIB
    const val PUBLIC_MEDIA_BUCKET_LIMIT_BYTES = 8L * MIB

    fun destinationFor(purpose: FileAssetPurpose): MediaDestination = when (purpose) {
        FileAssetPurpose.USER_AVATAR, FileAssetPurpose.USER_COVER -> MediaDestination.AVATAR
        FileAssetPurpose.PET_AVATAR, FileAssetPurpose.PET_GALLERY -> MediaDestination.PET
        FileAssetPurpose.ORGANIZATION_LOGO, FileAssetPurpose.ORGANIZATION_COVER ->
            MediaDestination.ORGANIZATION
        FileAssetPurpose.POST_MEDIA, FileAssetPurpose.ADOPTION_MEDIA,
        FileAssetPurpose.LOST_FOUND_MEDIA, FileAssetPurpose.EVENT_MEDIA,
        FileAssetPurpose.PRODUCT_MEDIA, FileAssetPurpose.SERVICE_PROFILE_MEDIA ->
            MediaDestination.POST
        FileAssetPurpose.STORY_MEDIA -> MediaDestination.STORY
        FileAssetPurpose.REEL_MEDIA -> MediaDestination.REEL
        else -> MediaDestination.POST
    }

    fun masterEdgePx(purpose: FileAssetPurpose): Int = when (destinationFor(purpose)) {
        MediaDestination.AVATAR -> AVATAR_MASTER_EDGE_PX
        MediaDestination.PET -> PET_MASTER_EDGE_PX
        MediaDestination.ORGANIZATION -> ORGANIZATION_MASTER_EDGE_PX
        MediaDestination.STORY, MediaDestination.REEL, MediaDestination.VITACORA,
        MediaDestination.POST, MediaDestination.VIDEO -> SOCIAL_MASTER_EDGE_PX
    }

    fun targetBytes(purpose: FileAssetPurpose): Long = when (destinationFor(purpose)) {
        MediaDestination.AVATAR -> AVATAR_TARGET_BYTES
        MediaDestination.PET -> PET_TARGET_BYTES
        MediaDestination.ORGANIZATION -> ORGANIZATION_TARGET_BYTES
        MediaDestination.POST, MediaDestination.STORY, MediaDestination.REEL,
        MediaDestination.VITACORA -> SOCIAL_IMAGE_TARGET_BYTES
        MediaDestination.VIDEO -> VIDEO_PROCESSED_HARD_CAP_BYTES
    }

    fun processedMaxBytes(purpose: FileAssetPurpose, mime: String? = null): Long {
        if (isVideoMime(mime)) return VIDEO_PROCESSED_HARD_CAP_BYTES
        return when (destinationFor(purpose)) {
            MediaDestination.AVATAR -> AVATAR_MASTER_MAX_BYTES
            MediaDestination.PET -> PET_MASTER_MAX_BYTES
            MediaDestination.ORGANIZATION -> ORGANIZATION_MASTER_MAX_BYTES
            MediaDestination.POST, MediaDestination.VITACORA -> POST_MASTER_MAX_BYTES
            MediaDestination.STORY, MediaDestination.REEL ->
                if (isImageMime(mime)) SOCIAL_IMAGE_HARD_CAP_BYTES else VIDEO_PROCESSED_HARD_CAP_BYTES
            MediaDestination.VIDEO -> VIDEO_PROCESSED_HARD_CAP_BYTES
        }
    }

    fun isImageMime(mime: String?): Boolean {
        val lower = mime?.lowercase().orEmpty()
        return lower.startsWith("image/") ||
            lower == "image/heic" ||
            lower == "image/heif"
    }

    fun isVideoMime(mime: String?): Boolean =
        mime?.lowercase()?.startsWith("video/") == true

    fun isHeic(mime: String?): Boolean {
        val lower = mime?.lowercase().orEmpty()
        return lower.contains("heic") || lower.contains("heif")
    }

    fun normalizesBeforeUpload(purpose: FileAssetPurpose, mime: String?): Boolean {
        if (isVideoMime(mime)) return true
        return isImageMime(mime) && destinationFor(purpose) != MediaDestination.VIDEO
    }

    fun rejectSource(sizeBytes: Long, mime: String?): String? {
        if (sizeBytes <= 0L) return "SIZE_INVALID"
        if (sizeBytes > MALICIOUS_SOURCE_MAX_BYTES) return "SOURCE_MALICIOUS_SIZE"
        return null
    }

    fun shouldUseTus(sizeBytes: Long): Boolean =
        ResumableUploadPolicy.shouldUseTus(sizeBytes)

    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val cap = maxEdge.coerceAtLeast(64)
        while (w / sample > cap * 2 || h / sample > cap * 2) {
            sample *= 2
        }
        return sample
    }

    fun outputSize(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
        val longEdge = maxOf(width, height).coerceAtLeast(1)
        if (longEdge <= maxEdge) return width.coerceAtLeast(1) to height.coerceAtLeast(1)
        val scale = maxEdge.toFloat() / longEdge.toFloat()
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
}

enum class MediaDestination {
    AVATAR,
    PET,
    ORGANIZATION,
    POST,
    STORY,
    REEL,
    VITACORA,
    VIDEO
}

package com.comunidapp.app.domain.media

import android.net.Uri
import com.comunidapp.app.domain.files.FileAssetPurpose
import java.io.File

/**
 * Single image pipeline used by onboarding, profile, pet, org, and social.
 * Phone-camera originals are decoded bounded + EXIF + JPEG; callers must not
 * treat the original file size as a user-facing limit.
 */
object ProfileMediaPipeline {
    const val USER_VISIBLE_MEDIA_LIMIT_FOR_NORMAL_PHONE_MEDIA = false
    const val INTERNAL_PROCESSED_IMAGE_MAX_BYTES = MediaIngestionPolicy.AVATAR_MASTER_MAX_BYTES
    const val SKIP_REINGEST_PROCESSED_AVATAR = true

    fun isProcessedAvatarJpeg(uriString: String): Boolean {
        val lower = uriString.lowercase()
        return lower.contains(AvatarPhotoTempStore.DIR_NAME.lowercase()) &&
            lower.contains(AvatarPhotoTempStore.FILE_PREFIX.lowercase()) &&
            (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
    }

    fun resolvedSizeBytes(uriString: String, fallback: Long): Long {
        val fromFile = fileLength(uriString)
        return when {
            fromFile > 1L -> fromFile
            fallback > 1L -> fallback
            fromFile > 0L -> fromFile
            else -> fallback.coerceAtLeast(0L)
        }
    }

    fun fileLength(uriString: String): Long {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return 0L
        return runCatching { File(path).takeIf { it.isFile }?.length() ?: 0L }.getOrDefault(0L)
    }

    fun shouldSkipReingest(uriString: String, purpose: FileAssetPurpose): Boolean {
        if (!SKIP_REINGEST_PROCESSED_AVATAR) return false
        return uriString.contains("leover_ingest_", ignoreCase = true) ||
            uriString.contains("leover_ucrop_out_", ignoreCase = true)
    }
}

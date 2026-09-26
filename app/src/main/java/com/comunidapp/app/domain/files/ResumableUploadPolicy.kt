package com.comunidapp.app.domain.files

data class TusUploadSessionHint(
    val uploadUrl: String,
    val offsetBytes: Long,
    val storagePath: String,
    val physicalBucket: String,
    val localFilePath: String,
    val mimeType: String,
    val totalBytes: Long
)

object ResumableUploadPolicy {
    const val STANDARD_THRESHOLD_BYTES = 6L * 1024L * 1024L
    const val CHUNK_BYTES = 512 * 1024
    const val PROTOCOL = "TUS"

    fun shouldUseTus(sizeBytes: Long): Boolean = sizeBytes > STANDARD_THRESHOLD_BYTES

    fun shouldUseTus(sizeBytes: Long, mimeType: String?): Boolean {
        if (mimeType?.lowercase()?.startsWith("video/") == true) return true
        return shouldUseTus(sizeBytes)
    }
}

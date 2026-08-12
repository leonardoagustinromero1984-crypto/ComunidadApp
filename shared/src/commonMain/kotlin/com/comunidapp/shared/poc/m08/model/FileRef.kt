package com.comunidapp.shared.poc.m08.model

/**
 * Platform-agnostic local file handle for LeoVer KMP POC 2.
 *
 * Intentionally does NOT expose android.net.Uri / NSURL.
 * [platformIdentifier] is an opaque token only understood by platform layers
 * (on Android it typically stores a content Uri string).
 */
data class FileRef(
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val platformIdentifier: String
) {
    init {
        require(name.isNotBlank()) { "FILENAME_REQUIRED" }
        require(sizeBytes > 0L) { "SIZE_INVALID" }
        require(platformIdentifier.isNotBlank()) { "PLATFORM_IDENTIFIER_REQUIRED" }
    }

    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true
}

sealed class ImagePickResult {
    data class Success(val file: FileRef) : ImagePickResult()
    data object Cancelled : ImagePickResult()
    data class Failure(val message: String) : ImagePickResult()
}

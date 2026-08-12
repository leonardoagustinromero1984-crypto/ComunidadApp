package com.comunidapp.shared.poc.m08.domain

import com.comunidapp.shared.poc.m08.model.FileRef

object FileRefRules {
    private val allowedImageMimes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/jpg"
    )

    fun validateForPetAvatar(file: FileRef): Result<FileRef> {
        if (file.sizeBytes > 8L * 1024L * 1024L) {
            return Result.failure(IllegalArgumentException("FILE_TOO_LARGE"))
        }
        val mime = file.mimeType?.lowercase()
        if (mime != null && mime !in allowedImageMimes && !mime.startsWith("image/")) {
            return Result.failure(IllegalArgumentException("MIME_NOT_ALLOWED"))
        }
        return Result.success(file)
    }
}

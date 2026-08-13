package com.comunidapp.shared.media

import com.comunidapp.shared.poc.m08.model.FileRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual fun createFileContentReader(): FileContentReader = IosFileContentReader()

@OptIn(ExperimentalForeignApi::class)
class IosFileContentReader : FileContentReader {
    override suspend fun read(fileRef: FileRef): Result<FileContent> = withContext(Dispatchers.Default) {
        try {
            val url = NSURL.URLWithString(fileRef.platformIdentifier)
                ?: return@withContext Result.failure(IllegalStateException("MEDIA_FILE_UNAVAILABLE"))
            val data = NSData.dataWithContentsOfURL(url)
                ?: return@withContext Result.failure(IllegalStateException("MEDIA_FILE_UNAVAILABLE"))
            val length = data.length.toInt()
            if (length <= 0) {
                return@withContext Result.failure(IllegalArgumentException("MEDIA_FILE_EMPTY"))
            }
            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
            val mime = fileRef.mimeType?.takeIf { it.isNotBlank() } ?: "image/jpeg"
            Result.success(
                FileContent(
                    bytes = bytes,
                    name = fileRef.name,
                    mimeType = mime,
                    sizeBytes = length.toLong()
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

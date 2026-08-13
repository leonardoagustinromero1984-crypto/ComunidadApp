package com.comunidapp.shared.media

import android.content.ContentResolver
import android.net.Uri
import com.comunidapp.shared.poc.m08.model.FileRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createFileContentReader(): FileContentReader =
    AndroidFileContentReader(AndroidContentResolverHolder.contentResolver)

/**
 * Opcional: el host Android puede setear ContentResolver si usa upload en proceso Android.
 */
object AndroidContentResolverHolder {
    @Volatile
    var contentResolver: ContentResolver? = null
}

class AndroidFileContentReader(
    private val resolver: ContentResolver?
) : FileContentReader {
    override suspend fun read(fileRef: FileRef): Result<FileContent> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = resolver
                ?: return@withContext Result.failure(IllegalStateException("MEDIA_READER_UNAVAILABLE"))
            val uri = Uri.parse(fileRef.platformIdentifier)
            val bytes = contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("MEDIA_FILE_UNAVAILABLE"))
            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("MEDIA_FILE_EMPTY"))
            }
            val mime = fileRef.mimeType?.takeIf { it.isNotBlank() }
                ?: contentResolver.getType(uri)
                ?: "image/jpeg"
            Result.success(
                FileContent(
                    bytes = bytes,
                    name = fileRef.name,
                    mimeType = mime,
                    sizeBytes = bytes.size.toLong()
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

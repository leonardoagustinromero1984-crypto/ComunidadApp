package com.comunidapp.app.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileLocalMetadata

interface FileLocalMetadataReader {
    suspend fun read(uriString: String): AppResult<FileLocalMetadata>
}

interface FileBytesReader {
    suspend fun readBytes(uriString: String): AppResult<ByteArray>
}

class AndroidContentFileMetadataReader(
    private val contentResolver: ContentResolver
) : FileLocalMetadataReader {
    override suspend fun read(uriString: String): AppResult<FileLocalMetadata> = try {
        val uri = Uri.parse(uriString)
        var name: String? = null
        var size: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: error("FILENAME_REQUIRED")
        val resolvedSize = size?.takeIf { it > 0L }
            ?: contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
                ?.takeIf { it > 0L }
            ?: error("SIZE_INVALID")
        AppResult.Success(
            FileLocalMetadata(
                originalFilename = resolvedName,
                declaredMimeType = contentResolver.getType(uri),
                sizeBytes = resolvedSize,
                sourceUriString = uriString
            )
        )
    } catch (throwable: Throwable) {
        AppResult.Failure(AppErrorMapper.fromThrowable(throwable))
    }
}

class AndroidFileBytesReader(
    private val contentResolver: ContentResolver
) : FileBytesReader {
    override suspend fun readBytes(uriString: String): AppResult<ByteArray> = try {
        val bytes = contentResolver.openInputStream(Uri.parse(uriString))
            ?.use { it.readBytes() }
            ?: error("FILE_READ_FAILED")
        if (bytes.isEmpty()) error("SIZE_INVALID")
        AppResult.Success(bytes)
    } catch (throwable: Throwable) {
        AppResult.Failure(AppErrorMapper.fromThrowable(throwable))
    }
}

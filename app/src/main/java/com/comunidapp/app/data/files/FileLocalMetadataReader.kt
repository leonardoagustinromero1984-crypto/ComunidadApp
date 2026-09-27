package com.comunidapp.app.data.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FileValidationRules
import com.comunidapp.app.domain.media.MediaDiagnostic

interface FileLocalMetadataReader {
    suspend fun read(uriString: String): AppResult<FileLocalMetadata>
}

interface FileBytesReader {
    suspend fun readBytes(uriString: String): AppResult<ByteArray>

    suspend fun materializeForUpload(uriString: String): AppResult<java.io.File> {
        return when (val bytes = readBytes(uriString)) {
            is AppResult.Failure -> bytes
            is AppResult.Success -> {
                val file = java.io.File.createTempFile("leover_up_", ".bin")
                file.writeBytes(bytes.data)
                AppResult.Success(file)
            }
        }
    }
}

class AndroidContentFileMetadataReader(
    private val contentResolver: ContentResolver
) : FileLocalMetadataReader {
    override suspend fun read(uriString: String): AppResult<FileLocalMetadata> = try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val file = uri.path?.let { java.io.File(it) }?.takeIf { it.exists() }
                ?: error("FILENAME_REQUIRED")
            val ext = FileNameSanitizer.extensionOf(file.name)
            val inferred = ext?.let { FileValidationRules.inferMimeFromExtension(it) }
            return AppResult.Success(
                FileLocalMetadata(
                    originalFilename = file.name.ifBlank { "avatar.jpg" },
                    declaredMimeType = inferred ?: "image/jpeg",
                    sizeBytes = file.length().takeIf { it > 0L } ?: error("SIZE_INVALID"),
                    sourceUriString = uriString
                )
            )
        }
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
        val resolverMime = contentResolver.getType(uri)?.trim()?.takeIf { it.isNotEmpty() }
        val ext = FileNameSanitizer.extensionOf(resolvedName)
        val declaredMime = when {
            !resolverMime.isNullOrBlank() &&
                !resolverMime.equals("application/octet-stream", ignoreCase = true) -> resolverMime
            else -> ext?.let { FileValidationRules.inferMimeFromExtension(it) }
        }
        if (MediaDiagnostic.isStaging) {
            MediaDiagnostic.logStaging(
                "MIME-DIAG scheme=${uri.scheme} host=${uri.host} ext=$ext " +
                    "resolverMime=$resolverMime inferred=$declaredMime size=$resolvedSize"
            )
        }
        AppResult.Success(
            FileLocalMetadata(
                originalFilename = resolvedName,
                declaredMimeType = declaredMime,
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
        val uri = Uri.parse(uriString)
        val bytes = if (uri.scheme == "file") {
            uri.path?.let { java.io.File(it).takeIf { file -> file.exists() }?.readBytes() }
        } else {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: error("FILE_READ_FAILED")
        if (bytes.isEmpty()) error("SIZE_INVALID")
        AppResult.Success(bytes)
    } catch (throwable: Throwable) {
        AppResult.Failure(AppErrorMapper.fromThrowable(throwable))
    }

    override suspend fun materializeForUpload(uriString: String): AppResult<java.io.File> = try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "file") {
            val file = uri.path?.let { java.io.File(it) }?.takeIf { it.exists() }
                ?: error("FILE_READ_FAILED")
            return AppResult.Success(file)
        }
        val out = java.io.File.createTempFile("leover_up_", ".bin")
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("FILE_READ_FAILED")
        if (out.length() <= 0L) error("SIZE_INVALID")
        AppResult.Success(out)
    } catch (throwable: Throwable) {
        AppResult.Failure(AppErrorMapper.fromThrowable(throwable))
    }
}

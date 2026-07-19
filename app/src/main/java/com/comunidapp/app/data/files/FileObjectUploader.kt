package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType

interface FileObjectUploader {
    suspend fun uploadBytes(
        physicalBucket: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): AppResult<Unit>
}

class MockFileObjectUploader : FileObjectUploader {
    override suspend fun uploadBytes(
        physicalBucket: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Int) -> Unit
    ): AppResult<Unit> {
        val denied = validateTarget(physicalBucket, storagePath, bytes, mimeType)
        if (denied != null) return denied
        onProgress(0)
        onProgress(50)
        onProgress(100)
        return AppResult.Success(Unit)
    }
}

class SupabaseFileObjectUploader : FileObjectUploader {
    override suspend fun uploadBytes(
        physicalBucket: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Int) -> Unit
    ): AppResult<Unit> {
        val denied = validateTarget(physicalBucket, storagePath, bytes, mimeType)
        if (denied != null) return denied
        return try {
            onProgress(0)
            supabase.storage.from(physicalBucket).upload(storagePath, bytes) {
                contentType = ContentType.parse(mimeType)
                upsert = false
            }
            onProgress(100)
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppErrorMapper.fromThrowable(throwable))
        }
    }
}

private fun validateTarget(
    physicalBucket: String,
    storagePath: String,
    bytes: ByteArray,
    mimeType: String
): AppResult.Failure? {
    if (physicalBucket.equals("leover", ignoreCase = true)) {
        return fileUploadFailure("LEGACY_BUCKET_DENIED", AppErrorKind.FORBIDDEN)
    }
    if (physicalBucket.isBlank() || storagePath.isBlank() ||
        storagePath.startsWith("content://", ignoreCase = true) ||
        storagePath.startsWith("data:", ignoreCase = true) ||
        bytes.isEmpty() || mimeType.isBlank()
    ) {
        return fileUploadFailure("VALIDATION", AppErrorKind.VALIDATION)
    }
    return null
}

internal fun fileUploadFailure(
    code: String,
    kind: AppErrorKind = AppErrorKind.VALIDATION
): AppResult.Failure = AppResult.Failure(
    AppError(
        kind = kind,
        userMessage = "No pudimos procesar el archivo.",
        technicalMessage = code,
        code = code
    )
)

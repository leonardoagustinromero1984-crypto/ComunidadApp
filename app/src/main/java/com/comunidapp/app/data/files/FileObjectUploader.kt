package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppErrorMapper
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import com.comunidapp.app.domain.files.TusUploadSessionHint
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface FileObjectUploader {
    suspend fun uploadBytes(
        physicalBucket: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Int) -> Unit = {}
    ): AppResult<Unit>

    suspend fun uploadFile(
        physicalBucket: String,
        storagePath: String,
        file: File,
        mimeType: String,
        sizeBytes: Long = file.length(),
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        resumeUrl: String? = null,
        onSession: (TusUploadSessionHint) -> Unit = {}
    ): AppResult<Unit> {
        if (isCancelled()) return fileUploadFailure("CANCELLED")
        return uploadBytes(physicalBucket, storagePath, file.readBytes(), mimeType, onProgress)
    }
}

class MockFileObjectUploader : FileObjectUploader {
    val usedTus = AtomicBoolean(false)
    val lastOffset = AtomicLong(0)
    val failOnce = AtomicBoolean(false)
    val resumeUrls = AtomicReference<String?>(null)
    val cancelledSeen = AtomicBoolean(false)

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

    override suspend fun uploadFile(
        physicalBucket: String,
        storagePath: String,
        file: File,
        mimeType: String,
        sizeBytes: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        resumeUrl: String?,
        onSession: (TusUploadSessionHint) -> Unit
    ): AppResult<Unit> {
        val bytes = if (file.exists()) file.readBytes() else ByteArray(sizeBytes.coerceAtLeast(1).toInt().coerceAtMost(64))
        val denied = validateTarget(physicalBucket, storagePath, bytes.takeIf { it.isNotEmpty() } ?: byteArrayOf(1), mimeType)
        if (denied != null) return denied
        if (ResumableUploadPolicy.shouldUseTus(sizeBytes)) {
            usedTus.set(true)
            var offset = if (!resumeUrl.isNullOrBlank()) lastOffset.get() else 0L
            val url = resumeUrl ?: "tus://mock/$storagePath"
            resumeUrls.set(url)
            val total = sizeBytes.coerceAtLeast(1L)
            while (offset < total) {
                if (isCancelled()) {
                    cancelledSeen.set(true)
                    return fileUploadFailure("CANCELLED")
                }
                offset = (offset + ResumableUploadPolicy.CHUNK_BYTES).coerceAtMost(total)
                lastOffset.set(offset)
                onSession(
                    TusUploadSessionHint(
                        uploadUrl = url,
                        offsetBytes = offset,
                        storagePath = storagePath,
                        physicalBucket = physicalBucket,
                        localFilePath = file.absolutePath,
                        mimeType = mimeType,
                        totalBytes = total
                    )
                )
                onProgress(((offset * 100) / total).toInt().coerceIn(0, 99))
                if (failOnce.getAndSet(false) && offset < total) {
                    return fileUploadFailure("NETWORK")
                }
            }
            onProgress(100)
            return AppResult.Success(Unit)
        }
        return uploadBytes(physicalBucket, storagePath, bytes, mimeType, onProgress)
    }
}

class SupabaseFileObjectUploader(
    private val tus: SupabaseTusUploader = SupabaseTusUploader()
) : FileObjectUploader {
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

    override suspend fun uploadFile(
        physicalBucket: String,
        storagePath: String,
        file: File,
        mimeType: String,
        sizeBytes: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        resumeUrl: String?,
        onSession: (TusUploadSessionHint) -> Unit
    ): AppResult<Unit> {
        val probe = byteArrayOf(1)
        val denied = validateTarget(physicalBucket, storagePath, probe, mimeType)
        if (denied != null) return denied
        if (!file.exists() || sizeBytes <= 0L) return fileUploadFailure("VALIDATION")
        return if (ResumableUploadPolicy.shouldUseTus(sizeBytes)) {
            tus.uploadFile(
                physicalBucket = physicalBucket,
                storagePath = storagePath,
                file = file,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                onProgress = onProgress,
                isCancelled = isCancelled,
                resumeUrl = resumeUrl,
                onSession = onSession
            )
        } else {
            uploadBytes(physicalBucket, storagePath, file.readBytes(), mimeType, onProgress)
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

package com.comunidapp.app.data.files

import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import com.comunidapp.app.domain.files.TusUploadSessionHint
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readRemaining
import java.io.File
import java.io.RandomAccessFile
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseTusUploader(
    private val http: HttpClient = HttpClient(Android) {
        expectSuccess = false
    }
) {
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
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = supabase.auth.currentSessionOrNull()?.accessToken
                ?: error("NOT_AUTHENTICATED")
            val base = BuildConfig.SUPABASE_URL.trimEnd('/')
            val createUrl = "$base/storage/v1/upload/resumable"
            var uploadUrl = resumeUrl
            var offset = 0L
            if (!uploadUrl.isNullOrBlank()) {
                offset = headOffset(uploadUrl, token)
            } else {
                uploadUrl = create(
                    createUrl = createUrl,
                    token = token,
                    bucket = physicalBucket,
                    objectName = storagePath,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes
                )
                offset = 0L
            }
            onSession(
                TusUploadSessionHint(
                    uploadUrl = uploadUrl,
                    offsetBytes = offset,
                    storagePath = storagePath,
                    physicalBucket = physicalBucket,
                    localFilePath = file.absolutePath,
                    mimeType = mimeType,
                    totalBytes = sizeBytes
                )
            )
            RandomAccessFile(file, "r").use { raf ->
                while (offset < sizeBytes) {
                    if (isCancelled()) error("CANCELLED")
                    val chunk = minOf(ResumableUploadPolicy.CHUNK_BYTES.toLong(), sizeBytes - offset).toInt()
                    val bytes = ByteArray(chunk)
                    raf.seek(offset)
                    raf.readFully(bytes)
                    offset = patch(uploadUrl, token, offset, bytes)
                    val percent = ((offset * 100L) / sizeBytes).toInt().coerceIn(0, 99)
                    onProgress(percent)
                    onSession(
                        TusUploadSessionHint(
                            uploadUrl = uploadUrl,
                            offsetBytes = offset,
                            storagePath = storagePath,
                            physicalBucket = physicalBucket,
                            localFilePath = file.absolutePath,
                            mimeType = mimeType,
                            totalBytes = sizeBytes
                        )
                    )
                }
            }
            onProgress(100)
        }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = { throwable ->
                if (throwable.message == "CANCELLED") {
                    fileUploadFailure("CANCELLED")
                } else {
                    AppResult.Failure(
                        com.comunidapp.app.core.result.AppErrorMapper.fromThrowable(throwable)
                    )
                }
            }
        )
    }

    private suspend fun create(
        createUrl: String,
        token: String,
        bucket: String,
        objectName: String,
        mimeType: String,
        sizeBytes: Long
    ): String {
        val metadata = listOf(
            "bucketName ${b64(bucket)}",
            "objectName ${b64(objectName)}",
            "contentType ${b64(mimeType)}",
            "cacheControl ${b64("3600")}"
        ).joinToString(",")
        val response = http.request(createUrl) {
            method = HttpMethod.Post
            headers {
                append("Authorization", "Bearer $token")
                append("apikey", BuildConfig.SUPABASE_ANON_KEY)
                append("Tus-Resumable", "1.0.0")
                append("Upload-Length", sizeBytes.toString())
                append("Upload-Metadata", metadata)
                append("x-upsert", "false")
            }
        }
        if (response.status != HttpStatusCode.Created &&
            response.status != HttpStatusCode.OK
        ) {
            error("TUS_CREATE_${response.status.value}")
        }
        val location = response.headers["Location"] ?: response.headers["location"]
            ?: error("TUS_LOCATION_MISSING")
        return if (location.startsWith("http")) location
        else BuildConfig.SUPABASE_URL.trimEnd('/') + location
    }

    private suspend fun headOffset(uploadUrl: String, token: String): Long {
        val response = http.request(uploadUrl) {
            method = HttpMethod.Head
            headers {
                append("Authorization", "Bearer $token")
                append("apikey", BuildConfig.SUPABASE_ANON_KEY)
                append("Tus-Resumable", "1.0.0")
            }
        }
        return response.headers["Upload-Offset"]?.toLongOrNull()
            ?: response.headers["upload-offset"]?.toLongOrNull()
            ?: 0L
    }

    private suspend fun patch(
        uploadUrl: String,
        token: String,
        offset: Long,
        bytes: ByteArray
    ): Long {
        val response: HttpResponse = http.request(uploadUrl) {
            method = HttpMethod.Patch
            headers {
                append("Authorization", "Bearer $token")
                append("apikey", BuildConfig.SUPABASE_ANON_KEY)
                append("Tus-Resumable", "1.0.0")
                append("Upload-Offset", offset.toString())
                append("Content-Type", "application/offset+octet-stream")
            }
            setBody(bytes)
        }
        if (response.status.value !in 200..204) {
            // consume body to free connection
            runCatching { response.bodyAsChannel().readRemaining() }
            error("TUS_PATCH_${response.status.value}")
        }
        return response.headers["Upload-Offset"]?.toLongOrNull()
            ?: response.headers["upload-offset"]?.toLongOrNull()
            ?: (offset + bytes.size)
    }

    private fun b64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}

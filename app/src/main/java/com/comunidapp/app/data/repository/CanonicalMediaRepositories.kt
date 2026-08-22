package com.comunidapp.app.data.repository

import com.comunidapp.app.core.config.AppConfigProvider
import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.files.FileObjectUploader
import com.comunidapp.app.data.files.SupabaseFileObjectUploader
import com.comunidapp.app.data.files.fileUploadFailure
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalMediaAssetRow
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.failureFromThrowable
import com.comunidapp.app.data.repository.M04SupabaseRpcSupport.runRpc
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalMedia
import com.comunidapp.app.domain.media.MediaDiagnostic
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FilePathBuildRequest
import com.comunidapp.app.domain.files.FilePathBuilder
import com.comunidapp.app.domain.files.FileProcessingStatus
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileSignedAccess
import com.comunidapp.app.domain.files.FileSignedAccessRules
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionState
import com.comunidapp.app.domain.files.FileValidationRules
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class CanonicalFileObjectUploader(
    private val inner: FileObjectUploader = SupabaseFileObjectUploader()
) : FileObjectUploader {
    override suspend fun uploadBytes(
        physicalBucket: String,
        storagePath: String,
        bytes: ByteArray,
        mimeType: String,
        onProgress: (Int) -> Unit
    ): AppResult<Unit> {
        if (!CanonicalMedia.isCanonicalBucket(physicalBucket)) {
            return fileUploadFailure("LEGACY_BUCKET_DENIED", AppErrorKind.FORBIDDEN)
        }
        return when (val result = inner.uploadBytes(physicalBucket, storagePath, bytes, mimeType, onProgress)) {
            is AppResult.Success -> result
            is AppResult.Failure -> AppResult.Failure(
                result.error.copy(
                    code = CanonicalMedia.STEP_STORAGE_UPLOAD,
                    technicalMessage = "${CanonicalMedia.STEP_STORAGE_UPLOAD}: ${result.error.technicalMessage}"
                )
            )
        }
    }

    override suspend fun uploadFile(
        physicalBucket: String,
        storagePath: String,
        file: java.io.File,
        mimeType: String,
        sizeBytes: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
        resumeUrl: String?,
        onSession: (com.comunidapp.app.domain.files.TusUploadSessionHint) -> Unit
    ): AppResult<Unit> {
        if (!CanonicalMedia.isCanonicalBucket(physicalBucket)) {
            return fileUploadFailure("LEGACY_BUCKET_DENIED", AppErrorKind.FORBIDDEN)
        }
        return when (
            val result = inner.uploadFile(
                physicalBucket, storagePath, file, mimeType, sizeBytes,
                onProgress, isCancelled, resumeUrl, onSession
            )
        ) {
            is AppResult.Success -> result
            is AppResult.Failure -> AppResult.Failure(
                result.error.copy(
                    code = CanonicalMedia.STEP_STORAGE_UPLOAD,
                    technicalMessage = "${CanonicalMedia.STEP_STORAGE_UPLOAD}: ${result.error.technicalMessage}"
                )
            )
        }
    }
}

class CanonicalFileUploadRepository : FileUploadRepository {
    private val sessions = ConcurrentHashMap<String, FileUploadSession>()

    override suspend fun prepareUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long?
    ): AppResult<PreparedFileUpload> {
        FileValidationRules.validateUploadRequest(request)
            .getOrElse { return stepFail(CanonicalMedia.STEP_REGISTER_MEDIA, it.message ?: "VALIDATION") }
        FileNameSanitizer.sanitize(request.originalFilename)
            .getOrElse { return stepFail(CanonicalMedia.STEP_REGISTER_MEDIA, it.message ?: "FILENAME") }
        val ownerUserId = when (val owner = request.owner) {
            is FileAssetOwner.User -> owner.userId
            else -> createdByUserId
        }
        if (ownerUserId.isBlank()) {
            return stepFail(CanonicalMedia.STEP_REGISTER_MEDIA, "OWNER_REQUIRED")
        }
        val pathId = UUID.randomUUID().toString()
        val storagePath = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = request.purpose,
                owner = request.owner,
                assetId = pathId,
                safeFilename = request.originalFilename,
                resourceRef = request.resourceRef
            )
        ).getOrElse {
            return stepFail(CanonicalMedia.STEP_REGISTER_MEDIA, it.message ?: "PATH")
        }
        val physicalBucket = CanonicalMedia.physicalBucketForPurpose(request.purpose)
        val logicalBucket = when (physicalBucket) {
            CanonicalMedia.BUCKET_PUBLIC -> FileLogicalBucket.PUBLIC_MEDIA
            CanonicalMedia.BUCKET_MODERATION -> FileLogicalBucket.MODERATION_EVIDENCE
            else -> FileLogicalBucket.PUBLIC_MEDIA
        }
        if (supabase.auth.currentUserOrNull() == null) {
            return stepFail("REGISTER_MEDIA_AUTH", "NOT_AUTHENTICATED")
        }
        val assetId = try {
            registerMediaAsset(
                physicalBucket = physicalBucket,
                storagePath = storagePath,
                mime = request.declaredMimeType ?: "image/jpeg",
                sizeBytes = request.sizeBytes
            )
        } catch (error: RegisterMediaParseException) {
            return stepFail(error.code, error.code)
        } catch (error: RegisterMediaException) {
            return stepFail(error.code, error.code)
        } catch (error: Exception) {
            val classified = RegisterFailure.classify(error)
            return stepFail(classified.code, classified.code)
        }
        val sessionId = "canon-up-$pathId"
        val session = FileUploadSession(
            id = sessionId,
            assetId = assetId,
            versionId = pathId,
            state = FileUploadSessionState.CREATED,
            progressPercent = 0,
            createdAtEpochMs = nowEpochMs,
            expiresAtEpochMs = clockExpiresAtEpochMs
        )
        sessions[sessionId] = session
        return AppResult.Success(
            PreparedFileUpload(
                session = session,
                physicalBucket = physicalBucket,
                storagePath = storagePath,
                assetId = assetId,
                versionId = pathId,
                logicalBucket = logicalBucket
            )
        )
    }

    override suspend fun createUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long?
    ): AppResult<FileUploadSession> = when (
        val prepared = prepareUploadSession(request, createdByUserId, nowEpochMs, clockExpiresAtEpochMs)
    ) {
        is AppResult.Success -> AppResult.Success(prepared.data.session)
        is AppResult.Failure -> prepared
    }

    override suspend fun validateUpload(sessionId: String): AppResult<FileUploadSession> =
        mutate(sessionId) { it.copy(state = FileUploadSessionState.READY_TO_UPLOAD) }

    override suspend fun startUpload(sessionId: String): AppResult<FileUploadSession> =
        mutate(sessionId) { it.copy(state = FileUploadSessionState.UPLOADING) }

    override suspend fun updateProgress(
        sessionId: String,
        progressPercent: Int
    ): AppResult<FileUploadSession> =
        mutate(sessionId) { it.copy(progressPercent = progressPercent.coerceIn(0, 100)) }

    override suspend fun completeUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> =
        mutate(sessionId) {
            it.copy(state = FileUploadSessionState.COMPLETED, progressPercent = 100)
        }

    override suspend fun failUpload(
        sessionId: String,
        failureCode: String
    ): AppResult<FileUploadSession> =
        mutate(sessionId) { it.copy(state = FileUploadSessionState.FAILED, failureCode = failureCode) }

    override suspend fun cancelUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> =
        mutate(sessionId) { it.copy(state = FileUploadSessionState.CANCELLED) }

    private fun mutate(
        sessionId: String,
        transform: (FileUploadSession) -> FileUploadSession
    ): AppResult<FileUploadSession> {
        val current = sessions[sessionId] ?: return fileUploadFailure("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = transform(current)
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    private suspend fun registerMediaAsset(
        physicalBucket: String,
        storagePath: String,
        mime: String,
        sizeBytes: Long
    ): String {
        return try {
            callRegisterMedia(physicalBucket, storagePath, mime, sizeBytes)
        } catch (error: Exception) {
            existingMediaAssetId(physicalBucket, storagePath)?.let { return it }
            val classified = RegisterFailure.classify(error)
            if (classified.stage == "CONFLICT") {
                existingMediaAssetId(physicalBucket, storagePath)?.let { return it }
                    ?: throw RegisterMediaException("RECOVERY", classified.httpStatus, classified.pgCode, error)
            }
            val signal = error.message.orEmpty().uppercase()
            val authish = classified.stage == "AUTH" || "JWT" in signal || "401" in signal ||
                "NOT_AUTHENTICATED" in signal || "PGRST301" in signal
            if (authish && supabase.auth.currentUserOrNull() != null) {
                runCatching { supabase.auth.refreshCurrentSession() }
                try {
                    callRegisterMedia(physicalBucket, storagePath, mime, sizeBytes)
                } catch (retry: Exception) {
                    existingMediaAssetId(physicalBucket, storagePath)
                        ?: throw RegisterFailure.asException(retry)
                }
            } else {
                throw RegisterFailure.asException(error)
            }
        }
    }

    private suspend fun callRegisterMedia(
        physicalBucket: String,
        storagePath: String,
        mime: String,
        sizeBytes: Long
    ): String {
        val authPresent = supabase.auth.currentUserOrNull() != null
        MediaDiagnostic.logStaging("MEDIA-REGISTER-AUTH=${if (authPresent) "YES" else "NO"}")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-BUCKET=$physicalBucket")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-PATH-NAME=${storagePath.substringAfterLast('/')}")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-MIME=$mime")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-SIZE=$sizeBytes")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-STORAGE-BEFORE-RPC=NO")
        val result = try {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_REGISTER_MEDIA,
                parameters = buildJsonObject {
                    put("p_bucket", physicalBucket)
                    put("p_path", storagePath)
                    put("p_mime", mime)
                    put("p_size", sizeBytes)
                }
            )
        } catch (error: Exception) {
            val classified = RegisterFailure.classify(error)
            MediaDiagnostic.logStaging("MEDIA-REGISTER-HTTP-OK=NO")
            MediaDiagnostic.logStaging("MEDIA-REGISTER-HTTP=${classified.httpStatus ?: "NA"}")
            MediaDiagnostic.logStaging("MEDIA-REGISTER-PGRST=${classified.pgCode ?: "NA"}")
            MediaDiagnostic.logStaging("MEDIA-REGISTER-STAGE=${classified.stage}")
            throw RegisterFailure.asException(error)
        }
        MediaDiagnostic.logStaging("MEDIA-REGISTER-HTTP-OK=YES")
        val body = result.data
        val decoded = RegisterMediaUuid.decode(body)
        MediaDiagnostic.logStaging("MEDIA-REGISTER-RESPONSE-KIND=${decoded.kind}")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-DATA-START=${decoded.dataStart}")
        if (decoded.id != null) {
            MediaDiagnostic.logStaging("MEDIA-REGISTER-ID-PARSED=YES")
            return decoded.id
        }
        MediaDiagnostic.logStaging("MEDIA-REGISTER-ID-PARSED=NO")
        MediaDiagnostic.logStaging("MEDIA-REGISTER-PARSE-TYPE=${decoded.kind}")
        existingMediaAssetId(physicalBucket, storagePath)?.let { recovered ->
            MediaDiagnostic.logStaging("MEDIA-REGISTER-ID-PARSED=YES")
            return recovered
        }
        throw RegisterMediaParseException(decoded.kind)
    }

    private suspend fun existingMediaAssetId(bucket: String, objectPath: String): String? =
        runCatching { uuidOrNull(selectMediaByPath(bucket, objectPath)?.id) }.getOrNull()

    private fun uuidOrNull(raw: String?): String? =
        runCatching { UUID.fromString(raw?.trim()).toString() }.getOrNull()

    private fun stepFail(step: String, technical: String): AppResult.Failure =
        AppResult.Failure(
            AppError(
                kind = AppErrorKind.SERVER,
                userMessage = "No pudimos procesar el archivo.",
                technicalMessage = "$step: $technical",
                code = step
            )
        )
}

class CanonicalFileAssetRepository : FileAssetRepository {

    override suspend fun createDraftAsset(
        purpose: FileAssetPurpose,
        owner: FileAssetOwner,
        visibility: FileAssetVisibility,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<FileAsset> = fileUploadFailure("CANONICAL_MEDIA_REGISTER_FIRST")

    override suspend fun getAsset(assetId: String): AppResult<FileAsset> = runRpc {
        selectMedia(assetId)?.toFileAsset() ?: error(CanonicalMedia.STEP_MEDIA_SELECT)
    }.let { result ->
        if (result is AppResult.Failure) {
            AppResult.Failure(
                result.error.copy(
                    code = CanonicalMedia.STEP_MEDIA_SELECT,
                    technicalMessage = "${CanonicalMedia.STEP_MEDIA_SELECT}: ${result.error.technicalMessage}"
                )
            )
        } else {
            result
        }
    }

    override suspend fun listAssetsForResource(resource: FileResourceRef): AppResult<List<FileAsset>> =
        AppResult.Success(emptyList())

    override suspend fun linkAsset(assetId: String, link: FileAssetLink): AppResult<FileAssetLink> =
        AppResult.Success(link.copy(assetId = assetId))

    override suspend fun unlinkAsset(
        assetId: String,
        resource: FileResourceRef,
        relationType: FileRelationType
    ): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun markDeleted(assetId: String, nowEpochMs: Long): AppResult<FileAsset> =
        fileUploadFailure("MEDIA_DELETE_DEFERRED", AppErrorKind.FORBIDDEN)

    override suspend fun restoreAsset(assetId: String, nowEpochMs: Long): AppResult<FileAsset> =
        fileUploadFailure("MEDIA_RESTORE_DEFERRED")
}

class CanonicalFileDownloadRepository(
    private val assets: FileAssetRepository = CanonicalFileAssetRepository()
) : FileDownloadRepository {

    override suspend fun requestAccess(
        request: FileAccessRequest,
        context: FileAuthContext
    ): AppResult<FileAccessDecision> =
        when (val result = assets.getAsset(request.assetId)) {
            is AppResult.Success -> AppResult.Success(FileAuthorization.canRead(context, result.data))
            is AppResult.Failure -> result
        }

    override suspend fun resolvePublicAsset(assetId: String): AppResult<FileAsset> =
        assets.getAsset(assetId)

    override suspend fun requestSignedUrl(
        request: FileAccessRequest,
        context: FileAuthContext,
        nowEpochMs: Long
    ): AppResult<FileSignedAccess> {
        val asset = when (val result = assets.getAsset(request.assetId)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val decision = FileAuthorization.canRead(context, asset)
        if (decision != FileAccessDecision.ALLOWED) {
            return failureFromThrowable(IllegalStateException("FORBIDDEN_${decision.name}"))
        }
        val row = try {
            selectMedia(request.assetId) ?: error(CanonicalMedia.STEP_MEDIA_SELECT)
        } catch (error: Exception) {
            return AppResult.Failure(
                AppError(
                    kind = AppErrorKind.NOT_FOUND,
                    userMessage = "No pudimos mostrar la foto.",
                    technicalMessage = "${CanonicalMedia.STEP_MEDIA_SELECT}: ${error.message}",
                    code = CanonicalMedia.STEP_MEDIA_SELECT
                )
            )
        }
        val ttl = FileSignedAccessRules.ttlSeconds(request.ttlClass)
        val temporaryUrl = try {
            if (CanonicalMedia.isPublicBucket(row.bucket)) {
                val base = AppConfigProvider.get().supabaseUrl
                    ?: com.comunidapp.app.BuildConfig.SUPABASE_URL
                CanonicalMedia.publicObjectUrl(base, row.bucket, row.objectPath)
            } else {
                supabase.storage.from(row.bucket).createSignedUrl(
                    path = row.objectPath,
                    expiresIn = ttl.seconds
                )
            }
        } catch (error: Exception) {
            return AppResult.Failure(
                AppError(
                    kind = AppErrorKind.SERVER,
                    userMessage = "No pudimos mostrar la foto.",
                    technicalMessage = "${CanonicalMedia.STEP_SIGNED_URL_RESOLUTION}: ${error.message}",
                    code = CanonicalMedia.STEP_SIGNED_URL_RESOLUTION
                )
            )
        }
        return AppResult.Success(
            FileSignedAccess(
                assetId = asset.id,
                temporaryUrl = temporaryUrl,
                expiresAtEpochMs = nowEpochMs + ttl * 1000L,
                ttlClass = request.ttlClass
            )
        )
    }
}

internal suspend fun selectMedia(assetId: String): CanonicalMediaAssetRow? {
    val result = supabase.from(CanonicalBackend.MEDIA_ASSETS)
        .select { filter { eq("id", assetId) } }
    val element = Json.parseToJsonElement(result.data)
    return M08RpcDecoding.decodeRows<CanonicalMediaAssetRow>(element).firstOrNull()
}

internal suspend fun selectMediaByPath(bucket: String, objectPath: String): CanonicalMediaAssetRow? {
    val result = supabase.from(CanonicalBackend.MEDIA_ASSETS)
        .select {
            filter {
                eq("bucket", bucket)
                eq("object_path", objectPath)
            }
        }
    val element = Json.parseToJsonElement(result.data)
    return M08RpcDecoding.decodeRows<CanonicalMediaAssetRow>(element).firstOrNull()
}

private class RegisterMediaException(
    val stage: String,
    val httpStatus: Int?,
    val pgCode: String?,
    cause: Throwable? = null
) : IllegalStateException("REGISTER_MEDIA_$stage", cause) {
    val code: String = "REGISTER_MEDIA_$stage"
}

private class RegisterMediaParseException(val kind: String) :
    IllegalStateException("REGISTER_MEDIA_PARSE_$kind") {
    val code: String = "REGISTER_MEDIA_PARSE_$kind"
}

private object RegisterFailure {
    data class Classified(val stage: String, val httpStatus: Int?, val pgCode: String?) {
        val code: String = "REGISTER_MEDIA_$stage"
    }

    fun asException(error: Throwable): RegisterMediaException {
        if (error is RegisterMediaException) return error
        val classified = classify(error)
        return RegisterMediaException(classified.stage, classified.httpStatus, classified.pgCode, error)
    }

    fun classify(error: Throwable): Classified {
        val rest = generateSequence(error) { it.cause }.firstOrNull { it is RestException } as? RestException
        val http = rest?.statusCode
        val pg = rest?.error?.takeIf { it.isNotBlank() }
            ?: rest?.description?.takeIf { it.isNotBlank() }
        val signal = listOf(
            error::class.java.simpleName,
            error.message,
            rest?.error,
            rest?.description,
            rest?.message,
            pg
        ).joinToString(" ").uppercase()
        val stage = when {
            http == 401 || "PGRST301" in signal || "JWT" in signal ||
                "NOT_AUTHENTICATED" in signal || "23503" in signal ||
                "FOREIGN KEY" in signal || "PERSONS" in signal -> "AUTH"
            "23505" in signal || "UNIQUE" in signal || "DUPLICATE" in signal ||
                "CONFLICT" in signal -> "CONFLICT"
            http == 403 || "42501" in signal || "RLS" in signal ||
                "PERMISSION" in signal -> "RLS"
            "PGRST202" in signal || "SCHEMA CACHE" in signal ||
                ("FUNCTION" in signal && "NOT FOUND" in signal) -> "RPC"
            http != null && http >= 400 -> "RPC"
            else -> "UNKNOWN"
        }
        return Classified(stage, http, pg ?: rest?.error)
    }
}

/**
 * SQL contract: canon_register_media(...) RETURNS uuid.
 * PostgREST serializes that scalar as a JSON string, or as the raw uuid text.
 */
private object RegisterMediaUuid {
    data class Decoded(val kind: String, val dataStart: String, val id: String?)

    fun decode(raw: String): Decoded {
        val dataStart = dataStartOf(raw)
        if (raw.isBlank()) return Decoded("empty", dataStart, null)
        val element = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        if (element == null) {
            val id = uuidOrNull(raw)
            return Decoded(if (id != null) "primitive" else "unknown", dataStart, id)
        }
        val kind = when (element) {
            JsonNull -> "empty"
            is JsonPrimitive -> "primitive"
            is JsonArray -> "array"
            is JsonObject -> "object"
        }
        val id = when (element) {
            is JsonPrimitive -> uuidOrNull(element.content)
            else -> null
        }
        return Decoded(kind, dataStart, id)
    }

    private fun dataStartOf(raw: String): String {
        val first = raw.trimStart().firstOrNull() ?: return "empty"
        return when (first) {
            '"' -> "quote"
            '{' -> "brace"
            '[' -> "bracket"
            else -> if (first.isLetterOrDigit()) "scalar" else "other"
        }
    }

    private fun uuidOrNull(raw: String?): String? =
        runCatching { UUID.fromString(raw?.trim()).toString() }.getOrNull()
}

internal fun CanonicalMediaAssetRow.toFileAsset(): FileAsset {
    val owner = when (ownerKind.trim().uppercase()) {
        "ORGANIZATION" -> FileAssetOwner.Organization(ownerOrganizationId.orEmpty())
        "PLATFORM" -> FileAssetOwner.Platform()
        else -> FileAssetOwner.User(ownerPersonId ?: createdBy.orEmpty())
    }
    val ready = lifecycleStatus.equals("READY", ignoreCase = true)
    return FileAsset(
        id = id,
        owner = owner,
        purpose = CanonicalMedia.inferPurpose(objectPath),
        visibility = CanonicalMedia.displayVisibility(bucket, visibility),
        status = if (ready) FileAssetStatus.READY else FileAssetStatus.DRAFT,
        createdByUserId = createdBy ?: ownerPersonId.orEmpty(),
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
        processingStatus = FileProcessingStatus.NOT_REQUIRED
    )
}

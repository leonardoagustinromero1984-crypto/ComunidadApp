package com.comunidapp.app.data.repository

import com.comunidapp.app.core.result.AppError
import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.domain.files.FileAccessRequest
import com.comunidapp.app.domain.files.FileAsset
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetLinkRules
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetRules
import com.comunidapp.app.domain.files.FileAssetStatus
import com.comunidapp.app.domain.files.FileAssetVersion
import com.comunidapp.app.domain.files.FileAssetVersionRules
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileNameSanitizer
import com.comunidapp.app.domain.files.FilePathBuildRequest
import com.comunidapp.app.domain.files.FilePathBuilder
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileRetentionPolicy
import com.comunidapp.app.domain.files.FileRetentionRules
import com.comunidapp.app.domain.files.FileSignedAccess
import com.comunidapp.app.domain.files.FileSignedAccessRules
import com.comunidapp.app.domain.files.FileSignedTtlClass
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionRules
import com.comunidapp.app.domain.files.FileUploadSessionState
import com.comunidapp.app.domain.files.FileValidationRules
import com.comunidapp.app.domain.files.FileVersionStatus
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.files.authorization.FileAccessDecision
import com.comunidapp.app.domain.files.authorization.FileAuthContext
import com.comunidapp.app.domain.files.authorization.FileAuthorization
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

interface FileAssetRepository {
    suspend fun createDraftAsset(
        purpose: FileAssetPurpose,
        owner: FileAssetOwner,
        visibility: FileAssetVisibility,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<FileAsset>

    suspend fun getAsset(assetId: String): AppResult<FileAsset>

    suspend fun listAssetsForResource(resource: FileResourceRef): AppResult<List<FileAsset>>

    suspend fun linkAsset(
        assetId: String,
        link: FileAssetLink
    ): AppResult<FileAssetLink>

    suspend fun unlinkAsset(
        assetId: String,
        resource: FileResourceRef,
        relationType: FileRelationType
    ): AppResult<Unit>

    suspend fun markDeleted(assetId: String, nowEpochMs: Long): AppResult<FileAsset>

    suspend fun restoreAsset(assetId: String, nowEpochMs: Long): AppResult<FileAsset>
}

interface FileUploadRepository {
    suspend fun prepareUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long? = null
    ): AppResult<PreparedFileUpload>

    suspend fun createUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long? = null
    ): AppResult<FileUploadSession>

    suspend fun validateUpload(sessionId: String): AppResult<FileUploadSession>

    suspend fun startUpload(sessionId: String): AppResult<FileUploadSession>

    suspend fun updateProgress(sessionId: String, progressPercent: Int): AppResult<FileUploadSession>

    suspend fun completeUpload(sessionId: String, nowEpochMs: Long): AppResult<FileUploadSession>

    suspend fun failUpload(sessionId: String, failureCode: String): AppResult<FileUploadSession>

    suspend fun cancelUpload(sessionId: String, nowEpochMs: Long): AppResult<FileUploadSession>
}

interface FileDownloadRepository {
    suspend fun requestAccess(
        request: FileAccessRequest,
        context: FileAuthContext
    ): AppResult<FileAccessDecision>

    suspend fun resolvePublicAsset(assetId: String): AppResult<FileAsset>

    suspend fun requestSignedUrl(
        request: FileAccessRequest,
        context: FileAuthContext,
        nowEpochMs: Long
    ): AppResult<FileSignedAccess>
}

interface FileAccessRepository {
    suspend fun canRead(context: FileAuthContext, assetId: String): AppResult<FileAccessDecision>
    suspend fun canUpload(
        context: FileAuthContext,
        purpose: FileAssetPurpose,
        owner: FileAssetOwner
    ): AppResult<FileAccessDecision>
    suspend fun canReplace(context: FileAuthContext, assetId: String): AppResult<FileAccessDecision>
    suspend fun canDelete(
        context: FileAuthContext,
        assetId: String,
        nowEpochMs: Long
    ): AppResult<FileAccessDecision>
}

interface FileRetentionRepository {
    suspend fun getRetentionPolicy(purpose: FileAssetPurpose): AppResult<FileRetentionPolicy>
    suspend fun canPhysicallyDelete(assetId: String, nowEpochMs: Long): AppResult<Boolean>
    suspend fun requestLegalHold(assetId: String): AppResult<FileAsset>
    suspend fun releaseLegalHold(assetId: String): AppResult<FileAsset>
}

private fun fileRepoFail(code: String, kind: AppErrorKind = AppErrorKind.VALIDATION): AppResult.Failure =
    AppResult.Failure(
        AppError(
            kind = kind,
            userMessage = "Operación de archivo no válida.",
            technicalMessage = code,
            code = code
        )
    )

/**
 * Mocks deterministas M05. No usan content:// ni red ni disco.
 * IDs predecibles vía contador; paths lógicos tipados.
 */
class MockFileAssetRepository : FileAssetRepository {
    private val assets = ConcurrentHashMap<String, FileAsset>()
    private val links = ConcurrentHashMap<String, MutableList<FileAssetLink>>()
    private val versions = ConcurrentHashMap<String, FileAssetVersion>()
    private val seq = AtomicInteger(0)

    fun resetForTests() {
        assets.clear()
        links.clear()
        versions.clear()
        seq.set(0)
    }

    fun versionsForTests(): Map<String, FileAssetVersion> = versions.toMap()

    fun linksForTests(assetId: String): List<FileAssetLink> =
        links[assetId].orEmpty().toList()

    override suspend fun createDraftAsset(
        purpose: FileAssetPurpose,
        owner: FileAssetOwner,
        visibility: FileAssetVisibility,
        createdByUserId: String,
        nowEpochMs: Long
    ): AppResult<FileAsset> {
        val id = "asset-${seq.incrementAndGet()}"
        val draft = FileAssetRules.validateNew(
            id = id,
            owner = owner,
            purpose = purpose,
            visibility = visibility,
            createdByUserId = createdByUserId,
            nowEpochMs = nowEpochMs
        ).getOrElse {
            return fileRepoFail(it.message ?: "VALIDATION")
        }
        assets[id] = draft
        return AppResult.Success(draft)
    }

    override suspend fun getAsset(assetId: String): AppResult<FileAsset> {
        val asset = assets[assetId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        return AppResult.Success(asset)
    }

    override suspend fun listAssetsForResource(resource: FileResourceRef): AppResult<List<FileAsset>> {
        val ids = links.flatMap { (assetId, list) ->
            list.filter { it.resource == resource }.map { assetId }
        }.toSet()
        return AppResult.Success(ids.mapNotNull { assets[it] })
    }

    override suspend fun linkAsset(assetId: String, link: FileAssetLink): AppResult<FileAssetLink> {
        val asset = assets[assetId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val existing = links.getOrPut(assetId) { mutableListOf() }
        val hasPrimary = existing.any {
            it.resource == link.resource &&
                it.relationType == link.relationType &&
                it.isPrimary
        }
        val validated = FileAssetLinkRules.validate(asset, link.copy(assetId = assetId), hasPrimary)
            .getOrElse { return fileRepoFail(it.message ?: "VALIDATION") }
        existing.add(validated)
        return AppResult.Success(validated)
    }

    override suspend fun unlinkAsset(
        assetId: String,
        resource: FileResourceRef,
        relationType: FileRelationType
    ): AppResult<Unit> {
        val list = links[assetId] ?: return AppResult.Success(Unit)
        list.removeAll { it.resource == resource && it.relationType == relationType }
        // unlink no borra asset físico
        return AppResult.Success(Unit)
    }

    override suspend fun markDeleted(assetId: String, nowEpochMs: Long): AppResult<FileAsset> {
        val asset = assets[assetId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = asset.copy(
            status = FileAssetStatus.DELETED,
            deletedAtEpochMs = nowEpochMs,
            updatedAtEpochMs = nowEpochMs
        )
        assets[assetId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun restoreAsset(assetId: String, nowEpochMs: Long): AppResult<FileAsset> {
        val asset = assets[assetId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        if (asset.status != FileAssetStatus.DELETED) {
            return fileRepoFail("NOT_DELETED")
        }
        val updated = asset.copy(
            status = FileAssetStatus.READY,
            deletedAtEpochMs = null,
            updatedAtEpochMs = nowEpochMs
        )
        assets[assetId] = updated
        return AppResult.Success(updated)
    }

    internal fun putVersion(version: FileAssetVersion) {
        versions[version.id] = version
    }

    internal fun updateAsset(asset: FileAsset) {
        assets[asset.id] = asset
    }
}

class MockFileUploadRepository(
    private val assets: MockFileAssetRepository = MockFileAssetRepository(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) : FileUploadRepository {
    private val sessions = ConcurrentHashMap<String, FileUploadSession>()
    private val seq = AtomicInteger(0)

    fun resetForTests() {
        sessions.clear()
        seq.set(0)
        assets.resetForTests()
    }

    fun assetRepo(): MockFileAssetRepository = assets

    override suspend fun prepareUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long?
    ): AppResult<PreparedFileUpload> {
        FileValidationRules.validateUploadRequest(request)
            .getOrElse { return fileRepoFail(it.message ?: "VALIDATION") }
        val draft = when (
            val created = assets.createDraftAsset(
                purpose = request.purpose,
                owner = request.owner,
                visibility = request.requestedVisibility,
                createdByUserId = createdByUserId,
                nowEpochMs = nowEpochMs
            )
        ) {
            is AppResult.Success -> created.data
            is AppResult.Failure -> return created
        }
        val safe = FileNameSanitizer.sanitize(request.originalFilename)
            .getOrElse { return fileRepoFail(it.message ?: "VALIDATION") }
        val path = FilePathBuilder.build(
            FilePathBuildRequest(
                purpose = request.purpose,
                owner = request.owner,
                assetId = draft.id,
                safeFilename = safe,
                resourceRef = request.resourceRef
            )
        ).getOrElse { return fileRepoFail(it.message ?: "VALIDATION") }
        val versionId = "ver-${seq.incrementAndGet()}"
        val bucket = FilePurposePolicy.resolveLogicalBucket(request.purpose)
        if (bucket == FileLogicalBucket.LEGACY_LEOVER_READ_ONLY) {
            return fileRepoFail("LEGACY_BUCKET_DENIED", AppErrorKind.FORBIDDEN)
        }
        val version = FileAssetVersionRules.validate(
            id = versionId,
            assetId = draft.id,
            logicalBucket = bucket,
            storagePath = path,
            originalFilename = request.originalFilename,
            safeFilename = safe,
            declaredMimeType = request.declaredMimeType,
            detectedMimeType = null,
            sizeBytes = request.sizeBytes,
            status = FileVersionStatus.PENDING,
            nowEpochMs = nowEpochMs,
            purpose = request.purpose
        ).getOrElse { return fileRepoFail(it.message ?: "VALIDATION") }
        assets.putVersion(version)
        val sessionId = "up-${seq.incrementAndGet()}"
        val session = FileUploadSession(
            id = sessionId,
            assetId = draft.id,
            versionId = versionId,
            state = FileUploadSessionState.CREATED,
            progressPercent = 0,
            createdAtEpochMs = nowEpochMs,
            expiresAtEpochMs = clockExpiresAtEpochMs
        )
        sessions[sessionId] = session
        val physicalBucket = M05SupabaseRpcSupport.logicalBucketToPhysical(bucket)
        if (physicalBucket.equals("leover", ignoreCase = true)) {
            return fileRepoFail("LEGACY_BUCKET_DENIED", AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(
            PreparedFileUpload(
                session = session,
                physicalBucket = physicalBucket,
                storagePath = path,
                assetId = draft.id,
                versionId = versionId,
                logicalBucket = bucket
            )
        )
    }

    override suspend fun createUploadSession(
        request: FileUploadRequest,
        createdByUserId: String,
        nowEpochMs: Long,
        clockExpiresAtEpochMs: Long?
    ): AppResult<FileUploadSession> = when (
        val prepared = prepareUploadSession(
            request = request,
            createdByUserId = createdByUserId,
            nowEpochMs = nowEpochMs,
            clockExpiresAtEpochMs = clockExpiresAtEpochMs
        )
    ) {
        is AppResult.Success -> AppResult.Success(prepared.data.session)
        is AppResult.Failure -> prepared
    }

    override suspend fun validateUpload(sessionId: String): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        FileUploadSessionRules.rejectIfExpired(session, clock())
            .getOrElse { return fileRepoFail(it.message ?: "EXPIRED") }
        val updated = session.copy(state = FileUploadSessionState.READY_TO_UPLOAD)
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun startUpload(sessionId: String): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        FileUploadSessionRules.rejectIfExpired(session, clock())
            .getOrElse { return fileRepoFail(it.message ?: "EXPIRED") }
        FileUploadSessionRules.rejectDoubleSubmit(session, FileUploadSessionState.UPLOADING)
            .getOrElse { return fileRepoFail(it.message ?: "DOUBLE_SUBMIT") }
        val updated = session.copy(state = FileUploadSessionState.UPLOADING, progressPercent = 0)
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun updateProgress(
        sessionId: String,
        progressPercent: Int
    ): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val p = FileUploadSessionRules.validateProgress(progressPercent)
            .getOrElse { return fileRepoFail(it.message ?: "PROGRESS") }
        val updated = session.copy(progressPercent = p)
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun completeUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        FileUploadSessionRules.canComplete(session, versionReady = true)
            .getOrElse { return fileRepoFail(it.message ?: "COMPLETE") }
        val asset = when (val a = assets.getAsset(session.assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        // Path lógico mock — nunca content://
        val mockUrlForbidden = "content://"
        check(!session.id.startsWith(mockUrlForbidden))
        assets.updateAsset(
            asset.copy(
                status = FileAssetStatus.READY,
                currentVersionId = session.versionId,
                updatedAtEpochMs = nowEpochMs
            )
        )
        val versions = assets.versionsForTests()
        versions[session.versionId]?.let {
            assets.putVersion(it.copy(status = FileVersionStatus.READY))
        }
        val updated = session.copy(
            state = FileUploadSessionState.COMPLETED,
            progressPercent = 100
        )
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun failUpload(
        sessionId: String,
        failureCode: String
    ): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = session.copy(
            state = FileUploadSessionState.FAILED,
            failureCode = failureCode
        )
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun cancelUpload(
        sessionId: String,
        nowEpochMs: Long
    ): AppResult<FileUploadSession> {
        val session = sessions[sessionId] ?: return fileRepoFail("NOT_FOUND", AppErrorKind.NOT_FOUND)
        val updated = FileUploadSessionRules.cancelIdempotent(session, nowEpochMs)
        sessions[sessionId] = updated
        return AppResult.Success(updated)
    }
}

class MockFileDownloadRepository(
    private val assets: MockFileAssetRepository
) : FileDownloadRepository {

    override suspend fun requestAccess(
        request: FileAccessRequest,
        context: FileAuthContext
    ): AppResult<FileAccessDecision> {
        val asset = when (val a = assets.getAsset(request.assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        return AppResult.Success(FileAuthorization.canRead(context, asset))
    }

    override suspend fun resolvePublicAsset(assetId: String): AppResult<FileAsset> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        if (asset.visibility != FileAssetVisibility.PUBLIC ||
            asset.status != FileAssetStatus.READY
        ) {
            return fileRepoFail("NOT_PUBLIC", AppErrorKind.FORBIDDEN)
        }
        return AppResult.Success(asset)
    }

    override suspend fun requestSignedUrl(
        request: FileAccessRequest,
        context: FileAuthContext,
        nowEpochMs: Long
    ): AppResult<FileSignedAccess> {
        val asset = when (val a = assets.getAsset(request.assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        FileSignedAccessRules.validateRequest(asset, request.ttlClass)
            .getOrElse { return fileRepoFail(it.message ?: "SIGNED") }
        val decision = FileAuthorization.canRead(context, asset)
        if (decision != FileAccessDecision.ALLOWED &&
            request.ttlClass != FileSignedTtlClass.PUBLIC_RESOLUTION
        ) {
            return fileRepoFail(decision.name, AppErrorKind.FORBIDDEN)
        }
        val ttl = FileSignedAccessRules.ttlSeconds(request.ttlClass)
        // URL temporal mock — no se persiste en el asset
        val temporary = "https://mock.leover.local/signed/${asset.id}?exp=${nowEpochMs + ttl * 1000L}"
        return AppResult.Success(
            FileSignedAccess(
                assetId = asset.id,
                temporaryUrl = temporary,
                expiresAtEpochMs = nowEpochMs + ttl * 1000L,
                ttlClass = request.ttlClass
            )
        )
    }
}

class MockFileAccessRepository(
    private val assets: MockFileAssetRepository
) : FileAccessRepository {
    override suspend fun canRead(
        context: FileAuthContext,
        assetId: String
    ): AppResult<FileAccessDecision> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        return AppResult.Success(FileAuthorization.canRead(context, asset))
    }

    override suspend fun canUpload(
        context: FileAuthContext,
        purpose: FileAssetPurpose,
        owner: FileAssetOwner
    ): AppResult<FileAccessDecision> =
        AppResult.Success(FileAuthorization.canUpload(context, purpose, owner))

    override suspend fun canReplace(
        context: FileAuthContext,
        assetId: String
    ): AppResult<FileAccessDecision> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        return AppResult.Success(FileAuthorization.canReplace(context, asset))
    }

    override suspend fun canDelete(
        context: FileAuthContext,
        assetId: String,
        nowEpochMs: Long
    ): AppResult<FileAccessDecision> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        val links = assets.linksForTests(assetId).size
        return AppResult.Success(
            FileAuthorization.canDelete(context, asset, links, nowEpochMs)
        )
    }
}

class MockFileRetentionRepository(
    private val assets: MockFileAssetRepository
) : FileRetentionRepository {
    override suspend fun getRetentionPolicy(purpose: FileAssetPurpose): AppResult<FileRetentionPolicy> =
        AppResult.Success(FileRetentionRules.defaultPolicy(purpose))

    override suspend fun canPhysicallyDelete(
        assetId: String,
        nowEpochMs: Long
    ): AppResult<Boolean> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        val links = assets.linksForTests(assetId).size
        val ok = FileRetentionRules.canPhysicallyDelete(asset, links, nowEpochMs).isSuccess
        return AppResult.Success(ok)
    }

    override suspend fun requestLegalHold(assetId: String): AppResult<FileAsset> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        val updated = asset.copy(legalHold = true)
        assets.updateAsset(updated)
        return AppResult.Success(updated)
    }

    override suspend fun releaseLegalHold(assetId: String): AppResult<FileAsset> {
        val asset = when (val a = assets.getAsset(assetId)) {
            is AppResult.Success -> a.data
            is AppResult.Failure -> return a
        }
        val updated = asset.copy(legalHold = false)
        assets.updateAsset(updated)
        return AppResult.Success(updated)
    }
}

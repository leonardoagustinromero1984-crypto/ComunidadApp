package com.comunidapp.app.data.files

import com.comunidapp.app.core.result.AppErrorKind
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.repository.FileAssetRepository
import com.comunidapp.app.data.repository.FileUploadRepository
import com.comunidapp.app.domain.files.FileAssetLink
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLocalMetadata
import com.comunidapp.app.domain.files.FilePurposePolicy
import com.comunidapp.app.domain.files.FileRelationType
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileUiErrorMapper
import com.comunidapp.app.domain.files.FileUploadPhase
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadUiState
import com.comunidapp.app.domain.files.FileValidationRules
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.files.ResumableUploadPolicy
import com.comunidapp.app.domain.files.TusUploadSessionHint
import com.comunidapp.app.domain.media.ImageIngest
import com.comunidapp.app.domain.media.MediaDiagnostic
import com.comunidapp.app.domain.media.MediaIngestionPolicy
import com.comunidapp.app.domain.media.ProfileMediaPipeline
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileUploadCoordinator(
    private val uploadRepository: FileUploadRepository,
    private val assetRepository: FileAssetRepository,
    private val objectUploader: FileObjectUploader,
    private val metadataReader: FileLocalMetadataReader,
    private val bytesReader: FileBytesReader,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val imageIngest: ImageIngest = ImageIngest.NoOp
) {
    private data class LastAttempt(
        val uriString: String,
        val request: FileUploadRequest,
        val actorUserId: String,
        val prepared: PreparedFileUpload? = null,
        val localFilePath: String? = null,
        val tusHint: TusUploadSessionHint? = null
    )

    private val submitting = AtomicBoolean(false)
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()
    private val cancelledSessions = ConcurrentHashMap.newKeySet<String>()
    private var lastAttempt: LastAttempt? = null
    private val lastTusHint = AtomicReference<TusUploadSessionHint?>(null)
    private val mutableUiState = MutableStateFlow(FileUploadUiState())
    val uiState: StateFlow<FileUploadUiState> = mutableUiState.asStateFlow()

    suspend fun selectAndValidate(
        uri: String,
        purpose: FileAssetPurpose,
        owner: FileAssetOwner,
        visibility: FileAssetVisibility,
        resourceRef: FileResourceRef? = null,
        actorUserId: String,
        existingCount: Int = 0
    ): AppResult<FileLocalMetadata> {
        mutableUiState.value = FileUploadUiState(
            phase = FileUploadPhase.Validating,
            previewUri = uri
        )
        if (uri.isBlank() || actorUserId.isBlank() ||
            purpose == FileAssetPurpose.OTHER ||
            FilePurposePolicy.resolveLogicalBucket(purpose).name.contains("LEGACY") ||
            (FilePurposePolicy.isSensitive(purpose) && visibility == FileAssetVisibility.PUBLIC) ||
            (owner is FileAssetOwner.User && owner.userId != actorUserId)
        ) {
            return fail("VALIDATION")
        }
        return when (val metadata = metadataReader.read(uri)) {
            is AppResult.Failure -> {
                updateFailure(metadata)
                metadata
            }
            is AppResult.Success -> {
                val request = FileUploadRequest(
                    purpose = purpose,
                    owner = owner,
                    resourceRef = resourceRef,
                    originalFilename = metadata.data.originalFilename,
                    declaredMimeType = metadata.data.declaredMimeType,
                    sizeBytes = metadata.data.sizeBytes,
                    requestedVisibility = visibility
                )
                val validation = FileValidationRules.validateUploadRequest(request, existingCount)
                if (validation.isFailure) {
                    fail(validation.exceptionOrNull()?.message ?: "VALIDATION")
                } else {
                    mutableUiState.value = mutableUiState.value.copy(
                        phase = FileUploadPhase.Idle,
                        previewUri = uri,
                        userMessage = null
                    )
                    metadata
                }
            }
        }
    }

    suspend fun startUpload(
        uriString: String,
        request: FileUploadRequest,
        actorUserId: String
    ): AppResult<PreparedFileUpload> {
        if (!submitting.compareAndSet(false, true)) return fail("DOUBLE_SUBMIT")
        val ingested = runCatching { imageIngest.normalize(uriString, request.purpose) }
        if (ingested.isFailure) {
            submitting.set(false)
            return fail(MediaDiagnostic.fromThrowable(ingested.exceptionOrNull()))
        }
        val ingestedResult = ingested.getOrThrow()
        val imageLike = MediaIngestionPolicy.isImageMime(request.declaredMimeType) ||
            MediaIngestionPolicy.isImageMime(ingestedResult.mimeType)
        if (imageLike && ingestedResult.normalized != true) {
            submitting.set(false)
            return fail(MediaDiagnostic.DECODE)
        }
        val workUri = if (ingestedResult.normalized) ingestedResult.uriString else uriString
        val resolvedSize = ProfileMediaPipeline.resolvedSizeBytes(
            workUri,
            ingestedResult.sizeBytes.takeIf { it > 1L } ?: request.sizeBytes
        )
        val workRequest = request.copy(
            originalFilename = if (ingestedResult.normalized) "photo.jpg" else request.originalFilename,
            declaredMimeType = ingestedResult.mimeType.ifBlank { request.declaredMimeType },
            sizeBytes = resolvedSize
        )
        if (ingestedResult.normalized) {
            FileValidationRules.validateProcessedSize(
                request.purpose,
                resolvedSize,
                workRequest.declaredMimeType
            )
                .getOrElse {
                    submitting.set(false)
                    return fail(MediaDiagnostic.SIZE)
                }
        }
        val resume = lastAttempt?.takeIf {
            it.uriString == workUri && it.prepared != null && it.tusHint != null
        }
        lastAttempt = LastAttempt(workUri, workRequest, actorUserId, resume?.prepared, resume?.localFilePath, resume?.tusHint)
        mutableUiState.value = FileUploadUiState(
            phase = FileUploadPhase.Preparing,
            previewUri = workUri,
            submittingLocked = true
        )
        var sessionId: String? = resume?.prepared?.session?.id
        try {
            val metadata = when (
                val selected = selectAndValidate(
                    uri = workUri,
                    purpose = workRequest.purpose,
                    owner = workRequest.owner,
                    visibility = workRequest.requestedVisibility,
                    resourceRef = workRequest.resourceRef,
                    actorUserId = actorUserId
                )
            ) {
                is AppResult.Success -> selected.data
                is AppResult.Failure -> return selected
            }
            val normalizedRequest = workRequest.copy(
                originalFilename = metadata.originalFilename,
                declaredMimeType = metadata.declaredMimeType,
                sizeBytes = metadata.sizeBytes
            )
            mutableUiState.value = mutableUiState.value.copy(
                phase = FileUploadPhase.Preparing,
                submittingLocked = true
            )
            val prepared = resume?.prepared ?: when (
                val result = uploadRepository.prepareUploadSession(
                    request = normalizedRequest,
                    createdByUserId = actorUserId,
                    nowEpochMs = clock()
                )
            ) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> {
                    updateFailure(result)
                    return result
                }
            }
            sessionId = prepared.session.id
            activeSessions += sessionId
            mutableUiState.value = mutableUiState.value.copy(
                phase = FileUploadPhase.Uploading,
                sessionId = sessionId,
                assetId = prepared.assetId,
                canCancel = true,
                submittingLocked = true
            )
            if (resume == null) {
                when (val started = uploadRepository.startUpload(sessionId)) {
                    is AppResult.Failure -> {
                        updateFailure(started)
                        return started
                    }
                    is AppResult.Success -> Unit
                }
            }
            val useTus = ResumableUploadPolicy.shouldUseTus(normalizedRequest.sizeBytes)
            val uploaded = if (useTus) {
                val file = resume?.localFilePath?.let { File(it).takeIf { f -> f.exists() } }
                    ?: when (val materialized = bytesReader.materializeForUpload(workUri)) {
                        is AppResult.Success -> materialized.data
                        is AppResult.Failure -> {
                            uploadRepository.failUpload(sessionId, materialized.error.code ?: "FILE_READ_FAILED")
                            updateFailure(materialized)
                            return materialized
                        }
                    }
                lastAttempt = LastAttempt(workUri, normalizedRequest, actorUserId, prepared, file.absolutePath, resume?.tusHint)
                objectUploader.uploadFile(
                    physicalBucket = prepared.physicalBucket,
                    storagePath = prepared.storagePath,
                    file = file,
                    mimeType = normalizedRequest.declaredMimeType ?: "application/octet-stream",
                    sizeBytes = normalizedRequest.sizeBytes,
                    onProgress = { progress ->
                        if (sessionId !in cancelledSessions) {
                            mutableUiState.value = mutableUiState.value.copy(
                                phase = FileUploadPhase.Uploading,
                                progressPercent = progress.coerceIn(0, 100),
                                canCancel = progress < 100
                            )
                        }
                    },
                    isCancelled = { sessionId in cancelledSessions },
                    resumeUrl = resume?.tusHint?.uploadUrl,
                    onSession = { hint ->
                        lastTusHint.set(hint)
                        lastAttempt = lastAttempt?.copy(tusHint = hint)
                    }
                )
            } else {
                val bytes = when (val read = bytesReader.readBytes(workUri)) {
                    is AppResult.Success -> read.data
                    is AppResult.Failure -> {
                        uploadRepository.failUpload(sessionId, read.error.code ?: "FILE_READ_FAILED")
                        updateFailure(read)
                        return read
                    }
                }
                uploadRepository.updateProgress(sessionId, 0)
                objectUploader.uploadBytes(
                    physicalBucket = prepared.physicalBucket,
                    storagePath = prepared.storagePath,
                    bytes = bytes,
                    mimeType = normalizedRequest.declaredMimeType ?: "application/octet-stream"
                ) { progress ->
                    if (sessionId !in cancelledSessions) {
                        mutableUiState.value = mutableUiState.value.copy(
                            phase = FileUploadPhase.Uploading,
                            progressPercent = progress.coerceIn(0, 100),
                            canCancel = progress < 100
                        )
                    }
                }
            }
            if (sessionId in cancelledSessions) {
                mutableUiState.value = mutableUiState.value.copy(
                    phase = FileUploadPhase.Cancelled,
                    canCancel = false,
                    submittingLocked = false
                )
                return fail("CANCELLED", preserveCancelled = true)
            }
            if (uploaded is AppResult.Failure) {
                MediaDiagnostic.logUpload(uploaded.error)
                val resumable = lastTusHint.get() != null && uploaded.error.code != "CANCELLED"
                if (!resumable) {
                    uploadRepository.failUpload(sessionId, uploaded.error.code ?: "UPLOAD_FAILED")
                }
                lastAttempt = lastAttempt?.copy(prepared = prepared)
                updateFailure(uploaded)
                return uploaded
            }
            uploadRepository.updateProgress(sessionId, 100)
            mutableUiState.value = mutableUiState.value.copy(
                phase = FileUploadPhase.Completing,
                progressPercent = 100,
                canCancel = false
            )
            when (val completed = uploadRepository.completeUpload(sessionId, clock())) {
                is AppResult.Failure -> {
                    MediaDiagnostic.logStaging(
                        MediaDiagnostic.classifyDb(
                            completed.error.code,
                            completed.error.technicalMessage
                        )
                    )
                    updateFailure(completed)
                    return completed
                }
                is AppResult.Success -> Unit
            }
            mutableUiState.value = mutableUiState.value.copy(
                phase = FileUploadPhase.Ready,
                progressPercent = 100,
                previewUri = null,
                canRetry = false,
                canCancel = false,
                submittingLocked = false,
                userMessage = null
            )
            lastAttempt = null
            lastTusHint.set(null)
            return AppResult.Success(prepared)
        } finally {
            sessionId?.let { activeSessions.remove(it) }
            submitting.set(false)
            if (mutableUiState.value.submittingLocked) {
                mutableUiState.value = mutableUiState.value.copy(submittingLocked = false)
            }
        }
    }

    suspend fun cancel(sessionId: String): AppResult<Unit> {
        cancelledSessions += sessionId
        lastAttempt = lastAttempt?.copy(prepared = null, tusHint = null, localFilePath = null)
        lastTusHint.set(null)
        return when (val result = uploadRepository.cancelUpload(sessionId, clock())) {
            is AppResult.Success -> {
                activeSessions.remove(sessionId)
                mutableUiState.value = mutableUiState.value.copy(
                    phase = FileUploadPhase.Cancelled,
                    canCancel = false,
                    submittingLocked = false,
                    userMessage = FileUiErrorMapper.message("CANCELLED")
                )
                AppResult.Success(Unit)
            }
            is AppResult.Failure -> {
                updateFailure(result)
                result
            }
        }
    }

    suspend fun retry(): AppResult<PreparedFileUpload> {
        val attempt = lastAttempt ?: return fail("NOTHING_TO_RETRY")
        mutableUiState.value = mutableUiState.value.copy(canRetry = false)
        return startUpload(attempt.uriString, attempt.request, attempt.actorUserId)
    }

    suspend fun safeReplace(
        oldAssetId: String,
        uriString: String,
        request: FileUploadRequest,
        actorUserId: String,
        resource: FileResourceRef,
        relation: FileRelationType,
        markOldDeleted: Boolean = false
    ): AppResult<PreparedFileUpload> {
        val uploaded = startUpload(uriString, request, actorUserId)
        if (uploaded is AppResult.Failure) return uploaded
        uploaded as AppResult.Success
        val newAssetId = uploaded.data.assetId
        val linked = assetRepository.linkAsset(
            newAssetId,
            FileAssetLink(
                assetId = newAssetId,
                resource = resource,
                relationType = relation,
                isPrimary = false,
                createdAtEpochMs = clock()
            )
        )
        if (linked is AppResult.Failure) return linked
        val unlinked = assetRepository.unlinkAsset(oldAssetId, resource, relation)
        if (unlinked is AppResult.Failure) return unlinked
        if (markOldDeleted) {
            val deleted = assetRepository.markDeleted(oldAssetId, clock())
            if (deleted is AppResult.Failure) return deleted
        }
        return uploaded
    }

    suspend fun unlink(
        assetId: String,
        resource: FileResourceRef,
        relation: FileRelationType
    ): AppResult<Unit> = assetRepository.unlinkAsset(assetId, resource, relation)

    suspend fun requestDelete(assetId: String): AppResult<Unit> =
        when (val result = assetRepository.markDeleted(assetId, clock())) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }

    fun clearAllSensitiveState() {
        activeSessions.clear()
        cancelledSessions.clear()
        lastAttempt = null
        lastTusHint.set(null)
        submitting.set(false)
        mutableUiState.value = FileUploadUiState()
    }

    fun activeSessionIdsForTests(): Set<String> = activeSessions.toSet()

    private fun fail(
        code: String,
        preserveCancelled: Boolean = false
    ): AppResult.Failure {
        val failure = fileUploadFailure(
            code,
            if (code == "DOUBLE_SUBMIT") AppErrorKind.CONFLICT else AppErrorKind.VALIDATION
        )
        if (!preserveCancelled) updateFailure(failure)
        return failure
    }

    private fun updateFailure(failure: AppResult.Failure) {
        val diagnostic = MediaDiagnostic.fromSignal(
            failure.error.code,
            failure.error.technicalMessage
        )
        if (diagnostic != null) MediaDiagnostic.logStaging(diagnostic)
        mutableUiState.value = mutableUiState.value.copy(
            phase = FileUploadPhase.Failed,
            userMessage = FileUiErrorMapper.message(failure.error),
            canRetry = failure.error.code !in setOf("FORBIDDEN", "VALIDATION"),
            canCancel = false,
            submittingLocked = false,
            temporaryDisplayUrl = null
        )
    }
}

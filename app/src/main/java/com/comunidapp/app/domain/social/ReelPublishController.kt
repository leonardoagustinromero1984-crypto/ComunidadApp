package com.comunidapp.app.domain.social

import android.content.Context
import android.net.Uri
import com.comunidapp.app.BuildConfig
import com.comunidapp.app.core.result.AppResult
import com.comunidapp.app.data.local.PendingSocialPublishStore
import com.comunidapp.app.data.model.FeedPost
import com.comunidapp.app.data.model.PostType
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.repository.AuthProvider
import com.comunidapp.app.domain.files.FileAssetOwner
import com.comunidapp.app.domain.files.FileAssetPurpose
import com.comunidapp.app.domain.files.FileAssetVisibility
import com.comunidapp.app.domain.files.FileLogicalBucket
import com.comunidapp.app.domain.files.FileResourceRef
import com.comunidapp.app.domain.files.FileResourceType
import com.comunidapp.app.domain.files.FileUploadPhase
import com.comunidapp.app.domain.files.FileUploadRequest
import com.comunidapp.app.domain.files.FileUploadSession
import com.comunidapp.app.domain.files.FileUploadSessionState
import com.comunidapp.app.domain.files.PreparedFileUpload
import com.comunidapp.app.domain.media.ReelPublishTrace
import com.comunidapp.app.domain.vitacora.VitaCoraSocialSave
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ReelPublishController(
    private val store: PendingSocialPublishStore
) {
    private val mutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _job = MutableStateFlow<PendingSocialPublish?>(null)
    val job: StateFlow<PendingSocialPublish?> = _job.asStateFlow()

    @Volatile
    private var boundUserId: String? = null

    suspend fun restore() {
        val userId = currentUserId()
        boundUserId = userId
        val stored = store.read(userId)
        if (stored == null || !stored.belongsTo(userId)) {
            _job.value = null
            return
        }
        if (stored.state == PendingSocialPublishState.SUCCESS ||
            stored.state == PendingSocialPublishState.CANCELLED
        ) {
            cleanupFiles(stored)
            store.clear(userId)
            _job.value = null
            return
        }
        if (stored.isActive && isStale(stored)) {
            persist(
                stored.copy(
                    state = PendingSocialPublishState.FAILED,
                    errorCategory = if (stored.reelCreated) {
                        PendingSocialPublishErrors.LINK_FAILED
                    } else {
                        PendingSocialPublishErrors.TIMEOUT
                    },
                    lastErrorPhase = stored.nextPhase().name,
                    progressPercent = null
                )
            )
            return
        }
        _job.value = stored
    }

    fun onSessionEnded() {
        val leaving = boundUserId ?: _job.value?.actorUserId
        _job.value = null
        boundUserId = null
        runCatching {
            ReelPublishScheduler.cancel(com.comunidapp.app.LeoverApplication.instance)
        }
        ioScope.launch {
            val stored = store.read(leaving) ?: return@launch
            if (stored.state == PendingSocialPublishState.SUCCESS ||
                stored.state == PendingSocialPublishState.CANCELLED
            ) {
                cleanupFiles(stored)
                store.clear(leaving)
            }
        }
    }

    fun bindVisibleJob(userId: String?) {
        boundUserId = userId
        val shown = _job.value
        if (shown != null && !shown.belongsTo(userId)) {
            _job.value = null
        }
        if (userId.isNullOrBlank()) {
            _job.value = null
            return
        }
        ioScope.launch {
            val stored = store.read(userId) ?: return@launch
            if (!stored.belongsTo(userId)) {
                if (_job.value?.belongsTo(userId) != true) _job.value = null
                return@launch
            }
            if (stored.shouldKeepForResume) {
                _job.value = if (stored.isActive && isStale(stored)) {
                    persist(
                        stored.copy(
                            state = PendingSocialPublishState.FAILED,
                            errorCategory = if (stored.reelCreated) {
                                PendingSocialPublishErrors.LINK_FAILED
                            } else {
                                PendingSocialPublishErrors.TIMEOUT
                            },
                            lastErrorPhase = stored.nextPhase().name,
                            progressPercent = null
                        )
                    )
                } else {
                    stored
                }
            }
        }
    }

    fun visibleJobFor(userId: String?): PendingSocialPublish? =
        _job.value?.takeIf { it.belongsTo(userId) }

    suspend fun accept(
        context: Context,
        source: Uri,
        caption: String,
        locationText: String?,
        localityId: String?,
        compositionJson: String?,
        petIds: List<String>
    ): Result<PendingSocialPublish> = mutex.withLock {
        val author = AuthProvider.repository.getCurrentUser()
            ?: return Result.failure(IllegalStateException("NOT_AUTHENTICATED"))
        boundUserId = author.id
        val existing = store.read(author.id) ?: _job.value?.takeIf { it.belongsTo(author.id) }
        if (existing?.isActive == true) {
            return Result.failure(IllegalStateException("REEL_JOB_ACTIVE"))
        }
        if (existing != null && !existing.isActive) {
            cleanupFiles(existing)
            store.clear(author.id)
        }
        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()
        val dir = jobDir(context, jobId)
        dir.mkdirs()
        val sourceFile = File(dir, "source.mp4")
        val copyStarted = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            copySource(context, source, sourceFile)
        }
        ReelPublishTrace.mark(
            ReelPublishTrace.Stage.SOURCE_COPY,
            fileSize = sourceFile.length(),
            mime = VideoExportPolicy.TARGET_VIDEO_MIME,
            result = "OK"
        )
        logStage("source_copy", System.currentTimeMillis() - copyStarted, sourceFile.length())
        val resolvedPets = petIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val job = PendingSocialPublish(
            jobId = jobId,
            state = PendingSocialPublishState.PREPARING,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            actorUserId = author.id,
            caption = caption.trim(),
            locationText = locationText?.trim()?.ifBlank { null },
            localityId = localityId,
            compositionJson = compositionJson,
            petIds = resolvedPets,
            saveToVitaCora = resolvedPets.isNotEmpty(),
            sourcePath = sourceFile.absolutePath,
            uploadPath = sourceFile.absolutePath,
            mimeType = VideoExportPolicy.TARGET_VIDEO_MIME,
            filename = sourceFile.name,
            sizeBytes = sourceFile.length()
        )
        persist(job)
        ReelPublishScheduler.enqueue(context, jobId)
        Result.success(job)
    }

    suspend fun retry(context: Context) {
        val userId = currentUserId()
        val current = store.read(userId) ?: _job.value?.takeIf { it.belongsTo(userId) } ?: return
        if (!current.belongsTo(userId) || !current.canRetry) return
        persist(
            current.copy(
                state = when (current.nextPhase()) {
                    ReelPublishPhase.PREPARE -> PendingSocialPublishState.PREPARING
                    ReelPublishPhase.UPLOAD, ReelPublishPhase.REGISTER ->
                        PendingSocialPublishState.UPLOADING
                    else -> PendingSocialPublishState.PUBLISHING
                },
                errorCategory = null,
                lastErrorPhase = null,
                progressPercent = current.progressPercent
            )
        )
        ReelPublishScheduler.enqueue(context, current.jobId, replace = true)
    }

    suspend fun cancel(context: Context) {
        val userId = currentUserId()
        val current = store.read(userId) ?: _job.value?.takeIf { it.belongsTo(userId) } ?: return
        if (!current.belongsTo(userId) || !current.canCancel) return
        ReelPublishScheduler.cancel(context)
        cleanupFiles(current)
        store.clear(userId)
        _job.value = null
    }

    suspend fun confirmVitaCora(): Result<Unit> {
        val userId = currentUserId()
        val current = store.read(userId) ?: _job.value?.takeIf { it.belongsTo(userId) }
            ?: return Result.success(Unit)
        if (current.state != PendingSocialPublishState.SUCCESS || current.vitaCoraResolved) {
            return Result.success(Unit)
        }
        val postId = current.backendPostId ?: return Result.success(Unit)
        val result = VitaCoraSocialSave.saveApprovedReel(
            contentId = postId,
            petIds = current.petIds,
            compositionJson = current.compositionJson,
            mediaAssetId = current.assetId,
            mediaMime = current.mimeType
        )
        if (result.isSuccess) {
            persist(current.copy(vitaCoraResolved = true, saveToVitaCora = false))
            maybeClearTerminal(current.jobId, userId)
        }
        return result
    }

    suspend fun skipVitaCora() {
        val userId = currentUserId()
        val current = store.read(userId) ?: _job.value?.takeIf { it.belongsTo(userId) } ?: return
        persist(current.copy(vitaCoraResolved = true, saveToVitaCora = false))
        maybeClearTerminal(current.jobId, userId)
    }

    suspend fun dismissTerminal() {
        val userId = currentUserId()
        val current = store.read(userId) ?: _job.value?.takeIf { it.belongsTo(userId) } ?: return
        if (!current.belongsTo(userId) || current.isActive) return
        cleanupFiles(current)
        store.clear(userId)
        _job.value = null
    }

    suspend fun runJob(context: Context, jobId: String): Boolean = mutex.withLock {
        val userId = currentUserId()
        val current = store.read(userId)?.takeIf { it.jobId == jobId } ?: return false
        if (!current.belongsTo(userId)) return false
        if (current.state == PendingSocialPublishState.SUCCESS ||
            current.state == PendingSocialPublishState.CANCELLED
        ) {
            return true
        }
        if (current.state == PendingSocialPublishState.FAILED && !current.canRetry) return false
        return execute(context, current)
    }

    private suspend fun execute(context: Context, initial: PendingSocialPublish): Boolean {
        var job = initial
        try {
            val author = AuthProvider.repository.getCurrentUser()
                ?: error("NOT_AUTHENTICATED")
            if (!job.belongsTo(author.id)) return false
            if (job.state == PendingSocialPublishState.FAILED) {
                job = persist(
                    job.copy(
                        state = when (job.nextPhase()) {
                            ReelPublishPhase.PREPARE -> PendingSocialPublishState.PREPARING
                            ReelPublishPhase.UPLOAD, ReelPublishPhase.REGISTER ->
                                PendingSocialPublishState.UPLOADING
                            else -> PendingSocialPublishState.PUBLISHING
                        },
                        errorCategory = null,
                        lastErrorPhase = null
                    )
                )
            }
            if (job.nextPhase() == ReelPublishPhase.PREPARE ||
                job.nextPhase() == ReelPublishPhase.UPLOAD ||
                job.nextPhase() == ReelPublishPhase.REGISTER
            ) {
                job = persist(job.copy(state = PendingSocialPublishState.PREPARING, progressPercent = null))
                val sourceFile = job.sourcePath?.let { File(it) }?.takeIf { it.exists() }
                    ?: error("FILE_READ_FAILED")
                val dest = File(jobDir(context, job.jobId), "upload.mp4")
                val prepareStarted = System.currentTimeMillis()
                ReelPublishTrace.mark(ReelPublishTrace.Stage.PREPARE_START, fileSize = sourceFile.length())
                val prepared = withTimeout(120_000L) {
                    SocialMediaPipeline.prepare(
                        context = context,
                        source = Uri.fromFile(sourceFile),
                        expectVideo = true,
                        outputFile = dest
                    ).getOrThrow()
                }
                ReelPublishTrace.mark(
                    ReelPublishTrace.Stage.PREPARE_DONE,
                    fileSize = prepared.sizeBytes,
                    mime = prepared.mimeType,
                    result = prepared.exportDecision
                )
                logStage(
                    "transcode",
                    System.currentTimeMillis() - prepareStarted,
                    prepared.sizeBytes,
                    prepared.exportDecision
                )
                if (prepared.exportDecision == VideoExportPolicy.DECISION_TRANSCODE &&
                    sourceFile.absolutePath != dest.absolutePath &&
                    dest.exists()
                ) {
                    sourceFile.delete()
                }
                job = persist(
                    job.copy(
                        uploadPath = prepared.uri.path ?: dest.absolutePath,
                        mimeType = prepared.mimeType,
                        filename = prepared.filename,
                        sizeBytes = prepared.sizeBytes,
                        exportDecision = prepared.exportDecision,
                        sourcePath = if (dest.exists()) dest.absolutePath else job.sourcePath
                    ).withPhaseCompleted(ReelPublishPhase.PREPARE)
                )
                job = persist(job.copy(state = PendingSocialPublishState.UPLOADING, progressPercent = null))
                val uploadFile = File(job.uploadPath ?: dest.absolutePath)
                if (!uploadFile.exists() || uploadFile.length() <= 0L) error("FILE_READ_FAILED")
                val request = FileUploadRequest(
                    purpose = FileAssetPurpose.REEL_MEDIA,
                    owner = FileAssetOwner.User(author.id),
                    resourceRef = FileResourceRef(FileResourceType.REEL, job.jobId),
                    originalFilename = job.filename,
                    declaredMimeType = job.mimeType,
                    sizeBytes = job.sizeBytes.coerceAtLeast(uploadFile.length()),
                    requestedVisibility = FileAssetVisibility.PUBLIC
                )
                if (job.registerCompleted && !job.assetId.isNullOrBlank()) {
                    DataProvider.fileUploadCoordinator.primeRegisteredResume(
                        uriString = Uri.fromFile(uploadFile).toString(),
                        request = request,
                        actorUserId = author.id,
                        prepared = preparedUploadOf(job),
                        localFilePath = uploadFile.absolutePath
                    )
                }
                val uploadStarted = System.currentTimeMillis()
                ReelPublishTrace.mark(
                    ReelPublishTrace.Stage.UPLOAD_START,
                    fileSize = uploadFile.length(),
                    mime = job.mimeType
                )
                val upload = coroutineScope {
                    val progressJob = launch {
                        DataProvider.fileUploadCoordinator.uiState.collect { state ->
                            if (state.phase == FileUploadPhase.Uploading) {
                                val latest = _job.value?.takeIf { it.belongsTo(author.id) } ?: return@collect
                                _job.value = latest.copy(
                                    state = PendingSocialPublishState.UPLOADING,
                                    progressPercent = state.progressPercent.coerceIn(0, 100),
                                    bytesUploaded = latest.sizeBytes.takeIf { it > 0L }
                                        ?.let { it * state.progressPercent / 100 },
                                    totalBytes = latest.sizeBytes
                                )
                            }
                        }
                    }
                    try {
                        withTimeout(90_000L) {
                            DataProvider.fileUploadCoordinator.startUpload(
                                uriString = Uri.fromFile(uploadFile).toString(),
                                request = request,
                                actorUserId = author.id
                            )
                        }
                    } finally {
                        progressJob.cancel()
                    }
                }
                when (upload) {
                    is AppResult.Failure -> {
                        DataProvider.fileUploadCoordinator.lastPreparedOrNull()?.let { registered ->
                            job = persist(
                                job.copy(
                                    assetId = registered.assetId,
                                    sessionId = registered.session.id,
                                    versionId = registered.versionId,
                                    physicalBucket = registered.physicalBucket,
                                    storagePath = registered.storagePath,
                                    logicalBucket = registered.logicalBucket.name,
                                    registerCompleted = true
                                )
                            )
                            ReelPublishTrace.mark(
                                ReelPublishTrace.Stage.REGISTER_SUCCESS,
                                result = registered.assetId.take(8)
                            )
                        }
                        ReelPublishTrace.mark(
                            ReelPublishTrace.Stage.UPLOAD_FAIL,
                            result = upload.error.code
                        )
                        logStage("upload", System.currentTimeMillis() - uploadStarted, job.sizeBytes, "FAIL")
                        fail(job, upload.error.code, upload.error.technicalMessage)
                        return false
                    }
                    is AppResult.Success -> {
                        job = persist(
                            job.copy(
                                assetId = upload.data.assetId,
                                sessionId = upload.data.session.id,
                                versionId = upload.data.versionId,
                                physicalBucket = upload.data.physicalBucket,
                                storagePath = upload.data.storagePath,
                                logicalBucket = upload.data.logicalBucket.name,
                                registerCompleted = true,
                                uploadCompleted = true,
                                sizeBytes = job.sizeBytes
                            ).withPhaseCompleted(ReelPublishPhase.UPLOAD)
                                .withPhaseCompleted(ReelPublishPhase.REGISTER)
                        )
                        ReelPublishTrace.mark(
                            ReelPublishTrace.Stage.REGISTER_SUCCESS,
                            result = upload.data.assetId.take(8)
                        )
                        ReelPublishTrace.mark(
                            ReelPublishTrace.Stage.UPLOAD_SUCCESS,
                            fileSize = job.sizeBytes,
                            result = "OK"
                        )
                        logStage("upload", System.currentTimeMillis() - uploadStarted, job.sizeBytes, "OK")
                        logStage("register", 0L, job.sizeBytes, upload.data.assetId.take(8))
                    }
                }
            }
            job = persist(job.copy(state = PendingSocialPublishState.PUBLISHING, progressPercent = null))
            val assetId = job.assetId ?: error("REGISTER_FAILED")
            val createStarted = System.currentTimeMillis()
            val postId = if (job.reelCreated) {
                job.backendPostId ?: recoverReelId(assetId) ?: error("CREATE_FAILED")
            } else {
                val recovered = recoverReelId(assetId)
                if (!recovered.isNullOrBlank()) {
                    recovered
                } else if (job.createUncertain) {
                    fail(job, PendingSocialPublishErrors.CREATE_FAILED, "CREATE_UNCERTAIN")
                    return false
                } else {
                    job = persist(job.copy(createStarted = true))
                    ReelPublishTrace.mark(ReelPublishTrace.Stage.CREATE_REEL_START, result = assetId.take(8))
                    val now = System.currentTimeMillis()
                    val created = withTimeout(20_000L) {
                        DataProvider.feedRepository.addReel(
                            FeedPost(
                                id = "",
                                authorId = author.id,
                                authorName = author.name,
                                authorImageUrl = author.profileImageUrl,
                                type = PostType.REEL,
                                title = "",
                                content = job.caption,
                                mediaAssetId = assetId,
                                locationText = job.locationText,
                                createdAt = now,
                                updatedAt = now,
                                petId = job.petIds.firstOrNull(),
                                petIds = job.petIds,
                                localityId = job.localityId,
                                compositionJson = job.compositionJson,
                                mediaMime = job.mimeType
                            ),
                            assetId
                        )
                    }
                    created.getOrElse { error ->
                        val again = recoverReelId(assetId)
                        if (!again.isNullOrBlank()) again else throw error
                    }
                }
            }
            ReelPublishTrace.mark(
                ReelPublishTrace.Stage.CREATE_REEL_SUCCESS,
                result = postId.take(8)
            )
            logStage("create", System.currentTimeMillis() - createStarted, job.sizeBytes, postId.take(8))
            job = persist(
                job.copy(
                    backendPostId = postId,
                    createStarted = true,
                    createCompleted = true
                ).withPhaseCompleted(ReelPublishPhase.CREATE)
            )
            noteCreatedReel(postId, assetId)
            if (job.petIds.isNotEmpty() && !job.petsAttached) {
                job = persist(
                    job.copy(petsAttached = true).withPhaseCompleted(ReelPublishPhase.ATTACH_PETS)
                )
                ReelPublishTrace.mark(ReelPublishTrace.Stage.ATTACH_PETS, result = job.petIds.size.toString())
                logStage("attach_pets", 0L, job.sizeBytes, job.petIds.size.toString())
            } else if (job.petIds.isEmpty()) {
                job = persist(job.copy(petsAttached = true, vitaCoraResolved = true))
            }
            if (job.petIds.isNotEmpty() && !job.vitaCoraResolved) {
                val attachStarted = System.currentTimeMillis()
                val vitaCora = VitaCoraSocialSave.saveApprovedReel(
                    contentId = postId,
                    petIds = job.petIds,
                    compositionJson = job.compositionJson,
                    mediaAssetId = assetId,
                    mediaMime = job.mimeType
                )
                logStage(
                    "vitacora",
                    System.currentTimeMillis() - attachStarted,
                    job.sizeBytes,
                    if (vitaCora.isSuccess) "OK" else "FAIL"
                )
                if (vitaCora.isFailure) {
                    ReelPublishTrace.mark(ReelPublishTrace.Stage.VITACORA, result = "FAIL")
                    fail(job, PendingSocialPublishErrors.LINK_FAILED, "VITACORA")
                    return false
                }
                job = persist(
                    job.copy(vitaCoraResolved = true, saveToVitaCora = false)
                        .withPhaseCompleted(ReelPublishPhase.VITACORA)
                )
                ReelPublishTrace.mark(ReelPublishTrace.Stage.VITACORA, result = "OK")
            }
            persist(
                job.copy(
                    state = PendingSocialPublishState.SUCCESS,
                    backendPostId = postId,
                    createStarted = true,
                    createCompleted = true,
                    petsAttached = true,
                    progressPercent = 100,
                    errorCategory = null,
                    lastErrorPhase = null,
                    saveToVitaCora = false,
                    vitaCoraResolved = true
                ).withPhaseCompleted(ReelPublishPhase.DONE)
            )
            cleanupFiles(job)
            ReelPublishTrace.mark(ReelPublishTrace.Stage.UI_SUCCESS, result = "OK")
            ReelPublishTrace.logDebugTimings()
            ioScope.launch {
                runCatching { DataProvider.feedRepository.refreshPosts() }
                runCatching { DataProvider.feedRepository.refreshStories() }
            }
            return true
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            val uncertain = !job.reelCreated &&
                job.state == PendingSocialPublishState.PUBLISHING &&
                job.createStarted
            fail(
                job.copy(createUncertain = job.createUncertain || uncertain),
                if (job.reelCreated) PendingSocialPublishErrors.LINK_FAILED else PendingSocialPublishErrors.TIMEOUT,
                "TIMEOUT"
            )
            return false
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            fail(job, error.message, error.message)
            return false
        }
    }

    private suspend fun recoverReelId(assetId: String): String? =
        DataProvider.feedRepository.findOwnReelIdByMediaAsset(assetId).getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun noteCreatedReel(postId: String, assetId: String) {
        ReelPublishTrace.mark(ReelPublishTrace.Stage.ASSOCIATE, result = "CREATE_ID")
        ReelPublishTrace.mark(ReelPublishTrace.Stage.CONFIRM, result = postId.take(8))
        logStage("confirm", 0L, null, "ID_ONLY")
        if (BuildConfig.DEBUG && assetId.isNotBlank()) {
            ioScope.launch {
                val associated = recoverReelId(assetId)
                if (associated != null && associated != postId) {
                    ReelPublishTrace.mark(ReelPublishTrace.Stage.CONFIRM, result = "LOOKUP_MISMATCH")
                }
            }
        }
    }

    private suspend fun fail(job: PendingSocialPublish, code: String?, technical: String?) {
        val category = PendingSocialPublishErrors.fromBlob(
            "${code.orEmpty()} ${technical.orEmpty()}",
            reelAlreadyCreated = job.reelCreated
        )
        val updated = persist(
            job.copy(
                state = PendingSocialPublishState.FAILED,
                errorCategory = category,
                lastErrorPhase = job.nextPhase().name,
                progressPercent = null
            )
        )
        ReelPublishTrace.mark(ReelPublishTrace.Stage.UI_ERROR, result = category)
        ReelPublishTrace.logDebugTimings()
        if (!updated.shouldKeepFiles) cleanupFiles(updated)
    }

    private suspend fun persist(job: PendingSocialPublish): PendingSocialPublish {
        val next = job.copy(updatedAtEpochMs = System.currentTimeMillis())
        store.write(next)
        if (next.belongsTo(boundUserId ?: currentUserId())) {
            _job.value = next
        }
        return next
    }

    private suspend fun maybeClearTerminal(jobId: String, userId: String?) {
        val current = store.read(userId)?.takeIf { it.jobId == jobId } ?: return
        if (current.state == PendingSocialPublishState.SUCCESS) {
            cleanupFiles(current)
            store.clear(userId)
            _job.value = null
        }
    }

    private fun preparedUploadOf(job: PendingSocialPublish): PreparedFileUpload {
        val session = FileUploadSession(
            id = job.sessionId.orEmpty(),
            assetId = job.assetId.orEmpty(),
            versionId = job.versionId.orEmpty(),
            state = FileUploadSessionState.UPLOADING,
            createdAtEpochMs = job.createdAtEpochMs
        )
        return PreparedFileUpload(
            session = session,
            physicalBucket = job.physicalBucket.orEmpty(),
            storagePath = job.storagePath.orEmpty(),
            assetId = job.assetId.orEmpty(),
            versionId = job.versionId.orEmpty(),
            logicalBucket = runCatching { FileLogicalBucket.valueOf(job.logicalBucket.orEmpty()) }
                .getOrDefault(FileLogicalBucket.PUBLIC_MEDIA)
        )
    }

    private fun isStale(job: PendingSocialPublish): Boolean {
        val age = System.currentTimeMillis() - job.updatedAtEpochMs
        return age > STALE_MS
    }

    private suspend fun currentUserId(): String? =
        AuthProvider.repository.getCurrentUser()?.id

    companion object {
        private const val STALE_MS = 15L * 60L * 1000L
        @Volatile
        private var instance: ReelPublishController? = null

        fun get(): ReelPublishController =
            instance ?: initialize(com.comunidapp.app.LeoverApplication.instance)

        fun initialize(context: Context): ReelPublishController {
            val created = instance ?: ReelPublishController(PendingSocialPublishStore(context)).also {
                instance = it
            }
            return created
        }

        fun jobDir(context: Context, jobId: String): File =
            File(context.applicationContext.filesDir, "reel_jobs/$jobId")

        fun copySource(context: Context, source: Uri, dest: File) {
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: error("FILE_READ_FAILED")
            if (!dest.exists() || dest.length() <= 0L) error("FILE_READ_FAILED")
        }

        fun cleanupFiles(job: PendingSocialPublish) {
            job.sourcePath?.let { path -> File(path).parentFile?.deleteRecursively() }
            job.uploadPath?.let { path ->
                val parent = File(path).parentFile
                if (parent != null && parent.name.isNotBlank()) parent.deleteRecursively()
            }
        }

        fun logStage(name: String, durationMs: Long, bytes: Long? = null, result: String? = null) {
            if (!BuildConfig.DEBUG) return
            val safeResult = result?.take(24).orEmpty()
            val size = bytes?.takeIf { it > 0L }?.let { " size=$it" }.orEmpty()
            android.util.Log.i("ReelPublishStage", "stage=$name durationMs=$durationMs$size result=$safeResult")
        }
    }
}

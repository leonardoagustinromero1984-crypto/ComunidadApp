package com.comunidapp.shared.lostfound

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.domain.lostfound.LostFoundStatusRules
import com.comunidapp.shared.remote.LostFoundInsertCommand
import com.comunidapp.shared.remote.LostFoundMediaUploadGateway
import com.comunidapp.shared.remote.LostFoundRemoteGateway
import com.comunidapp.shared.remote.LostFoundWriteGateway
import com.comunidapp.shared.remote.PartialLostFoundMediaUploadGateway
import com.comunidapp.shared.remote.RemoteLostFoundMapper
import com.comunidapp.shared.remote.mapLostFoundThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Lost/Found REAL_REMOTE — read `lost_found_posts` + write insert/status (KMP-8/22).
 * Media M05: PARTIAL cuando no hay upload gateway.
 */
internal class RemoteLostFoundRepository(
    private val gateway: LostFoundRemoteGateway,
    private val writeGateway: LostFoundWriteGateway,
    private val sessionRepository: SessionRepository,
    private val mediaUploadGateway: LostFoundMediaUploadGateway = PartialLostFoundMediaUploadGateway()
) : LostFoundRepository {
    override val dataMode: LostFoundDataMode = LostFoundDataMode.REAL_REMOTE

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeList(filter: LostFoundListFilter): Flow<VerticalLoadState<List<LostFoundSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList(filter))
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDetail(id: LostFoundId): Flow<VerticalLoadState<LostFoundDetail>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                val session = sessionRepository.currentSession()
                if (session !is SessionState.Authenticated) {
                    emit(VerticalLoadState.Error("Tu sesión no está disponible."))
                    return@flow
                }
                gateway.fetchById(id.value).fold(
                    onSuccess = { row ->
                        if (row == null) {
                            emit(VerticalLoadState.Error("No encontramos ese contenido."))
                            return@fold
                        }
                        val detail = RemoteLostFoundMapper.toDetail(row)
                        if (detail == null) {
                            emit(VerticalLoadState.Error("No encontramos ese contenido."))
                        } else {
                            val canManage = !row.authorId.isNullOrBlank() &&
                                row.authorId == session.user.userId
                            emit(VerticalLoadState.Content(detail.copy(viewerCanManage = canManage)))
                        }
                    },
                    onFailure = { emit(VerticalLoadState.Error(mapLostFoundThrowable(it))) }
                )
            }
        }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun publish(request: LostFoundPublishRequest): LostFoundPublishResult {
        LostFoundDraftValidator.validate(request.draft).exceptionOrNull()?.let {
            return LostFoundPublishResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return LostFoundPublishResult.Unauthenticated("Tu sesión no está disponible.")
        }
        val user = session.user
        val draft = request.draft
        val command = LostFoundInsertCommand(
            authorId = user.userId,
            authorName = LostFoundPublishMapper.authorName(user),
            type = LostFoundPublishMapper.typeWire(draft.type),
            petName = draft.displayName?.trim()?.takeIf { it.isNotBlank() },
            species = LostFoundPublishMapper.speciesWire(draft.speciesLabel),
            location = LostFoundPublishMapper.locationText(draft),
            description = draft.description.trim(),
            contactInfo = LostFoundPublishMapper.resolveContactInfo(draft, user),
            status = LostFoundPublishMapper.initialStatus().name,
            photoUrl = null
        )
        val insertResult = writeGateway.insert(command)
        val newId = insertResult.getOrElse { return mapPublishThrowable(it) }

        var mediaAttached = false
        var mediaDeferred = false
        val media = request.media
        if (media != null) {
            val upload = mediaUploadGateway.uploadForCase(
                caseId = newId,
                actorUserId = user.userId,
                file = media
            )
            val assetId = upload.getOrNull()
            if (assetId != null) {
                val photoUpdate = writeGateway.updatePhotoUrl(newId, assetId)
                if (photoUpdate.isSuccess) {
                    mediaAttached = true
                } else {
                    mediaDeferred = true
                }
            } else {
                mediaDeferred = true
            }
        }

        val publicCode = writeGateway.fetchPublicCode(newId).getOrNull()
        refreshTick.update { it + 1 }
        return LostFoundPublishResult.Success(
            id = LostFoundId(newId),
            publicCode = publicCode,
            mediaAttached = mediaAttached,
            mediaDeferred = mediaDeferred
        )
    }

    override suspend fun markResolved(id: LostFoundId): LostFoundManageResult {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return LostFoundManageResult.Unauthenticated("Tu sesión no está disponible.")
        }
        val row = gateway.fetchById(id.value).getOrElse {
            return mapManageThrowable(it)
        } ?: return LostFoundManageResult.BackendError("No encontramos ese contenido.")

        if (row.authorId.isNullOrBlank() || row.authorId != session.user.userId) {
            return LostFoundManageResult.Forbidden("No tenés permiso para esta acción.")
        }
        val status = RemoteLostFoundMapper.mapStatus(row.status)
            ?: return LostFoundManageResult.Conflict("No se puede resolver este caso.")
        if (!LostFoundStatusRules.canResolve(status)) {
            return LostFoundManageResult.Conflict("No se puede resolver este caso.")
        }

        writeGateway.updateStatus(id.value, LostFoundCaseStatus.RESOLVED.name).getOrElse {
            return mapManageThrowable(it)
        }
        refreshTick.update { it + 1 }
        return LostFoundManageResult.Success
    }

    override suspend fun updateOwnerContent(
        id: LostFoundId,
        description: String?,
        location: String?
    ): LostFoundManageResult {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return LostFoundManageResult.Unauthenticated("Tu sesión no está disponible.")
        }
        val row = gateway.fetchById(id.value).getOrElse {
            return mapManageThrowable(it)
        } ?: return LostFoundManageResult.BackendError("No encontramos ese contenido.")

        if (row.authorId.isNullOrBlank() || row.authorId != session.user.userId) {
            return LostFoundManageResult.Forbidden("No tenés permiso para esta acción.")
        }

        writeGateway.updateOwnerFields(
            id = id.value,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            location = location?.trim()?.takeIf { it.isNotEmpty() }
        ).getOrElse { return mapManageThrowable(it) }
        refreshTick.update { it + 1 }
        return LostFoundManageResult.Success
    }

    private suspend fun loadList(filter: LostFoundListFilter): VerticalLoadState<List<LostFoundSummary>> {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return VerticalLoadState.Error("Tu sesión no está disponible.")
        }
        return gateway.listPosts().fold(
            onSuccess = { rows ->
                val mapped = rows.mapNotNull(RemoteLostFoundMapper::toSummary)
                val filtered = mapped.filter { summary ->
                    when (filter) {
                        LostFoundListFilter.ALL -> true
                        LostFoundListFilter.LOST -> summary.type == LostFoundCaseType.LOST
                        LostFoundListFilter.FOUND -> summary.type == LostFoundCaseType.FOUND
                    }
                }
                if (filtered.isEmpty()) VerticalLoadState.Empty
                else VerticalLoadState.Content(filtered)
            },
            onFailure = { VerticalLoadState.Error(mapLostFoundThrowable(it)) }
        )
    }

    private fun mapManageThrowable(t: Throwable): LostFoundManageResult {
        val msg = mapLostFoundThrowable(t)
        val raw = t.message.orEmpty().lowercase()
        return when {
            "401" in raw || "jwt" in raw || "not authenticated" in raw ->
                LostFoundManageResult.Unauthenticated(msg)
            "403" in raw || "forbidden" in raw || "rls" in raw || "permission" in raw ->
                LostFoundManageResult.Forbidden(msg)
            "invalid_transition" in raw || "conflict" in raw ->
                LostFoundManageResult.Conflict(msg)
            else -> LostFoundManageResult.BackendError(msg)
        }
    }
}

internal class UnconfiguredLostFoundRepository : LostFoundRepository {
    override val dataMode: LostFoundDataMode = LostFoundDataMode.REAL_REMOTE

    override fun observeList(filter: LostFoundListFilter): Flow<VerticalLoadState<List<LostFoundSummary>>> =
        flow {
            emit(VerticalLoadState.Loading)
            emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
        }

    override fun observeDetail(id: LostFoundId): Flow<VerticalLoadState<LostFoundDetail>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override suspend fun refresh() = Unit

    override suspend fun publish(request: LostFoundPublishRequest): LostFoundPublishResult =
        LostFoundPublishResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))

    override suspend fun markResolved(id: LostFoundId): LostFoundManageResult =
        LostFoundManageResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))

    override suspend fun updateOwnerContent(
        id: LostFoundId,
        description: String?,
        location: String?
    ): LostFoundManageResult =
        LostFoundManageResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))
}

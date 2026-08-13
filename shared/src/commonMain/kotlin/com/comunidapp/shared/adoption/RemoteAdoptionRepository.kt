package com.comunidapp.shared.adoption

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.domain.adoption.AdoptionStatusRules
import com.comunidapp.shared.remote.AdoptionRemoteGateway
import com.comunidapp.shared.remote.AdoptionWriteKind
import com.comunidapp.shared.remote.RemoteAdoptionMapper
import com.comunidapp.shared.remote.RemoteCreateAdoptionParams
import com.comunidapp.shared.remote.classifyAdoptionWrite
import com.comunidapp.shared.remote.mapAdoptionThrowable
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
 * Adopciones REAL_REMOTE — list/detail + create publish M09.
 */
internal class RemoteAdoptionRepository(
    private val gateway: AdoptionRemoteGateway,
    private val sessionRepository: SessionRepository
) : AdoptionRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.REAL_REMOTE

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeList(): Flow<VerticalLoadState<List<AdoptionSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList())
            }
        }

    override fun observeDetail(id: AdoptionId): Flow<VerticalLoadState<AdoptionDetail>> = flow {
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
                val detail = RemoteAdoptionMapper.toDetail(row)
                if (detail == null || !AdoptionStatusRules.isPubliclyVisible(detail.status)) {
                    emit(VerticalLoadState.Error("No encontramos ese contenido."))
                } else {
                    emit(VerticalLoadState.Content(detail))
                }
            },
            onFailure = { emit(VerticalLoadState.Error(mapAdoptionThrowable(it))) }
        )
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun publish(draft: AdoptionPublishDraft): AdoptionPublishResult {
        AdoptionPublishDraftValidator.validate(draft).exceptionOrNull()?.let {
            return AdoptionPublishResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return AdoptionPublishResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return gateway.create(
            RemoteCreateAdoptionParams(
                petId = draft.petId.value,
                title = draft.title.trim(),
                description = draft.description.trim(),
                requirements = draft.requirements.trim(),
                locationText = draft.approximateLocation.displayLabel().takeIf {
                    it != "Zona no especificada"
                }.orEmpty().ifBlank {
                    listOfNotNull(
                        draft.approximateLocation.locality.takeIf { it.isNotBlank() },
                        draft.approximateLocation.region?.takeIf { it.isNotBlank() }
                    ).joinToString(", ")
                },
                publish = draft.publishImmediately
            )
        ).fold(
            onSuccess = { row ->
                refreshTick.update { it + 1 }
                AdoptionPublishResult.Success(
                    id = AdoptionId(row.id),
                    published = row.status.equals("PUBLISHED", ignoreCase = true)
                )
            },
            onFailure = { t ->
                val msg = mapAdoptionThrowable(t)
                when (classifyAdoptionWrite(t)) {
                    AdoptionWriteKind.UNAUTHENTICATED ->
                        AdoptionPublishResult.Unauthenticated(msg)
                    AdoptionWriteKind.FORBIDDEN ->
                        AdoptionPublishResult.Forbidden(msg)
                    AdoptionWriteKind.CONFLICT ->
                        AdoptionPublishResult.Conflict(msg)
                    AdoptionWriteKind.VALIDATION ->
                        AdoptionPublishResult.ValidationError(msg)
                    AdoptionWriteKind.BACKEND ->
                        AdoptionPublishResult.BackendError(msg)
                }
            }
        )
    }

    private suspend fun loadList(): VerticalLoadState<List<AdoptionSummary>> {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return VerticalLoadState.Error("Tu sesión no está disponible.")
        }
        return gateway.listPublished().fold(
            onSuccess = { rows ->
                val visible = rows.mapNotNull(RemoteAdoptionMapper::toSummary).filter {
                    AdoptionStatusRules.isPubliclyVisible(it.status)
                }
                if (visible.isEmpty()) VerticalLoadState.Empty
                else VerticalLoadState.Content(visible)
            },
            onFailure = { VerticalLoadState.Error(mapAdoptionThrowable(it)) }
        )
    }
}

internal class UnconfiguredAdoptionRepository : AdoptionRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.REAL_REMOTE

    override fun observeList(): Flow<VerticalLoadState<List<AdoptionSummary>>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override fun observeDetail(id: AdoptionId): Flow<VerticalLoadState<AdoptionDetail>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override suspend fun refresh() = Unit

    override suspend fun publish(draft: AdoptionPublishDraft) =
        AdoptionPublishResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))
}

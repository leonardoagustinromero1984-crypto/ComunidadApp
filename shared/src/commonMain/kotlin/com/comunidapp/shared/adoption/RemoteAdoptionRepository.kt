package com.comunidapp.shared.adoption

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.domain.adoption.AdoptionStatusRules
import com.comunidapp.shared.remote.AdoptionRemoteGateway
import com.comunidapp.shared.remote.RemoteAdoptionMapper
import com.comunidapp.shared.remote.mapAdoptionThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Adopciones REAL_REMOTE — RPC M09 `m09_list_published_adoptions` / `m09_get_adoption`.
 * Visibilidad: backend + [AdoptionStatusRules.isPubliclyVisible].
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
}

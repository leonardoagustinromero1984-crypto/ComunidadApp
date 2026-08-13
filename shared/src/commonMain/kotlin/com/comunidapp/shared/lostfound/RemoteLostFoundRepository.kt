package com.comunidapp.shared.lostfound

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.remote.LostFoundRemoteGateway
import com.comunidapp.shared.remote.RemoteLostFoundMapper
import com.comunidapp.shared.remote.mapLostFoundThrowable
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
 * Lost/Found REAL_REMOTE — PostgREST `lost_found_posts`.
 * Autorización: sesión autenticada + RLS. UI SAFE sin coords/PII.
 */
internal class RemoteLostFoundRepository(
    private val gateway: LostFoundRemoteGateway,
    private val sessionRepository: SessionRepository
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

    override fun observeDetail(id: LostFoundId): Flow<VerticalLoadState<LostFoundDetail>> = flow {
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
                    emit(VerticalLoadState.Content(detail))
                }
            },
            onFailure = { emit(VerticalLoadState.Error(mapLostFoundThrowable(it))) }
        )
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
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
}

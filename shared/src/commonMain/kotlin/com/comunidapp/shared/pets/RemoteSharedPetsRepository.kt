package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.remote.PetsRemoteGateway
import com.comunidapp.shared.remote.RemotePetsMapper
import com.comunidapp.shared.remote.mapPetsThrowable
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
 * Mascotas REAL_REMOTE — RPC `m08_list_accessible_pets` + SELECT `pets`.
 * Autorización en backend (RLS / m08_actor_can_read_pet).
 */
internal class RemoteSharedPetsRepository(
    private val gateway: PetsRemoteGateway,
    private val sessionRepository: SessionRepository
) : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.REAL_REMOTE

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList(userId))
            }
        }

    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> = flow {
        emit(VerticalLoadState.Loading)
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            emit(VerticalLoadState.Error("Tu sesión no está disponible."))
            return@flow
        }
        val result = gateway.fetchPetById(petId.value)
        result.fold(
            onSuccess = { row ->
                if (row == null) {
                    emit(VerticalLoadState.Error("No encontramos ese contenido."))
                } else {
                    emit(VerticalLoadState.Content(RemotePetsMapper.toDetail(row)))
                }
            },
            onFailure = { emit(VerticalLoadState.Error(mapPetsThrowable(it))) }
        )
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    private suspend fun loadList(userId: String): VerticalLoadState<List<PetSummary>> {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return VerticalLoadState.Error("Tu sesión no está disponible.")
        }
        if (session.user.userId != userId) {
            return VerticalLoadState.Error("No tenés permiso para ver estas mascotas.")
        }
        return gateway.listAccessibleActivePets().fold(
            onSuccess = { rows ->
                if (rows.isEmpty()) VerticalLoadState.Empty
                else VerticalLoadState.Content(rows.map(RemotePetsMapper::toSummary))
            },
            onFailure = { VerticalLoadState.Error(mapPetsThrowable(it)) }
        )
    }
}

internal class UnconfiguredSharedPetsRepository : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.REAL_REMOTE

    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> = flow {
        emit(VerticalLoadState.Loading)
        emit(VerticalLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override suspend fun refresh() = Unit
}

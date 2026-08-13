package com.comunidapp.shared.profile

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.remote.ProfileRemoteGateway
import com.comunidapp.shared.remote.RemoteProfileMapper
import com.comunidapp.shared.remote.mapProfileThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Perfil propio REAL_REMOTE — SELECT `users` bajo RLS.
 */
internal class RemoteUserProfileRepository(
    private val gateway: ProfileRemoteGateway,
    private val sessionRepository: SessionRepository
) : UserProfileRepository {
    override val dataMode: ProfileDataMode = ProfileDataMode.REAL_REMOTE

    override fun observeMyProfile(userId: String): Flow<ProfileLoadState> = flow {
        emit(ProfileLoadState.Loading)
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            emit(ProfileLoadState.Error("Tu sesión no está disponible."))
            return@flow
        }
        if (session.user.userId != userId) {
            emit(ProfileLoadState.Error("No tenés permiso para ver este perfil."))
            return@flow
        }
        val result = gateway.fetchMyProfile(userId)
        result.fold(
            onSuccess = { row ->
                if (row == null) {
                    emit(ProfileLoadState.Error("No encontramos ese contenido."))
                } else {
                    emit(
                        ProfileLoadState.Content(
                            RemoteProfileMapper.toSummary(row, sessionEmail = session.user.email)
                        )
                    )
                }
            },
            onFailure = { emit(ProfileLoadState.Error(mapProfileThrowable(it))) }
        )
    }
}

/**
 * Config ausente — sigue REAL_REMOTE (no fake).
 */
internal class UnconfiguredUserProfileRepository : UserProfileRepository {
    override val dataMode: ProfileDataMode = ProfileDataMode.REAL_REMOTE

    override fun observeMyProfile(userId: String): Flow<ProfileLoadState> = flow {
        emit(ProfileLoadState.Loading)
        emit(
            ProfileLoadState.Error(
                AuthFailureMessages.message(AuthFailure.Unavailable)
            )
        )
    }
}

package com.comunidapp.shared.profile

import com.comunidapp.shared.auth.AuthFailure
import com.comunidapp.shared.auth.AuthFailureMessages
import com.comunidapp.shared.media.MediaResolver
import com.comunidapp.shared.poc.m08.model.FileRef
import com.comunidapp.shared.remote.ProfileRemoteGateway
import com.comunidapp.shared.remote.ProfileWriteRemoteGateway
import com.comunidapp.shared.remote.RemoteProfileMapper
import com.comunidapp.shared.remote.mapProfileThrowable
import com.comunidapp.shared.remote.mapProfileWriteThrowable
import com.comunidapp.shared.session.SessionRepository
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.ui.ErrorSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface UserProfileRepository {
    val dataMode: ProfileDataMode
    fun observeMyProfile(userId: String): Flow<ProfileLoadState>
    suspend fun updateProfile(draft: ProfileUpdateDraft): ProfileUpdateResult
    suspend fun uploadAvatar(file: FileRef): ProfileUpdateResult
}

class GetMyProfileUseCase(private val repository: UserProfileRepository) {
    operator fun invoke(userId: String): Flow<ProfileLoadState> =
        repository.observeMyProfile(userId)
}

/**
 * Perfil propio REAL_REMOTE — SELECT `users` + `update_my_profile` + avatar legacy.
 */
internal class RemoteUserProfileRepository(
    private val gateway: ProfileRemoteGateway,
    private val writeGateway: ProfileWriteRemoteGateway,
    private val avatarUpload: ProfileAvatarUploadGateway,
    private val sessionRepository: SessionRepository,
    private val mediaResolver: MediaResolver?
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
        emit(loadProfile(userId, session.user.email))
    }

    override suspend fun updateProfile(draft: ProfileUpdateDraft): ProfileUpdateResult {
        ProfileUpdateDraftValidator.validate(draft).exceptionOrNull()?.let {
            return ProfileUpdateResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return ProfileUpdateResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return writeGateway.updateMyProfile(draft).fold(
            onSuccess = {
                when (val state = loadProfile(session.user.userId, session.user.email)) {
                    is ProfileLoadState.Content -> ProfileUpdateResult.Success(state.profile)
                    is ProfileLoadState.Error -> ProfileUpdateResult.BackendError(state.message)
                    ProfileLoadState.Loading -> ProfileUpdateResult.BackendError(
                        "No pudimos recargar el perfil."
                    )
                }
            },
            onFailure = { t ->
                val msg = mapProfileWriteThrowable(t)
                val raw = t.message.orEmpty().lowercase()
                when {
                    "401" in raw || "not authenticated" in raw ->
                        ProfileUpdateResult.Unauthenticated(msg)
                    "403" in raw || "forbidden" in raw ->
                        ProfileUpdateResult.Forbidden(msg)
                    else -> ProfileUpdateResult.BackendError(msg)
                }
            }
        )
    }

    override suspend fun uploadAvatar(file: FileRef): ProfileUpdateResult {
        val session = sessionRepository.currentSession()
        if (session !is SessionState.Authenticated) {
            return ProfileUpdateResult.Unauthenticated("Tu sesión no está disponible.")
        }
        return when (val uploaded = avatarUpload.upload(session.user.userId, file)) {
            is ProfileAvatarUploadResult.ValidationError ->
                ProfileUpdateResult.ValidationError(uploaded.message)
            is ProfileAvatarUploadResult.BackendError ->
                ProfileUpdateResult.BackendError(uploaded.message)
            is ProfileAvatarUploadResult.Success -> {
                val update = updateProfile(ProfileUpdateDraft(avatarPath = uploaded.avatarPath))
                mediaResolver?.invalidateProfileAvatars()
                update
            }
        }
    }

    private suspend fun loadProfile(userId: String, email: String?): ProfileLoadState {
        return gateway.fetchMyProfile(userId).fold(
            onSuccess = { row ->
                if (row == null) ProfileLoadState.Error("No encontramos ese contenido.")
                else ProfileLoadState.Content(RemoteProfileMapper.toSummary(row, sessionEmail = email))
            },
            onFailure = { ProfileLoadState.Error(mapProfileThrowable(it)) }
        )
    }
}

internal class UnconfiguredUserProfileRepository : UserProfileRepository {
    override val dataMode: ProfileDataMode = ProfileDataMode.REAL_REMOTE

    override fun observeMyProfile(userId: String): Flow<ProfileLoadState> = flow {
        emit(ProfileLoadState.Loading)
        emit(ProfileLoadState.Error(AuthFailureMessages.message(AuthFailure.Unavailable)))
    }

    override suspend fun updateProfile(draft: ProfileUpdateDraft) =
        ProfileUpdateResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))

    override suspend fun uploadAvatar(file: FileRef) =
        ProfileUpdateResult.BackendError(AuthFailureMessages.message(AuthFailure.Unavailable))
}

class FakeUserProfileRepository(
    private val clock: com.comunidapp.shared.platform.PlatformClock =
        com.comunidapp.shared.platform.PlatformClock.SYSTEM,
    private var profile: UserProfileSummary? = null,
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : UserProfileRepository {
    override val dataMode: ProfileDataMode = ProfileDataMode.SHARED_FAKE

    override fun observeMyProfile(userId: String): Flow<ProfileLoadState> = flow {
        emit(ProfileLoadState.Loading)
        if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
        if (fail) {
            emit(ProfileLoadState.Error(ErrorSanitizer.sanitize(RuntimeException("PROFILE_BACKEND_FAIL"))))
            return@flow
        }
        val resolved = profile ?: defaultProfile(userId, clock)
        emit(ProfileLoadState.Content(resolved))
    }

    override suspend fun updateProfile(draft: ProfileUpdateDraft): ProfileUpdateResult {
        ProfileUpdateDraftValidator.validate(draft).exceptionOrNull()?.let {
            return ProfileUpdateResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        val base = profile ?: defaultProfile("u1", clock)
        val next = base.copy(
            displayName = draft.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: base.displayName,
            approximateLocation = listOfNotNull(draft.city, draft.province)
                .joinToString(", ").ifBlank { base.approximateLocation },
            avatarRef = draft.avatarPath ?: base.avatarRef,
            mediaRef = com.comunidapp.shared.media.MediaRefParser.fromProfileFields(
                draft.avatarPath ?: base.avatarRef,
                null
            )
        )
        profile = next
        return ProfileUpdateResult.Success(next)
    }

    override suspend fun uploadAvatar(file: FileRef): ProfileUpdateResult =
        updateProfile(ProfileUpdateDraft(avatarPath = "users/u1/avatar/${file.name}"))

    companion object {
        fun defaultProfile(
            userId: String,
            clock: com.comunidapp.shared.platform.PlatformClock
        ): UserProfileSummary {
            val now = clock.nowEpochMs()
            return UserProfileSummary(
                userId = userId,
                displayName = "Demo LeoVer",
                email = "demo@leover.test",
                approximateLocation = "CABA (aprox.)",
                avatarRef = null,
                createdAtEpochMs = now - 86_400_000L,
                updatedAtEpochMs = now
            )
        }
    }
}

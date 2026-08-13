package com.comunidapp.shared.profile

import com.comunidapp.shared.platform.PlatformClock
import com.comunidapp.shared.ui.ErrorSanitizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface UserProfileRepository {
    val dataMode: ProfileDataMode
    fun observeMyProfile(userId: String): Flow<ProfileLoadState>
}

class GetMyProfileUseCase(private val repository: UserProfileRepository) {
    operator fun invoke(userId: String): Flow<ProfileLoadState> =
        repository.observeMyProfile(userId)
}

class FakeUserProfileRepository(
    private val clock: PlatformClock = PlatformClock.SYSTEM,
    private val profile: UserProfileSummary? = null,
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : UserProfileRepository {
    override val dataMode: ProfileDataMode = ProfileDataMode.SHARED_FAKE

    override fun observeMyProfile(userId: String): Flow<ProfileLoadState> = flow {
        emit(ProfileLoadState.Loading)
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            emit(ProfileLoadState.Error(ErrorSanitizer.sanitize(RuntimeException("PROFILE_BACKEND_FAIL"))))
            return@flow
        }
        val resolved = profile ?: defaultProfile(userId, clock)
        emit(ProfileLoadState.Content(resolved))
    }

    companion object {
        fun defaultProfile(userId: String, clock: PlatformClock): UserProfileSummary {
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

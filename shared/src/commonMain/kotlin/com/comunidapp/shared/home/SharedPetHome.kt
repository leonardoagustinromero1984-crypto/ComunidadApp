package com.comunidapp.shared.home

import com.comunidapp.app.domain.pets.PetAggregate
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.shared.platform.PlatformClock
import com.comunidapp.shared.session.SessionState
import com.comunidapp.shared.session.SessionUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Catálogo mínimo KMP-2 (compat).
 * Preferir [com.comunidapp.shared.pets.FakeSharedPetsRepository] + [com.comunidapp.shared.vertical.LeoVerSharedApp].
 */
interface SharedPetHomeRepository {
    fun observePets(): Flow<SharedHomeLoadState>
}

sealed class SharedHomeLoadState {
    data object Loading : SharedHomeLoadState()
    data object Empty : SharedHomeLoadState()
    data class Content(val pets: List<PetAggregate>) : SharedHomeLoadState()
    data class Error(val message: String) : SharedHomeLoadState()
}

class FakeSharedPetHomeRepository(
    private val clock: PlatformClock = PlatformClock.SYSTEM,
    private val seed: List<PetAggregate> = defaultPets(clock),
    private val fail: Boolean = false,
    private val artificialDelayMs: Long = 0L
) : SharedPetHomeRepository {
    override fun observePets(): Flow<SharedHomeLoadState> = flow {
        emit(SharedHomeLoadState.Loading)
        if (artificialDelayMs > 0L) delay(artificialDelayMs)
        if (fail) {
            emit(SharedHomeLoadState.Error("SHARED_HOME_UNAVAILABLE"))
            return@flow
        }
        if (seed.isEmpty()) emit(SharedHomeLoadState.Empty)
        else emit(SharedHomeLoadState.Content(seed))
    }

    companion object {
        fun defaultPets(clock: PlatformClock): List<PetAggregate> {
            val now = clock.nowEpochMs()
            return listOf(
                PetAggregate(
                    id = PetId("shared-luna"),
                    displayName = "Luna",
                    status = PetLifecycleStatus.ACTIVE,
                    principal = PetPrincipalHolder.Person("demo-user"),
                    legacyOwnerUserId = "demo-user",
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                ),
                PetAggregate(
                    id = PetId("shared-michi"),
                    displayName = "Michi",
                    status = PetLifecycleStatus.ACTIVE,
                    principal = PetPrincipalHolder.Person("demo-user"),
                    legacyOwnerUserId = "demo-user",
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            )
        }
    }
}

/** @deprecated Usar [SessionState] / [com.comunidapp.shared.session.FakeSessionRepository]. */
@Deprecated("Use SessionState + FakeSessionRepository", ReplaceWith("SessionState"))
data class SharedSessionState(
    val isAuthenticated: Boolean,
    val displayLabel: String
)

/** @deprecated Usar FakeSessionRepository. */
@Deprecated("Use FakeSessionRepository")
object SharedSessionStub {
    fun guest(): SharedSessionState =
        SharedSessionState(isAuthenticated = false, displayLabel = "Invitado (stub)")

    fun demoAuthenticated(): SharedSessionState =
        SharedSessionState(isAuthenticated = true, displayLabel = "demo-user (fake session)")

    fun toSessionState(stub: SharedSessionState): SessionState =
        if (stub.isAuthenticated) {
            SessionState.Authenticated(
                SessionUser(userId = "demo-user", displayName = stub.displayLabel)
            )
        } else {
            SessionState.Unauthenticated
        }
}

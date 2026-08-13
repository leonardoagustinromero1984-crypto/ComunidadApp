package com.comunidapp.shared.pets

import com.comunidapp.app.domain.pets.PetAggregate
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetLifecycleStatus
import com.comunidapp.app.domain.pets.PetPrincipalHolder
import com.comunidapp.shared.media.MediaRef
import com.comunidapp.shared.platform.PlatformClock
import com.comunidapp.shared.ui.ErrorSanitizer
import com.comunidapp.shared.ui.VerticalLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * Presentación segura — no expone ownerId / principal crudo / microchip completo.
 */
data class PetSummary(
    val id: PetId,
    val displayName: String,
    val speciesLabel: String,
    val status: PetLifecycleStatus,
    val hasAvatar: Boolean,
    val mediaRef: MediaRef? = null
)

data class PetDetailView(
    val id: PetId,
    val displayName: String,
    val speciesLabel: String,
    val breedText: String?,
    val sexLabel: String?,
    val status: PetLifecycleStatus,
    val hasAvatar: Boolean,
    val passportHint: String?,
    val mediaRef: MediaRef? = null
)

enum class PetsDataMode {
    REAL_REMOTE,
    SHARED_FAKE
}

interface SharedPetsRepository {
    val dataMode: PetsDataMode
    fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>>
    fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>>
    suspend fun refresh()
}

fun PetAggregate.toSummary(speciesLabel: String, hasAvatar: Boolean = media.avatar != null): PetSummary =
    PetSummary(
        id = id,
        displayName = displayName,
        speciesLabel = speciesLabel,
        status = status,
        hasAvatar = hasAvatar,
        mediaRef = media.avatar?.fileAssetId?.let { MediaRef.Asset(it) }
    )

data class FakePetSeed(
    val aggregate: PetAggregate,
    val speciesLabel: String,
    val breedText: String? = null,
    val sexLabel: String? = null,
    val passportHint: String? = "Pasaporte: disponible (demo)"
)

class FakeSharedPetsRepository(
    private val clock: PlatformClock = PlatformClock.SYSTEM,
    private val seeds: List<FakePetSeed> = defaultSeeds(clock),
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.SHARED_FAKE

    private val refreshTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList())
            }
        }

    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> = flow {
        emit(VerticalLoadState.Loading)
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PET_NOT_FOUND"))))
            return@flow
        }
        val seed = seeds.firstOrNull { it.aggregate.id == petId }
        if (seed == null) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PET_NOT_FOUND"))))
            return@flow
        }
        emit(VerticalLoadState.Content(seed.toDetail()))
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    private suspend fun loadList(): VerticalLoadState<List<PetSummary>> {
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            return VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE")))
        }
        if (seeds.isEmpty()) return VerticalLoadState.Empty
        return VerticalLoadState.Content(
            seeds.map {
                it.aggregate.toSummary(it.speciesLabel, it.aggregate.media.avatar != null)
            }
        )
    }

    private fun FakePetSeed.toDetail(): PetDetailView =
        PetDetailView(
            id = aggregate.id,
            displayName = aggregate.displayName,
            speciesLabel = speciesLabel,
            breedText = breedText,
            sexLabel = sexLabel,
            status = aggregate.status,
            hasAvatar = aggregate.media.avatar != null,
            passportHint = passportHint
        )

    companion object {
        fun defaultSeeds(clock: PlatformClock): List<FakePetSeed> {
            val now = clock.nowEpochMs()
            return listOf(
                FakePetSeed(
                    aggregate = PetAggregate(
                        id = PetId("shared-luna"),
                        displayName = "Luna",
                        status = PetLifecycleStatus.ACTIVE,
                        principal = PetPrincipalHolder.Person("demo-user"),
                        legacyOwnerUserId = "demo-user",
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    ),
                    speciesLabel = "Perro",
                    breedText = "Mestiza",
                    sexLabel = "Hembra"
                ),
                FakePetSeed(
                    aggregate = PetAggregate(
                        id = PetId("shared-michi"),
                        displayName = "Michi",
                        status = PetLifecycleStatus.ACTIVE,
                        principal = PetPrincipalHolder.Person("demo-user"),
                        legacyOwnerUserId = "demo-user",
                        createdAtEpochMs = now,
                        updatedAtEpochMs = now
                    ),
                    speciesLabel = "Gato",
                    breedText = "Común europeo",
                    sexLabel = "Macho"
                )
            )
        }
    }
}

class ObserveMyPetsUseCase(private val repository: SharedPetsRepository) {
    operator fun invoke(userId: String) = repository.observeMyPets(userId)
}

class GetPetDetailUseCase(private val repository: SharedPetsRepository) {
    operator fun invoke(petId: PetId) = repository.observePetDetail(petId)
}

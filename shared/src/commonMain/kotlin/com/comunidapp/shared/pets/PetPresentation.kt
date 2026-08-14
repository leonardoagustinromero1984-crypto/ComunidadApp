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
    val mediaRef: MediaRef? = null,
    val description: String? = null,
    val sizeLabel: String? = null,
    val ageYears: Int? = null,
    val ageMonths: Int? = null,
    val color: String? = null,
    /** Solo detalle autenticado — nunca PublicContent. */
    val health: PetHealthSummary? = null
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
    suspend fun create(draft: PetCreateDraft): PetCreateResult
    suspend fun update(petId: PetId, draft: PetEditDraft): PetEditResult
    suspend fun updateHealth(petId: PetId, draft: PetHealthDraft): PetHealthWriteResult
    suspend fun archive(petId: PetId, reason: String? = null): PetLifecycleResult
    suspend fun restore(petId: PetId): PetLifecycleResult
    suspend fun markDeceased(petId: PetId, reason: String? = null): PetLifecycleResult
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
    seeds: List<FakePetSeed> = defaultSeeds(clock),
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : SharedPetsRepository {
    override val dataMode: PetsDataMode = PetsDataMode.SHARED_FAKE

    private val refreshTick = MutableStateFlow(0)
    private val pets = seeds.toMutableList()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMyPets(userId: String): Flow<VerticalLoadState<List<PetSummary>>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                emit(loadList())
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observePetDetail(petId: PetId): Flow<VerticalLoadState<PetDetailView>> =
        refreshTick.asStateFlow().flatMapLatest {
            flow {
                emit(VerticalLoadState.Loading)
                if (delayMs > 0L) delay(delayMs)
                if (fail) {
                    emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PET_NOT_FOUND"))))
                    return@flow
                }
                val seed = pets.firstOrNull { it.aggregate.id == petId }
                if (seed == null) {
                    emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PET_NOT_FOUND"))))
                    return@flow
                }
                emit(VerticalLoadState.Content(seed.toDetail()))
            }
        }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun create(draft: PetCreateDraft): PetCreateResult {
        PetCreateDraftValidator.validate(draft).exceptionOrNull()?.let {
            return PetCreateResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (fail) {
            return PetCreateResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        val id = PetId("fake-pet-${draft.name.trim().lowercase().replace(' ', '-')}")
        return PetCreateResult.Success(
            id = id,
            avatarAttached = draft.avatarFile != null
        )
    }

    override suspend fun update(petId: PetId, draft: PetEditDraft): PetEditResult {
        PetEditDraftValidator.validate(draft).exceptionOrNull()?.let {
            return PetEditResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (fail) {
            return PetEditResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        if (pets.none { it.aggregate.id == petId }) {
            return PetEditResult.BackendError("No encontramos ese contenido.")
        }
        refreshTick.update { it + 1 }
        return PetEditResult.Success(
            id = petId,
            avatarAttached = draft.avatarFile != null
        )
    }

    override suspend fun updateHealth(petId: PetId, draft: PetHealthDraft): PetHealthWriteResult {
        PetHealthDraftValidator.validate(draft).exceptionOrNull()?.let {
            return PetHealthWriteResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (fail) {
            return PetHealthWriteResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        if (pets.none { it.aggregate.id == petId }) {
            return PetHealthWriteResult.BackendError("No encontramos ese contenido.")
        }
        lastHealthDraft = draft
        refreshTick.update { it + 1 }
        return PetHealthWriteResult.Success(petId)
    }

    override suspend fun archive(petId: PetId, reason: String?): PetLifecycleResult {
        if (fail) {
            return PetLifecycleResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        val idx = pets.indexOfFirst { it.aggregate.id == petId }
        if (idx < 0) return PetLifecycleResult.BackendError("No encontramos ese contenido.")
        val current = pets[idx]
        when (current.aggregate.status) {
            PetLifecycleStatus.ARCHIVED ->
                return PetLifecycleResult.Conflict("La mascota ya está archivada.")
            PetLifecycleStatus.DECEASED ->
                return PetLifecycleResult.Conflict("No se puede archivar una mascota fallecida.")
            PetLifecycleStatus.ACTIVE -> Unit
        }
        pets[idx] = current.copy(
            aggregate = current.aggregate.copy(
                status = PetLifecycleStatus.ARCHIVED,
                archivedAtEpochMs = clock.nowEpochMs()
            )
        )
        refreshTick.update { it + 1 }
        return PetLifecycleResult.Success(petId, PetLifecycleStatus.ARCHIVED)
    }

    override suspend fun restore(petId: PetId): PetLifecycleResult {
        if (fail) {
            return PetLifecycleResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        val idx = pets.indexOfFirst { it.aggregate.id == petId }
        if (idx < 0) return PetLifecycleResult.BackendError("No encontramos ese contenido.")
        val current = pets[idx]
        if (current.aggregate.status != PetLifecycleStatus.ARCHIVED) {
            return PetLifecycleResult.Conflict("Solo se pueden restaurar mascotas archivadas.")
        }
        pets[idx] = current.copy(
            aggregate = current.aggregate.copy(
                status = PetLifecycleStatus.ACTIVE,
                archivedAtEpochMs = null
            )
        )
        refreshTick.update { it + 1 }
        return PetLifecycleResult.Success(petId, PetLifecycleStatus.ACTIVE)
    }

    override suspend fun markDeceased(petId: PetId, reason: String?): PetLifecycleResult {
        if (fail) {
            return PetLifecycleResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE"))
            )
        }
        val idx = pets.indexOfFirst { it.aggregate.id == petId }
        if (idx < 0) return PetLifecycleResult.BackendError("No encontramos ese contenido.")
        val current = pets[idx]
        if (current.aggregate.status == PetLifecycleStatus.DECEASED) {
            return PetLifecycleResult.Conflict("La mascota ya está marcada como fallecida.")
        }
        if (current.aggregate.status != PetLifecycleStatus.ACTIVE) {
            return PetLifecycleResult.Conflict("Solo se puede marcar como fallecida desde ACTIVE.")
        }
        pets[idx] = current.copy(
            aggregate = current.aggregate.copy(
                status = PetLifecycleStatus.DECEASED,
                deceasedAtEpochMs = clock.nowEpochMs(),
                archivedAtEpochMs = null
            )
        )
        refreshTick.update { it + 1 }
        return PetLifecycleResult.Success(petId, PetLifecycleStatus.DECEASED)
    }

    var lastHealthDraft: PetHealthDraft? = null
        private set

    private suspend fun loadList(): VerticalLoadState<List<PetSummary>> {
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            return VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("PETS_UNAVAILABLE")))
        }
        val active = pets.filter { it.aggregate.status == PetLifecycleStatus.ACTIVE }
        if (active.isEmpty()) return VerticalLoadState.Empty
        return VerticalLoadState.Content(
            active.map {
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
            passportHint = passportHint,
            mediaRef = aggregate.media.avatar?.fileAssetId?.let { MediaRef.Asset(it) }
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

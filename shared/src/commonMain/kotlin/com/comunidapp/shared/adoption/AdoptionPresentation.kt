package com.comunidapp.shared.adoption

import com.comunidapp.shared.domain.adoption.AdoptionListingStatus
import com.comunidapp.shared.location.ApproximateLocation
import com.comunidapp.shared.media.MediaRef
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
 * Id opaco de listado/detalle público — no es UUID de DB ni ownerId.
 */
data class AdoptionId(val value: String) {
    init {
        require(value.isNotBlank()) { "ADOPTION_ID_BLANK" }
    }
}

enum class AdoptionDataMode {
    REAL_REMOTE,
    SHARED_FAKE
}

/**
 * Modelo SAFE para UI — sin coords, teléfono, email, userId.
 */
data class AdoptionSummary(
    val id: AdoptionId,
    val status: AdoptionListingStatus,
    val displayName: String,
    val speciesLabel: String,
    val approximateAgeLabel: String?,
    val sexLabel: String?,
    val approximateLocation: ApproximateLocation,
    val publicCode: String?,
    val hasPhoto: Boolean,
    val mediaRef: MediaRef? = null
)

data class AdoptionDetail(
    val id: AdoptionId,
    val status: AdoptionListingStatus,
    val displayName: String,
    val speciesLabel: String,
    val breedText: String?,
    val approximateAgeLabel: String?,
    val sexLabel: String?,
    val description: String,
    val approximateLocation: ApproximateLocation,
    val publisherDisplayName: String?,
    val publicCode: String?,
    val hasPhoto: Boolean,
    val mediaRef: MediaRef? = null
)

data class AdoptionDraft(
    val displayName: String,
    val speciesLabel: String,
    val description: String,
    val approximateLocation: ApproximateLocation,
    val sexLabel: String? = null,
    val approximateAgeLabel: String? = null
)

object AdoptionDraftValidator {
    fun validate(draft: AdoptionDraft): Result<Unit> {
        if (draft.displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("ADOPTION_DRAFT_NAME_BLANK"))
        }
        if (draft.speciesLabel.isBlank()) {
            return Result.failure(IllegalArgumentException("ADOPTION_DRAFT_SPECIES_BLANK"))
        }
        if (draft.description.trim().length < 8) {
            return Result.failure(IllegalArgumentException("ADOPTION_DRAFT_DESCRIPTION_SHORT"))
        }
        return Result.success(Unit)
    }
}

interface AdoptionRepository {
    val dataMode: AdoptionDataMode
    fun observeList(): Flow<VerticalLoadState<List<AdoptionSummary>>>
    fun observeDetail(id: AdoptionId): Flow<VerticalLoadState<AdoptionDetail>>
    suspend fun refresh()
    suspend fun publish(draft: AdoptionPublishDraft): AdoptionPublishResult
}

class GetAdoptionsUseCase(private val repository: AdoptionRepository) {
    operator fun invoke() = repository.observeList()
}

class GetAdoptionDetailUseCase(private val repository: AdoptionRepository) {
    operator fun invoke(id: AdoptionId) = repository.observeDetail(id)
}

data class FakeAdoptionSeed(
    val summary: AdoptionSummary,
    val detail: AdoptionDetail
)

class FakeAdoptionRepository(
    private val seeds: List<FakeAdoptionSeed> = defaultSeeds(),
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : AdoptionRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.SHARED_FAKE

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
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("ADOPTION_NOT_FOUND"))))
            return@flow
        }
        val seed = seeds.firstOrNull { it.summary.id == id }
        if (seed == null) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("ADOPTION_NOT_FOUND"))))
            return@flow
        }
        emit(VerticalLoadState.Content(seed.detail))
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    override suspend fun publish(draft: AdoptionPublishDraft): AdoptionPublishResult {
        AdoptionPublishDraftValidator.validate(draft).exceptionOrNull()?.let {
            return AdoptionPublishResult.ValidationError(ErrorSanitizer.sanitize(it))
        }
        if (fail) {
            return AdoptionPublishResult.BackendError(
                ErrorSanitizer.sanitize(IllegalStateException("ADOPTION_UNAVAILABLE"))
            )
        }
        val id = AdoptionId("fake-adopt-${draft.petId.value}")
        refreshTick.update { it + 1 }
        return AdoptionPublishResult.Success(id = id, published = draft.publishImmediately)
    }

    private suspend fun loadList(): VerticalLoadState<List<AdoptionSummary>> {
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            return VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("ADOPTION_UNAVAILABLE")))
        }
        val visible = seeds.map { it.summary }.filter {
            com.comunidapp.shared.domain.adoption.AdoptionStatusRules.isPubliclyVisible(it.status)
        }
        if (visible.isEmpty()) return VerticalLoadState.Empty
        return VerticalLoadState.Content(visible)
    }

    companion object {
        fun defaultSeeds(): List<FakeAdoptionSeed> {
            val zone = ApproximateLocation("Villa Crespo", "CABA", "AR")
            val id = AdoptionId("demo-adopt-nube")
            val id2 = AdoptionId("demo-adopt-teo")
            return listOf(
                FakeAdoptionSeed(
                    summary = AdoptionSummary(
                        id = id,
                        status = AdoptionListingStatus.PUBLISHED,
                        displayName = "Nube",
                        speciesLabel = "Gato",
                        approximateAgeLabel = "1 año",
                        sexLabel = "Hembra",
                        approximateLocation = zone,
                        publicCode = "LV-A-3003",
                        hasPhoto = true
                    ),
                    detail = AdoptionDetail(
                        id = id,
                        status = AdoptionListingStatus.PUBLISHED,
                        displayName = "Nube",
                        speciesLabel = "Gato",
                        breedText = "Común europeo",
                        approximateAgeLabel = "1 año",
                        sexLabel = "Hembra",
                        description = "Cariñosa, vacunada, busca hogar tranquilo.",
                        approximateLocation = zone,
                        publisherDisplayName = "Refugio demo",
                        publicCode = "LV-A-3003",
                        hasPhoto = true
                    )
                ),
                FakeAdoptionSeed(
                    summary = AdoptionSummary(
                        id = id2,
                        status = AdoptionListingStatus.PUBLISHED,
                        displayName = "Teo",
                        speciesLabel = "Perro",
                        approximateAgeLabel = "3 años",
                        sexLabel = "Macho",
                        approximateLocation = ApproximateLocation("Caballito", "CABA", "AR"),
                        publicCode = "LV-A-3004",
                        hasPhoto = false
                    ),
                    detail = AdoptionDetail(
                        id = id2,
                        status = AdoptionListingStatus.PUBLISHED,
                        displayName = "Teo",
                        speciesLabel = "Perro",
                        breedText = "Mestizo",
                        approximateAgeLabel = "3 años",
                        sexLabel = "Macho",
                        description = "Enérgico, bueno con niños, necesita paseos.",
                        approximateLocation = ApproximateLocation("Caballito", "CABA", "AR"),
                        publisherDisplayName = "Familia demo",
                        publicCode = "LV-A-3004",
                        hasPhoto = false
                    )
                )
            )
        }
    }
}

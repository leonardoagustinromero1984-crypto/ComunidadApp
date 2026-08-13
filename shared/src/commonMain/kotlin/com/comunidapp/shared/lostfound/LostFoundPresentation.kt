package com.comunidapp.shared.lostfound

import com.comunidapp.shared.domain.lostfound.LostFoundCaseStatus
import com.comunidapp.shared.domain.lostfound.LostFoundCaseType
import com.comunidapp.shared.location.ApproximateLocation
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
data class LostFoundId(val value: String) {
    init {
        require(value.isNotBlank()) { "LOST_FOUND_ID_BLANK" }
    }
}

enum class LostFoundListFilter {
    ALL,
    LOST,
    FOUND
}

enum class LostFoundDataMode {
    REAL_REMOTE,
    SHARED_FAKE
}

/**
 * Modelo SAFE para UI — sin coords, teléfono, email, userId, ownerId.
 */
data class LostFoundSummary(
    val id: LostFoundId,
    val type: LostFoundCaseType,
    val status: LostFoundCaseStatus,
    val displayName: String?,
    val speciesLabel: String,
    val approximateLocation: ApproximateLocation,
    val reportedAtLabel: String,
    val publicCode: String?,
    val hasPhoto: Boolean
)

data class LostFoundDetail(
    val id: LostFoundId,
    val type: LostFoundCaseType,
    val status: LostFoundCaseStatus,
    val displayName: String?,
    val speciesLabel: String,
    val breedText: String?,
    val sexLabel: String?,
    val description: String,
    val approximateLocation: ApproximateLocation,
    val reportedAtLabel: String,
    val publicCode: String?,
    val publisherDisplayName: String?,
    val hasPhoto: Boolean
)

data class LostFoundDraft(
    val type: LostFoundCaseType,
    val displayName: String?,
    val speciesLabel: String,
    val description: String,
    val approximateLocation: ApproximateLocation
)

object LostFoundDraftValidator {
    fun validate(draft: LostFoundDraft): Result<Unit> {
        if (draft.speciesLabel.isBlank()) {
            return Result.failure(IllegalArgumentException("LOST_FOUND_DRAFT_SPECIES_BLANK"))
        }
        if (draft.description.trim().length < 8) {
            return Result.failure(IllegalArgumentException("LOST_FOUND_DRAFT_DESCRIPTION_SHORT"))
        }
        if (draft.type == LostFoundCaseType.LOST && draft.displayName.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("LOST_FOUND_DRAFT_NAME_REQUIRED"))
        }
        return Result.success(Unit)
    }
}

interface LostFoundRepository {
    val dataMode: LostFoundDataMode
    fun observeList(filter: LostFoundListFilter): Flow<VerticalLoadState<List<LostFoundSummary>>>
    fun observeDetail(id: LostFoundId): Flow<VerticalLoadState<LostFoundDetail>>
    suspend fun refresh()
}

class GetLostFoundCasesUseCase(private val repository: LostFoundRepository) {
    operator fun invoke(filter: LostFoundListFilter) = repository.observeList(filter)
}

class GetLostFoundDetailUseCase(private val repository: LostFoundRepository) {
    operator fun invoke(id: LostFoundId) = repository.observeDetail(id)
}

data class FakeLostFoundSeed(
    val summary: LostFoundSummary,
    val detail: LostFoundDetail
)

class FakeLostFoundRepository(
    private val seeds: List<FakeLostFoundSeed> = defaultSeeds(),
    private val fail: Boolean = false,
    private val delayMs: Long = 0L
) : LostFoundRepository {
    override val dataMode: LostFoundDataMode = LostFoundDataMode.SHARED_FAKE

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
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("LOST_FOUND_NOT_FOUND"))))
            return@flow
        }
        val seed = seeds.firstOrNull { it.summary.id == id }
        if (seed == null) {
            emit(VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("LOST_FOUND_NOT_FOUND"))))
            return@flow
        }
        emit(VerticalLoadState.Content(seed.detail))
    }

    override suspend fun refresh() {
        refreshTick.update { it + 1 }
    }

    private suspend fun loadList(filter: LostFoundListFilter): VerticalLoadState<List<LostFoundSummary>> {
        if (delayMs > 0L) delay(delayMs)
        if (fail) {
            return VerticalLoadState.Error(ErrorSanitizer.sanitize(IllegalStateException("LOST_FOUND_UNAVAILABLE")))
        }
        val filtered = seeds.map { it.summary }.filter { summary ->
            when (filter) {
                LostFoundListFilter.ALL -> true
                LostFoundListFilter.LOST -> summary.type == LostFoundCaseType.LOST
                LostFoundListFilter.FOUND -> summary.type == LostFoundCaseType.FOUND
            }
        }
        if (filtered.isEmpty()) return VerticalLoadState.Empty
        return VerticalLoadState.Content(filtered)
    }

    companion object {
        fun defaultSeeds(): List<FakeLostFoundSeed> {
            val zonePalermo = ApproximateLocation("Palermo", "CABA", "AR")
            val zoneRecoleta = ApproximateLocation("Recoleta", "CABA", "AR")
            val lostId = LostFoundId("demo-lost-luna")
            val foundId = LostFoundId("demo-found-gato")
            return listOf(
                FakeLostFoundSeed(
                    summary = LostFoundSummary(
                        id = lostId,
                        type = LostFoundCaseType.LOST,
                        status = LostFoundCaseStatus.ACTIVE,
                        displayName = "Luna",
                        speciesLabel = "Perro",
                        approximateLocation = zonePalermo,
                        reportedAtLabel = "hace 2 días",
                        publicCode = "LV-L-1001",
                        hasPhoto = true
                    ),
                    detail = LostFoundDetail(
                        id = lostId,
                        type = LostFoundCaseType.LOST,
                        status = LostFoundCaseStatus.ACTIVE,
                        displayName = "Luna",
                        speciesLabel = "Perro",
                        breedText = "Mestiza",
                        sexLabel = "Hembra",
                        description = "Se perdió cerca de la plaza. Collar rojo.",
                        approximateLocation = zonePalermo,
                        reportedAtLabel = "hace 2 días",
                        publicCode = "LV-L-1001",
                        publisherDisplayName = "Demo LeoVer",
                        hasPhoto = true
                    )
                ),
                FakeLostFoundSeed(
                    summary = LostFoundSummary(
                        id = foundId,
                        type = LostFoundCaseType.FOUND,
                        status = LostFoundCaseStatus.ACTIVE,
                        displayName = null,
                        speciesLabel = "Gato",
                        approximateLocation = zoneRecoleta,
                        reportedAtLabel = "hoy",
                        publicCode = "LV-F-2002",
                        hasPhoto = false
                    ),
                    detail = LostFoundDetail(
                        id = foundId,
                        type = LostFoundCaseType.FOUND,
                        status = LostFoundCaseStatus.ACTIVE,
                        displayName = null,
                        speciesLabel = "Gato",
                        breedText = null,
                        sexLabel = "Desconocido",
                        description = "Encontrado en la vereda, amigable, sin collar.",
                        approximateLocation = zoneRecoleta,
                        reportedAtLabel = "hoy",
                        publicCode = "LV-F-2002",
                        publisherDisplayName = "Vecino demo",
                        hasPhoto = false
                    )
                )
            )
        }
    }
}

package com.comunidapp.shared.adoption

/**
 * Postulación M09 — datos privados del postulante (no mezclar con AdoptionSummary público).
 * contactPhone solo para el propio postulante en "Mis postulaciones".
 */
data class AdoptionApplicationId(val value: String) {
    init {
        require(value.isNotBlank()) { "APPLICATION_ID_BLANK" }
    }
}

enum class AdoptionApplicationStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    val labelEs: String
        get() = when (this) {
            SUBMITTED -> "Enviada"
            UNDER_REVIEW -> "En revisión"
            ACCEPTED -> "Aceptada"
            REJECTED -> "Rechazada"
            WITHDRAWN -> "Retirada"
        }

    companion object {
        fun parse(raw: String?): AdoptionApplicationStatus? =
            when (raw?.trim()?.uppercase()) {
                "SUBMITTED", "PENDING" -> SUBMITTED
                "UNDER_REVIEW" -> UNDER_REVIEW
                "ACCEPTED" -> ACCEPTED
                "REJECTED" -> REJECTED
                "WITHDRAWN" -> WITHDRAWN
                else -> null
            }

        fun canWithdraw(status: AdoptionApplicationStatus): Boolean =
            status == SUBMITTED || status == UNDER_REVIEW

        fun canMarkUnderReview(status: AdoptionApplicationStatus): Boolean =
            status == SUBMITTED

        fun canAccept(status: AdoptionApplicationStatus): Boolean =
            status == SUBMITTED || status == UNDER_REVIEW

        fun canReject(status: AdoptionApplicationStatus): Boolean =
            status == SUBMITTED || status == UNDER_REVIEW
    }
}

data class AdoptionApplicationSummary(
    val id: AdoptionApplicationId,
    val adoptionId: AdoptionId,
    val status: AdoptionApplicationStatus,
    val adoptionTitle: String,
    val petName: String,
    val submittedAtLabel: String,
    val messagePreview: String
)

/**
 * Vista privada para el publicador/manager autorizado.
 * No reutilizar en listados públicos de adopción.
 */
data class AdoptionApplicationReviewSummary(
    val id: AdoptionApplicationId,
    val adoptionId: AdoptionId,
    val status: AdoptionApplicationStatus,
    val adoptionTitle: String,
    val petName: String,
    val submittedAtLabel: String,
    val applicantDisplayName: String,
    val messagePreview: String
)

data class AdoptionApplicationReviewDetail(
    val id: AdoptionApplicationId,
    val adoptionId: AdoptionId,
    val status: AdoptionApplicationStatus,
    val adoptionTitle: String,
    val petName: String,
    val submittedAtLabel: String,
    val applicantDisplayName: String,
    val message: String,
    val housingType: String?,
    val hasOtherPets: Boolean?,
    val previousExperience: String?,
    val contactPhone: String?,
    val rejectionReason: String?
)

data class AdoptionApplicationDraft(
    val adoptionId: AdoptionId,
    val message: String,
    val housingType: String? = null,
    val hasOtherPets: Boolean? = null,
    val previousExperience: String? = null,
    /** Opcional; solo se envía al backend — no se muestra en listados públicos. */
    val contactPhone: String? = null
)

object AdoptionApplicationDraftValidator {
    fun validate(draft: AdoptionApplicationDraft): Result<Unit> {
        val msg = draft.message.trim()
        if (msg.isEmpty() || msg.length > 2000) {
            return Result.failure(IllegalArgumentException("APPLICATION_MESSAGE_INVALID"))
        }
        val phone = draft.contactPhone?.trim().orEmpty()
        if (phone.length > 40) {
            return Result.failure(IllegalArgumentException("APPLICATION_PHONE_TOO_LONG"))
        }
        return Result.success(Unit)
    }
}

sealed interface AdoptionApplicationResult {
    data class Success(val id: AdoptionApplicationId) : AdoptionApplicationResult
    data class ValidationError(val message: String) : AdoptionApplicationResult
    data class Unauthenticated(val message: String) : AdoptionApplicationResult
    data class Forbidden(val message: String) : AdoptionApplicationResult
    data class Conflict(val message: String) : AdoptionApplicationResult
    data class BackendError(val message: String) : AdoptionApplicationResult
}

interface AdoptionApplicationRepository {
    val dataMode: AdoptionDataMode
    suspend fun submit(draft: AdoptionApplicationDraft): AdoptionApplicationResult
    suspend fun withdraw(id: AdoptionApplicationId): AdoptionApplicationResult
    suspend fun listMine(): Result<List<AdoptionApplicationSummary>>
    suspend fun listReceived(statusFilter: String? = null): Result<List<AdoptionApplicationReviewSummary>>
    suspend fun getForReview(id: AdoptionApplicationId): Result<AdoptionApplicationReviewDetail>
    suspend fun markUnderReview(id: AdoptionApplicationId): AdoptionApplicationResult
    suspend fun accept(id: AdoptionApplicationId): AdoptionApplicationResult
    suspend fun reject(id: AdoptionApplicationId, reason: String? = null): AdoptionApplicationResult
}

class UnconfiguredAdoptionApplicationRepository : AdoptionApplicationRepository {
    override val dataMode: AdoptionDataMode = AdoptionDataMode.REAL_REMOTE
    override suspend fun submit(draft: AdoptionApplicationDraft) =
        AdoptionApplicationResult.BackendError("Servicio no configurado.")
    override suspend fun withdraw(id: AdoptionApplicationId) =
        AdoptionApplicationResult.BackendError("Servicio no configurado.")
    override suspend fun listMine(): Result<List<AdoptionApplicationSummary>> =
        Result.failure(IllegalStateException("UNAVAILABLE"))
    override suspend fun listReceived(statusFilter: String?): Result<List<AdoptionApplicationReviewSummary>> =
        Result.failure(IllegalStateException("UNAVAILABLE"))
    override suspend fun getForReview(id: AdoptionApplicationId): Result<AdoptionApplicationReviewDetail> =
        Result.failure(IllegalStateException("UNAVAILABLE"))
    override suspend fun markUnderReview(id: AdoptionApplicationId) =
        AdoptionApplicationResult.BackendError("Servicio no configurado.")
    override suspend fun accept(id: AdoptionApplicationId) =
        AdoptionApplicationResult.BackendError("Servicio no configurado.")
    override suspend fun reject(id: AdoptionApplicationId, reason: String?) =
        AdoptionApplicationResult.BackendError("Servicio no configurado.")
}

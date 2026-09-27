package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionProcessSnapshot
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.FinalizedAdoption
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.SubmitApplicationParams
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.pets.PetId
import com.comunidapp.app.domain.pets.PetTransferRepository
import com.comunidapp.app.domain.pets.PetTransferStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Canonical adoption application read/write. Rows live in Postgres.
 * This repository does not write the in-memory adoption store.
 *
 * Interviews, documents, and agreements are not canonical completion gates.
 * They stay on the local mock repositories until a later contract.
 */
@Serializable
internal data class CanonicalAdoptionApplicationRow(
    val id: String,
    @SerialName("publication_id") val publicationId: String,
    @SerialName("applicant_user_id") val applicantUserId: String,
    @SerialName("applicant_name") val applicantName: String? = null,
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("pet_id") val petId: String? = null,
    @SerialName("pet_name") val petName: String? = null,
    @SerialName("publication_status") val publicationStatus: String? = null
)

internal data class CanonicalAdoptionApplicationRecord(
    val application: AdoptionApplication,
    val petId: String?,
    val publicationStatus: String
)

class CanonicalAdoptionApplicationRepository : AdoptionApplicationRepository {

    override fun observeMyApplications(applicantUserId: String): Flow<List<AdoptionApplication>> = flow {
        val rows = fetchMine().map { it.application }
        val uid = applicantUserId.trim()
        emit(if (uid.isEmpty()) rows else rows.filter { it.applicantUserId == uid })
    }

    override fun observeReceivedApplications(
        managerUserId: String,
        statusFilter: AdoptionApplicationStatus?
    ): Flow<List<AdoptionApplication>> = flow {
        val rows = fetchManaged(null).map { it.application }
        emit(
            rows.filter { statusFilter == null || it.status == statusFilter }
                .sortedByDescending { it.submittedAt }
        )
    }

    override suspend fun getApplicationById(id: String): Result<AdoptionApplication> {
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val result = rpc { fetchOne(id).application }
        val error = result.exceptionOrNull() ?: return result
        return if (M09AdoptionErrorMapper.codeOf(error) == "NOT_FOUND") {
            fail("APPLICATION_NOT_FOUND")
        } else {
            result
        }
    }

    override suspend fun submitApplication(params: SubmitApplicationParams): Result<AdoptionApplication> {
        if (params.adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        return rpc {
            val id: String = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_APPLY_ADOPTION,
                parameters = buildJsonObject { put("p_publication_id", params.adoptionId) }
            ).decodeAs()
            fetchOne(id).application
        }
    }

    override suspend fun withdrawApplication(id: String): Result<AdoptionApplication> =
        unavailable()

    override suspend fun markUnderReview(id: String): Result<AdoptionApplication> =
        unavailable()

    override suspend fun acceptApplication(id: String): Result<AdoptionApplication> {
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        return rpc {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_ACCEPT_ADOPTION_APPLICATION,
                parameters = buildJsonObject { put("p_application_id", id) }
            )
            fetchOne(id).application
        }
    }

    override suspend fun rejectApplication(id: String, reason: String?): Result<AdoptionApplication> =
        unavailable()

    override suspend fun reactivateApplication(id: String): Result<AdoptionApplication> {
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        return rpc {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_REACTIVATE_ADOPTION_APPLICATION,
                parameters = buildJsonObject { put("p_application_id", id) }
            )
            fetchOne(id).application
        }
    }

    internal suspend fun fetchMine(): List<CanonicalAdoptionApplicationRecord> =
        rpcRows<CanonicalAdoptionApplicationRow>(CanonicalBackend.RPC_LIST_MY_ADOPTION_APPLICATIONS)
            .map { it.toRecord() }

    internal suspend fun fetchManaged(publicationId: String?): List<CanonicalAdoptionApplicationRecord> {
        val params = buildJsonObject {
            if (publicationId.isNullOrBlank()) put("p_publication_id", JsonNull)
            else put("p_publication_id", publicationId)
        }
        return rpcRows<CanonicalAdoptionApplicationRow>(
            CanonicalBackend.RPC_LIST_ADOPTION_APPLICATIONS,
            params
        ).map { it.toRecord() }
    }

    private suspend fun fetchOne(id: String): CanonicalAdoptionApplicationRecord {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_GET_ADOPTION_APPLICATION,
            parameters = buildJsonObject { put("p_application_id", id) }
        ).decodeAs()
        return M08RpcDecoding.decodeRow<CanonicalAdoptionApplicationRow>(element).toRecord()
    }
}

/**
 * Canonical completion starts a care transfer to the accepted applicant.
 * It does not call the legacy finalize RPC and does not mark the publication closed.
 * Publication status changes only after the recipient accepts the transfer.
 */
class CanonicalAdoptionCompletionRepository(
    private val applications: () -> AdoptionApplicationRepository,
    private val adoptions: () -> AdoptionRepository,
    private val transfers: () -> PetTransferRepository?
) : AdoptionCompletionRepository {

    override suspend fun getProcessSnapshot(adoptionId: String): Result<AdoptionProcessSnapshot> {
        if (adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        val repo = applications() as? CanonicalAdoptionApplicationRepository
            ?: return fail("FORBIDDEN")
        return rpc {
            val managed = runCatching { repo.fetchManaged(adoptionId) }
            val isManager = managed.isSuccess
            val managedRows = managed.getOrDefault(emptyList())
            val ownRows = if (isManager) {
                emptyList()
            } else {
                repo.fetchMine().filter { it.application.adoptionId == adoptionId }
            }
            if (!isManager && ownRows.isEmpty()) {
                val code = managed.exceptionOrNull()?.let { M09AdoptionErrorMapper.codeOf(it) }
                if (code == "NOT_FOUND") {
                    throw M09AdoptionException(
                        "ADOPTION_NOT_FOUND",
                        M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND")
                    )
                }
                throw M09AdoptionException("FORBIDDEN", M09AdoptionErrorMapper.userMessage("FORBIDDEN"))
            }
            val rows = if (isManager) managedRows else ownRows
            val post = adoptions().getAdoptionById(adoptionId).getOrNull()
            val publicationStatus = rows.firstOrNull()?.publicationStatus
                ?: when (post?.status) {
                    AdoptionStatus.PUBLISHED -> "OPEN"
                    AdoptionStatus.PAUSED -> "HIDDEN"
                    AdoptionStatus.CLOSED, AdoptionStatus.ADOPTED -> "CLOSED"
                    else -> null
                }
            if (publicationStatus == null && post == null && rows.isEmpty()) {
                throw M09AdoptionException("ADOPTION_NOT_FOUND", M09AdoptionErrorMapper.userMessage("ADOPTION_NOT_FOUND"))
            }
            val accepted = rows.filter { it.application.status == AdoptionApplicationStatus.ACCEPTED }
            val open = publicationStatus.equals("OPEN", ignoreCase = true)
            val petId = rows.firstOrNull()?.petId ?: post?.petId
            val pendingTransfer = if (isManager && !petId.isNullOrBlank()) {
                runCatching {
                    transfers()?.listHistory(PetId(petId))
                        ?.any { it.status == PetTransferStatus.PENDING } == true
                }.getOrDefault(false)
            } else {
                false
            }
            val blockers = mutableListOf<String>()
            if (isManager && accepted.isEmpty()) blockers += "Falta un candidato aceptado"
            if (isManager && accepted.size > 1) blockers += "Hay más de un candidato aceptado"
            if (isManager && !open) blockers += "La publicación ya no está abierta"
            if (isManager && pendingTransfer) {
                blockers += "Hay una transferencia de cuidado pendiente de aceptación"
            }
            val completed = rows.firstOrNull { it.application.status == AdoptionApplicationStatus.COMPLETED }
            val closed = publicationStatus.equals("CLOSED", ignoreCase = true)
            val anchor = completed ?: accepted.singleOrNull()
            val finalized = if (closed && anchor != null) {
                FinalizedAdoption(
                    id = anchor.application.id,
                    adoptionId = adoptionId,
                    applicationId = anchor.application.id,
                    petId = anchor.petId ?: petId,
                    adopterUserId = anchor.application.applicantUserId,
                    finalizedAt = anchor.application.submittedAt,
                    finalizedBy = anchor.application.reviewedBy.orEmpty()
                )
            } else {
                null
            }
            AdoptionProcessSnapshot(
                adoptionId = adoptionId,
                adoptionStatus = publicationStatus.toAdoptionStatus(post?.status),
                acceptedApplication = accepted.singleOrNull()?.application ?: completed?.application,
                interviews = emptyList(),
                documents = emptyList(),
                agreement = null,
                finalized = finalized,
                followUpPlan = null,
                followUpChecks = emptyList(),
                canFinalize = isManager && open && accepted.size == 1 && !pendingTransfer && finalized == null,
                finalizeBlockers = blockers
            )
        }
    }

    override suspend fun finalizeAdoption(adoptionId: String): Result<FinalizedAdoption> {
        val snapshot = getProcessSnapshot(adoptionId).getOrElse { return Result.failure(it) }
        if (!snapshot.canFinalize) return fail("ADOPTION_NOT_READY_TO_FINALIZE")
        val accepted = snapshot.acceptedApplication ?: return fail("ADOPTION_NOT_READY_TO_FINALIZE")
        val repo = applications() as? CanonicalAdoptionApplicationRepository
            ?: return fail("FORBIDDEN")
        return rpc {
            val record = repo.fetchManaged(adoptionId)
                .firstOrNull { it.application.id == accepted.id }
                ?: throw M09AdoptionException(
                    "APPLICATION_NOT_FOUND",
                    M09AdoptionErrorMapper.userMessage("APPLICATION_NOT_FOUND")
                )
            val petId = record.petId ?: snapshot.finalized?.petId
            if (petId.isNullOrBlank()) {
                throw M09AdoptionException("PET_NOT_FOUND", M09AdoptionErrorMapper.userMessage("PET_NOT_FOUND"))
            }
            val element: JsonElement = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_INITIATE_CARE_TRANSFER,
                parameters = buildJsonObject {
                    put("p_pet_id", petId)
                    put("p_target_kind", "PERSON")
                    put("p_target_person_id", accepted.applicantUserId)
                    put("p_target_organization_id", JsonNull)
                    put("p_share_personal_media", false)
                }
            ).decodeAs()
            val transfer = M08RpcDecoding.decodeRow<CanonicalCareTransferRow>(element)
            (adoptions() as? CanonicalAdoptionRepository)?.let { runCatching { it.refresh() } }
            FinalizedAdoption(
                id = transfer.id,
                adoptionId = adoptionId,
                applicationId = accepted.id,
                petId = petId,
                adopterUserId = accepted.applicantUserId,
                finalizedAt = System.currentTimeMillis(),
                finalizedBy = transfer.initiatedByUserId.orEmpty()
            )
        }
    }
}

private fun CanonicalAdoptionApplicationRow.toRecord(): CanonicalAdoptionApplicationRecord {
    val submitted = parseEpoch(createdAt) ?: 0L
    return CanonicalAdoptionApplicationRecord(
        application = AdoptionApplication(
            id = id,
            adoptionId = publicationId,
            applicantUserId = applicantUserId,
            applicantName = applicantName.orEmpty(),
            message = "",
            status = AdoptionApplicationStatus.fromString(status),
            submittedAt = submitted,
            adoptionTitle = petName.orEmpty(),
            petName = petName.orEmpty(),
            createdAt = submitted,
            updatedAt = submitted
        ),
        petId = petId,
        publicationStatus = publicationStatus ?: "OPEN"
    )
}

private fun String?.toAdoptionStatus(fallback: AdoptionStatus?): AdoptionStatus = when (this?.uppercase()) {
    "CLOSED" -> AdoptionStatus.CLOSED
    "HIDDEN" -> AdoptionStatus.PAUSED
    "OPEN" -> AdoptionStatus.PUBLISHED
    else -> fallback ?: AdoptionStatus.PUBLISHED
}

private suspend fun <T> rpc(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is M09AdoptionException) {
        Result.failure(error)
    } else {
        val code = M09AdoptionErrorMapper.codeOf(error)
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code), error))
    }
}

private fun <T> fail(code: String): Result<T> =
    Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))

private fun unavailable(): Result<AdoptionApplication> = fail("APPLICATION_INVALID_TRANSITION")

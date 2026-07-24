package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.AdoptionAgreement
import com.comunidapp.app.data.model.AdoptionAgreementStatus
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionDocumentRequirement
import com.comunidapp.app.data.model.AdoptionDocumentStatus
import com.comunidapp.app.data.model.AdoptionDocumentType
import com.comunidapp.app.data.model.AdoptionFollowUpCheck
import com.comunidapp.app.data.model.AdoptionFollowUpPlan
import com.comunidapp.app.data.model.AdoptionFollowUpPlanStatus
import com.comunidapp.app.data.model.AdoptionFollowUpStatus
import com.comunidapp.app.data.model.AdoptionInterview
import com.comunidapp.app.data.model.AdoptionInterviewStatus
import com.comunidapp.app.data.model.AdoptionInterviewType
import com.comunidapp.app.data.model.AdoptionProcessSnapshot
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.model.AdoptionWelfareStatus
import com.comunidapp.app.data.model.FinalizedAdoption
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Reject public leover bucket / http public URLs for sensitive docs. */
object AdoptionDocumentRefValidator {
    fun isUnsafePublicReference(ref: String?): Boolean {
        if (ref.isNullOrBlank()) return false
        val lower = ref.trim().lowercase()
        if (lower.startsWith("m05://") || lower.startsWith("file_asset:")) return false
        if (lower.contains("/object/public/leover")) return true
        if (lower.contains("bucket/leover") || lower.contains("bucket=leover")) return true
        if ((lower.startsWith("http://") || lower.startsWith("https://")) &&
            lower.contains("/leover/")
        ) {
            return true
        }
        return false
    }
}

interface AdoptionInterviewRepository {
    fun observeInterviews(adoptionId: String): Flow<List<AdoptionInterview>>
    suspend fun getInterviewById(id: String): Result<AdoptionInterview>
    suspend fun scheduleInterview(
        adoptionId: String,
        applicationId: String,
        scheduledAt: Long,
        type: AdoptionInterviewType,
        locationOrLink: String?,
        notes: String?
    ): Result<AdoptionInterview>
    suspend fun confirmInterview(id: String): Result<AdoptionInterview>
    suspend fun completeInterview(id: String, outcome: String?): Result<AdoptionInterview>
    suspend fun cancelInterview(id: String): Result<AdoptionInterview>
}

interface AdoptionDocumentRepository {
    fun observeDocuments(adoptionId: String): Flow<List<AdoptionDocumentRequirement>>
    suspend fun requestDocument(
        adoptionId: String,
        applicationId: String,
        type: AdoptionDocumentType,
        required: Boolean = true
    ): Result<AdoptionDocumentRequirement>
    suspend fun submitDocumentReference(
        requirementId: String,
        storagePath: String
    ): Result<AdoptionDocumentRequirement>
    suspend fun reviewDocument(
        requirementId: String,
        approve: Boolean,
        rejectionReason: String? = null
    ): Result<AdoptionDocumentRequirement>
}

interface AdoptionAgreementRepository {
    fun observeAgreement(adoptionId: String): Flow<AdoptionAgreement?>
    suspend fun getAgreement(adoptionId: String): Result<AdoptionAgreement>
    suspend fun createAgreement(
        adoptionId: String,
        applicationId: String,
        termsVersion: String,
        termsSnapshot: String
    ): Result<AdoptionAgreement>
    suspend fun acceptAgreement(agreementId: String): Result<AdoptionAgreement>
    suspend fun cancelAgreement(agreementId: String): Result<AdoptionAgreement>
}

interface AdoptionCompletionRepository {
    suspend fun getProcessSnapshot(adoptionId: String): Result<AdoptionProcessSnapshot>
    suspend fun finalizeAdoption(adoptionId: String): Result<FinalizedAdoption>
}

interface AdoptionFollowUpRepository {
    fun observePlan(adoptionId: String): Flow<AdoptionFollowUpPlan?>
    fun observeChecks(adoptionId: String): Flow<List<AdoptionFollowUpCheck>>
    suspend fun getPlan(adoptionId: String): Result<AdoptionFollowUpPlan>
    suspend fun getCheckById(checkId: String): Result<AdoptionFollowUpCheck>
    suspend fun completeCheck(
        checkId: String,
        notes: String?,
        welfareStatus: AdoptionWelfareStatus,
        evidenceRef: String? = null
    ): Result<AdoptionFollowUpCheck>
}

/**
 * Shared in-memory store for M09 completion workflow (tests + mock DataProvider).
 */
class M09CompletionMemoryStore {
    val interviews = MutableStateFlow<List<AdoptionInterview>>(emptyList())
    val documents = MutableStateFlow<List<AdoptionDocumentRequirement>>(emptyList())
    val agreements = MutableStateFlow<List<AdoptionAgreement>>(emptyList())
    val finalized = MutableStateFlow<List<FinalizedAdoption>>(emptyList())
    val plans = MutableStateFlow<List<AdoptionFollowUpPlan>>(emptyList())
    val checks = MutableStateFlow<List<AdoptionFollowUpCheck>>(emptyList())
    /** (petId, previous, new, reason) */
    val petHistory = mutableListOf<Quad>()
    /** (petId, fromUser, toUser) */
    val transfers = mutableListOf<Triple<String, String?, String>>()

    data class Quad(val petId: String, val previous: String, val newStatus: String, val reason: String)

    fun clear() {
        interviews.value = emptyList()
        documents.value = emptyList()
        agreements.value = emptyList()
        finalized.value = emptyList()
        plans.value = emptyList()
        checks.value = emptyList()
        petHistory.clear()
        transfers.clear()
    }
}

class MockAdoptionInterviewRepository(
    private val actorUserId: () -> String? = { null },
    private val applications: () -> List<AdoptionApplication> = { emptyList() },
    private val isManager: (adoptionId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: M09CompletionMemoryStore = M09CompletionMemoryStore()
) : AdoptionInterviewRepository {

    fun store(): M09CompletionMemoryStore = store

    override fun observeInterviews(adoptionId: String): Flow<List<AdoptionInterview>> =
        store.interviews.map { list -> list.filter { it.adoptionId == adoptionId } }

    override suspend fun getInterviewById(id: String): Result<AdoptionInterview> {
        if (id.isBlank()) return fail("INTERVIEW_NOT_FOUND")
        val item = store.interviews.value.find { it.id == id } ?: return fail("INTERVIEW_NOT_FOUND")
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (!canView(item, actor)) return fail("INTERVIEW_NOT_ALLOWED")
        return Result.success(item)
    }

    override suspend fun scheduleInterview(
        adoptionId: String,
        applicationId: String,
        scheduledAt: Long,
        type: AdoptionInterviewType,
        locationOrLink: String?,
        notes: String?
    ): Result<AdoptionInterview> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (adoptionId.isBlank() || applicationId.isBlank()) return fail("INTERVIEW_NOT_FOUND")
        if (!isManager(adoptionId, actor)) return fail("INTERVIEW_NOT_ALLOWED")
        val app = applications().find { it.id == applicationId && it.adoptionId == adoptionId }
            ?: return fail("APPLICATION_NOT_FOUND")
        if (app.status != AdoptionApplicationStatus.ACCEPTED) return fail("INTERVIEW_NOT_ALLOWED")
        if (store.finalized.value.any { it.adoptionId == adoptionId }) {
            return fail("ADOPTION_ALREADY_FINALIZED")
        }
        val now = System.currentTimeMillis()
        val row = AdoptionInterview(
            id = "int_$now",
            adoptionId = adoptionId,
            applicationId = applicationId,
            scheduledAt = scheduledAt,
            type = type,
            locationOrLink = locationOrLink?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            status = AdoptionInterviewStatus.SCHEDULED,
            createdBy = actor,
            createdAt = now,
            updatedAt = now
        )
        store.interviews.value = listOf(row) + store.interviews.value
        return Result.success(row)
    }

    override suspend fun confirmInterview(id: String): Result<AdoptionInterview> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("INTERVIEW_NOT_FOUND")
        val existing = store.interviews.value.find { it.id == id } ?: return fail("INTERVIEW_NOT_FOUND")
        val app = applications().find { it.id == existing.applicationId }
        if (app?.applicantUserId != actor && !isManager(existing.adoptionId, actor)) {
            return fail("INTERVIEW_NOT_ALLOWED")
        }
        if (existing.status == AdoptionInterviewStatus.CONFIRMED) return Result.success(existing)
        if (existing.status != AdoptionInterviewStatus.SCHEDULED) {
            return fail("INTERVIEW_INVALID_TRANSITION")
        }
        return replace(existing.copy(status = AdoptionInterviewStatus.CONFIRMED, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun completeInterview(id: String, outcome: String?): Result<AdoptionInterview> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("INTERVIEW_NOT_FOUND")
        val existing = store.interviews.value.find { it.id == id } ?: return fail("INTERVIEW_NOT_FOUND")
        if (!isManager(existing.adoptionId, actor)) return fail("INTERVIEW_NOT_ALLOWED")
        if (existing.status == AdoptionInterviewStatus.COMPLETED) return Result.success(existing)
        if (existing.status == AdoptionInterviewStatus.CANCELLED) {
            return fail("INTERVIEW_INVALID_TRANSITION")
        }
        if (existing.status != AdoptionInterviewStatus.SCHEDULED &&
            existing.status != AdoptionInterviewStatus.CONFIRMED
        ) {
            return fail("INTERVIEW_INVALID_TRANSITION")
        }
        val now = System.currentTimeMillis()
        return replace(
            existing.copy(
                status = AdoptionInterviewStatus.COMPLETED,
                completedAt = now,
                outcome = outcome?.trim()?.ifBlank { null },
                updatedAt = now
            )
        )
    }

    override suspend fun cancelInterview(id: String): Result<AdoptionInterview> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("INTERVIEW_NOT_FOUND")
        val existing = store.interviews.value.find { it.id == id } ?: return fail("INTERVIEW_NOT_FOUND")
        if (!isManager(existing.adoptionId, actor)) return fail("INTERVIEW_NOT_ALLOWED")
        if (existing.status == AdoptionInterviewStatus.CANCELLED) return Result.success(existing)
        if (existing.status == AdoptionInterviewStatus.COMPLETED) {
            return fail("INTERVIEW_ALREADY_COMPLETED")
        }
        return replace(
            existing.copy(status = AdoptionInterviewStatus.CANCELLED, updatedAt = System.currentTimeMillis())
        )
    }

    private fun canView(item: AdoptionInterview, actor: String): Boolean {
        if (isManager(item.adoptionId, actor)) return true
        return applications().find { it.id == item.applicationId }?.applicantUserId == actor
    }

    private fun replace(updated: AdoptionInterview): Result<AdoptionInterview> {
        store.interviews.value = store.interviews.value.map { if (it.id == updated.id) updated else it }
        return Result.success(updated)
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

class MockAdoptionDocumentRepository(
    private val actorUserId: () -> String? = { null },
    private val applications: () -> List<AdoptionApplication> = { emptyList() },
    private val isManager: (adoptionId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: M09CompletionMemoryStore = M09CompletionMemoryStore()
) : AdoptionDocumentRepository {

    override fun observeDocuments(adoptionId: String): Flow<List<AdoptionDocumentRequirement>> =
        store.documents.map { list -> list.filter { it.adoptionId == adoptionId } }

    override suspend fun requestDocument(
        adoptionId: String,
        applicationId: String,
        type: AdoptionDocumentType,
        required: Boolean
    ): Result<AdoptionDocumentRequirement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (!isManager(adoptionId, actor)) return fail("DOCUMENT_FORBIDDEN")
        val app = applications().find { it.id == applicationId && it.adoptionId == adoptionId }
            ?: return fail("APPLICATION_NOT_FOUND")
        if (app.status != AdoptionApplicationStatus.ACCEPTED) return fail("DOCUMENT_FORBIDDEN")
        val now = System.currentTimeMillis()
        val row = AdoptionDocumentRequirement(
            id = "doc_$now",
            adoptionId = adoptionId,
            applicationId = applicationId,
            type = type,
            required = required,
            status = if (required) AdoptionDocumentStatus.PENDING else AdoptionDocumentStatus.NOT_REQUIRED,
            createdAt = now,
            updatedAt = now
        )
        store.documents.value = listOf(row) + store.documents.value
        return Result.success(row)
    }

    override suspend fun submitDocumentReference(
        requirementId: String,
        storagePath: String
    ): Result<AdoptionDocumentRequirement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (requirementId.isBlank()) return fail("DOCUMENT_REQUIREMENT_NOT_FOUND")
        val existing = store.documents.value.find { it.id == requirementId }
            ?: return fail("DOCUMENT_REQUIREMENT_NOT_FOUND")
        val app = applications().find { it.id == existing.applicationId }
        if (app?.applicantUserId != actor) return fail("DOCUMENT_FORBIDDEN")
        if (AdoptionDocumentRefValidator.isUnsafePublicReference(storagePath)) {
            return fail("DOCUMENT_UNSAFE_REFERENCE")
        }
        val path = storagePath.trim()
        if (path.isEmpty()) return fail("DOCUMENT_UNSAFE_REFERENCE")
        val now = System.currentTimeMillis()
        return replace(
            existing.copy(
                storagePath = path,
                status = AdoptionDocumentStatus.SUBMITTED,
                submittedAt = now,
                updatedAt = now
            )
        )
    }

    override suspend fun reviewDocument(
        requirementId: String,
        approve: Boolean,
        rejectionReason: String?
    ): Result<AdoptionDocumentRequirement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (requirementId.isBlank()) return fail("DOCUMENT_REQUIREMENT_NOT_FOUND")
        val existing = store.documents.value.find { it.id == requirementId }
            ?: return fail("DOCUMENT_REQUIREMENT_NOT_FOUND")
        if (!isManager(existing.adoptionId, actor)) return fail("DOCUMENT_FORBIDDEN")
        if (existing.status != AdoptionDocumentStatus.SUBMITTED &&
            existing.status != AdoptionDocumentStatus.REJECTED
        ) {
            if (existing.status == AdoptionDocumentStatus.APPROVED && approve) {
                return Result.success(existing)
            }
        }
        if (existing.status != AdoptionDocumentStatus.SUBMITTED) {
            return fail("DOCUMENT_FORBIDDEN")
        }
        val now = System.currentTimeMillis()
        return replace(
            existing.copy(
                status = if (approve) AdoptionDocumentStatus.APPROVED else AdoptionDocumentStatus.REJECTED,
                rejectionReason = if (approve) null else rejectionReason?.trim()?.ifBlank { null },
                reviewedAt = now,
                updatedAt = now
            )
        )
    }

    private fun replace(updated: AdoptionDocumentRequirement): Result<AdoptionDocumentRequirement> {
        store.documents.value = store.documents.value.map { if (it.id == updated.id) updated else it }
        return Result.success(updated)
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

class MockAdoptionAgreementRepository(
    private val actorUserId: () -> String? = { null },
    private val applications: () -> List<AdoptionApplication> = { emptyList() },
    private val isManager: (adoptionId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: M09CompletionMemoryStore = M09CompletionMemoryStore()
) : AdoptionAgreementRepository {

    override fun observeAgreement(adoptionId: String): Flow<AdoptionAgreement?> =
        store.agreements.map { list ->
            list.filter { it.adoptionId == adoptionId && AdoptionAgreementStatus.isActive(it.status) }
                .maxByOrNull { it.createdAt ?: 0L }
        }

    override suspend fun getAgreement(adoptionId: String): Result<AdoptionAgreement> {
        if (adoptionId.isBlank()) return fail("AGREEMENT_NOT_FOUND")
        val agreement = store.agreements.value
            .filter { it.adoptionId == adoptionId && AdoptionAgreementStatus.isActive(it.status) }
            .maxByOrNull { it.createdAt ?: 0L }
            ?: return fail("AGREEMENT_NOT_FOUND")
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (!canAccess(agreement, actor)) return fail("AGREEMENT_FORBIDDEN")
        return Result.success(agreement)
    }

    override suspend fun createAgreement(
        adoptionId: String,
        applicationId: String,
        termsVersion: String,
        termsSnapshot: String
    ): Result<AdoptionAgreement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (!isManager(adoptionId, actor)) return fail("AGREEMENT_FORBIDDEN")
        val app = applications().find { it.id == applicationId && it.adoptionId == adoptionId }
            ?: return fail("APPLICATION_NOT_FOUND")
        if (app.status != AdoptionApplicationStatus.ACCEPTED) return fail("AGREEMENT_FORBIDDEN")
        if (store.agreements.value.any {
                it.adoptionId == adoptionId && AdoptionAgreementStatus.isActive(it.status)
            }
        ) {
            return fail("AGREEMENT_ALREADY_EXISTS")
        }
        val adoption = InMemoryDataStore.getAdoptionPostById(adoptionId)
        val now = System.currentTimeMillis()
        val row = AdoptionAgreement(
            id = "agr_$now",
            adoptionId = adoptionId,
            applicationId = applicationId,
            adopterUserId = app.applicantUserId,
            publisherUserId = adoption?.publisherId,
            publisherOrganizationId = adoption?.publisherOrganizationId,
            termsVersion = termsVersion.trim().ifBlank { "1.0" },
            termsSnapshot = termsSnapshot.trim(),
            status = AdoptionAgreementStatus.PENDING_ADOPTER,
            createdAt = now,
            updatedAt = now
        )
        store.agreements.value = listOf(row) + store.agreements.value
        return Result.success(row)
    }

    override suspend fun acceptAgreement(agreementId: String): Result<AdoptionAgreement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (agreementId.isBlank()) return fail("AGREEMENT_NOT_FOUND")
        val existing = store.agreements.value.find { it.id == agreementId }
            ?: return fail("AGREEMENT_NOT_FOUND")
        if (existing.status == AdoptionAgreementStatus.ACCEPTED) {
            return fail("AGREEMENT_ALREADY_ACCEPTED")
        }
        if (existing.status == AdoptionAgreementStatus.CANCELLED) {
            return fail("AGREEMENT_NOT_FOUND")
        }
        val now = System.currentTimeMillis()
        val updated = when {
            actor == existing.adopterUserId && existing.adopterAcceptedAt == null -> {
                val next = existing.copy(adopterAcceptedAt = now, updatedAt = now)
                resolveStatus(next)
            }
            isManager(existing.adoptionId, actor) && existing.publisherAcceptedAt == null -> {
                val next = existing.copy(publisherAcceptedAt = now, updatedAt = now)
                resolveStatus(next)
            }
            else -> return fail("AGREEMENT_FORBIDDEN")
        }
        store.agreements.value = store.agreements.value.map { if (it.id == updated.id) updated else it }
        return Result.success(updated)
    }

    override suspend fun cancelAgreement(agreementId: String): Result<AdoptionAgreement> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        val existing = store.agreements.value.find { it.id == agreementId }
            ?: return fail("AGREEMENT_NOT_FOUND")
        if (!isManager(existing.adoptionId, actor)) return fail("AGREEMENT_FORBIDDEN")
        if (existing.status == AdoptionAgreementStatus.ACCEPTED) {
            return fail("AGREEMENT_ALREADY_ACCEPTED")
        }
        if (existing.status == AdoptionAgreementStatus.CANCELLED) return Result.success(existing)
        val updated = existing.copy(
            status = AdoptionAgreementStatus.CANCELLED,
            updatedAt = System.currentTimeMillis()
        )
        store.agreements.value = store.agreements.value.map { if (it.id == updated.id) updated else it }
        return Result.success(updated)
    }

    private fun resolveStatus(agreement: AdoptionAgreement): AdoptionAgreement {
        val both = agreement.adopterAcceptedAt != null && agreement.publisherAcceptedAt != null
        return when {
            both -> agreement.copy(status = AdoptionAgreementStatus.ACCEPTED)
            agreement.adopterAcceptedAt == null -> agreement.copy(status = AdoptionAgreementStatus.PENDING_ADOPTER)
            else -> agreement.copy(status = AdoptionAgreementStatus.PENDING_PUBLISHER)
        }
    }

    private fun canAccess(agreement: AdoptionAgreement, actor: String): Boolean =
        actor == agreement.adopterUserId || isManager(agreement.adoptionId, actor)

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

class MockAdoptionCompletionRepository(
    private val actorUserId: () -> String? = { null },
    private val applications: () -> List<AdoptionApplication> = { emptyList() },
    private val isManager: (adoptionId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: M09CompletionMemoryStore = M09CompletionMemoryStore(),
    var failTransfer: Boolean = false
) : AdoptionCompletionRepository {

    fun store(): M09CompletionMemoryStore = store

    override suspend fun getProcessSnapshot(adoptionId: String): Result<AdoptionProcessSnapshot> {
        if (adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        val adoption = InMemoryDataStore.getAdoptionPostById(adoptionId)
            ?: return fail("ADOPTION_NOT_FOUND")
        val actor = actorUserId()
        if (actor.isNullOrBlank()) return fail("NOT_AUTHENTICATED")
        val accepted = applications().filter {
            it.adoptionId == adoptionId && it.status == AdoptionApplicationStatus.ACCEPTED
        }
        val acceptedApp = accepted.singleOrNull()
        if (!isManager(adoptionId, actor) && acceptedApp?.applicantUserId != actor) {
            return fail("FORBIDDEN")
        }
        val interviews = store.interviews.value.filter { it.adoptionId == adoptionId }
        val documents = store.documents.value.filter { it.adoptionId == adoptionId }
        val agreement = store.agreements.value
            .filter { it.adoptionId == adoptionId && AdoptionAgreementStatus.isActive(it.status) }
            .maxByOrNull { it.createdAt ?: 0L }
        val finalizedRow = store.finalized.value.find { it.adoptionId == adoptionId }
        val plan = store.plans.value.find { it.adoptionId == adoptionId }
        val checks = store.checks.value
            .filter { it.adoptionId == adoptionId }
            .map { it.withOverdueDetection() }
        val blockers = mutableListOf<String>()
        if (finalizedRow == null) {
            if (adoption.status != AdoptionStatus.PAUSED) {
                blockers += "La publicación debe estar pausada"
            }
            if (acceptedApp == null) blockers += "Falta un candidato aceptado"
            if (accepted.size > 1) blockers += "Hay más de un candidato aceptado"
            if (interviews.none { it.status == AdoptionInterviewStatus.COMPLETED }) {
                blockers += "Falta completar la entrevista"
            }
            val requiredDocs = documents.filter { it.required }
            if (requiredDocs.isEmpty()) {
                blockers += "Falta solicitar documentación obligatoria"
            } else if (requiredDocs.any {
                    it.status != AdoptionDocumentStatus.APPROVED &&
                        it.status != AdoptionDocumentStatus.NOT_REQUIRED
                }
            ) {
                blockers += "Documentación obligatoria pendiente"
            }
            if (agreement?.status != AdoptionAgreementStatus.ACCEPTED) {
                blockers += "Acuerdo pendiente de aceptación"
            }
        }
        return Result.success(
            AdoptionProcessSnapshot(
                adoptionId = adoptionId,
                adoptionStatus = adoption.status,
                acceptedApplication = acceptedApp,
                interviews = interviews,
                documents = documents,
                agreement = agreement,
                finalized = finalizedRow,
                followUpPlan = plan,
                followUpChecks = checks,
                canFinalize = finalizedRow == null && blockers.isEmpty(),
                finalizeBlockers = blockers
            )
        )
    }

    override suspend fun finalizeAdoption(adoptionId: String): Result<FinalizedAdoption> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        if (!isManager(adoptionId, actor)) return fail("FORBIDDEN")

        store.finalized.value.find { it.adoptionId == adoptionId }?.let {
            return Result.success(it)
        }

        val snapshot = getProcessSnapshot(adoptionId).getOrElse { return Result.failure(it) }
        if (!snapshot.canFinalize) return fail("ADOPTION_NOT_READY_TO_FINALIZE")
        val app = snapshot.acceptedApplication ?: return fail("ADOPTION_NOT_READY_TO_FINALIZE")
        val adoption = InMemoryDataStore.getAdoptionPostById(adoptionId)
            ?: return fail("ADOPTION_NOT_FOUND")
        if (adoption.status != AdoptionStatus.PAUSED) return fail("ADOPTION_NOT_READY_TO_FINALIZE")

        if (failTransfer) return fail("ADOPTION_TRANSFER_FAILED")

        val now = System.currentTimeMillis()
        val petId = adoption.petId
        if (!petId.isNullOrBlank()) {
            val pet = InMemoryDataStore.getPetById(petId)
            if (pet != null) {
                val previous = pet.status
                InMemoryDataStore.updatePet(
                    pet.copy(
                        status = "ARCHIVED",
                        ownerId = app.applicantUserId,
                        archivedAt = now,
                        updatedAt = now
                    )
                )
                store.petHistory += M09CompletionMemoryStore.Quad(
                    petId, previous, "ARCHIVED", "ADOPTED"
                )
                store.transfers += Triple(petId, adoption.publisherId, app.applicantUserId)
            } else {
                return fail("ADOPTION_TRANSFER_FAILED")
            }
        }

        InMemoryDataStore.updateAdoptionPost(
            adoption.copy(status = AdoptionStatus.ADOPTED, updatedAt = now)
        )

        val planId = "plan_$now"
        val plan = AdoptionFollowUpPlan(
            id = planId,
            adoptionId = adoptionId,
            adopterUserId = app.applicantUserId,
            status = AdoptionFollowUpPlanStatus.ACTIVE,
            createdAt = now
        )
        store.plans.value = listOf(plan) + store.plans.value
        val dayMs = 24L * 60L * 60L * 1000L
        val checkRows = listOf(7, 30, 90).mapIndexed { index, days ->
            AdoptionFollowUpCheck(
                id = "chk_${now}_$index",
                planId = planId,
                adoptionId = adoptionId,
                dueAt = now + days * dayMs,
                status = AdoptionFollowUpStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
        store.checks.value = checkRows + store.checks.value

        val finalizedRow = FinalizedAdoption(
            id = "fin_$now",
            adoptionId = adoptionId,
            applicationId = app.id,
            petId = petId,
            adopterUserId = app.applicantUserId,
            finalizedAt = now,
            finalizedBy = actor,
            followUpPlanId = planId
        )
        store.finalized.value = listOf(finalizedRow) + store.finalized.value
        if (!petId.isNullOrBlank()) {
            runCatching {
                // Best-effort M11 hook; mock store shares via DataProvider when wired
                com.comunidapp.app.data.provider.DataProvider.shelterPetRepository
                    .onAdoptionFinalized(petId)
            }
        }
        return Result.success(finalizedRow)
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

class MockAdoptionFollowUpRepository(
    private val actorUserId: () -> String? = { null },
    private val isManager: (adoptionId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: M09CompletionMemoryStore = M09CompletionMemoryStore()
) : AdoptionFollowUpRepository {

    override fun observePlan(adoptionId: String): Flow<AdoptionFollowUpPlan?> =
        store.plans.map { list -> list.find { it.adoptionId == adoptionId } }

    override fun observeChecks(adoptionId: String): Flow<List<AdoptionFollowUpCheck>> =
        store.checks.map { list ->
            list.filter { it.adoptionId == adoptionId }
                .map { it.withOverdueDetection() }
                .sortedBy { it.dueAt }
        }

    override suspend fun getPlan(adoptionId: String): Result<AdoptionFollowUpPlan> {
        if (adoptionId.isBlank()) return fail("FOLLOWUP_NOT_FOUND")
        val plan = store.plans.value.find { it.adoptionId == adoptionId }
            ?: return fail("FOLLOWUP_NOT_FOUND")
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (!canAccess(plan.adoptionId, plan.adopterUserId, actor)) return fail("FOLLOWUP_FORBIDDEN")
        return Result.success(plan)
    }

    override suspend fun getCheckById(checkId: String): Result<AdoptionFollowUpCheck> {
        if (checkId.isBlank()) return fail("FOLLOWUP_NOT_FOUND")
        val check = store.checks.value.find { it.id == checkId }?.withOverdueDetection()
            ?: return fail("FOLLOWUP_NOT_FOUND")
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        val plan = store.plans.value.find { it.id == check.planId }
        if (plan == null || !canAccess(check.adoptionId, plan.adopterUserId, actor)) {
            return fail("FOLLOWUP_FORBIDDEN")
        }
        return Result.success(check)
    }

    override suspend fun completeCheck(
        checkId: String,
        notes: String?,
        welfareStatus: AdoptionWelfareStatus,
        evidenceRef: String?
    ): Result<AdoptionFollowUpCheck> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (checkId.isBlank()) return fail("FOLLOWUP_NOT_FOUND")
        val existing = store.checks.value.find { it.id == checkId }
            ?: return fail("FOLLOWUP_NOT_FOUND")
        if (existing.status == AdoptionFollowUpStatus.COMPLETED) {
            return fail("FOLLOWUP_ALREADY_COMPLETED")
        }
        val plan = store.plans.value.find { it.id == existing.planId }
            ?: return fail("FOLLOWUP_NOT_FOUND")
        if (!canAccess(existing.adoptionId, plan.adopterUserId, actor)) {
            return fail("FOLLOWUP_FORBIDDEN")
        }
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = AdoptionFollowUpStatus.COMPLETED,
            completedAt = now,
            notes = notes?.trim()?.ifBlank { null },
            welfareStatus = welfareStatus,
            evidenceRef = evidenceRef?.trim()?.ifBlank { null },
            updatedAt = now
        )
        store.checks.value = store.checks.value.map { if (it.id == updated.id) updated else it }
        val remaining = store.checks.value.filter {
            it.planId == plan.id && it.status != AdoptionFollowUpStatus.COMPLETED &&
                it.status != AdoptionFollowUpStatus.CANCELLED
        }
        if (remaining.isEmpty()) {
            store.plans.value = store.plans.value.map {
                if (it.id == plan.id) {
                    it.copy(status = AdoptionFollowUpPlanStatus.COMPLETED, completedAt = now)
                } else it
            }
        }
        return Result.success(updated)
    }

    private fun canAccess(adoptionId: String, adopterUserId: String, actor: String): Boolean =
        actor == adopterUserId || isManager(adoptionId, actor)

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

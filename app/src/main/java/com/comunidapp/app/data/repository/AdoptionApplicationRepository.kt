package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.AdoptionApplication
import com.comunidapp.app.data.model.AdoptionApplicationStatus
import com.comunidapp.app.data.model.AdoptionStatus
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionErrorMapper
import com.comunidapp.app.data.remote.supabase.m09.M09AdoptionException
import com.comunidapp.app.data.remote.supabase.m09.SubmitApplicationParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

interface AdoptionApplicationRepository {
    fun observeMyApplications(applicantUserId: String = ""): Flow<List<AdoptionApplication>>
    fun observeReceivedApplications(
        managerUserId: String = "",
        statusFilter: AdoptionApplicationStatus? = null
    ): Flow<List<AdoptionApplication>>

    suspend fun getApplicationById(id: String): Result<AdoptionApplication>
    suspend fun submitApplication(params: SubmitApplicationParams): Result<AdoptionApplication>
    suspend fun withdrawApplication(id: String): Result<AdoptionApplication>
    suspend fun markUnderReview(id: String): Result<AdoptionApplication>
    suspend fun acceptApplication(id: String): Result<AdoptionApplication>
    suspend fun rejectApplication(id: String, reason: String? = null): Result<AdoptionApplication>
}

/**
 * In-memory M09 application rules for mock/local and unit tests.
 */
class MockAdoptionApplicationRepository(
    private val actorUserId: () -> String? = { null },
    private val actorName: () -> String = { "Usuario" },
    private val canManagePet: (petId: String, userId: String) -> Boolean = { _, _ -> false },
    private val store: MutableStateFlow<List<AdoptionApplication>> = MutableStateFlow(emptyList())
) : AdoptionApplicationRepository {

    fun seed(applications: List<AdoptionApplication>) {
        store.value = applications
    }

    fun snapshot(): List<AdoptionApplication> = store.value

    fun clear() {
        store.value = emptyList()
    }

    override fun observeMyApplications(applicantUserId: String): Flow<List<AdoptionApplication>> =
        store.map { list ->
            val uid = applicantUserId.ifBlank { actorUserId().orEmpty() }
            list.filter { it.applicantUserId == uid }
                .sortedByDescending { it.submittedAt }
        }

    override fun observeReceivedApplications(
        managerUserId: String,
        statusFilter: AdoptionApplicationStatus?
    ): Flow<List<AdoptionApplication>> =
        store.map { list ->
            val uid = managerUserId.ifBlank { actorUserId().orEmpty() }
            list.filter { app ->
                isManagerOfAdoption(app.adoptionId, uid) &&
                    (statusFilter == null || app.status == statusFilter)
            }.sortedByDescending { it.submittedAt }
        }

    override suspend fun getApplicationById(id: String): Result<AdoptionApplication> {
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val app = store.value.find { it.id == id } ?: return fail("APPLICATION_NOT_FOUND")
        val actor = actorUserId()
        if (actor.isNullOrBlank()) return fail("NOT_AUTHENTICATED")
        if (app.applicantUserId != actor && !isManagerOfAdoption(app.adoptionId, actor)) {
            return fail("APPLICATION_FORBIDDEN")
        }
        return Result.success(enrich(app))
    }

    override suspend fun submitApplication(params: SubmitApplicationParams): Result<AdoptionApplication> {
        val actor = actorUserId()
        if (actor.isNullOrBlank()) return fail("NOT_AUTHENTICATED")
        if (params.adoptionId.isBlank()) return fail("ADOPTION_NOT_FOUND")
        val message = params.message.trim()
        if (message.isEmpty() || message.length > 2000) return fail("APPLICATION_MESSAGE_REQUIRED")

        val adoption = InMemoryDataStore.getAdoptionPostById(params.adoptionId)
            ?: return fail("ADOPTION_NOT_FOUND")
        if (adoption.status != AdoptionStatus.PUBLISHED) {
            return fail("ADOPTION_NOT_ACCEPTING_APPLICATIONS")
        }
        if (isOwnerOrManager(adoption.publisherId, adoption.petId, actor)) {
            return fail("CANNOT_APPLY_TO_OWN_ADOPTION")
        }
        val hasActive = store.value.any {
            it.adoptionId == params.adoptionId &&
                it.applicantUserId == actor &&
                AdoptionApplicationStatus.isActive(it.status)
        }
        if (hasActive) return fail("APPLICATION_ALREADY_EXISTS")

        val now = System.currentTimeMillis()
        val app = AdoptionApplication(
            id = "app_$now",
            adoptionId = params.adoptionId,
            applicantUserId = actor,
            applicantName = actorName(),
            message = message,
            housingType = params.housingType?.trim()?.ifBlank { null },
            hasOtherPets = params.hasOtherPets,
            previousExperience = params.previousExperience?.trim()?.ifBlank { null },
            contactPhone = params.contactPhone?.trim()?.ifBlank { null },
            status = AdoptionApplicationStatus.SUBMITTED,
            submittedAt = now,
            adoptionTitle = adoption.displayTitle,
            petName = adoption.name,
            petPhotoUrl = adoption.photoUrl,
            createdAt = now,
            updatedAt = now
        )
        store.value = listOf(app) + store.value
        return Result.success(app)
    }

    override suspend fun withdrawApplication(id: String): Result<AdoptionApplication> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val existing = store.value.find { it.id == id } ?: return fail("APPLICATION_NOT_FOUND")
        if (existing.applicantUserId != actor) return fail("APPLICATION_FORBIDDEN")
        if (existing.status == AdoptionApplicationStatus.WITHDRAWN) {
            return Result.success(existing)
        }
        if (existing.status != AdoptionApplicationStatus.SUBMITTED &&
            existing.status != AdoptionApplicationStatus.UNDER_REVIEW
        ) {
            return fail("APPLICATION_NOT_ACTIVE")
        }
        return replace(existing.copy(status = AdoptionApplicationStatus.WITHDRAWN, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun markUnderReview(id: String): Result<AdoptionApplication> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val existing = store.value.find { it.id == id } ?: return fail("APPLICATION_NOT_FOUND")
        if (!isManagerOfAdoption(existing.adoptionId, actor)) return fail("APPLICATION_FORBIDDEN")
        if (existing.status == AdoptionApplicationStatus.UNDER_REVIEW) {
            return Result.success(existing)
        }
        if (existing.status != AdoptionApplicationStatus.SUBMITTED) {
            return fail("APPLICATION_INVALID_TRANSITION")
        }
        val now = System.currentTimeMillis()
        return replace(
            existing.copy(
                status = AdoptionApplicationStatus.UNDER_REVIEW,
                reviewedAt = existing.reviewedAt ?: now,
                reviewedBy = actor,
                updatedAt = now
            )
        )
    }

    override suspend fun acceptApplication(id: String): Result<AdoptionApplication> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val existing = store.value.find { it.id == id } ?: return fail("APPLICATION_NOT_FOUND")
        if (!isManagerOfAdoption(existing.adoptionId, actor)) return fail("APPLICATION_FORBIDDEN")

        if (existing.status == AdoptionApplicationStatus.ACCEPTED) {
            return Result.success(existing)
        }
        when (existing.status) {
            AdoptionApplicationStatus.REJECTED -> return fail("APPLICATION_ALREADY_REJECTED")
            AdoptionApplicationStatus.WITHDRAWN -> return fail("APPLICATION_ALREADY_WITHDRAWN")
            AdoptionApplicationStatus.SUBMITTED, AdoptionApplicationStatus.UNDER_REVIEW -> Unit
            else -> return fail("APPLICATION_INVALID_TRANSITION")
        }

        val adoption = InMemoryDataStore.getAdoptionPostById(existing.adoptionId)
            ?: return fail("ADOPTION_NOT_FOUND")
        if (adoption.status != AdoptionStatus.PUBLISHED) {
            return fail("ADOPTION_NOT_ACCEPTING_APPLICATIONS")
        }
        if (store.value.any {
                it.adoptionId == existing.adoptionId &&
                    it.status == AdoptionApplicationStatus.ACCEPTED &&
                    it.id != id
            }
        ) {
            return fail("APPLICATION_ALREADY_ACCEPTED")
        }

        val now = System.currentTimeMillis()
        val accepted = existing.copy(
            status = AdoptionApplicationStatus.ACCEPTED,
            reviewedAt = now,
            reviewedBy = actor,
            updatedAt = now
        )
        store.value = store.value.map { app ->
            when {
                app.id == id -> accepted
                app.adoptionId == existing.adoptionId &&
                    (app.status == AdoptionApplicationStatus.SUBMITTED ||
                        app.status == AdoptionApplicationStatus.UNDER_REVIEW) -> {
                    app.copy(
                        status = AdoptionApplicationStatus.REJECTED,
                        reviewedAt = now,
                        reviewedBy = actor,
                        rejectionReason = app.rejectionReason?.ifBlank { null }
                            ?: "Se seleccionó otra postulación",
                        updatedAt = now
                    )
                }
                else -> app
            }
        }
        InMemoryDataStore.updateAdoptionPost(
            adoption.copy(status = AdoptionStatus.PAUSED, updatedAt = now)
        )
        return Result.success(accepted)
    }

    override suspend fun rejectApplication(id: String, reason: String?): Result<AdoptionApplication> {
        val actor = actorUserId() ?: return fail("NOT_AUTHENTICATED")
        if (id.isBlank()) return fail("APPLICATION_NOT_FOUND")
        val existing = store.value.find { it.id == id } ?: return fail("APPLICATION_NOT_FOUND")
        if (!isManagerOfAdoption(existing.adoptionId, actor)) return fail("APPLICATION_FORBIDDEN")
        if (existing.status == AdoptionApplicationStatus.REJECTED) {
            return Result.success(existing)
        }
        when (existing.status) {
            AdoptionApplicationStatus.ACCEPTED -> return fail("APPLICATION_ALREADY_ACCEPTED")
            AdoptionApplicationStatus.WITHDRAWN -> return fail("APPLICATION_ALREADY_WITHDRAWN")
            AdoptionApplicationStatus.SUBMITTED, AdoptionApplicationStatus.UNDER_REVIEW -> Unit
            else -> return fail("APPLICATION_INVALID_TRANSITION")
        }
        val now = System.currentTimeMillis()
        return replace(
            existing.copy(
                status = AdoptionApplicationStatus.REJECTED,
                reviewedAt = now,
                reviewedBy = actor,
                rejectionReason = reason?.trim()?.ifBlank { null },
                updatedAt = now
            )
        )
    }

    private fun replace(updated: AdoptionApplication): Result<AdoptionApplication> {
        store.value = store.value.map { if (it.id == updated.id) updated else it }
        return Result.success(updated)
    }

    private fun enrich(app: AdoptionApplication): AdoptionApplication {
        val adoption = InMemoryDataStore.getAdoptionPostById(app.adoptionId) ?: return app
        return app.copy(
            adoptionTitle = app.adoptionTitle.ifBlank { adoption.displayTitle },
            petName = app.petName.ifBlank { adoption.name },
            petPhotoUrl = app.petPhotoUrl ?: adoption.photoUrl
        )
    }

    private fun isManagerOfAdoption(adoptionId: String, userId: String): Boolean {
        val adoption = InMemoryDataStore.getAdoptionPostById(adoptionId) ?: return false
        return isOwnerOrManager(adoption.publisherId, adoption.petId, userId)
    }

    private fun isOwnerOrManager(publisherId: String?, petId: String?, actor: String): Boolean {
        if (publisherId == actor) return true
        if (!petId.isNullOrBlank() && canManagePet(petId, actor)) return true
        return false
    }

    private fun <T> fail(code: String): Result<T> =
        Result.failure(M09AdoptionException(code, M09AdoptionErrorMapper.userMessage(code)))
}

/** Optional shared store used by DataProvider mock wiring. */
object SharedAdoptionApplicationStore {
    val flow = MutableStateFlow<List<AdoptionApplication>>(emptyList())
    fun asStateFlow() = flow.asStateFlow()
}

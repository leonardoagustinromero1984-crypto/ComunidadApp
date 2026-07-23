package com.comunidapp.app.data.repository

import com.comunidapp.app.data.mock.InMemoryDataStore
import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeRequestStatus
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.FosterUrgency
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.remote.supabase.m10.M10FosterException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class FosterTemporaryCustodyGrant(
    val id: String,
    val petId: String,
    val fosterUserId: String,
    val placementId: String,
    val roleCode: String = "TEMPORARY_CUSTODIAN",
    val active: Boolean = true
)

class M10FosterMemoryStore {
    val homes = MutableStateFlow<List<FosterHomeProfile>>(emptyList())
    val requests = MutableStateFlow<List<FosterHomeRequest>>(emptyList())
    val placements = MutableStateFlow<List<FosterPlacement>>(emptyList())
    val temporaryCustody = MutableStateFlow<List<FosterTemporaryCustodyGrant>>(emptyList())
    /** petId → owner/principal user id (mock M08). */
    val petPrincipal = MutableStateFlow<Map<String, String>>(emptyMap())
    val expenses = MutableStateFlow<List<com.comunidapp.app.data.model.FosterExpense>>(emptyList())
    val evolution = MutableStateFlow<List<com.comunidapp.app.data.model.FosterEvolutionEntry>>(emptyList())
    val helpRequests = MutableStateFlow<List<com.comunidapp.app.data.model.FosterHelpRequest>>(emptyList())
    val contributions = MutableStateFlow<List<com.comunidapp.app.data.model.FosterHelpContribution>>(emptyList())
    /** When true, completePlacement fails with FOSTER_TEMPORARY_PERMISSION_REVOKE_FAILED. */
    var forceRevokeFailure: Boolean = false

    fun clear() {
        homes.value = emptyList()
        requests.value = emptyList()
        placements.value = emptyList()
        temporaryCustody.value = emptyList()
        petPrincipal.value = emptyMap()
        expenses.value = emptyList()
        evolution.value = emptyList()
        helpRequests.value = emptyList()
        contributions.value = emptyList()
        forceRevokeFailure = false
    }
}

interface FosterHomeRepository {
    fun observeAvailableFosterHomes(): Flow<List<FosterHomePublicListing>>
    fun observeMyFosterHome(ownerUserId: String): Flow<FosterHomeProfile?>
    suspend fun getFosterHomeById(id: String): Result<FosterHomeProfile>
    suspend fun getPublicFosterHomeById(id: String): Result<FosterHomePublicListing>
    suspend fun createFosterHome(input: CreateFosterHomeInput): Result<FosterHomeProfile>
    suspend fun updateFosterHome(input: UpdateFosterHomeInput): Result<FosterHomeProfile>
    suspend fun changeAvailability(
        homeId: String,
        availability: FosterAvailabilityStatus
    ): Result<FosterHomeProfile>
    suspend fun setHomeStatus(homeId: String, status: FosterHomeStatus): Result<FosterHomeProfile>
}

data class CreateFosterHomeInput(
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val hasOtherAnimals: Boolean? = null,
    val observations: String? = null,
    val zoneText: String,
    val publicLocationText: String? = null,
    val privateAddressText: String? = null,
    val activate: Boolean = false
)

data class UpdateFosterHomeInput(
    val homeId: String,
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptedSizes: Set<String>,
    val acceptsSpecialNeeds: Boolean = false,
    val acceptsEmergencies: Boolean = false,
    val hasOtherAnimals: Boolean? = null,
    val observations: String? = null,
    val zoneText: String,
    val publicLocationText: String? = null,
    val privateAddressText: String? = null
)

interface FosterRequestRepository {
    fun observeSentRequests(userId: String): Flow<List<FosterHomeRequest>>
    fun observeReceivedRequests(ownerUserId: String): Flow<List<FosterHomeRequest>>
    suspend fun getRequestById(id: String): Result<FosterHomeRequest>
    suspend fun submitRequest(input: SubmitFosterRequestInput): Result<FosterHomeRequest>
    suspend fun cancelRequest(requestId: String): Result<FosterHomeRequest>
    suspend fun markUnderReview(requestId: String): Result<FosterHomeRequest>
    suspend fun acceptRequest(requestId: String): Result<FosterHomeRequest>
    suspend fun rejectRequest(requestId: String, reason: String?): Result<FosterHomeRequest>
}

data class SubmitFosterRequestInput(
    val fosterHomeId: String,
    val petId: String,
    val message: String,
    val urgency: FosterUrgency = FosterUrgency.NORMAL,
    val requestedStartAt: Long? = null,
    val estimatedEndAt: Long? = null,
    val specialNeeds: String? = null,
    val requesterOrganizationId: String? = null
)

interface FosterPlacementRepository {
    fun observeActivePlacementsForHome(homeId: String): Flow<List<FosterPlacement>>
    fun observeActivePlacementsForUser(userId: String): Flow<List<FosterPlacement>>
    fun observePlacementHistory(userId: String): Flow<List<FosterPlacement>>
    suspend fun getPlacementById(id: String): Result<FosterPlacement>
    suspend fun startPlacement(
        requestId: String,
        initialNotes: String? = null
    ): Result<FosterPlacement>
    suspend fun completePlacement(
        placementId: String,
        reason: com.comunidapp.app.data.model.FosterPlacementEndReason,
        notes: String? = null
    ): Result<FosterPlacement>
    suspend fun cancelReservedPlacement(
        placementId: String,
        reason: String? = null
    ): Result<FosterPlacement>
}

private fun failM10(code: String): Nothing = throw M10FosterException(code, M10FosterErrorMapper.userMessage(code))

class MockFosterHomeRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore
) : FosterHomeRepository {

    override fun observeAvailableFosterHomes(): Flow<List<FosterHomePublicListing>> =
        store.homes.map { list ->
            list.filter { it.status == FosterHomeStatus.ACTIVE }
                .map { it.toPublicListing() }
        }

    override fun observeMyFosterHome(ownerUserId: String): Flow<FosterHomeProfile?> =
        store.homes.map { list ->
            list.firstOrNull {
                it.ownerUserId == ownerUserId &&
                    it.status != FosterHomeStatus.CLOSED
            }
        }

    override suspend fun getFosterHomeById(id: String): Result<FosterHomeProfile> = runCatching {
        if (id.isBlank()) failM10("FOSTER_HOME_NOT_FOUND")
        store.homes.value.find { it.id == id } ?: failM10("FOSTER_HOME_NOT_FOUND")
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun getPublicFosterHomeById(id: String): Result<FosterHomePublicListing> =
        getFosterHomeById(id).fold(
            onSuccess = { home ->
                if (home.status != FosterHomeStatus.ACTIVE) {
                    M10FosterErrorMapper.fail("FOSTER_HOME_NOT_ACTIVE")
                } else {
                    Result.success(home.toPublicListing())
                }
            },
            onFailure = { M10FosterErrorMapper.failure(it) }
        )

    override suspend fun createFosterHome(input: CreateFosterHomeInput): Result<FosterHomeProfile> =
        runCatching {
            val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
            if (input.totalCapacity <= 0) failM10("FOSTER_HOME_CAPACITY_INVALID")
            if (input.displayName.isBlank() || input.zoneText.isBlank()) failM10("FOSTER_HOME_NOT_FOUND")
            val hasActive = store.homes.value.any {
                it.ownerUserId == actor && it.status != FosterHomeStatus.CLOSED
            }
            if (hasActive) failM10("FOSTER_HOME_ALREADY_EXISTS")
            val now = System.currentTimeMillis()
            val status = if (input.activate) FosterHomeStatus.ACTIVE else FosterHomeStatus.DRAFT
            val availability = when {
                status != FosterHomeStatus.ACTIVE -> FosterAvailabilityStatus.UNAVAILABLE
                else -> FosterAvailabilityStatus.AVAILABLE
            }
            val row = FosterHomeProfile(
                id = UUID.randomUUID().toString(),
                ownerUserId = actor,
                displayName = input.displayName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                status = status,
                availabilityStatus = availability,
                totalCapacity = input.totalCapacity,
                acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                acceptedSizes = input.acceptedSizes.map { it.uppercase() }.toSet(),
                acceptsSpecialNeeds = input.acceptsSpecialNeeds,
                acceptsEmergencies = input.acceptsEmergencies,
                hasOtherAnimals = input.hasOtherAnimals,
                observations = input.observations?.trim()?.takeIf { it.isNotEmpty() },
                zoneText = input.zoneText.trim(),
                publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
                privateAddressText = input.privateAddressText?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
            store.homes.value = listOf(row) + store.homes.value
            row
        }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun updateFosterHome(input: UpdateFosterHomeInput): Result<FosterHomeProfile> =
        runCatching {
            val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
            if (input.homeId.isBlank()) failM10("FOSTER_HOME_NOT_FOUND")
            if (input.totalCapacity <= 0) failM10("FOSTER_HOME_CAPACITY_INVALID")
            val existing = store.homes.value.find { it.id == input.homeId }
                ?: failM10("FOSTER_HOME_NOT_FOUND")
            if (existing.ownerUserId != actor) failM10("FORBIDDEN")
            if (existing.status == FosterHomeStatus.CLOSED) failM10("FOSTER_HOME_NOT_ACTIVE")
            val used = existing.currentOccupancy + existing.reservedCount
            if (input.totalCapacity < used) failM10("FOSTER_PLACEMENT_CAPACITY_EXCEEDED")
            val updated = existing.copy(
                displayName = input.displayName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                totalCapacity = input.totalCapacity,
                acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                acceptedSizes = input.acceptedSizes.map { it.uppercase() }.toSet(),
                acceptsSpecialNeeds = input.acceptsSpecialNeeds,
                acceptsEmergencies = input.acceptsEmergencies,
                hasOtherAnimals = input.hasOtherAnimals,
                observations = input.observations?.trim()?.takeIf { it.isNotEmpty() },
                zoneText = input.zoneText.trim(),
                publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
                privateAddressText = input.privateAddressText?.trim()?.takeIf { it.isNotEmpty() },
                availabilityStatus = recomputeAvailability(existing.status, input.totalCapacity, existing.currentOccupancy, existing.reservedCount),
                updatedAt = System.currentTimeMillis()
            )
            store.homes.value = store.homes.value.map { if (it.id == updated.id) updated else it }
            updated
        }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun changeAvailability(
        homeId: String,
        availability: FosterAvailabilityStatus
    ): Result<FosterHomeProfile> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val existing = store.homes.value.find { it.id == homeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (existing.ownerUserId != actor) failM10("FORBIDDEN")
        val updated = existing.copy(
            availabilityStatus = availability,
            updatedAt = System.currentTimeMillis()
        )
        store.homes.value = store.homes.value.map { if (it.id == homeId) updated else it }
        updated
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun setHomeStatus(
        homeId: String,
        status: FosterHomeStatus
    ): Result<FosterHomeProfile> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val existing = store.homes.value.find { it.id == homeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (existing.ownerUserId != actor) failM10("FORBIDDEN")
        val availability = when (status) {
            FosterHomeStatus.ACTIVE -> recomputeAvailability(
                status, existing.totalCapacity, existing.currentOccupancy, existing.reservedCount
            )
            else -> FosterAvailabilityStatus.UNAVAILABLE
        }
        val updated = existing.copy(
            status = status,
            availabilityStatus = availability,
            updatedAt = System.currentTimeMillis()
        )
        store.homes.value = store.homes.value.map { if (it.id == homeId) updated else it }
        updated
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })
}

class MockFosterRequestRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore,
    private val resolvePet: (String) -> Pet?
) : FosterRequestRepository {

    override fun observeSentRequests(userId: String): Flow<List<FosterHomeRequest>> =
        store.requests.map { list -> list.filter { it.requesterUserId == userId } }

    override fun observeReceivedRequests(ownerUserId: String): Flow<List<FosterHomeRequest>> =
        store.requests.map { list ->
            val homeIds = store.homes.value.filter { it.ownerUserId == ownerUserId }.map { it.id }.toSet()
            list.filter { it.fosterHomeId in homeIds }
        }

    override suspend fun getRequestById(id: String): Result<FosterHomeRequest> = runCatching {
        if (id.isBlank()) failM10("FOSTER_REQUEST_NOT_FOUND")
        store.requests.value.find { it.id == id } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun submitRequest(input: SubmitFosterRequestInput): Result<FosterHomeRequest> =
        runCatching {
            val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
            if (input.fosterHomeId.isBlank() || input.petId.isBlank()) failM10("FOSTER_REQUEST_NOT_FOUND")
            if (input.message.isBlank()) failM10("FOSTER_REQUEST_INVALID_TRANSITION")
            val home = store.homes.value.find { it.id == input.fosterHomeId }
                ?: failM10("FOSTER_HOME_NOT_FOUND")
            if (home.status != FosterHomeStatus.ACTIVE) failM10("FOSTER_HOME_NOT_ACTIVE")
            if (home.availabilityStatus == FosterAvailabilityStatus.UNAVAILABLE ||
                home.availabilityStatus == FosterAvailabilityStatus.FULL
            ) {
                failM10("FOSTER_HOME_UNAVAILABLE")
            }
            if (home.freeSlots <= 0) failM10("FOSTER_HOME_FULL")
            val pet = resolvePet(input.petId) ?: failM10("PET_NOT_FOUND")
            if (pet.status.equals("DECEASED", true) || pet.status.equals("ARCHIVED", true)) {
                failM10("PET_NOT_ELIGIBLE_FOR_FOSTER")
            }
            val principal = store.petPrincipal.value[pet.id]
            if (principal != null && principal != actor && input.requesterOrganizationId.isNullOrBlank()) {
                // Without org auth in mock, require principal match
                failM10("FORBIDDEN")
            }
            if (home.acceptedSpecies.isNotEmpty() &&
                pet.species.name.uppercase() !in home.acceptedSpecies
            ) {
                failM10("FOSTER_HOME_INCOMPATIBLE")
            }
            if (home.acceptedSizes.isNotEmpty() &&
                pet.size.name.uppercase() !in home.acceptedSizes
            ) {
                failM10("FOSTER_HOME_INCOMPATIBLE")
            }
            if (input.urgency == FosterUrgency.EMERGENCY && !home.acceptsEmergencies) {
                failM10("FOSTER_HOME_INCOMPATIBLE")
            }
            if (!input.specialNeeds.isNullOrBlank() && !home.acceptsSpecialNeeds) {
                failM10("FOSTER_HOME_INCOMPATIBLE")
            }
            val activePlacement = store.placements.value.any {
                it.petId == pet.id &&
                    (it.status == FosterPlacementStatus.ACTIVE || it.status == FosterPlacementStatus.RESERVED)
            }
            if (activePlacement) failM10("PET_ALREADY_IN_FOSTER")
            val dup = store.requests.value.any {
                it.fosterHomeId == home.id && it.petId == pet.id && it.status.isActive
            }
            if (dup) failM10("FOSTER_REQUEST_ALREADY_EXISTS")
            val now = System.currentTimeMillis()
            val row = FosterHomeRequest(
                id = UUID.randomUUID().toString(),
                fosterHomeId = home.id,
                petId = pet.id,
                petName = pet.name,
                requesterUserId = actor,
                requesterOrganizationId = input.requesterOrganizationId,
                requesterDisplayName = null,
                message = input.message.trim(),
                urgency = input.urgency,
                requestedStartAt = input.requestedStartAt,
                estimatedEndAt = input.estimatedEndAt,
                specialNeeds = input.specialNeeds?.trim()?.takeIf { it.isNotEmpty() },
                status = FosterHomeRequestStatus.SUBMITTED,
                createdAt = now
            )
            store.requests.value = listOf(row) + store.requests.value
            row
        }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun cancelRequest(requestId: String): Result<FosterHomeRequest> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val req = store.requests.value.find { it.id == requestId } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
        if (req.requesterUserId != actor) failM10("FOSTER_REQUEST_FORBIDDEN")
        if (req.status == FosterHomeRequestStatus.CANCELLED) return@runCatching req
        if (req.status != FosterHomeRequestStatus.SUBMITTED &&
            req.status != FosterHomeRequestStatus.UNDER_REVIEW
        ) {
            failM10("FOSTER_REQUEST_NOT_ACTIVE")
        }
        transition(req, FosterHomeRequestStatus.CANCELLED, actor, null)
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun markUnderReview(requestId: String): Result<FosterHomeRequest> =
        ownerTransition(requestId, FosterHomeRequestStatus.UNDER_REVIEW)

    override suspend fun acceptRequest(requestId: String): Result<FosterHomeRequest> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val req = store.requests.value.find { it.id == requestId } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
        val home = store.homes.value.find { it.id == req.fosterHomeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (home.ownerUserId != actor) failM10("FOSTER_REQUEST_FORBIDDEN")
        if (req.status == FosterHomeRequestStatus.ACCEPTED) return@runCatching req
        if (req.status != FosterHomeRequestStatus.SUBMITTED &&
            req.status != FosterHomeRequestStatus.UNDER_REVIEW
        ) {
            failM10("FOSTER_REQUEST_INVALID_TRANSITION")
        }
        if (home.freeSlots <= 0) failM10("FOSTER_HOME_FULL")
        val accepted = transition(req, FosterHomeRequestStatus.ACCEPTED, actor, null)
        // Reserve capacity (not yet occupied)
        val reservedHome = home.copy(
            reservedCount = home.reservedCount + 1,
            availabilityStatus = recomputeAvailability(
                home.status, home.totalCapacity, home.currentOccupancy, home.reservedCount + 1
            ),
            updatedAt = System.currentTimeMillis()
        )
        store.homes.value = store.homes.value.map { if (it.id == home.id) reservedHome else it }
        // Placement RESERVED
        val placement = FosterPlacement(
            id = UUID.randomUUID().toString(),
            fosterRequestId = accepted.id,
            fosterHomeId = home.id,
            petId = accepted.petId,
            petName = accepted.petName,
            requesterUserId = accepted.requesterUserId,
            requesterOrganizationId = accepted.requesterOrganizationId,
            fosterUserId = home.ownerUserId,
            status = FosterPlacementStatus.RESERVED,
            startedAt = System.currentTimeMillis(),
            estimatedEndAt = accepted.estimatedEndAt
        )
        store.placements.value = listOf(placement) + store.placements.value
        accepted
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun rejectRequest(
        requestId: String,
        reason: String?
    ): Result<FosterHomeRequest> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val req = store.requests.value.find { it.id == requestId } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
        val home = store.homes.value.find { it.id == req.fosterHomeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (home.ownerUserId != actor) failM10("FOSTER_REQUEST_FORBIDDEN")
        if (req.status == FosterHomeRequestStatus.REJECTED) return@runCatching req
        if (req.status != FosterHomeRequestStatus.SUBMITTED &&
            req.status != FosterHomeRequestStatus.UNDER_REVIEW
        ) {
            failM10("FOSTER_REQUEST_INVALID_TRANSITION")
        }
        transition(req, FosterHomeRequestStatus.REJECTED, actor, reason)
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    private suspend fun ownerTransition(
        requestId: String,
        target: FosterHomeRequestStatus
    ): Result<FosterHomeRequest> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        val req = store.requests.value.find { it.id == requestId } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
        val home = store.homes.value.find { it.id == req.fosterHomeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (home.ownerUserId != actor) failM10("FOSTER_REQUEST_FORBIDDEN")
        if (req.status == target) return@runCatching req
        if (req.status != FosterHomeRequestStatus.SUBMITTED) failM10("FOSTER_REQUEST_INVALID_TRANSITION")
        transition(req, target, actor, null)
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    private fun transition(
        req: FosterHomeRequest,
        status: FosterHomeRequestStatus,
        actor: String,
        reason: String?
    ): FosterHomeRequest {
        val updated = req.copy(
            status = status,
            reviewedAt = System.currentTimeMillis(),
            reviewedBy = actor,
            rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        )
        store.requests.value = store.requests.value.map { if (it.id == req.id) updated else it }
        return updated
    }
}

class MockFosterPlacementRepository(
    private val actorUserId: () -> String?,
    private val store: M10FosterMemoryStore
) : FosterPlacementRepository {

    override fun observeActivePlacementsForHome(homeId: String): Flow<List<FosterPlacement>> =
        store.placements.map { list ->
            list.filter {
                it.fosterHomeId == homeId &&
                    (it.status == FosterPlacementStatus.ACTIVE || it.status == FosterPlacementStatus.RESERVED)
            }
        }

    override fun observeActivePlacementsForUser(userId: String): Flow<List<FosterPlacement>> =
        store.placements.map { list ->
            list.filter {
                (it.fosterUserId == userId || it.requesterUserId == userId) &&
                    (it.status == FosterPlacementStatus.ACTIVE || it.status == FosterPlacementStatus.RESERVED)
            }
        }

    override suspend fun getPlacementById(id: String): Result<FosterPlacement> = runCatching {
        if (id.isBlank()) failM10("FOSTER_PLACEMENT_NOT_FOUND")
        store.placements.value.find { it.id == id } ?: failM10("FOSTER_PLACEMENT_NOT_FOUND")
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun startPlacement(
        requestId: String,
        initialNotes: String?
    ): Result<FosterPlacement> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        if (requestId.isBlank()) failM10("FOSTER_REQUEST_NOT_FOUND")
        val req = store.requests.value.find { it.id == requestId } ?: failM10("FOSTER_REQUEST_NOT_FOUND")
        if (req.status != FosterHomeRequestStatus.ACCEPTED) failM10("FOSTER_REQUEST_INVALID_TRANSITION")
        val home = store.homes.value.find { it.id == req.fosterHomeId } ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (home.ownerUserId != actor) failM10("FORBIDDEN")
        store.placements.value.find {
            it.fosterRequestId == requestId && it.status == FosterPlacementStatus.ACTIVE
        }?.let { return@runCatching it }
        val otherActive = store.placements.value.any {
            it.petId == req.petId &&
                it.status == FosterPlacementStatus.ACTIVE &&
                it.fosterRequestId != requestId
        }
        if (otherActive) failM10("FOSTER_PLACEMENT_ALREADY_ACTIVE")
        // Capacity: occupancy + other reserved (excluding this request's reserve)
        val reservedOther = home.reservedCount.coerceAtLeast(0)
        // Convert reserve → occupancy
        val newOccupancy = home.currentOccupancy + 1
        val newReserved = (home.reservedCount - 1).coerceAtLeast(0)
        if (newOccupancy + newReserved > home.totalCapacity) {
            failM10("FOSTER_PLACEMENT_CAPACITY_EXCEEDED")
        }
        val now = System.currentTimeMillis()
        val existingReserved = store.placements.value.find {
            it.fosterRequestId == requestId && it.status == FosterPlacementStatus.RESERVED
        }
        val custodyId = UUID.randomUUID().toString()
        val placement = (existingReserved ?: FosterPlacement(
            id = UUID.randomUUID().toString(),
            fosterRequestId = req.id,
            fosterHomeId = home.id,
            petId = req.petId,
            petName = req.petName,
            requesterUserId = req.requesterUserId,
            requesterOrganizationId = req.requesterOrganizationId,
            fosterUserId = home.ownerUserId,
            status = FosterPlacementStatus.RESERVED,
            startedAt = now,
            estimatedEndAt = req.estimatedEndAt
        )).copy(
            status = FosterPlacementStatus.ACTIVE,
            startedAt = now,
            initialNotes = initialNotes?.trim()?.takeIf { it.isNotEmpty() },
            temporaryResponsibilityId = custodyId
        )
        store.placements.value = if (existingReserved != null) {
            store.placements.value.map { if (it.id == placement.id) placement else it }
        } else {
            listOf(placement) + store.placements.value
        }
        store.temporaryCustody.value = listOf(
            FosterTemporaryCustodyGrant(
                id = custodyId,
                petId = req.petId,
                fosterUserId = home.ownerUserId,
                placementId = placement.id
            )
        ) + store.temporaryCustody.value
        // Principal unchanged
        val updatedHome = home.copy(
            currentOccupancy = newOccupancy,
            reservedCount = newReserved,
            availabilityStatus = recomputeAvailability(
                home.status, home.totalCapacity, newOccupancy, newReserved
            ),
            updatedAt = now
        )
        store.homes.value = store.homes.value.map { if (it.id == home.id) updatedHome else it }
        placement
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override fun observePlacementHistory(userId: String): Flow<List<FosterPlacement>> =
        store.placements.map { list ->
            list.filter { it.fosterUserId == userId || it.requesterUserId == userId }
                .sortedByDescending { it.startedAt }
        }

    override suspend fun completePlacement(
        placementId: String,
        reason: com.comunidapp.app.data.model.FosterPlacementEndReason,
        notes: String?
    ): Result<FosterPlacement> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        if (placementId.isBlank()) failM10("FOSTER_PLACEMENT_NOT_FOUND")
        val placement = store.placements.value.find { it.id == placementId }
            ?: failM10("FOSTER_PLACEMENT_NOT_FOUND")
        if (placement.status == FosterPlacementStatus.COMPLETED) return@runCatching placement
        if (placement.status != FosterPlacementStatus.ACTIVE) {
            failM10("FOSTER_PLACEMENT_NOT_ACTIVE")
        }
        val home = store.homes.value.find { it.id == placement.fosterHomeId }
            ?: failM10("FOSTER_HOME_NOT_FOUND")
        val principal = store.petPrincipal.value[placement.petId]
        val allowed = actor == placement.fosterUserId ||
            actor == placement.requesterUserId ||
            actor == principal ||
            actor == home.ownerUserId
        if (!allowed) failM10("FOSTER_PLACEMENT_COMPLETION_FORBIDDEN")
        if (store.forceRevokeFailure) failM10("FOSTER_TEMPORARY_PERMISSION_REVOKE_FAILED")

        val now = System.currentTimeMillis()
        // Revoke TEMPORARY_CUSTODIAN
        store.temporaryCustody.value = store.temporaryCustody.value.map { g ->
            if (g.placementId == placement.id && g.active) g.copy(active = false) else g
        }
        val completed = placement.copy(
            status = FosterPlacementStatus.COMPLETED,
            endedAt = now,
            endReason = reason.name,
            endNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
            endedBy = actor
        )
        store.placements.value = store.placements.value.map {
            if (it.id == placement.id) completed else it
        }
        val newOccupancy = (home.currentOccupancy - 1).coerceAtLeast(0)
        val updatedHome = home.copy(
            currentOccupancy = newOccupancy,
            availabilityStatus = recomputeAvailability(
                home.status, home.totalCapacity, newOccupancy, home.reservedCount
            ),
            updatedAt = now
        )
        store.homes.value = store.homes.value.map { if (it.id == home.id) updatedHome else it }
        // Close open help requests
        store.helpRequests.value = store.helpRequests.value.map { hr ->
            if (hr.placementId == placement.id && hr.status.isEditable) {
                hr.copy(
                    status = com.comunidapp.app.data.model.FosterHelpStatus.CANCELLED,
                    closedAt = now
                )
            } else hr
        }
        // PRINCIPAL unchanged — petPrincipal map untouched
        completed
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })

    override suspend fun cancelReservedPlacement(
        placementId: String,
        reason: String?
    ): Result<FosterPlacement> = runCatching {
        val actor = actorUserId() ?: failM10("NOT_AUTHENTICATED")
        if (placementId.isBlank()) failM10("FOSTER_PLACEMENT_NOT_FOUND")
        val placement = store.placements.value.find { it.id == placementId }
            ?: failM10("FOSTER_PLACEMENT_NOT_FOUND")
        if (placement.status == FosterPlacementStatus.CANCELLED) return@runCatching placement
        if (placement.status != FosterPlacementStatus.RESERVED) {
            failM10("FOSTER_PLACEMENT_INVALID_TRANSITION")
        }
        val home = store.homes.value.find { it.id == placement.fosterHomeId }
            ?: failM10("FOSTER_HOME_NOT_FOUND")
        if (actor != placement.fosterUserId && actor != home.ownerUserId &&
            actor != placement.requesterUserId
        ) {
            failM10("FOSTER_PLACEMENT_COMPLETION_FORBIDDEN")
        }
        val now = System.currentTimeMillis()
        val cancelled = placement.copy(
            status = FosterPlacementStatus.CANCELLED,
            endedAt = now,
            endReason = com.comunidapp.app.data.model.FosterPlacementEndReason.CANCELLED_BEFORE_START.name,
            endNotes = reason?.trim()?.takeIf { it.isNotEmpty() },
            endedBy = actor
        )
        store.placements.value = store.placements.value.map {
            if (it.id == placement.id) cancelled else it
        }
        val newReserved = (home.reservedCount - 1).coerceAtLeast(0)
        store.homes.value = store.homes.value.map {
            if (it.id == home.id) {
                it.copy(
                    reservedCount = newReserved,
                    availabilityStatus = recomputeAvailability(
                        it.status, it.totalCapacity, it.currentOccupancy, newReserved
                    ),
                    updatedAt = now
                )
            } else it
        }
        cancelled
    }.fold({ Result.success(it) }, { M10FosterErrorMapper.failure(it) })
}

internal fun recomputeAvailability(
    status: FosterHomeStatus,
    capacity: Int,
    occupancy: Int,
    reserved: Int
): FosterAvailabilityStatus {
    if (status != FosterHomeStatus.ACTIVE) return FosterAvailabilityStatus.UNAVAILABLE
    val used = occupancy.coerceAtLeast(0) + reserved.coerceAtLeast(0)
    return when {
        used >= capacity.coerceAtLeast(0) -> FosterAvailabilityStatus.FULL
        used > 0 -> FosterAvailabilityStatus.LIMITED
        else -> FosterAvailabilityStatus.AVAILABLE
    }
}

/** Resolve pet from InMemory mock catalog for DataProvider wiring. */
fun m10ResolvePetFromStore(petId: String): Pet? =
    InMemoryDataStore.pets.value.find { it.id == petId }
        ?: InMemoryDataStore.getPetById(petId)

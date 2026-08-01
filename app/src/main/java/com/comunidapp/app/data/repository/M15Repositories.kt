package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM15FosterHomeInput
import com.comunidapp.app.data.model.M15AuditEvents
import com.comunidapp.app.data.model.M15FosterAvailabilityStatus
import com.comunidapp.app.data.model.M15FosterHome
import com.comunidapp.app.data.model.M15FosterHomePublicListing
import com.comunidapp.app.data.model.M15FosterHomeStatus
import com.comunidapp.app.data.model.M15FosterPlacement
import com.comunidapp.app.data.model.M15FosterPlacementStatus
import com.comunidapp.app.data.model.M15FosterRequest
import com.comunidapp.app.data.model.M15FosterRequestStatus
import com.comunidapp.app.data.model.M15FosterUrgency
import com.comunidapp.app.data.model.M15M06Hooks
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.SubmitM15FosterRequestInput
import com.comunidapp.app.data.model.UpdateM15FosterHomeInput
import com.comunidapp.app.data.remote.supabase.m15.M15ErrorMapper
import com.comunidapp.app.data.remote.supabase.m15.M15Exception
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LeoVer M15 — store + contratos + fakes (Bloque 1, sin red).
 */

class M15MemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    private val _homes = MutableStateFlow<List<M15FosterHome>>(emptyList())
    private val _requests = MutableStateFlow<List<M15FosterRequest>>(emptyList())
    private val _placements = MutableStateFlow<List<M15FosterPlacement>>(emptyList())
    private val _audit = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _m06 = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val petPrincipal = MutableStateFlow<Map<String, String>>(emptyMap())
    var m06InfrastructureAvailable: Boolean = false

    val homes: StateFlow<List<M15FosterHome>> = _homes.asStateFlow()
    val requests: StateFlow<List<M15FosterRequest>> = _requests.asStateFlow()
    val placements: StateFlow<List<M15FosterPlacement>> = _placements.asStateFlow()
    val auditLog: StateFlow<List<Pair<String, String>>> = _audit.asStateFlow()
    val m06PreparedHooks: StateFlow<List<Pair<String, String>>> = _m06.asStateFlow()

    fun nextId(prefix: String): String = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }

    fun upsertHome(home: M15FosterHome) {
        _homes.update { list ->
            val without = list.filterNot { it.id == home.id }
            (without + home).sortedByDescending { it.updatedAt }
        }
    }

    fun upsertRequest(request: M15FosterRequest) {
        _requests.update { list ->
            val without = list.filterNot { it.id == request.id }
            (without + request).sortedByDescending { it.createdAt }
        }
    }

    fun upsertPlacement(placement: M15FosterPlacement) {
        _placements.update { list ->
            val without = list.filterNot { it.id == placement.id }
            (without + placement).sortedByDescending { it.startedAt }
        }
    }

    fun audit(event: String, entityId: String) {
        _audit.update { listOf(event to entityId) + it }
    }

    fun recordM06(eventKey: String, entityId: String) {
        val key = eventKey to entityId
        _m06.update { list ->
            if (list.any { it == key }) list else listOf(key) + list
        }
        if (!m06InfrastructureAvailable) {
            val infra = M15M06Hooks.INFRASTRUCTURE to "infra"
            _m06.update { list ->
                if (list.any { it.first == M15M06Hooks.INFRASTRUCTURE }) list else listOf(infra) + list
            }
        }
    }
}

interface M15AuthorityPolicy {
    fun canManageHome(actorUserId: String, ownerUserId: String): Boolean
    fun canReviewRequest(actorUserId: String, homeOwnerUserId: String): Boolean
    fun canSubmitRequest(actorUserId: String, pet: Pet, petPrincipalId: String?): Boolean
}

class MockM15AuthorityPolicy : M15AuthorityPolicy {
    override fun canManageHome(actorUserId: String, ownerUserId: String): Boolean =
        actorUserId == ownerUserId

    override fun canReviewRequest(actorUserId: String, homeOwnerUserId: String): Boolean =
        actorUserId == homeOwnerUserId

    override fun canSubmitRequest(actorUserId: String, pet: Pet, petPrincipalId: String?): Boolean {
        if (pet.ownerId == actorUserId) return true
        if (petPrincipalId != null && petPrincipalId == actorUserId) return true
        return false
    }
}

interface M15FosterHomeRepository {
    fun observeAvailableHomes(): Flow<List<M15FosterHomePublicListing>>
    fun observeMyHome(ownerUserId: String): Flow<M15FosterHome?>
    suspend fun getHomeById(id: String): Result<M15FosterHome>
    suspend fun getPublicHomeById(id: String): Result<M15FosterHomePublicListing>
    suspend fun createHome(input: CreateM15FosterHomeInput): Result<M15FosterHome>
    suspend fun updateHome(input: UpdateM15FosterHomeInput): Result<M15FosterHome>
    suspend fun activateHome(homeId: String): Result<M15FosterHome>
}

interface M15FosterRequestRepository {
    fun observeSentRequests(userId: String): Flow<List<M15FosterRequest>>
    fun observeReceivedRequests(ownerUserId: String): Flow<List<M15FosterRequest>>
    suspend fun getRequestById(id: String): Result<M15FosterRequest>
    suspend fun submitRequest(input: SubmitM15FosterRequestInput): Result<M15FosterRequest>
    suspend fun cancelRequest(requestId: String): Result<M15FosterRequest>
    suspend fun markUnderReview(requestId: String): Result<M15FosterRequest>
    suspend fun acceptRequest(requestId: String): Result<M15FosterRequest>
    suspend fun rejectRequest(requestId: String, reason: String?): Result<M15FosterRequest>
}

interface M15FosterPlacementRepository {
    fun observeActivePlacementsForHome(homeId: String): Flow<List<M15FosterPlacement>>
    fun observeActivePlacementsForUser(userId: String): Flow<List<M15FosterPlacement>>
    suspend fun getPlacementById(id: String): Result<M15FosterPlacement>
    suspend fun startPlacement(requestId: String, initialNotes: String? = null): Result<M15FosterPlacement>
}

private fun failM15(code: String): Nothing =
    throw M15Exception(code, M15ErrorMapper.userMessage(code))

class MockM15FosterHomeRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore,
    private val authority: M15AuthorityPolicy = MockM15AuthorityPolicy()
) : M15FosterHomeRepository {

    override fun observeAvailableHomes(): Flow<List<M15FosterHomePublicListing>> =
        store.homes.map { list ->
            list.filter { it.status == M15FosterHomeStatus.ACTIVE }
                .map { it.toPublicListing() }
        }

    override fun observeMyHome(ownerUserId: String): Flow<M15FosterHome?> =
        store.homes.map { list ->
            list.firstOrNull {
                it.ownerUserId == ownerUserId && it.status != M15FosterHomeStatus.CLOSED
            }
        }

    override suspend fun getHomeById(id: String): Result<M15FosterHome> = runCatching {
        if (id.isBlank()) failM15("M15_FOSTER_HOME_NOT_FOUND")
        store.homes.value.find { it.id == id } ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
    }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })

    override suspend fun getPublicHomeById(id: String): Result<M15FosterHomePublicListing> =
        getHomeById(id).fold(
            onSuccess = { home ->
                if (home.status != M15FosterHomeStatus.ACTIVE) {
                    M15ErrorMapper.fail("M15_FOSTER_HOME_NOT_ACTIVE")
                } else {
                    Result.success(home.toPublicListing())
                }
            },
            onFailure = { M15ErrorMapper.failure(it) }
        )

    override suspend fun createHome(input: CreateM15FosterHomeInput): Result<M15FosterHome> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
                M15Validators.validateCreateHome(input)?.let { failM15(it) }
                val hasActive = store.homes.value.any {
                    it.ownerUserId == actor && it.status != M15FosterHomeStatus.CLOSED
                }
                if (hasActive) failM15("M15_FOSTER_HOME_ALREADY_EXISTS")
                val now = System.currentTimeMillis()
                val status = if (input.activate) {
                    M15FosterHomeStatus.ACTIVE
                } else {
                    M15FosterHomeStatus.DRAFT
                }
                val availability = M15Validators.recomputeAvailability(
                    status, input.totalCapacity, 0, 0
                )
                val row = M15FosterHome(
                    id = store.nextId("m15_home"),
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
                    zoneText = input.zoneText.trim(),
                    publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
                    privateAddressText = input.privateAddressText?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = now,
                    updatedAt = now
                )
                store.upsertHome(row)
                store.audit(M15AuditEvents.HOME_CREATED, row.id)
                store.recordM06(M15M06Hooks.HOME_CREATED, row.id)
                if (status == M15FosterHomeStatus.ACTIVE) {
                    store.audit(M15AuditEvents.HOME_ACTIVATED, row.id)
                    store.recordM06(M15M06Hooks.HOME_ACTIVATED, row.id)
                }
                row
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }

    override suspend fun updateHome(input: UpdateM15FosterHomeInput): Result<M15FosterHome> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
                M15Validators.validateUpdateHome(input)?.let { failM15(it) }
                val existing = store.homes.value.find { it.id == input.homeId }
                    ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
                if (!authority.canManageHome(actor, existing.ownerUserId)) failM15("FORBIDDEN")
                if (existing.status == M15FosterHomeStatus.CLOSED) failM15("M15_FOSTER_HOME_NOT_ACTIVE")
                val used = existing.currentOccupancy + existing.reservedCount
                if (input.totalCapacity < used) failM15("M15_FOSTER_PLACEMENT_CAPACITY_EXCEEDED")
                val updated = existing.copy(
                    displayName = input.displayName.trim(),
                    description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                    totalCapacity = input.totalCapacity,
                    acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                    acceptedSizes = input.acceptedSizes.map { it.uppercase() }.toSet(),
                    acceptsSpecialNeeds = input.acceptsSpecialNeeds,
                    acceptsEmergencies = input.acceptsEmergencies,
                    zoneText = input.zoneText.trim(),
                    publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
                    privateAddressText = input.privateAddressText?.trim()?.takeIf { it.isNotEmpty() },
                    availabilityStatus = M15Validators.recomputeAvailability(
                        existing.status,
                        input.totalCapacity,
                        existing.currentOccupancy,
                        existing.reservedCount
                    ),
                    updatedAt = System.currentTimeMillis()
                )
                store.upsertHome(updated)
                store.audit(M15AuditEvents.HOME_STATUS_CHANGED, updated.id)
                updated
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }

    override suspend fun activateHome(homeId: String): Result<M15FosterHome> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
                val existing = store.homes.value.find { it.id == homeId }
                    ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
                if (!authority.canManageHome(actor, existing.ownerUserId)) failM15("FORBIDDEN")
                val updated = existing.copy(
                    status = M15FosterHomeStatus.ACTIVE,
                    availabilityStatus = M15Validators.recomputeAvailability(
                        M15FosterHomeStatus.ACTIVE,
                        existing.totalCapacity,
                        existing.currentOccupancy,
                        existing.reservedCount
                    ),
                    updatedAt = System.currentTimeMillis()
                )
                store.upsertHome(updated)
                store.audit(M15AuditEvents.HOME_ACTIVATED, updated.id)
                store.recordM06(M15M06Hooks.HOME_ACTIVATED, updated.id)
                updated
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }
}

class MockM15FosterRequestRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore,
    private val resolvePet: (String) -> Pet?,
    private val authority: M15AuthorityPolicy = MockM15AuthorityPolicy()
) : M15FosterRequestRepository {

    override fun observeSentRequests(userId: String): Flow<List<M15FosterRequest>> =
        store.requests.map { list -> list.filter { it.requesterUserId == userId } }

    override fun observeReceivedRequests(ownerUserId: String): Flow<List<M15FosterRequest>> =
        store.requests.map { list ->
            val homeIds = store.homes.value
                .filter { it.ownerUserId == ownerUserId }
                .map { it.id }
                .toSet()
            list.filter { it.fosterHomeId in homeIds }
        }

    override suspend fun getRequestById(id: String): Result<M15FosterRequest> = runCatching {
        if (id.isBlank()) failM15("M15_FOSTER_REQUEST_NOT_FOUND")
        store.requests.value.find { it.id == id } ?: failM15("M15_FOSTER_REQUEST_NOT_FOUND")
    }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })

    override suspend fun submitRequest(input: SubmitM15FosterRequestInput): Result<M15FosterRequest> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
                M15Validators.validateSubmitRequest(input)?.let { failM15(it) }
                val home = store.homes.value.find { it.id == input.fosterHomeId }
                    ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
                if (!home.status.acceptsRequests) failM15("M15_FOSTER_HOME_NOT_ACTIVE")
                if (home.availabilityStatus == M15FosterAvailabilityStatus.UNAVAILABLE ||
                    home.availabilityStatus == M15FosterAvailabilityStatus.FULL
                ) {
                    failM15("M15_FOSTER_HOME_UNAVAILABLE")
                }
                if (home.freeSlots <= 0) failM15("M15_FOSTER_HOME_FULL")
                val pet = resolvePet(input.petId) ?: failM15("PET_NOT_FOUND")
                if (pet.status.equals("DECEASED", true) || pet.status.equals("ARCHIVED", true)) {
                    failM15("PET_NOT_ELIGIBLE_FOR_FOSTER")
                }
                val principal = store.petPrincipal.value[pet.id]
                if (!authority.canSubmitRequest(actor, pet, principal) &&
                    input.requesterOrganizationId.isNullOrBlank()
                ) {
                    failM15("FORBIDDEN")
                }
                if (home.acceptedSpecies.isNotEmpty() &&
                    pet.species.name.uppercase() !in home.acceptedSpecies
                ) {
                    failM15("M15_FOSTER_HOME_INCOMPATIBLE")
                }
                if (home.acceptedSizes.isNotEmpty() &&
                    pet.size.name.uppercase() !in home.acceptedSizes
                ) {
                    failM15("M15_FOSTER_HOME_INCOMPATIBLE")
                }
                if (input.urgency == M15FosterUrgency.EMERGENCY && !home.acceptsEmergencies) {
                    failM15("M15_FOSTER_HOME_INCOMPATIBLE")
                }
                if (!input.specialNeeds.isNullOrBlank() && !home.acceptsSpecialNeeds) {
                    failM15("M15_FOSTER_HOME_INCOMPATIBLE")
                }
                val activePlacement = store.placements.value.any {
                    it.petId == pet.id &&
                        (it.status == M15FosterPlacementStatus.ACTIVE ||
                            it.status == M15FosterPlacementStatus.RESERVED)
                }
                if (activePlacement) failM15("PET_ALREADY_IN_FOSTER")
                val dup = store.requests.value.any {
                    it.fosterHomeId == home.id && it.petId == pet.id && it.status.isActive
                }
                if (dup) failM15("M15_FOSTER_REQUEST_ALREADY_EXISTS")
                val now = System.currentTimeMillis()
                val row = M15FosterRequest(
                    id = store.nextId("m15_req"),
                    fosterHomeId = home.id,
                    petId = pet.id,
                    petName = pet.name,
                    requesterUserId = actor,
                    requesterOrganizationId = input.requesterOrganizationId,
                    message = input.message.trim(),
                    urgency = input.urgency,
                    requestedStartAt = input.requestedStartAt,
                    estimatedEndAt = input.estimatedEndAt,
                    specialNeeds = input.specialNeeds?.trim()?.takeIf { it.isNotEmpty() },
                    status = M15FosterRequestStatus.SUBMITTED,
                    createdAt = now
                )
                store.upsertRequest(row)
                store.audit(M15AuditEvents.REQUEST_SUBMITTED, row.id)
                store.recordM06(M15M06Hooks.REQUEST_SUBMITTED, row.id)
                row
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }

    override suspend fun cancelRequest(requestId: String): Result<M15FosterRequest> =
        transitionRequest(requestId, M15FosterRequestStatus.CANCELLED, requireRequester = true)

    override suspend fun markUnderReview(requestId: String): Result<M15FosterRequest> =
        transitionRequest(requestId, M15FosterRequestStatus.UNDER_REVIEW, requireOwner = true)

    override suspend fun acceptRequest(requestId: String): Result<M15FosterRequest> =
        store.withLock {
            runCatching {
                val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
                val req = store.requests.value.find { it.id == requestId }
                    ?: failM15("M15_FOSTER_REQUEST_NOT_FOUND")
                val home = store.homes.value.find { it.id == req.fosterHomeId }
                    ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
                if (!authority.canReviewRequest(actor, home.ownerUserId)) {
                    failM15("M15_FOSTER_REQUEST_FORBIDDEN")
                }
                if (req.status == M15FosterRequestStatus.ACCEPTED) return@runCatching req
                if (!M15Validators.canTransitionRequest(req.status, M15FosterRequestStatus.ACCEPTED)) {
                    failM15("M15_FOSTER_REQUEST_INVALID_TRANSITION")
                }
                if (home.freeSlots <= 0) failM15("M15_FOSTER_HOME_FULL")
                val accepted = applyRequestTransition(req, M15FosterRequestStatus.ACCEPTED, actor, null)
                val reservedHome = home.copy(
                    reservedCount = home.reservedCount + 1,
                    availabilityStatus = M15Validators.recomputeAvailability(
                        home.status,
                        home.totalCapacity,
                        home.currentOccupancy,
                        home.reservedCount + 1
                    ),
                    updatedAt = System.currentTimeMillis()
                )
                store.upsertHome(reservedHome)
                val placement = M15FosterPlacement(
                    id = store.nextId("m15_plc"),
                    fosterRequestId = accepted.id,
                    fosterHomeId = home.id,
                    petId = accepted.petId,
                    petName = accepted.petName,
                    requesterUserId = accepted.requesterUserId,
                    requesterOrganizationId = accepted.requesterOrganizationId,
                    fosterUserId = home.ownerUserId,
                    status = M15FosterPlacementStatus.RESERVED,
                    startedAt = System.currentTimeMillis(),
                    estimatedEndAt = accepted.estimatedEndAt
                )
                store.upsertPlacement(placement)
                store.audit(M15AuditEvents.PLACEMENT_RESERVED, placement.id)
                store.recordM06(M15M06Hooks.REQUEST_ACCEPTED, accepted.id)
                accepted
            }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
        }

    override suspend fun rejectRequest(requestId: String, reason: String?): Result<M15FosterRequest> =
        transitionRequest(
            requestId,
            M15FosterRequestStatus.REJECTED,
            requireOwner = true,
            rejectionReason = reason
        )

    private suspend fun transitionRequest(
        requestId: String,
        target: M15FosterRequestStatus,
        requireRequester: Boolean = false,
        requireOwner: Boolean = false,
        rejectionReason: String? = null
    ): Result<M15FosterRequest> = store.withLock {
        runCatching {
            val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
            val req = store.requests.value.find { it.id == requestId }
                ?: failM15("M15_FOSTER_REQUEST_NOT_FOUND")
            val home = store.homes.value.find { it.id == req.fosterHomeId }
                ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
            when {
                requireRequester && req.requesterUserId != actor ->
                    failM15("M15_FOSTER_REQUEST_FORBIDDEN")
                requireOwner && !authority.canReviewRequest(actor, home.ownerUserId) ->
                    failM15("M15_FOSTER_REQUEST_FORBIDDEN")
            }
            if (req.status == target) return@runCatching req
            if (!M15Validators.canTransitionRequest(req.status, target)) {
                failM15("M15_FOSTER_REQUEST_NOT_ACTIVE")
            }
            applyRequestTransition(req, target, actor, rejectionReason)
        }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
    }

    private fun applyRequestTransition(
        req: M15FosterRequest,
        target: M15FosterRequestStatus,
        actor: String,
        rejectionReason: String?
    ): M15FosterRequest {
        val updated = req.copy(
            status = target,
            reviewedAt = System.currentTimeMillis(),
            reviewedBy = actor,
            rejectionReason = rejectionReason?.trim()?.takeIf { it.isNotEmpty() }
        )
        store.upsertRequest(updated)
        store.audit(M15AuditEvents.REQUEST_REVIEWED, updated.id)
        return updated
    }
}

class MockM15FosterPlacementRepository(
    private val actorUserId: () -> String?,
    private val store: M15MemoryStore,
    private val authority: M15AuthorityPolicy = MockM15AuthorityPolicy()
) : M15FosterPlacementRepository {

    override fun observeActivePlacementsForHome(homeId: String): Flow<List<M15FosterPlacement>> =
        store.placements.map { list ->
            list.filter {
                it.fosterHomeId == homeId &&
                    (it.status == M15FosterPlacementStatus.ACTIVE ||
                        it.status == M15FosterPlacementStatus.RESERVED)
            }
        }

    override fun observeActivePlacementsForUser(userId: String): Flow<List<M15FosterPlacement>> =
        store.placements.map { list ->
            list.filter {
                (it.fosterUserId == userId || it.requesterUserId == userId) &&
                    (it.status == M15FosterPlacementStatus.ACTIVE ||
                        it.status == M15FosterPlacementStatus.RESERVED)
            }
        }

    override suspend fun getPlacementById(id: String): Result<M15FosterPlacement> = runCatching {
        if (id.isBlank()) failM15("M15_FOSTER_PLACEMENT_NOT_FOUND")
        store.placements.value.find { it.id == id } ?: failM15("M15_FOSTER_PLACEMENT_NOT_FOUND")
    }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })

    override suspend fun startPlacement(
        requestId: String,
        initialNotes: String?
    ): Result<M15FosterPlacement> = store.withLock {
        runCatching {
            val actor = actorUserId() ?: failM15("NOT_AUTHENTICATED")
            val req = store.requests.value.find { it.id == requestId }
                ?: failM15("M15_FOSTER_REQUEST_NOT_FOUND")
            val home = store.homes.value.find { it.id == req.fosterHomeId }
                ?: failM15("M15_FOSTER_HOME_NOT_FOUND")
            if (!authority.canReviewRequest(actor, home.ownerUserId)) failM15("FORBIDDEN")
            val placement = store.placements.value.firstOrNull {
                it.fosterRequestId == requestId &&
                    it.status == M15FosterPlacementStatus.RESERVED
            } ?: failM15("M15_FOSTER_PLACEMENT_NOT_FOUND")
            if (!M15Validators.canTransitionPlacement(
                    placement.status,
                    M15FosterPlacementStatus.ACTIVE
                )
            ) {
                failM15("M15_FOSTER_PLACEMENT_INVALID_TRANSITION")
            }
            val activeForPet = store.placements.value.any {
                it.petId == placement.petId &&
                    it.id != placement.id &&
                    it.status == M15FosterPlacementStatus.ACTIVE
            }
            if (activeForPet) failM15("M15_FOSTER_PLACEMENT_ALREADY_ACTIVE")
            val updatedHome = home.copy(
                reservedCount = (home.reservedCount - 1).coerceAtLeast(0),
                currentOccupancy = home.currentOccupancy + 1,
                availabilityStatus = M15Validators.recomputeAvailability(
                    home.status,
                    home.totalCapacity,
                    home.currentOccupancy + 1,
                    (home.reservedCount - 1).coerceAtLeast(0)
                ),
                updatedAt = System.currentTimeMillis()
            )
            store.upsertHome(updatedHome)
            val updated = placement.copy(
                status = M15FosterPlacementStatus.ACTIVE,
                initialNotes = initialNotes?.trim()?.takeIf { it.isNotEmpty() },
                startedAt = System.currentTimeMillis()
            )
            store.upsertPlacement(updated)
            store.audit(M15AuditEvents.PLACEMENT_STARTED, updated.id)
            store.recordM06(M15M06Hooks.PLACEMENT_STARTED, updated.id)
            updated
        }.fold({ Result.success(it) }, { M15ErrorMapper.failure(it) })
    }
}

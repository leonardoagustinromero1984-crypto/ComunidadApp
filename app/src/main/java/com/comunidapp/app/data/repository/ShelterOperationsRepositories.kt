package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterPlacementStatus
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterPublicListing
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyContribution
import com.comunidapp.app.data.model.ShelterSupplyRequest
import com.comunidapp.app.data.model.ShelterVolunteerAssignment
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.model.ShelterVolunteerStatus
import com.comunidapp.app.data.model.recomputeShelterAvailability
import com.comunidapp.app.data.model.toPublicListing
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Mock grant for M08 org CO_RESPONSIBLE linked to shelter intake. */
data class ShelterOrgResponsibilityGrant(
    val id: String,
    val petId: String,
    val organizationId: String,
    val placementId: String,
    val roleCode: String = "CO_RESPONSIBLE",
    val active: Boolean = true
)

class M11ShelterMemoryStore {
    val profiles = MutableStateFlow<List<ShelterProfile>>(emptyList())
    val placements = MutableStateFlow<List<ShelterPetPlacement>>(emptyList())
    val volunteers = MutableStateFlow<List<ShelterVolunteerAssignment>>(emptyList())
    val orgResponsibilities = MutableStateFlow<List<ShelterOrgResponsibilityGrant>>(emptyList())
    /** orgId → status (mock M03). */
    val organizationStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    /** orgId → set of userIds with manage permission. */
    val orgManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** orgId → set of userIds with view permission. */
    val orgViewers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** petId → PRINCIPAL user id. */
    val petPrincipal = MutableStateFlow<Map<String, String>>(emptyMap())
    /** Optional link to M10 store for FOSTER_RETURN. */
    var fosterStore: M10FosterMemoryStore? = null
    /** M11 Bloque 2 — campañas e insumos. */
    val campaigns = MutableStateFlow<List<ShelterCampaign>>(emptyList())
    val campaignUpdates = MutableStateFlow<List<ShelterCampaignUpdate>>(emptyList())
    val supplyRequests = MutableStateFlow<List<ShelterSupplyRequest>>(emptyList())
    val supplyContributions = MutableStateFlow<List<ShelterSupplyContribution>>(emptyList())
    val auditEvents = MutableStateFlow<List<M11AuditEvent>>(emptyList())
    val m06Hooks = MutableStateFlow<List<String>>(emptyList())

    fun clear() {
        profiles.value = emptyList()
        placements.value = emptyList()
        volunteers.value = emptyList()
        orgResponsibilities.value = emptyList()
        organizationStatus.value = emptyMap()
        orgManagers.value = emptyMap()
        orgViewers.value = emptyMap()
        petPrincipal.value = emptyMap()
        fosterStore = null
        campaigns.value = emptyList()
        campaignUpdates.value = emptyList()
        supplyRequests.value = emptyList()
        supplyContributions.value = emptyList()
        auditEvents.value = emptyList()
        m06Hooks.value = emptyList()
    }
}

/** Evento auditable M07 (mock). */
data class M11AuditEvent(val eventKey: String, val resourceId: String)

data class CreateShelterProfileInput(
    val organizationId: String,
    val branchId: String? = null,
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptsEmergencies: Boolean = false,
    val publicZoneText: String? = null,
    val internalAddressRef: String? = null,
    val activate: Boolean = false
)

data class UpdateShelterProfileInput(
    val shelterId: String,
    val displayName: String,
    val description: String? = null,
    val totalCapacity: Int,
    val acceptedSpecies: Set<String>,
    val acceptsEmergencies: Boolean = false,
    val publicZoneText: String? = null,
    val internalAddressRef: String? = null
)

interface ShelterProfileRepository {
    fun observePublicShelters(): Flow<List<ShelterPublicListing>>
    fun observeMyShelters(userId: String): Flow<List<ShelterProfile>>
    suspend fun getShelterById(id: String): Result<ShelterProfile>
    suspend fun createShelter(input: CreateShelterProfileInput): Result<ShelterProfile>
    suspend fun updateShelter(input: UpdateShelterProfileInput): Result<ShelterProfile>
    suspend fun changeStatus(shelterId: String, status: ShelterStatus): Result<ShelterProfile>
}

interface ShelterPetRepository {
    fun observeShelterPets(shelterId: String): Flow<List<ShelterPetPlacement>>
    suspend fun getPetPlacement(id: String): Result<ShelterPetPlacement>
    suspend fun reserveCapacity(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String? = null
    ): Result<ShelterPetPlacement>
    suspend fun admitPet(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String? = null,
        relatedFosterPlacementId: String? = null
    ): Result<ShelterPetPlacement>
    suspend fun changePlacementStatus(
        placementId: String,
        status: ShelterPetPlacementStatus
    ): Result<ShelterPetPlacement>
    suspend fun releasePet(
        placementId: String,
        endReason: ShelterPetEndReason,
        notes: String? = null
    ): Result<ShelterPetPlacement>
    /** Hook M09: adopción finalizada cierra alojamiento abierto. */
    suspend fun onAdoptionFinalized(petId: String): Result<Unit>
}

interface ShelterVolunteerRepository {
    fun observeVolunteers(shelterId: String): Flow<List<ShelterVolunteerAssignment>>
    suspend fun inviteVolunteer(
        shelterId: String,
        userId: String,
        role: ShelterVolunteerRole,
        notes: String? = null
    ): Result<ShelterVolunteerAssignment>
    suspend fun acceptAssignment(assignmentId: String): Result<ShelterVolunteerAssignment>
    suspend fun pauseAssignment(assignmentId: String): Result<ShelterVolunteerAssignment>
    suspend fun endAssignment(assignmentId: String): Result<ShelterVolunteerAssignment>
}

private fun failM11(code: String): Nothing =
    throw M11ShelterException(code, M11ShelterErrorMapper.userMessage(code))

private fun M11ShelterMemoryStore.canManage(actor: String, orgId: String): Boolean =
    orgManagers.value[orgId]?.contains(actor) == true

private fun M11ShelterMemoryStore.canView(actor: String, orgId: String): Boolean =
    canManage(actor, orgId) || orgViewers.value[orgId]?.contains(actor) == true

private fun M11ShelterMemoryStore.requireOrgEligible(orgId: String) {
    val st = organizationStatus.value[orgId] ?: failM11("ORGANIZATION_NOT_ELIGIBLE")
    if (st != "ACTIVE") failM11("ORGANIZATION_NOT_ELIGIBLE")
}

private fun syncCapacity(store: M11ShelterMemoryStore, shelterId: String) {
    val shelter = store.profiles.value.find { it.id == shelterId } ?: return
    val open = store.placements.value.filter {
        it.shelterProfileId == shelterId && it.status.isOpen
    }
    val reserved = open.count { it.status == ShelterPetPlacementStatus.RESERVED }
    val occupied = open.count { it.status != ShelterPetPlacementStatus.RESERVED }
    val updated = shelter.copy(
        reservedCapacity = reserved,
        currentOccupancy = occupied,
        updatedAt = System.currentTimeMillis()
    )
    store.profiles.value = store.profiles.value.map { if (it.id == shelterId) updated else it }
}

class MockShelterProfileRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterProfileRepository {

    override fun observePublicShelters(): Flow<List<ShelterPublicListing>> =
        store.profiles.map { list ->
            list.filter { it.status == ShelterStatus.ACTIVE }
                .map { it.toPublicListing() }
        }

    override fun observeMyShelters(userId: String): Flow<List<ShelterProfile>> =
        store.profiles.map { list ->
            list.filter { store.canView(userId, it.organizationId) }
        }

    override suspend fun getShelterById(id: String): Result<ShelterProfile> = runCatching {
        if (id.isBlank()) failM11("SHELTER_NOT_FOUND")
        store.profiles.value.find { it.id == id } ?: failM11("SHELTER_NOT_FOUND")
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun createShelter(input: CreateShelterProfileInput): Result<ShelterProfile> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.organizationId.isBlank()) failM11("ORGANIZATION_NOT_ELIGIBLE")
            store.requireOrgEligible(input.organizationId)
            if (!store.canManage(actor, input.organizationId)) failM11("SHELTER_FORBIDDEN")
            if (input.totalCapacity <= 0) failM11("SHELTER_CAPACITY_EXCEEDED")
            val branchKey = input.branchId
            val existsActive = store.profiles.value.any {
                it.organizationId == input.organizationId &&
                    it.branchId == branchKey &&
                    it.status == ShelterStatus.ACTIVE
            }
            if (existsActive && input.activate) failM11("SHELTER_ALREADY_EXISTS")
            val now = System.currentTimeMillis()
            val status = if (input.activate) ShelterStatus.ACTIVE else ShelterStatus.DRAFT
            val row = ShelterProfile(
                id = UUID.randomUUID().toString(),
                organizationId = input.organizationId,
                branchId = input.branchId,
                displayName = input.displayName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                status = status,
                totalCapacity = input.totalCapacity,
                acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                acceptsEmergencies = input.acceptsEmergencies,
                publicZoneText = input.publicZoneText?.trim()?.takeIf { it.isNotEmpty() },
                internalAddressRef = input.internalAddressRef?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now
            )
            store.profiles.value = listOf(row) + store.profiles.value
            row
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun updateShelter(input: UpdateShelterProfileInput): Result<ShelterProfile> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.shelterId.isBlank()) failM11("SHELTER_NOT_FOUND")
            val current = store.profiles.value.find { it.id == input.shelterId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManage(actor, current.organizationId)) failM11("SHELTER_FORBIDDEN")
            if (input.totalCapacity <= 0) failM11("SHELTER_CAPACITY_EXCEEDED")
            if (input.totalCapacity < current.usedSlots) failM11("SHELTER_CAPACITY_EXCEEDED")
            val updated = current.copy(
                displayName = input.displayName.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                totalCapacity = input.totalCapacity,
                acceptedSpecies = input.acceptedSpecies.map { it.uppercase() }.toSet(),
                acceptsEmergencies = input.acceptsEmergencies,
                publicZoneText = input.publicZoneText?.trim()?.takeIf { it.isNotEmpty() },
                internalAddressRef = input.internalAddressRef?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
            store.profiles.value = store.profiles.value.map { if (it.id == updated.id) updated else it }
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun changeStatus(
        shelterId: String,
        status: ShelterStatus
    ): Result<ShelterProfile> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.profiles.value.find { it.id == shelterId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, current.organizationId)) failM11("SHELTER_FORBIDDEN")
        if (status == ShelterStatus.ACTIVE) {
            store.requireOrgEligible(current.organizationId)
            val conflict = store.profiles.value.any {
                it.id != current.id &&
                    it.organizationId == current.organizationId &&
                    it.branchId == current.branchId &&
                    it.status == ShelterStatus.ACTIVE
            }
            if (conflict) failM11("SHELTER_ALREADY_EXISTS")
        }
        val updated = current.copy(status = status, updatedAt = System.currentTimeMillis())
        store.profiles.value = store.profiles.value.map { if (it.id == updated.id) updated else it }
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })
}

class MockShelterPetRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore,
    private val resolvePet: (String) -> Pet?
) : ShelterPetRepository {

    override fun observeShelterPets(shelterId: String): Flow<List<ShelterPetPlacement>> =
        store.placements.map { list -> list.filter { it.shelterProfileId == shelterId } }

    override suspend fun getPetPlacement(id: String): Result<ShelterPetPlacement> = runCatching {
        if (id.isBlank()) failM11("SHELTER_PET_NOT_FOUND")
        store.placements.value.find { it.id == id } ?: failM11("SHELTER_PET_NOT_FOUND")
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun reserveCapacity(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String?
    ): Result<ShelterPetPlacement> = admitInternal(
        shelterId, petId, intakeType, notes, null, ShelterPetPlacementStatus.RESERVED
    )

    override suspend fun admitPet(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String?,
        relatedFosterPlacementId: String?
    ): Result<ShelterPetPlacement> = admitInternal(
        shelterId, petId, intakeType, notes, relatedFosterPlacementId, ShelterPetPlacementStatus.ACTIVE
    )

    private suspend fun admitInternal(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String?,
        relatedFosterPlacementId: String?,
        targetStatus: ShelterPetPlacementStatus
    ): Result<ShelterPetPlacement> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        if (shelterId.isBlank()) failM11("SHELTER_NOT_FOUND")
        if (petId.isBlank()) failM11("PET_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == shelterId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!shelter.status.isOperative) failM11("SHELTER_NOT_ACTIVE")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_FORBIDDEN")
        val pet = resolvePet(petId) ?: failM11("PET_NOT_FOUND")
        if (pet.status.equals("DECEASED", ignoreCase = true)) failM11("SHELTER_PET_NOT_ELIGIBLE")
        val already = store.placements.value.any { it.petId == petId && it.status.isOpen }
        if (already) failM11("SHELTER_PET_ALREADY_ACTIVE")
        if (shelter.freeSlots <= 0) failM11("SHELTER_FULL")
        // M10: FOSTER_RETURN closes active foster placement
        var fosterId = relatedFosterPlacementId
        if (intakeType == ShelterIntakeType.FOSTER_RETURN) {
            val fs = store.fosterStore
            if (fs != null) {
                val activeFoster = fs.placements.value.find {
                    it.petId == petId && it.status == FosterPlacementStatus.ACTIVE
                }
                if (activeFoster != null) {
                    fosterId = activeFoster.id
                    fs.placements.value = fs.placements.value.map {
                        if (it.id == activeFoster.id) {
                            it.copy(
                                status = FosterPlacementStatus.COMPLETED,
                                endedAt = System.currentTimeMillis(),
                                endReason = "FOSTER_RETURN",
                                endedBy = actor
                            )
                        } else it
                    }
                    fs.temporaryCustody.value = fs.temporaryCustody.value.map { g ->
                        if (g.placementId == activeFoster.id && g.active) g.copy(active = false) else g
                    }
                    val home = fs.homes.value.find { it.id == activeFoster.fosterHomeId }
                    if (home != null) {
                        val occ = (home.currentOccupancy - 1).coerceAtLeast(0)
                        fs.homes.value = fs.homes.value.map {
                            if (it.id == home.id) it.copy(
                                currentOccupancy = occ,
                                availabilityStatus = recomputeAvailability(
                                    home.status, home.totalCapacity, occ, home.reservedCount
                                ),
                                updatedAt = System.currentTimeMillis()
                            ) else it
                        }
                    }
                }
            }
        }
        val respId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val placement = ShelterPetPlacement(
            id = UUID.randomUUID().toString(),
            shelterProfileId = shelterId,
            petId = petId,
            petName = pet.name,
            intakeType = intakeType,
            status = targetStatus,
            admittedAt = now,
            admittedBy = actor,
            sourceOrganizationId = shelter.organizationId,
            sourceUserId = actor,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            organizationalResponsibilityId = respId,
            relatedFosterPlacementId = fosterId
        )
        store.placements.value = listOf(placement) + store.placements.value
        store.orgResponsibilities.value = listOf(
            ShelterOrgResponsibilityGrant(
                id = respId,
                petId = petId,
                organizationId = shelter.organizationId,
                placementId = placement.id
            )
        ) + store.orgResponsibilities.value
        // PRINCIPAL untouched
        syncCapacity(store, shelterId)
        placement
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun changePlacementStatus(
        placementId: String,
        status: ShelterPetPlacementStatus
    ): Result<ShelterPetPlacement> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val p = store.placements.value.find { it.id == placementId }
            ?: failM11("SHELTER_PET_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == p.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_FORBIDDEN")
        if (!p.status.isOpen) failM11("SHELTER_PET_INVALID_TRANSITION")
        if (status !in setOf(
                ShelterPetPlacementStatus.ACTIVE,
                ShelterPetPlacementStatus.QUARANTINE,
                ShelterPetPlacementStatus.MEDICAL_CARE,
                ShelterPetPlacementStatus.RESERVED
            )
        ) {
            failM11("SHELTER_PET_INVALID_TRANSITION")
        }
        if (p.status == status) return@runCatching p
        val updated = p.copy(status = status)
        store.placements.value = store.placements.value.map { if (it.id == p.id) updated else it }
        syncCapacity(store, shelter.id)
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun releasePet(
        placementId: String,
        endReason: ShelterPetEndReason,
        notes: String?
    ): Result<ShelterPetPlacement> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val p = store.placements.value.find { it.id == placementId }
            ?: failM11("SHELTER_PET_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == p.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_FORBIDDEN")
        if (!p.status.isOpen) {
            if (p.status == ShelterPetPlacementStatus.RELEASED ||
                p.status == ShelterPetPlacementStatus.ADOPTED
            ) {
                return@runCatching p
            }
            failM11("SHELTER_PET_INVALID_TRANSITION")
        }
        val terminal = when (endReason) {
            ShelterPetEndReason.ADOPTED -> ShelterPetPlacementStatus.ADOPTED
            ShelterPetEndReason.DECEASED -> ShelterPetPlacementStatus.DECEASED
            ShelterPetEndReason.TRANSFERRED, ShelterPetEndReason.FOSTERED ->
                ShelterPetPlacementStatus.TRANSFERRED
            else -> ShelterPetPlacementStatus.RELEASED
        }
        val updated = p.copy(
            status = terminal,
            endedAt = System.currentTimeMillis(),
            endReason = endReason,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() } ?: p.notes
        )
        store.placements.value = store.placements.value.map { if (it.id == p.id) updated else it }
        store.orgResponsibilities.value = store.orgResponsibilities.value.map { g ->
            if (g.placementId == p.id && g.active) g.copy(active = false) else g
        }
        // PRINCIPAL map untouched
        syncCapacity(store, shelter.id)
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun onAdoptionFinalized(petId: String): Result<Unit> = runCatching {
        val open = store.placements.value.filter { it.petId == petId && it.status.isOpen }
        open.forEach { p ->
            releasePet(p.id, ShelterPetEndReason.ADOPTED, "M09 finalization").getOrThrow()
        }
        Unit
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })
}

class MockShelterVolunteerRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore,
    private val knownUserIds: () -> Set<String> = { emptySet() }
) : ShelterVolunteerRepository {

    override fun observeVolunteers(shelterId: String): Flow<List<ShelterVolunteerAssignment>> =
        store.volunteers.map { list -> list.filter { it.shelterProfileId == shelterId } }

    override suspend fun inviteVolunteer(
        shelterId: String,
        userId: String,
        role: ShelterVolunteerRole,
        notes: String?
    ): Result<ShelterVolunteerAssignment> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val shelter = store.profiles.value.find { it.id == shelterId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_VOLUNTEER_FORBIDDEN")
        if (userId.isBlank() || (knownUserIds().isNotEmpty() && userId !in knownUserIds())) {
            failM11("SHELTER_VOLUNTEER_NOT_FOUND")
        }
        val dup = store.volunteers.value.any {
            it.shelterProfileId == shelterId &&
                it.userId == userId &&
                it.role == role &&
                it.status.isOpen
        }
        if (dup) failM11("SHELTER_VOLUNTEER_ALREADY_ASSIGNED")
        val row = ShelterVolunteerAssignment(
            id = UUID.randomUUID().toString(),
            shelterProfileId = shelterId,
            userId = userId,
            role = role,
            status = ShelterVolunteerStatus.INVITED,
            startsAt = System.currentTimeMillis(),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            invitedBy = actor
        )
        store.volunteers.value = listOf(row) + store.volunteers.value
        row
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun acceptAssignment(assignmentId: String): Result<ShelterVolunteerAssignment> =
        transition(assignmentId, ShelterVolunteerStatus.ACTIVE, allowSelf = true)

    override suspend fun pauseAssignment(assignmentId: String): Result<ShelterVolunteerAssignment> =
        transition(assignmentId, ShelterVolunteerStatus.PAUSED, allowSelf = false)

    override suspend fun endAssignment(assignmentId: String): Result<ShelterVolunteerAssignment> =
        transition(assignmentId, ShelterVolunteerStatus.ENDED, allowSelf = false)

    private suspend fun transition(
        assignmentId: String,
        target: ShelterVolunteerStatus,
        allowSelf: Boolean
    ): Result<ShelterVolunteerAssignment> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val a = store.volunteers.value.find { it.id == assignmentId }
            ?: failM11("SHELTER_VOLUNTEER_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == a.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        val isManager = store.canManage(actor, shelter.organizationId)
        val isSelf = actor == a.userId
        if (!isManager && !(allowSelf && isSelf)) failM11("SHELTER_VOLUNTEER_FORBIDDEN")
        if (a.status == target) return@runCatching a
        if (a.status == ShelterVolunteerStatus.ENDED || a.status == ShelterVolunteerStatus.REJECTED) {
            failM11("SHELTER_VOLUNTEER_INVALID_TRANSITION")
        }
        when (target) {
            ShelterVolunteerStatus.ACTIVE -> {
                if (a.status != ShelterVolunteerStatus.INVITED &&
                    a.status != ShelterVolunteerStatus.PAUSED
                ) {
                    failM11("SHELTER_VOLUNTEER_INVALID_TRANSITION")
                }
            }
            ShelterVolunteerStatus.PAUSED -> {
                if (a.status != ShelterVolunteerStatus.ACTIVE) {
                    failM11("SHELTER_VOLUNTEER_INVALID_TRANSITION")
                }
            }
            ShelterVolunteerStatus.ENDED -> { /* from open */ }
            else -> failM11("SHELTER_VOLUNTEER_INVALID_TRANSITION")
        }
        val updated = a.copy(
            status = target,
            endsAt = if (target == ShelterVolunteerStatus.ENDED) {
                System.currentTimeMillis()
            } else a.endsAt
        )
        store.volunteers.value = store.volunteers.value.map { if (it.id == a.id) updated else it }
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })
}

/** Volunteers never gain shelter.manage from assignment alone — enforced by canManage checks. */
fun ShelterVolunteerAssignment.grantsAdministrativeAuthority(): Boolean = false

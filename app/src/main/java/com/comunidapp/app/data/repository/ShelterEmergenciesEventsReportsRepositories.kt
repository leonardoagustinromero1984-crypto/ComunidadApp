package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ShelterCampaignMetrics
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCapacityMetrics
import com.comunidapp.app.data.model.ShelterEmergency
import com.comunidapp.app.data.model.ShelterEmergencyCategory
import com.comunidapp.app.data.model.ShelterEmergencyMetrics
import com.comunidapp.app.data.model.ShelterEmergencyPublicListing
import com.comunidapp.app.data.model.ShelterEmergencySeverity
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEmergencyVisibility
import com.comunidapp.app.data.model.ShelterEvent
import com.comunidapp.app.data.model.ShelterEventMetrics
import com.comunidapp.app.data.model.ShelterEventPublicListing
import com.comunidapp.app.data.model.ShelterEventRegistration
import com.comunidapp.app.data.model.ShelterEventRegistrationStatus
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterEventType
import com.comunidapp.app.data.model.ShelterEventVisibility
import com.comunidapp.app.data.model.ShelterOperationalSummary
import com.comunidapp.app.data.model.ShelterPetMetrics
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterReportExport
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyMetrics
import com.comunidapp.app.data.model.ShelterSupplyRequestStatus
import com.comunidapp.app.data.model.ShelterVolunteerMetrics
import com.comunidapp.app.data.model.ShelterVolunteerStatus
import com.comunidapp.app.data.model.isEmergencyExpired
import com.comunidapp.app.data.model.toPublicListing
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class CreateShelterEmergencyInput(
    val shelterProfileId: String,
    val petId: String? = null,
    val title: String,
    val description: String,
    val category: ShelterEmergencyCategory,
    val severity: ShelterEmergencySeverity = ShelterEmergencySeverity.MEDIUM,
    val visibility: ShelterEmergencyVisibility = ShelterEmergencyVisibility.PUBLIC,
    val startsAt: Long,
    val expiresAt: Long? = null,
    val evidenceRef: String? = null,
    val activate: Boolean = false
)

data class UpdateShelterEmergencyInput(
    val emergencyId: String,
    val title: String,
    val description: String,
    val category: ShelterEmergencyCategory,
    val severity: ShelterEmergencySeverity,
    val visibility: ShelterEmergencyVisibility,
    val startsAt: Long,
    val expiresAt: Long? = null,
    val evidenceRef: String? = null,
    val petId: String? = null
)

data class CreateShelterEventInput(
    val shelterProfileId: String,
    val title: String,
    val description: String,
    val eventType: ShelterEventType,
    val visibility: ShelterEventVisibility = ShelterEventVisibility.PUBLIC,
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int? = null,
    val publicLocationText: String? = null,
    val privateLocationText: String? = null,
    val coverAssetRef: String? = null,
    val publish: Boolean = false
)

data class UpdateShelterEventInput(
    val eventId: String,
    val title: String,
    val description: String,
    val eventType: ShelterEventType,
    val visibility: ShelterEventVisibility,
    val startsAt: Long,
    val endsAt: Long,
    val capacity: Int? = null,
    val publicLocationText: String? = null,
    val privateLocationText: String? = null,
    val coverAssetRef: String? = null
)

interface ShelterEmergencyRepository {
    fun observePublicEmergencies(): Flow<List<ShelterEmergencyPublicListing>>
    fun observeShelterEmergencies(shelterId: String): Flow<List<ShelterEmergency>>
    suspend fun getEmergency(id: String): Result<ShelterEmergency>
    suspend fun createEmergency(input: CreateShelterEmergencyInput): Result<ShelterEmergency>
    suspend fun updateEmergency(input: UpdateShelterEmergencyInput): Result<ShelterEmergency>
    suspend fun changeEmergencyStatus(
        emergencyId: String,
        status: ShelterEmergencyStatus
    ): Result<ShelterEmergency>
    suspend fun resolveEmergency(emergencyId: String, notes: String): Result<ShelterEmergency>
    suspend fun expireIfNeeded(emergencyId: String): Result<ShelterEmergency>
}

interface ShelterEventRepository {
    fun observePublicEvents(): Flow<List<ShelterEventPublicListing>>
    fun observeShelterEvents(shelterId: String): Flow<List<ShelterEvent>>
    suspend fun getEvent(id: String): Result<ShelterEvent>
    suspend fun createEvent(input: CreateShelterEventInput): Result<ShelterEvent>
    suspend fun updateEvent(input: UpdateShelterEventInput): Result<ShelterEvent>
    suspend fun changeEventStatus(eventId: String, status: ShelterEventStatus): Result<ShelterEvent>
    suspend fun register(eventId: String, notes: String? = null): Result<ShelterEventRegistration>
    suspend fun cancelRegistration(registrationId: String): Result<ShelterEventRegistration>
    suspend fun markAttendance(
        registrationId: String,
        attended: Boolean
    ): Result<ShelterEventRegistration>
    fun observeRegistrations(eventId: String): Flow<List<ShelterEventRegistration>>
}

interface ShelterReportRepository {
    suspend fun getOperationalSummary(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterOperationalSummary>
    suspend fun getCapacityMetrics(shelterId: String, from: Long, to: Long): Result<ShelterCapacityMetrics>
    suspend fun getPetMetrics(shelterId: String, from: Long, to: Long): Result<ShelterPetMetrics>
    suspend fun getVolunteerMetrics(shelterId: String, from: Long, to: Long): Result<ShelterVolunteerMetrics>
    suspend fun getCampaignMetrics(shelterId: String, from: Long, to: Long): Result<ShelterCampaignMetrics>
    suspend fun getSupplyMetrics(shelterId: String, from: Long, to: Long): Result<ShelterSupplyMetrics>
    suspend fun getEmergencyMetrics(shelterId: String, from: Long, to: Long): Result<ShelterEmergencyMetrics>
    suspend fun getEventMetrics(shelterId: String, from: Long, to: Long): Result<ShelterEventMetrics>
    suspend fun exportOperationalCsv(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterReportExport>
}

private fun failM11(code: String): Nothing =
    throw M11ShelterException(code, M11ShelterErrorMapper.userMessage(code))

private fun M11ShelterMemoryStore.canManage(actor: String, orgId: String): Boolean =
    orgManagers.value[orgId]?.contains(actor) == true

private fun M11ShelterMemoryStore.canView(actor: String, orgId: String): Boolean =
    canManage(actor, orgId) || orgViewers.value[orgId]?.contains(actor) == true

private fun M11ShelterMemoryStore.recordAudit(eventKey: String, resourceId: String) {
    auditEvents.value = auditEvents.value + M11AuditEvent(eventKey, resourceId)
}

private fun M11ShelterMemoryStore.recordM06Hook(hookName: String) {
    m06Hooks.value = m06Hooks.value + hookName
}

private fun validateEvidenceRef(ref: String?) {
    if (ref.isNullOrBlank()) return
    if (FosterSecureRefValidator.isUnsafePublicReference(ref)) failM11("SHELTER_EVIDENCE_REF_INVALID")
    val lower = ref.trim().lowercase()
    if (!lower.startsWith("m05://") && !lower.startsWith("file_asset:")) {
        failM11("SHELTER_EVIDENCE_REF_INVALID")
    }
}

private fun requireActiveShelter(store: M11ShelterMemoryStore, shelterId: String) {
    val shelter = store.profiles.value.find { it.id == shelterId } ?: failM11("SHELTER_NOT_FOUND")
    if (!shelter.status.isOperative) failM11("SHELTER_NOT_ACTIVE")
}

private fun shelterDisplayName(store: M11ShelterMemoryStore, shelterId: String): String? =
    store.profiles.value.find { it.id == shelterId }?.displayName

private fun validateReportRange(from: Long, to: Long) {
    if (from > to) failM11("SHELTER_REPORT_INVALID_RANGE")
}

private fun requireShelterForReport(
    store: M11ShelterMemoryStore,
    actor: String,
    shelterId: String
) {
    if (shelterId.isBlank()) failM11("SHELTER_NOT_FOUND")
    val shelter = store.profiles.value.find { it.id == shelterId } ?: failM11("SHELTER_NOT_FOUND")
    if (!store.canView(actor, shelter.organizationId)) failM11("SHELTER_REPORT_FORBIDDEN")
}

private fun requireShelterForReportExport(
    store: M11ShelterMemoryStore,
    actor: String,
    shelterId: String
) {
    if (shelterId.isBlank()) failM11("SHELTER_NOT_FOUND")
    val shelter = store.profiles.value.find { it.id == shelterId } ?: failM11("SHELTER_NOT_FOUND")
    if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_REPORT_FORBIDDEN")
}

private fun validatePetForShelter(store: M11ShelterMemoryStore, shelterId: String, petId: String?) {
    if (petId.isNullOrBlank()) return
    val accessible = store.placements.value.any {
        it.shelterProfileId == shelterId && it.petId == petId
    }
    if (!accessible) failM11("PET_NOT_FOUND")
}

private fun expireEmergencyIfNeededInternal(
    store: M11ShelterMemoryStore,
    emergency: ShelterEmergency,
    now: Long = System.currentTimeMillis()
): ShelterEmergency {
    if (!isEmergencyExpired(emergency.expiresAt, now, emergency.status)) return emergency
    val updated = emergency.copy(
        status = ShelterEmergencyStatus.EXPIRED,
        updatedAt = now
    )
    store.emergencies.value = store.emergencies.value.map { if (it.id == updated.id) updated else it }
    store.recordAudit(M11ShelterAuditEvents.SHELTER_EMERGENCY_STATUS_CHANGED, updated.id)
    return updated
}

private fun syncEventRegistrationCount(store: M11ShelterMemoryStore, eventId: String) {
    val event = store.events.value.find { it.id == eventId } ?: return
    val registered = store.eventRegistrations.value.count {
        it.eventId == eventId && it.status == ShelterEventRegistrationStatus.REGISTERED
    }
    val capacity = event.capacity
    val newStatus = when {
        event.status == ShelterEventStatus.CANCELLED ||
            event.status == ShelterEventStatus.COMPLETED ||
            event.status == ShelterEventStatus.DRAFT -> event.status
        capacity != null && registered >= capacity -> ShelterEventStatus.FULL
        event.status == ShelterEventStatus.FULL && (capacity == null || registered < capacity) ->
            ShelterEventStatus.PUBLISHED
        else -> event.status
    }
    val updated = event.copy(registeredCount = registered, status = newStatus, updatedAt = System.currentTimeMillis())
    store.events.value = store.events.value.map { if (it.id == eventId) updated else it }
}

private fun computeCapacityMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterCapacityMetrics {
    val shelter = store.profiles.value.find { it.id == shelterId } ?: failM11("SHELTER_NOT_FOUND")
    return ShelterCapacityMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        totalCapacity = shelter.totalCapacity,
        currentOccupancy = shelter.currentOccupancy,
        reservedCapacity = shelter.reservedCapacity,
        freeSlots = shelter.freeSlots
    )
}

private fun inRange(ts: Long?, from: Long, to: Long): Boolean =
    ts != null && ts in from..to

private fun computePetMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterPetMetrics {
    val placements = store.placements.value.filter { it.shelterProfileId == shelterId }
    return ShelterPetMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        activeCount = placements.count {
            it.status == ShelterPetPlacementStatus.ACTIVE ||
                it.status == ShelterPetPlacementStatus.RESERVED
        },
        quarantineCount = placements.count { it.status == ShelterPetPlacementStatus.QUARANTINE },
        medicalCareCount = placements.count { it.status == ShelterPetPlacementStatus.MEDICAL_CARE },
        releasesCount = placements.count {
            it.status == ShelterPetPlacementStatus.RELEASED &&
                inRange(it.endedAt, from, to)
        },
        adoptionsCount = placements.count {
            it.status == ShelterPetPlacementStatus.ADOPTED &&
                inRange(it.endedAt, from, to)
        }
    )
}

private fun computeVolunteerMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterVolunteerMetrics {
    val volunteers = store.volunteers.value.filter { it.shelterProfileId == shelterId }
    return ShelterVolunteerMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        activeCount = volunteers.count { it.status == ShelterVolunteerStatus.ACTIVE },
        pausedCount = volunteers.count { it.status == ShelterVolunteerStatus.PAUSED },
        endedCount = volunteers.count {
            it.status == ShelterVolunteerStatus.ENDED && inRange(it.endsAt, from, to)
        }
    )
}

private fun computeCampaignMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterCampaignMetrics {
    val campaigns = store.campaigns.value.filter { it.shelterProfileId == shelterId }
    return ShelterCampaignMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        activeCount = campaigns.count { it.status == ShelterCampaignStatus.ACTIVE },
        completedCount = campaigns.count {
            it.status == ShelterCampaignStatus.COMPLETED &&
                inRange(it.updatedAt, from, to)
        }
    )
}

private fun computeSupplyMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterSupplyMetrics {
    val requests = store.supplyRequests.value.filter { it.shelterProfileId == shelterId }
    return ShelterSupplyMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        openRequestsCount = requests.count { it.status.isOpen },
        fulfilledRequestsCount = requests.count {
            it.status == ShelterSupplyRequestStatus.FULFILLED &&
                inRange(it.updatedAt, from, to)
        },
        quantityReceivedTotal = requests.sumOf { it.quantityReceived }
    )
}

private fun computeEmergencyMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterEmergencyMetrics {
    val now = System.currentTimeMillis()
    val emergencies = store.emergencies.value
        .filter { it.shelterProfileId == shelterId }
        .map { expireEmergencyIfNeededInternal(store, it, now) }
    return ShelterEmergencyMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        activeCount = emergencies.count { it.status == ShelterEmergencyStatus.ACTIVE },
        criticalCount = emergencies.count {
            it.status == ShelterEmergencyStatus.ACTIVE &&
                it.severity == ShelterEmergencySeverity.CRITICAL
        },
        resolvedCount = emergencies.count {
            it.status == ShelterEmergencyStatus.RESOLVED &&
                inRange(it.resolvedAt, from, to)
        }
    )
}

private fun computeEventMetrics(
    store: M11ShelterMemoryStore,
    shelterId: String,
    from: Long,
    to: Long
): ShelterEventMetrics {
    val now = System.currentTimeMillis()
    val events = store.events.value.filter { it.shelterProfileId == shelterId }
    val regs = store.eventRegistrations.value.filter { reg ->
        events.any { it.id == reg.eventId }
    }
    return ShelterEventMetrics(
        shelterProfileId = shelterId,
        from = from,
        to = to,
        upcomingCount = events.count {
            it.status in setOf(ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL) &&
                it.startsAt >= now &&
                it.startsAt in from..to
        },
        completedCount = events.count {
            it.status == ShelterEventStatus.COMPLETED &&
                inRange(it.updatedAt, from, to)
        },
        registrationsCount = regs.count {
            it.status.isActive && inRange(it.registeredAt, from, to)
        }
    )
}

class MockShelterEmergencyRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterEmergencyRepository {

    override fun observePublicEmergencies(): Flow<List<ShelterEmergencyPublicListing>> =
        store.emergencies.map { list ->
            val now = System.currentTimeMillis()
            list.filter {
                it.visibility == ShelterEmergencyVisibility.PUBLIC &&
                    it.status == ShelterEmergencyStatus.ACTIVE &&
                    !isEmergencyExpired(it.expiresAt, now, it.status)
            }.map { emergency ->
                emergency.toPublicListing(shelterDisplayName(store, emergency.shelterProfileId))
            }
        }

    override fun observeShelterEmergencies(shelterId: String): Flow<List<ShelterEmergency>> =
        store.emergencies.map { list ->
            val actor = actorUserId()
            list.filter { emergency ->
                emergency.shelterProfileId == shelterId &&
                    actor != null &&
                    canReadEmergencyFor(actor, emergency)
            }
        }

    override suspend fun getEmergency(id: String): Result<ShelterEmergency> = runCatching {
        if (id.isBlank()) failM11("SHELTER_EMERGENCY_NOT_FOUND")
        val emergency = store.emergencies.value.find { it.id == id }
            ?: failM11("SHELTER_EMERGENCY_NOT_FOUND")
        val actor = actorUserId()
        if (emergency.visibility == ShelterEmergencyVisibility.INTERNAL) {
            if (actor == null || !canReadEmergencyFor(actor, emergency)) {
                failM11("SHELTER_EMERGENCY_FORBIDDEN")
            }
        }
        expireEmergencyIfNeededInternal(store, emergency)
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun createEmergency(input: CreateShelterEmergencyInput): Result<ShelterEmergency> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.shelterProfileId.isBlank()) failM11("SHELTER_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == input.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EMERGENCY_FORBIDDEN")
            val title = input.title.trim()
            val description = input.description.trim()
            if (title.isEmpty() || description.isEmpty()) failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
            validateEvidenceRef(input.evidenceRef)
            validatePetForShelter(store, shelter.id, input.petId)
            if (input.activate) requireActiveShelter(store, shelter.id)
            val now = System.currentTimeMillis()
            val status = if (input.activate) ShelterEmergencyStatus.ACTIVE else ShelterEmergencyStatus.DRAFT
            val row = ShelterEmergency(
                id = UUID.randomUUID().toString(),
                shelterProfileId = input.shelterProfileId,
                petId = input.petId?.trim()?.takeIf { it.isNotEmpty() },
                title = title,
                description = description,
                category = input.category,
                severity = input.severity,
                visibility = input.visibility,
                status = status,
                startsAt = input.startsAt,
                expiresAt = input.expiresAt,
                resolvedAt = null,
                resolutionNotes = null,
                evidenceRef = input.evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
                createdBy = actor,
                createdAt = now,
                updatedAt = now
            )
            store.emergencies.value = listOf(row) + store.emergencies.value
            store.recordAudit(M11ShelterAuditEvents.SHELTER_EMERGENCY_CREATED, row.id)
            if (status == ShelterEmergencyStatus.ACTIVE && row.severity == ShelterEmergencySeverity.CRITICAL) {
                store.recordM06Hook(M11ShelterNotificationHooks.EMERGENCY_CRITICAL_ACTIVATED)
            }
            row
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun updateEmergency(input: UpdateShelterEmergencyInput): Result<ShelterEmergency> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            if (input.emergencyId.isBlank()) failM11("SHELTER_EMERGENCY_NOT_FOUND")
            val current = store.emergencies.value.find { it.id == input.emergencyId }
                ?: failM11("SHELTER_EMERGENCY_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EMERGENCY_FORBIDDEN")
            if (current.status !in setOf(ShelterEmergencyStatus.DRAFT, ShelterEmergencyStatus.ACTIVE)) {
                failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
            }
            val title = input.title.trim()
            val description = input.description.trim()
            if (title.isEmpty() || description.isEmpty()) failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
            validateEvidenceRef(input.evidenceRef)
            validatePetForShelter(store, shelter.id, input.petId)
            val updated = current.copy(
                title = title,
                description = description,
                category = input.category,
                severity = input.severity,
                visibility = input.visibility,
                startsAt = input.startsAt,
                expiresAt = input.expiresAt,
                evidenceRef = input.evidenceRef?.trim()?.takeIf { it.isNotEmpty() },
                petId = input.petId?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis()
            )
            store.emergencies.value = store.emergencies.value.map { if (it.id == updated.id) updated else it }
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun changeEmergencyStatus(
        emergencyId: String,
        status: ShelterEmergencyStatus
    ): Result<ShelterEmergency> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.emergencies.value.find { it.id == emergencyId }
            ?: failM11("SHELTER_EMERGENCY_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EMERGENCY_FORBIDDEN")
        if (current.status == status) return@runCatching current
        if (status == ShelterEmergencyStatus.ACTIVE) {
            requireActiveShelter(store, shelter.id)
            if (current.status != ShelterEmergencyStatus.DRAFT) {
                failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
            }
            if (isEmergencyExpired(current.expiresAt, System.currentTimeMillis(), current.status)) {
                failM11("SHELTER_EMERGENCY_EXPIRED")
            }
        }
        when (status) {
            ShelterEmergencyStatus.RESOLVED -> failM11("SHELTER_EMERGENCY_RESOLUTION_REQUIRED")
            ShelterEmergencyStatus.CANCELLED -> {
                if (current.status !in setOf(
                        ShelterEmergencyStatus.DRAFT,
                        ShelterEmergencyStatus.ACTIVE
                    )
                ) {
                    failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
                }
            }
            ShelterEmergencyStatus.EXPIRED -> {
                if (current.status != ShelterEmergencyStatus.ACTIVE) {
                    failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
                }
            }
            ShelterEmergencyStatus.ACTIVE, ShelterEmergencyStatus.DRAFT -> Unit
            else -> failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
        }
        val updated = current.copy(status = status, updatedAt = System.currentTimeMillis())
        store.emergencies.value = store.emergencies.value.map { if (it.id == updated.id) updated else it }
        store.recordAudit(M11ShelterAuditEvents.SHELTER_EMERGENCY_STATUS_CHANGED, emergencyId)
        if (status == ShelterEmergencyStatus.ACTIVE && updated.severity == ShelterEmergencySeverity.CRITICAL) {
            store.recordM06Hook(M11ShelterNotificationHooks.EMERGENCY_CRITICAL_ACTIVATED)
        }
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun resolveEmergency(emergencyId: String, notes: String): Result<ShelterEmergency> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.emergencies.value.find { it.id == emergencyId }
                ?: failM11("SHELTER_EMERGENCY_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EMERGENCY_FORBIDDEN")
            if (current.status != ShelterEmergencyStatus.ACTIVE) failM11("SHELTER_EMERGENCY_INVALID_TRANSITION")
            val trimmed = notes.trim()
            if (trimmed.isEmpty()) failM11("SHELTER_EMERGENCY_RESOLUTION_REQUIRED")
            val now = System.currentTimeMillis()
            val updated = current.copy(
                status = ShelterEmergencyStatus.RESOLVED,
                resolvedAt = now,
                resolutionNotes = trimmed,
                updatedAt = now
            )
            store.emergencies.value = store.emergencies.value.map { if (it.id == updated.id) updated else it }
            store.recordAudit(M11ShelterAuditEvents.SHELTER_EMERGENCY_RESOLVED, emergencyId)
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun expireIfNeeded(emergencyId: String): Result<ShelterEmergency> = runCatching {
        if (emergencyId.isBlank()) failM11("SHELTER_EMERGENCY_NOT_FOUND")
        val current = store.emergencies.value.find { it.id == emergencyId }
            ?: failM11("SHELTER_EMERGENCY_NOT_FOUND")
        expireEmergencyIfNeededInternal(store, current)
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    private fun canReadEmergencyFor(actor: String, emergency: ShelterEmergency): Boolean {
        val shelter = store.profiles.value.find { it.id == emergency.shelterProfileId }
            ?: return false
        return when (emergency.visibility) {
            ShelterEmergencyVisibility.PUBLIC ->
                emergency.status == ShelterEmergencyStatus.ACTIVE ||
                    store.canView(actor, shelter.organizationId)
            ShelterEmergencyVisibility.INTERNAL ->
                store.canView(actor, shelter.organizationId)
            else -> store.canView(actor, shelter.organizationId)
        }
    }
}

class MockShelterEventRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterEventRepository {

    override fun observePublicEvents(): Flow<List<ShelterEventPublicListing>> =
        store.events.map { list ->
            list.filter {
                it.visibility == ShelterEventVisibility.PUBLIC &&
                    it.status in setOf(ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL)
            }.map { event ->
                event.toPublicListing(shelterDisplayName(store, event.shelterProfileId))
            }
        }

    override fun observeShelterEvents(shelterId: String): Flow<List<ShelterEvent>> =
        store.events.map { list ->
            val actor = actorUserId()
            list.filter { event ->
                event.shelterProfileId == shelterId &&
                    actor != null &&
                    canReadEventFor(actor, event)
            }
        }

    override suspend fun getEvent(id: String): Result<ShelterEvent> = runCatching {
        if (id.isBlank()) failM11("SHELTER_EVENT_NOT_FOUND")
        val event = store.events.value.find { it.id == id } ?: failM11("SHELTER_EVENT_NOT_FOUND")
        val actor = actorUserId()
        if (event.visibility == ShelterEventVisibility.INTERNAL) {
            if (actor == null || !canReadEventFor(actor, event)) {
                failM11("SHELTER_EVENT_FORBIDDEN")
            }
        }
        event
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun createEvent(input: CreateShelterEventInput): Result<ShelterEvent> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        if (input.shelterProfileId.isBlank()) failM11("SHELTER_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == input.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EVENT_FORBIDDEN")
        validateEventFields(input.startsAt, input.endsAt, input.capacity)
        validateEvidenceRef(input.coverAssetRef)
        if (input.publish) requireActiveShelter(store, shelter.id)
        val now = System.currentTimeMillis()
        val status = if (input.publish) ShelterEventStatus.PUBLISHED else ShelterEventStatus.DRAFT
        val row = ShelterEvent(
            id = UUID.randomUUID().toString(),
            shelterProfileId = input.shelterProfileId,
            title = input.title.trim(),
            description = input.description.trim(),
            eventType = input.eventType,
            visibility = input.visibility,
            status = status,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            capacity = input.capacity,
            registeredCount = 0,
            publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
            privateLocationText = input.privateLocationText?.trim()?.takeIf { it.isNotEmpty() },
            coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = actor,
            createdAt = now,
            updatedAt = now
        )
        if (row.title.isEmpty() || row.description.isEmpty()) failM11("SHELTER_EVENT_INVALID")
        store.events.value = listOf(row) + store.events.value
        store.recordAudit(M11ShelterAuditEvents.SHELTER_EVENT_CREATED, row.id)
        if (status == ShelterEventStatus.PUBLISHED) {
            store.recordM06Hook(M11ShelterNotificationHooks.EVENT_PUBLISHED)
        }
        row
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun updateEvent(input: UpdateShelterEventInput): Result<ShelterEvent> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        if (input.eventId.isBlank()) failM11("SHELTER_EVENT_NOT_FOUND")
        val current = store.events.value.find { it.id == input.eventId }
            ?: failM11("SHELTER_EVENT_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EVENT_FORBIDDEN")
        if (current.status !in setOf(ShelterEventStatus.DRAFT, ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL)) {
            failM11("SHELTER_EVENT_INVALID")
        }
        validateEventFields(input.startsAt, input.endsAt, input.capacity)
        validateEvidenceRef(input.coverAssetRef)
        val title = input.title.trim()
        val description = input.description.trim()
        if (title.isEmpty() || description.isEmpty()) failM11("SHELTER_EVENT_INVALID")
        val updated = current.copy(
            title = title,
            description = description,
            eventType = input.eventType,
            visibility = input.visibility,
            startsAt = input.startsAt,
            endsAt = input.endsAt,
            capacity = input.capacity,
            publicLocationText = input.publicLocationText?.trim()?.takeIf { it.isNotEmpty() },
            privateLocationText = input.privateLocationText?.trim()?.takeIf { it.isNotEmpty() },
            coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis()
        )
        store.events.value = store.events.value.map { if (it.id == updated.id) updated else it }
        syncEventRegistrationCount(store, updated.id)
        store.events.value.find { it.id == updated.id } ?: updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun changeEventStatus(
        eventId: String,
        status: ShelterEventStatus
    ): Result<ShelterEvent> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.events.value.find { it.id == eventId }
            ?: failM11("SHELTER_EVENT_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == current.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EVENT_FORBIDDEN")
        if (current.status == status) return@runCatching current
        when (status) {
            ShelterEventStatus.PUBLISHED -> {
                requireActiveShelter(store, shelter.id)
                if (current.status != ShelterEventStatus.DRAFT) {
                    failM11("SHELTER_EVENT_INVALID")
                }
            }
            ShelterEventStatus.COMPLETED -> {
                if (current.status !in setOf(ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL)) {
                    failM11("SHELTER_EVENT_INVALID")
                }
            }
            ShelterEventStatus.CANCELLED -> {
                if (current.status == ShelterEventStatus.COMPLETED) {
                    failM11("SHELTER_EVENT_INVALID")
                }
                cancelActiveRegistrations(store, eventId)
            }
            ShelterEventStatus.FULL -> failM11("SHELTER_EVENT_INVALID")
            else -> failM11("SHELTER_EVENT_INVALID")
        }
        val updated = current.copy(status = status, updatedAt = System.currentTimeMillis())
        store.events.value = store.events.value.map { if (it.id == updated.id) updated else it }
        store.recordAudit(M11ShelterAuditEvents.SHELTER_EVENT_STATUS_CHANGED, eventId)
        when (status) {
            ShelterEventStatus.PUBLISHED -> store.recordM06Hook(M11ShelterNotificationHooks.EVENT_PUBLISHED)
            ShelterEventStatus.CANCELLED -> store.recordM06Hook(M11ShelterNotificationHooks.EVENT_CANCELLED)
            else -> Unit
        }
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun register(eventId: String, notes: String?): Result<ShelterEventRegistration> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val event = store.events.value.find { it.id == eventId }
                ?: failM11("SHELTER_EVENT_NOT_FOUND")
            if (event.status !in setOf(ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL)) {
                failM11("SHELTER_EVENT_INVALID")
            }
            val duplicate = store.eventRegistrations.value.any {
                it.eventId == eventId && it.userId == actor && it.status.isActive
            }
            if (duplicate) failM11("SHELTER_EVENT_ALREADY_REGISTERED")
            val capacity = event.capacity
            val registeredCount = store.eventRegistrations.value.count {
                it.eventId == eventId && it.status == ShelterEventRegistrationStatus.REGISTERED
            }
            val waitlisted = capacity != null && registeredCount >= capacity
            val now = System.currentTimeMillis()
            val row = ShelterEventRegistration(
                id = UUID.randomUUID().toString(),
                eventId = eventId,
                userId = actor,
                status = if (waitlisted) {
                    ShelterEventRegistrationStatus.WAITLISTED
                } else {
                    ShelterEventRegistrationStatus.REGISTERED
                },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                registeredAt = now,
                cancelledAt = null
            )
            store.eventRegistrations.value = listOf(row) + store.eventRegistrations.value
            syncEventRegistrationCount(store, eventId)
            store.recordAudit(M11ShelterAuditEvents.SHELTER_EVENT_REGISTRATION, row.id)
            if (waitlisted) store.recordM06Hook(M11ShelterNotificationHooks.EVENT_WAITLISTED)
            row
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun cancelRegistration(registrationId: String): Result<ShelterEventRegistration> =
        runCatching {
            val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
            val current = store.eventRegistrations.value.find { it.id == registrationId }
                ?: failM11("SHELTER_EVENT_REGISTRATION_NOT_FOUND")
            val event = store.events.value.find { it.id == current.eventId }
                ?: failM11("SHELTER_EVENT_NOT_FOUND")
            val shelter = store.profiles.value.find { it.id == event.shelterProfileId }
                ?: failM11("SHELTER_NOT_FOUND")
            val isManager = store.canManage(actor, shelter.organizationId)
            if (!isManager && current.userId != actor) failM11("SHELTER_EVENT_FORBIDDEN")
            if (!current.status.isActive) return@runCatching current
            val now = System.currentTimeMillis()
            val updated = current.copy(
                status = ShelterEventRegistrationStatus.CANCELLED,
                cancelledAt = now
            )
            store.eventRegistrations.value = store.eventRegistrations.value.map {
                if (it.id == updated.id) updated else it
            }
            syncEventRegistrationCount(store, event.id)
            store.recordAudit(M11ShelterAuditEvents.SHELTER_EVENT_REGISTRATION_CANCELLED, registrationId)
            updated
        }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun markAttendance(
        registrationId: String,
        attended: Boolean
    ): Result<ShelterEventRegistration> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        val current = store.eventRegistrations.value.find { it.id == registrationId }
            ?: failM11("SHELTER_EVENT_REGISTRATION_NOT_FOUND")
        val event = store.events.value.find { it.id == current.eventId }
            ?: failM11("SHELTER_EVENT_NOT_FOUND")
        val shelter = store.profiles.value.find { it.id == event.shelterProfileId }
            ?: failM11("SHELTER_NOT_FOUND")
        if (!store.canManage(actor, shelter.organizationId)) failM11("SHELTER_EVENT_ATTENDANCE_FORBIDDEN")
        if (!current.status.isActive) failM11("SHELTER_EVENT_INVALID")
        val target = if (attended) {
            ShelterEventRegistrationStatus.ATTENDED
        } else {
            ShelterEventRegistrationStatus.NO_SHOW
        }
        val updated = current.copy(status = target)
        store.eventRegistrations.value = store.eventRegistrations.value.map {
            if (it.id == updated.id) updated else it
        }
        store.recordAudit(M11ShelterAuditEvents.SHELTER_EVENT_ATTENDANCE, registrationId)
        updated
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override fun observeRegistrations(eventId: String): Flow<List<ShelterEventRegistration>> =
        store.eventRegistrations.map { list ->
            val actor = actorUserId() ?: return@map emptyList()
            val event = store.events.value.find { it.id == eventId } ?: return@map emptyList()
            val shelter = store.profiles.value.find { it.id == event.shelterProfileId }
                ?: return@map emptyList()
            val isManager = store.canManage(actor, shelter.organizationId)
            list.filter { it.eventId == eventId }
                .filter { isManager || it.userId == actor }
                .sortedByDescending { it.registeredAt }
        }

    private fun validateEventFields(startsAt: Long, endsAt: Long, capacity: Int?) {
        if (endsAt <= startsAt) failM11("SHELTER_EVENT_INVALID")
        if (capacity != null && capacity <= 0) failM11("SHELTER_EVENT_INVALID")
    }

    private fun canReadEventFor(actor: String, event: ShelterEvent): Boolean {
        val shelter = store.profiles.value.find { it.id == event.shelterProfileId }
            ?: return false
        return when (event.visibility) {
            ShelterEventVisibility.PUBLIC ->
                event.status in setOf(ShelterEventStatus.PUBLISHED, ShelterEventStatus.FULL) ||
                    store.canView(actor, shelter.organizationId)
            ShelterEventVisibility.INTERNAL ->
                store.canView(actor, shelter.organizationId)
            else -> store.canView(actor, shelter.organizationId)
        }
    }
}

private fun cancelActiveRegistrations(store: M11ShelterMemoryStore, eventId: String) {
    val now = System.currentTimeMillis()
    store.eventRegistrations.value = store.eventRegistrations.value.map { reg ->
        if (reg.eventId == eventId && reg.status.isActive) {
            reg.copy(status = ShelterEventRegistrationStatus.CANCELLED, cancelledAt = now)
        } else reg
    }
    syncEventRegistrationCount(store, eventId)
}

class MockShelterReportRepository(
    private val actorUserId: () -> String?,
    private val store: M11ShelterMemoryStore
) : ShelterReportRepository {

    override suspend fun getOperationalSummary(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterOperationalSummary> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        validateReportRange(from, to)
        requireShelterForReport(store, actor, shelterId)
        val now = System.currentTimeMillis()
        ShelterOperationalSummary(
            shelterProfileId = shelterId,
            from = from,
            to = to,
            capacity = computeCapacityMetrics(store, shelterId, from, to),
            pets = computePetMetrics(store, shelterId, from, to),
            volunteers = computeVolunteerMetrics(store, shelterId, from, to),
            campaigns = computeCampaignMetrics(store, shelterId, from, to),
            supplies = computeSupplyMetrics(store, shelterId, from, to),
            emergencies = computeEmergencyMetrics(store, shelterId, from, to),
            events = computeEventMetrics(store, shelterId, from, to),
            generatedAt = now
        )
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    override suspend fun getCapacityMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterCapacityMetrics> = metricCall(shelterId, from, to) {
        computeCapacityMetrics(store, shelterId, from, to)
    }

    override suspend fun getPetMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterPetMetrics> = metricCall(shelterId, from, to) {
        computePetMetrics(store, shelterId, from, to)
    }

    override suspend fun getVolunteerMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterVolunteerMetrics> = metricCall(shelterId, from, to) {
        computeVolunteerMetrics(store, shelterId, from, to)
    }

    override suspend fun getCampaignMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterCampaignMetrics> = metricCall(shelterId, from, to) {
        computeCampaignMetrics(store, shelterId, from, to)
    }

    override suspend fun getSupplyMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterSupplyMetrics> = metricCall(shelterId, from, to) {
        computeSupplyMetrics(store, shelterId, from, to)
    }

    override suspend fun getEmergencyMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterEmergencyMetrics> = metricCall(shelterId, from, to) {
        computeEmergencyMetrics(store, shelterId, from, to)
    }

    override suspend fun getEventMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterEventMetrics> = metricCall(shelterId, from, to) {
        computeEventMetrics(store, shelterId, from, to)
    }

    override suspend fun exportOperationalCsv(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterReportExport> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        validateReportRange(from, to)
        requireShelterForReportExport(store, actor, shelterId)
        val summary = getOperationalSummary(shelterId, from, to).getOrElse {
            failM11(M11ShelterErrorMapper.codeOf(it))
        }
        val shelter = store.profiles.value.find { it.id == shelterId }
            ?: failM11("SHELTER_NOT_FOUND")
        val safeName = shelter.displayName
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifEmpty { "refugio" }
        val fileName = "leover_${safeName}_operativo_${from}_${to}.csv"
        val csv = buildString {
            appendLine("metric,value")
            appendLine("shelter_id,${summary.shelterProfileId}")
            appendLine("from,${summary.from}")
            appendLine("to,${summary.to}")
            appendLine("total_capacity,${summary.capacity.totalCapacity}")
            appendLine("current_occupancy,${summary.capacity.currentOccupancy}")
            appendLine("reserved_capacity,${summary.capacity.reservedCapacity}")
            appendLine("free_slots,${summary.capacity.freeSlots}")
            appendLine("pets_active,${summary.pets.activeCount}")
            appendLine("pets_quarantine,${summary.pets.quarantineCount}")
            appendLine("pets_medical_care,${summary.pets.medicalCareCount}")
            appendLine("pets_releases,${summary.pets.releasesCount}")
            appendLine("pets_adoptions,${summary.pets.adoptionsCount}")
            appendLine("volunteers_active,${summary.volunteers.activeCount}")
            appendLine("volunteers_paused,${summary.volunteers.pausedCount}")
            appendLine("volunteers_ended,${summary.volunteers.endedCount}")
            appendLine("campaigns_active,${summary.campaigns.activeCount}")
            appendLine("campaigns_completed,${summary.campaigns.completedCount}")
            appendLine("supply_open_requests,${summary.supplies.openRequestsCount}")
            appendLine("supply_fulfilled_requests,${summary.supplies.fulfilledRequestsCount}")
            appendLine("supply_quantity_received,${summary.supplies.quantityReceivedTotal}")
            appendLine("emergencies_active,${summary.emergencies.activeCount}")
            appendLine("emergencies_critical,${summary.emergencies.criticalCount}")
            appendLine("emergencies_resolved,${summary.emergencies.resolvedCount}")
            appendLine("events_upcoming,${summary.events.upcomingCount}")
            appendLine("events_completed,${summary.events.completedCount}")
            appendLine("event_registrations,${summary.events.registrationsCount}")
        }
        if (csv.isBlank()) failM11("SHELTER_REPORT_EXPORT_FAILED")
        val export = ShelterReportExport(
            csvContent = csv,
            fileName = fileName,
            generatedAt = summary.generatedAt
        )
        store.recordAudit(M11ShelterAuditEvents.SHELTER_REPORT_EXPORTED, shelterId)
        export
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })

    private inline fun <T> metricCall(
        shelterId: String,
        from: Long,
        to: Long,
        block: () -> T
    ): Result<T> = runCatching {
        val actor = actorUserId() ?: failM11("NOT_AUTHENTICATED")
        validateReportRange(from, to)
        requireShelterForReport(store, actor, shelterId)
        block()
    }.fold({ Result.success(it) }, { M11ShelterErrorMapper.failure(it) })
}

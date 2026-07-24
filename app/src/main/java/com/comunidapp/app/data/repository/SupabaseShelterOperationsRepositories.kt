package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterCampaignPublicListing
import com.comunidapp.app.data.model.ShelterCampaignMetrics
import com.comunidapp.app.data.model.ShelterCapacityMetrics
import com.comunidapp.app.data.model.ShelterEmergency
import com.comunidapp.app.data.model.ShelterEmergencyMetrics
import com.comunidapp.app.data.model.ShelterEmergencyPublicListing
import com.comunidapp.app.data.model.ShelterEmergencyStatus
import com.comunidapp.app.data.model.ShelterEvent
import com.comunidapp.app.data.model.ShelterEventMetrics
import com.comunidapp.app.data.model.ShelterEventPublicListing
import com.comunidapp.app.data.model.ShelterEventRegistration
import com.comunidapp.app.data.model.ShelterEventStatus
import com.comunidapp.app.data.model.ShelterOperationalSummary
import com.comunidapp.app.data.model.ShelterPetMetrics
import com.comunidapp.app.data.model.ShelterReportExport
import com.comunidapp.app.data.model.ShelterSupplyMetrics
import com.comunidapp.app.data.model.ShelterVolunteerMetrics
import com.comunidapp.app.data.model.ShelterIntakeType
import com.comunidapp.app.data.model.ShelterPetEndReason
import com.comunidapp.app.data.model.ShelterPetPlacement
import com.comunidapp.app.data.model.ShelterPetPlacementStatus
import com.comunidapp.app.data.model.ShelterProfile
import com.comunidapp.app.data.model.ShelterPublicListing
import com.comunidapp.app.data.model.ShelterStatus
import com.comunidapp.app.data.model.ShelterSupplyContribution
import com.comunidapp.app.data.model.ShelterSupplyRequest
import com.comunidapp.app.data.model.ShelterSupplyRequestPublicListing
import com.comunidapp.app.data.model.ShelterVolunteerAssignment
import com.comunidapp.app.data.model.ShelterVolunteerRole
import com.comunidapp.app.data.model.toPublicListing
import com.comunidapp.app.data.remote.supabase.m11.M11ShelterErrorMapper
import com.comunidapp.app.data.remote.supabase.m11.SupabaseShelterM11RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m11.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SupabaseShelterProfileRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterProfileRepository {
    override fun observePublicShelters(): Flow<List<ShelterPublicListing>> = flow {
        emit(runCatching { remote.listPublic().map { it.toDomain().toPublicListing() } }.getOrElse { emptyList() })
    }

    override fun observeMyShelters(userId: String): Flow<List<ShelterProfile>> = flow {
        emit(runCatching { remote.listMine().map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun getShelterById(id: String): Result<ShelterProfile> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_NOT_FOUND")
        else Result.success(remote.get(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun createShelter(input: CreateShelterProfileInput): Result<ShelterProfile> = try {
        Result.success(
            remote.create(
                buildJsonObject {
                    put("p_organization_id", input.organizationId)
                    put("p_display_name", input.displayName)
                    put("p_total_capacity", input.totalCapacity)
                    putJsonArray("p_accepted_species") {
                        input.acceptedSpecies.forEach { add(JsonPrimitive(it)) }
                    }
                    put("p_description", input.description)
                    put("p_branch_id", input.branchId)
                    put("p_accepts_emergencies", input.acceptsEmergencies)
                    put("p_public_zone_text", input.publicZoneText)
                    put("p_internal_address_ref", input.internalAddressRef)
                    put("p_activate", input.activate)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun updateShelter(input: UpdateShelterProfileInput): Result<ShelterProfile> = try {
        Result.success(
            remote.update(
                buildJsonObject {
                    put("p_shelter_id", input.shelterId)
                    put("p_display_name", input.displayName)
                    put("p_total_capacity", input.totalCapacity)
                    putJsonArray("p_accepted_species") {
                        input.acceptedSpecies.forEach { add(JsonPrimitive(it)) }
                    }
                    put("p_description", input.description)
                    put("p_accepts_emergencies", input.acceptsEmergencies)
                    put("p_public_zone_text", input.publicZoneText)
                    put("p_internal_address_ref", input.internalAddressRef)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun changeStatus(shelterId: String, status: ShelterStatus): Result<ShelterProfile> =
        try {
            Result.success(remote.changeStatus(shelterId, status.name).toDomain())
        } catch (t: Throwable) {
            M11ShelterErrorMapper.failure(t)
        }
}

class SupabaseShelterPetRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterPetRepository {
    override fun observeShelterPets(shelterId: String): Flow<List<ShelterPetPlacement>> = flow {
        emit(runCatching { remote.listPets(shelterId).map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun getPetPlacement(id: String): Result<ShelterPetPlacement> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_PET_NOT_FOUND")
        else Result.success(remote.getPet(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun reserveCapacity(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String?
    ): Result<ShelterPetPlacement> = try {
        Result.success(
            remote.reserve(
                buildJsonObject {
                    put("p_shelter_id", shelterId)
                    put("p_pet_id", petId)
                    put("p_intake_type", intakeType.name)
                    put("p_notes", notes)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun admitPet(
        shelterId: String,
        petId: String,
        intakeType: ShelterIntakeType,
        notes: String?,
        relatedFosterPlacementId: String?
    ): Result<ShelterPetPlacement> = try {
        Result.success(
            remote.admit(
                buildJsonObject {
                    put("p_shelter_id", shelterId)
                    put("p_pet_id", petId)
                    put("p_intake_type", intakeType.name)
                    put("p_notes", notes)
                    put("p_related_foster_placement_id", relatedFosterPlacementId)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun changePlacementStatus(
        placementId: String,
        status: ShelterPetPlacementStatus
    ): Result<ShelterPetPlacement> = try {
        Result.success(remote.changePetStatus(placementId, status.name).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun releasePet(
        placementId: String,
        endReason: ShelterPetEndReason,
        notes: String?
    ): Result<ShelterPetPlacement> = try {
        Result.success(remote.release(placementId, endReason.name, notes).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun onAdoptionFinalized(petId: String): Result<Unit> =
        Result.success(Unit)
}

class SupabaseShelterVolunteerRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterVolunteerRepository {
    override fun observeVolunteers(shelterId: String) = flow {
        emit(runCatching { remote.listVolunteers(shelterId).map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun inviteVolunteer(
        shelterId: String,
        userId: String,
        role: ShelterVolunteerRole,
        notes: String?
    ) = try {
        Result.success(
            remote.invite(
                buildJsonObject {
                    put("p_shelter_id", shelterId)
                    put("p_user_id", userId)
                    put("p_role", role.name)
                    put("p_notes", notes)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun acceptAssignment(assignmentId: String) = try {
        Result.success(remote.acceptVol(assignmentId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun pauseAssignment(assignmentId: String) = try {
        Result.success(remote.pauseVol(assignmentId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun endAssignment(assignmentId: String) = try {
        Result.success(remote.endVol(assignmentId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }
}

class SupabaseShelterCampaignRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterCampaignRepository {
    override fun observePublicCampaigns(): Flow<List<ShelterCampaignPublicListing>> = flow {
        emit(
            runCatching {
                remote.listPublicCampaigns().map { row ->
                    row.toDomain().toPublicListing(row.shelterDisplayName)
                }
            }.getOrElse { emptyList() }
        )
    }

    override fun observeShelterCampaigns(shelterId: String): Flow<List<ShelterCampaign>> = flow {
        emit(runCatching { remote.listShelterCampaigns(shelterId).map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun getCampaign(id: String): Result<ShelterCampaign> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_CAMPAIGN_NOT_FOUND")
        else Result.success(remote.getCampaign(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun createCampaign(input: CreateShelterCampaignInput): Result<ShelterCampaign> = try {
        Result.success(
            remote.createCampaign(
                buildJsonObject {
                    put("p_shelter_profile_id", input.shelterProfileId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_category", input.category.name)
                    put("p_visibility", input.visibility.name)
                    input.startsAt?.let { put("p_starts_at", it) }
                    input.endsAt?.let { put("p_ends_at", it) }
                    put("p_cover_asset_ref", input.coverAssetRef)
                    put("p_activate", input.activate)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun updateCampaign(input: UpdateShelterCampaignInput): Result<ShelterCampaign> = try {
        Result.success(
            remote.updateCampaign(
                buildJsonObject {
                    put("p_campaign_id", input.campaignId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_category", input.category.name)
                    put("p_visibility", input.visibility.name)
                    input.startsAt?.let { put("p_starts_at", it) }
                    input.endsAt?.let { put("p_ends_at", it) }
                    put("p_cover_asset_ref", input.coverAssetRef)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun changeCampaignStatus(
        campaignId: String,
        status: ShelterCampaignStatus
    ): Result<ShelterCampaign> = try {
        Result.success(remote.changeCampaignStatus(campaignId, status.name).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun addCampaignUpdate(
        input: AddShelterCampaignUpdateInput
    ): Result<ShelterCampaignUpdate> = try {
        Result.success(
            remote.addCampaignUpdate(
                buildJsonObject {
                    put("p_campaign_id", input.campaignId)
                    put("p_visibility", input.visibility.name)
                    put("p_message", input.message)
                    put("p_evidence_ref", input.evidenceRef)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override fun observeCampaignUpdates(campaignId: String): Flow<List<ShelterCampaignUpdate>> = flow {
        emit(
            runCatching { remote.listCampaignUpdates(campaignId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

class SupabaseShelterSupplyRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterSupplyRepository {
    override fun observePublicSupplyRequests(): Flow<List<ShelterSupplyRequestPublicListing>> = flow {
        emit(
            runCatching {
                remote.listPublicSupplyRequests().map { row ->
                    row.toDomain().toPublicListing(row.shelterDisplayName)
                }
            }.getOrElse { emptyList() }
        )
    }

    override fun observeShelterSupplyRequests(shelterId: String): Flow<List<ShelterSupplyRequest>> = flow {
        emit(
            runCatching { remote.listShelterSupplyRequests(shelterId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun getSupplyRequest(id: String): Result<ShelterSupplyRequest> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_SUPPLY_REQUEST_NOT_FOUND")
        else Result.success(remote.getSupplyRequest(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun createSupplyRequest(input: CreateSupplyRequestInput): Result<ShelterSupplyRequest> =
        try {
            Result.success(
                remote.createSupplyRequest(
                    buildJsonObject {
                        put("p_shelter_profile_id", input.shelterProfileId)
                        put("p_campaign_id", input.campaignId)
                        put("p_category", input.category.name)
                        put("p_item_name", input.itemName)
                        put("p_description", input.description)
                        put("p_quantity_requested", input.quantityRequested)
                        put("p_unit_text", input.unitText)
                        put("p_priority", input.priority.name)
                        input.expiresAt?.let { put("p_expires_at", it) }
                        put("p_public_notes", input.publicNotes)
                        put("p_internal_notes", input.internalNotes)
                        put("p_publish_open", input.publishOpen)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M11ShelterErrorMapper.failure(t)
        }

    override suspend fun updateSupplyRequest(input: UpdateSupplyRequestInput): Result<ShelterSupplyRequest> =
        try {
            Result.success(
                remote.updateSupplyRequest(
                    buildJsonObject {
                        put("p_request_id", input.requestId)
                        put("p_category", input.category.name)
                        put("p_item_name", input.itemName)
                        put("p_description", input.description)
                        put("p_quantity_requested", input.quantityRequested)
                        put("p_unit_text", input.unitText)
                        put("p_priority", input.priority.name)
                        input.expiresAt?.let { put("p_expires_at", it) }
                        put("p_public_notes", input.publicNotes)
                        put("p_internal_notes", input.internalNotes)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M11ShelterErrorMapper.failure(t)
        }

    override suspend fun cancelSupplyRequest(requestId: String): Result<ShelterSupplyRequest> = try {
        Result.success(remote.cancelSupplyRequest(requestId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun pledgeContribution(
        input: PledgeSupplyContributionInput
    ): Result<ShelterSupplyContribution> = try {
        Result.success(
            remote.pledgeContribution(
                buildJsonObject {
                    put("p_request_id", input.requestId)
                    put("p_quantity_committed", input.quantityCommitted)
                    put("p_contributor_notes", input.contributorNotes)
                    put("p_evidence_ref", input.evidenceRef)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun cancelContribution(contributionId: String): Result<ShelterSupplyContribution> = try {
        Result.success(remote.cancelContribution(contributionId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun confirmContribution(contributionId: String): Result<ShelterSupplyContribution> = try {
        Result.success(remote.confirmContribution(contributionId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun recordReceipt(
        contributionId: String,
        quantityReceived: Int,
        evidenceRef: String?,
        internalReceiptNotes: String?
    ): Result<ShelterSupplyContribution> = try {
        Result.success(
            remote.recordSupplyReceipt(
                buildJsonObject {
                    put("p_contribution_id", contributionId)
                    put("p_quantity_received", quantityReceived)
                    put("p_evidence_ref", evidenceRef)
                    put("p_internal_receipt_notes", internalReceiptNotes)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override fun observeContributions(requestId: String): Flow<List<ShelterSupplyContribution>> = flow {
        emit(
            runCatching { remote.listSupplyContributions(requestId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

class SupabaseShelterEmergencyRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterEmergencyRepository {
    override fun observePublicEmergencies(): Flow<List<ShelterEmergencyPublicListing>> = flow {
        emit(
            runCatching {
                remote.listPublicEmergencies().map { row ->
                    row.toDomain().toPublicListing(row.shelterDisplayName)
                }
            }.getOrElse { emptyList() }
        )
    }

    override fun observeShelterEmergencies(shelterId: String): Flow<List<ShelterEmergency>> = flow {
        emit(
            runCatching { remote.listShelterEmergencies(shelterId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun getEmergency(id: String): Result<ShelterEmergency> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_EMERGENCY_NOT_FOUND")
        else Result.success(remote.getEmergency(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun createEmergency(input: CreateShelterEmergencyInput): Result<ShelterEmergency> = try {
        Result.success(
            remote.createEmergency(
                buildJsonObject {
                    put("p_shelter_profile_id", input.shelterProfileId)
                    put("p_pet_id", input.petId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_category", input.category.name)
                    put("p_severity", input.severity.name)
                    put("p_visibility", input.visibility.name)
                    put("p_starts_at", input.startsAt)
                    input.expiresAt?.let { put("p_expires_at", it) }
                    put("p_evidence_ref", input.evidenceRef)
                    put("p_activate", input.activate)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun updateEmergency(input: UpdateShelterEmergencyInput): Result<ShelterEmergency> = try {
        Result.success(
            remote.updateEmergency(
                buildJsonObject {
                    put("p_emergency_id", input.emergencyId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_category", input.category.name)
                    put("p_severity", input.severity.name)
                    put("p_visibility", input.visibility.name)
                    put("p_starts_at", input.startsAt)
                    input.expiresAt?.let { put("p_expires_at", it) }
                    put("p_evidence_ref", input.evidenceRef)
                    put("p_pet_id", input.petId)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun changeEmergencyStatus(
        emergencyId: String,
        status: ShelterEmergencyStatus
    ): Result<ShelterEmergency> = try {
        Result.success(remote.changeEmergencyStatus(emergencyId, status.name).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun resolveEmergency(emergencyId: String, notes: String): Result<ShelterEmergency> = try {
        Result.success(remote.resolveEmergency(emergencyId, notes).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun expireIfNeeded(emergencyId: String): Result<ShelterEmergency> =
        getEmergency(emergencyId)
}

class SupabaseShelterEventRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterEventRepository {
    override fun observePublicEvents(): Flow<List<ShelterEventPublicListing>> = flow {
        emit(
            runCatching {
                remote.listPublicEvents().map { row ->
                    row.toDomain().toPublicListing(row.shelterDisplayName)
                }
            }.getOrElse { emptyList() }
        )
    }

    override fun observeShelterEvents(shelterId: String): Flow<List<ShelterEvent>> = flow {
        emit(
            runCatching { remote.listShelterEvents(shelterId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun getEvent(id: String): Result<ShelterEvent> = try {
        if (id.isBlank()) M11ShelterErrorMapper.fail("SHELTER_EVENT_NOT_FOUND")
        else Result.success(remote.getEvent(id).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun createEvent(input: CreateShelterEventInput): Result<ShelterEvent> = try {
        Result.success(
            remote.createEvent(
                buildJsonObject {
                    put("p_shelter_profile_id", input.shelterProfileId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_event_type", input.eventType.name)
                    put("p_visibility", input.visibility.name)
                    put("p_starts_at", input.startsAt)
                    put("p_ends_at", input.endsAt)
                    put("p_capacity", input.capacity)
                    put("p_public_location_text", input.publicLocationText)
                    put("p_private_location_text", input.privateLocationText)
                    put("p_cover_asset_ref", input.coverAssetRef)
                    put("p_publish", input.publish)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun updateEvent(input: UpdateShelterEventInput): Result<ShelterEvent> = try {
        Result.success(
            remote.updateEvent(
                buildJsonObject {
                    put("p_event_id", input.eventId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_event_type", input.eventType.name)
                    put("p_visibility", input.visibility.name)
                    put("p_starts_at", input.startsAt)
                    put("p_ends_at", input.endsAt)
                    put("p_capacity", input.capacity)
                    put("p_public_location_text", input.publicLocationText)
                    put("p_private_location_text", input.privateLocationText)
                    put("p_cover_asset_ref", input.coverAssetRef)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun changeEventStatus(
        eventId: String,
        status: ShelterEventStatus
    ): Result<ShelterEvent> = try {
        Result.success(remote.changeEventStatus(eventId, status.name).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun register(eventId: String, notes: String?): Result<ShelterEventRegistration> = try {
        Result.success(
            remote.registerEvent(
                buildJsonObject {
                    put("p_event_id", eventId)
                    put("p_notes", notes)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun cancelRegistration(registrationId: String): Result<ShelterEventRegistration> = try {
        Result.success(remote.cancelEventRegistration(registrationId).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun markAttendance(
        registrationId: String,
        attended: Boolean
    ): Result<ShelterEventRegistration> = try {
        Result.success(remote.markEventAttendance(registrationId, attended).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override fun observeRegistrations(eventId: String): Flow<List<ShelterEventRegistration>> = flow {
        emit(
            runCatching { remote.listEventRegistrations(eventId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

class SupabaseShelterReportRepository(
    private val remote: SupabaseShelterM11RemoteDataSource = SupabaseShelterM11RemoteDataSource()
) : ShelterReportRepository {
    override suspend fun getOperationalSummary(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterOperationalSummary> = try {
        if (shelterId.isBlank()) M11ShelterErrorMapper.fail("SHELTER_NOT_FOUND")
        else if (from > to) M11ShelterErrorMapper.fail("SHELTER_REPORT_INVALID_RANGE")
        else Result.success(remote.getOperationalSummary(shelterId, from, to).toDomain())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    override suspend fun getCapacityMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterCapacityMetrics> = metricCall(shelterId, from, to) {
        remote.getCapacityMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getPetMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterPetMetrics> = metricCall(shelterId, from, to) {
        remote.getPetMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getVolunteerMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterVolunteerMetrics> = metricCall(shelterId, from, to) {
        remote.getVolunteerMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getCampaignMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterCampaignMetrics> = metricCall(shelterId, from, to) {
        remote.getCampaignMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getSupplyMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterSupplyMetrics> = metricCall(shelterId, from, to) {
        remote.getSupplyMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getEmergencyMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterEmergencyMetrics> = metricCall(shelterId, from, to) {
        remote.getEmergencyMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun getEventMetrics(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterEventMetrics> = metricCall(shelterId, from, to) {
        remote.getEventMetrics(shelterId, from, to).toDomain()
    }

    override suspend fun exportOperationalCsv(
        shelterId: String,
        from: Long,
        to: Long
    ): Result<ShelterReportExport> {
        return try {
            if (shelterId.isBlank()) return M11ShelterErrorMapper.fail("SHELTER_NOT_FOUND")
            if (from > to) return M11ShelterErrorMapper.fail("SHELTER_REPORT_INVALID_RANGE")
            val summary = getOperationalSummary(shelterId, from, to)
                .getOrElse { return M11ShelterErrorMapper.failure(it) }
            val fileName = "leover_refugio_${shelterId}_operativo_${from}_${to}.csv"
            val csv = buildOperationalCsv(summary)
            if (csv.isBlank()) {
                M11ShelterErrorMapper.fail("SHELTER_REPORT_EXPORT_FAILED")
            } else {
                Result.success(
                    ShelterReportExport(
                        csvContent = csv,
                        fileName = fileName,
                        generatedAt = summary.generatedAt
                    )
                )
            }
        } catch (t: Throwable) {
            M11ShelterErrorMapper.failure(t)
        }
    }

    private suspend inline fun <T> metricCall(
        shelterId: String,
        from: Long,
        to: Long,
        block: suspend () -> T
    ): Result<T> = try {
        if (shelterId.isBlank()) M11ShelterErrorMapper.fail("SHELTER_NOT_FOUND")
        else if (from > to) M11ShelterErrorMapper.fail("SHELTER_REPORT_INVALID_RANGE")
        else Result.success(block())
    } catch (t: Throwable) {
        M11ShelterErrorMapper.failure(t)
    }

    private fun buildOperationalCsv(summary: ShelterOperationalSummary): String = buildString {
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
}

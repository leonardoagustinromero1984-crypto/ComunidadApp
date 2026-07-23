package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.ShelterCampaign
import com.comunidapp.app.data.model.ShelterCampaignStatus
import com.comunidapp.app.data.model.ShelterCampaignUpdate
import com.comunidapp.app.data.model.ShelterCampaignPublicListing
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

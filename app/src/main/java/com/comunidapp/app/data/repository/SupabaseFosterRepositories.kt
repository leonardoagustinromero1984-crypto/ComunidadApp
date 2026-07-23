package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.remote.supabase.m10.SupabaseFosterM10RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m10.toContributionDomain
import com.comunidapp.app.data.remote.supabase.m10.toDomain
import com.comunidapp.app.data.remote.supabase.m10.toEvolutionDomain
import com.comunidapp.app.data.remote.supabase.m10.toExpenseDomain
import com.comunidapp.app.data.remote.supabase.m10.toHelpDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class SupabaseFosterHomeRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterHomeRepository {

    override fun observeAvailableFosterHomes(): Flow<List<FosterHomePublicListing>> = flow {
        emit(
            runCatching { remote.listAvailableHomes().map { it.toDomain().toPublicListing() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeMyFosterHome(ownerUserId: String): Flow<FosterHomeProfile?> = flow {
        emit(runCatching { remote.getMyHome().toDomain() }.getOrNull())
    }

    override suspend fun getFosterHomeById(id: String): Result<FosterHomeProfile> = try {
        if (id.isBlank()) M10FosterErrorMapper.fail("FOSTER_HOME_NOT_FOUND")
        else Result.success(remote.getHome(id).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun getPublicFosterHomeById(id: String): Result<FosterHomePublicListing> =
        getFosterHomeById(id).map { it.toPublicListing() }

    override suspend fun createFosterHome(input: CreateFosterHomeInput): Result<FosterHomeProfile> =
        try {
            Result.success(
                remote.createHome(
                    buildJsonObject {
                        put("p_display_name", input.displayName)
                        put("p_description", input.description)
                        put("p_total_capacity", input.totalCapacity)
                        putJsonArray("p_accepted_species") {
                            input.acceptedSpecies.forEach { add(JsonPrimitive(it)) }
                        }
                        putJsonArray("p_accepted_sizes") {
                            input.acceptedSizes.forEach { add(JsonPrimitive(it)) }
                        }
                        put("p_accepts_special_needs", input.acceptsSpecialNeeds)
                        put("p_accepts_emergencies", input.acceptsEmergencies)
                        put("p_has_other_animals", input.hasOtherAnimals)
                        put("p_observations", input.observations)
                        put("p_zone_text", input.zoneText)
                        put("p_public_location_text", input.publicLocationText)
                        put("p_private_address_text", input.privateAddressText)
                        put("p_activate", input.activate)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M10FosterErrorMapper.failure(t)
        }

    override suspend fun updateFosterHome(input: UpdateFosterHomeInput): Result<FosterHomeProfile> =
        try {
            Result.success(
                remote.updateHome(
                    buildJsonObject {
                        put("p_home_id", input.homeId)
                        put("p_display_name", input.displayName)
                        put("p_description", input.description)
                        put("p_total_capacity", input.totalCapacity)
                        putJsonArray("p_accepted_species") {
                            input.acceptedSpecies.forEach { add(JsonPrimitive(it)) }
                        }
                        putJsonArray("p_accepted_sizes") {
                            input.acceptedSizes.forEach { add(JsonPrimitive(it)) }
                        }
                        put("p_accepts_special_needs", input.acceptsSpecialNeeds)
                        put("p_accepts_emergencies", input.acceptsEmergencies)
                        put("p_has_other_animals", input.hasOtherAnimals)
                        put("p_observations", input.observations)
                        put("p_zone_text", input.zoneText)
                        put("p_public_location_text", input.publicLocationText)
                        put("p_private_address_text", input.privateAddressText)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M10FosterErrorMapper.failure(t)
        }

    override suspend fun changeAvailability(
        homeId: String,
        availability: FosterAvailabilityStatus
    ): Result<FosterHomeProfile> = try {
        Result.success(remote.changeAvailability(homeId, availability.name).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun setHomeStatus(
        homeId: String,
        status: FosterHomeStatus
    ): Result<FosterHomeProfile> = try {
        Result.success(remote.setHomeStatus(homeId, status.name).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

class SupabaseFosterRequestRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterRequestRepository {

    override fun observeSentRequests(userId: String): Flow<List<FosterHomeRequest>> = flow {
        emit(runCatching { remote.listSent().map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override fun observeReceivedRequests(ownerUserId: String): Flow<List<FosterHomeRequest>> = flow {
        emit(runCatching { remote.listReceived().map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun getRequestById(id: String): Result<FosterHomeRequest> = try {
        if (id.isBlank()) M10FosterErrorMapper.fail("FOSTER_REQUEST_NOT_FOUND")
        else Result.success(remote.getRequest(id).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun submitRequest(input: SubmitFosterRequestInput): Result<FosterHomeRequest> =
        try {
            Result.success(
                remote.submitRequest(
                    buildJsonObject {
                        put("p_foster_home_id", input.fosterHomeId)
                        put("p_pet_id", input.petId)
                        put("p_message", input.message)
                        put("p_urgency", input.urgency.name)
                        put("p_special_needs", input.specialNeeds)
                        put("p_requester_organization_id", input.requesterOrganizationId)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M10FosterErrorMapper.failure(t)
        }

    override suspend fun cancelRequest(requestId: String): Result<FosterHomeRequest> = try {
        Result.success(remote.cancelRequest(requestId).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun markUnderReview(requestId: String): Result<FosterHomeRequest> = try {
        Result.success(remote.markUnderReview(requestId).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun acceptRequest(requestId: String): Result<FosterHomeRequest> = try {
        Result.success(remote.acceptRequest(requestId).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun rejectRequest(
        requestId: String,
        reason: String?
    ): Result<FosterHomeRequest> = try {
        Result.success(remote.rejectRequest(requestId, reason).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

class SupabaseFosterPlacementRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterPlacementRepository {

    override fun observeActivePlacementsForHome(homeId: String): Flow<List<FosterPlacement>> = flow {
        emit(
            runCatching { remote.listActivePlacements(homeId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeActivePlacementsForUser(userId: String): Flow<List<FosterPlacement>> = flow {
        emit(
            runCatching { remote.listActivePlacements(null).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun getPlacementById(id: String): Result<FosterPlacement> = try {
        if (id.isBlank()) M10FosterErrorMapper.fail("FOSTER_PLACEMENT_NOT_FOUND")
        else Result.success(remote.getPlacement(id).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun startPlacement(
        requestId: String,
        initialNotes: String?
    ): Result<FosterPlacement> = try {
        Result.success(remote.startPlacement(requestId, initialNotes).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override fun observePlacementHistory(userId: String): Flow<List<FosterPlacement>> = flow {
        emit(runCatching { remote.listHistory().map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun completePlacement(
        placementId: String,
        reason: com.comunidapp.app.data.model.FosterPlacementEndReason,
        notes: String?
    ): Result<FosterPlacement> = try {
        Result.success(remote.completePlacement(placementId, reason.name, notes).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun cancelReservedPlacement(
        placementId: String,
        reason: String?
    ): Result<FosterPlacement> = try {
        Result.success(remote.cancelPlacement(placementId, reason).toDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

class SupabaseFosterExpenseRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterExpenseRepository {
    override fun observeExpenses(placementId: String): Flow<List<com.comunidapp.app.data.model.FosterExpense>> =
        flow {
            emit(
                runCatching { remote.listExpenses(placementId).map { it.toExpenseDomain() } }
                    .getOrElse { emptyList() }
            )
        }

    override suspend fun addExpense(
        placementId: String,
        category: com.comunidapp.app.data.model.FosterExpenseCategory,
        description: String,
        amountMinor: Long,
        currency: String,
        occurredAt: Long,
        receiptRef: String?
    ): Result<com.comunidapp.app.data.model.FosterExpense> = try {
        Result.success(
            remote.addExpense(
                buildJsonObject {
                    put("p_placement_id", placementId)
                    put("p_category", category.name)
                    put("p_description", description)
                    put("p_amount_minor", amountMinor)
                    put("p_currency", currency)
                    put("p_occurred_at", java.time.Instant.ofEpochMilli(occurredAt).toString())
                    put("p_receipt_ref", receiptRef)
                }
            ).toExpenseDomain()
        )
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

class SupabaseFosterEvolutionRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterEvolutionRepository {
    override fun observeEvolution(placementId: String): Flow<List<com.comunidapp.app.data.model.FosterEvolutionEntry>> =
        flow {
            emit(
                runCatching { remote.listEvolution(placementId).map { it.toEvolutionDomain() } }
                    .getOrElse { emptyList() }
            )
        }

    override suspend fun addEvolution(
        placementId: String,
        title: String,
        description: String,
        healthStatus: com.comunidapp.app.data.model.FosterHealthStatus,
        weightGrams: Int?,
        occurredAt: Long,
        mediaRefs: List<String>,
        visibility: com.comunidapp.app.data.model.FosterEvolutionVisibility
    ): Result<com.comunidapp.app.data.model.FosterEvolutionEntry> = try {
        Result.success(
            remote.addEvolution(
                buildJsonObject {
                    put("p_placement_id", placementId)
                    put("p_title", title)
                    put("p_description", description)
                    put("p_health_status", healthStatus.name)
                    put("p_weight_grams", weightGrams)
                    put("p_occurred_at", java.time.Instant.ofEpochMilli(occurredAt).toString())
                    put("p_visibility", visibility.name)
                    putJsonArray("p_media_refs") {
                        mediaRefs.forEach { add(JsonPrimitive(it)) }
                    }
                }
            ).toEvolutionDomain()
        )
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

class SupabaseFosterHelpRepository(
    private val remote: SupabaseFosterM10RemoteDataSource = SupabaseFosterM10RemoteDataSource()
) : FosterHelpRepository {
    override fun observeHelpRequests(placementId: String) = flow {
        emit(
            runCatching { remote.listHelp(placementId).map { it.toHelpDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeContributions(helpRequestId: String) = flow {
        emit(emptyList<com.comunidapp.app.data.model.FosterHelpContribution>())
    }

    override suspend fun getHelpRequest(id: String): Result<com.comunidapp.app.data.model.FosterHelpRequest> =
        try {
            if (id.isBlank()) M10FosterErrorMapper.fail("FOSTER_HELP_REQUEST_NOT_FOUND")
            else Result.success(remote.getHelp(id).toHelpDomain())
        } catch (t: Throwable) {
            M10FosterErrorMapper.failure(t)
        }

    override suspend fun createHelpRequest(
        placementId: String,
        type: com.comunidapp.app.data.model.FosterHelpType,
        title: String,
        description: String,
        targetAmountMinor: Long?,
        currency: String?,
        quantityNeeded: Int?,
        urgency: com.comunidapp.app.data.model.FosterUrgency
    ) = try {
        Result.success(
            remote.createHelp(
                buildJsonObject {
                    put("p_placement_id", placementId)
                    put("p_type", type.name)
                    put("p_title", title)
                    put("p_description", description)
                    put("p_target_amount_minor", targetAmountMinor)
                    put("p_currency", currency)
                    put("p_quantity_needed", quantityNeeded)
                    put("p_urgency", urgency.name)
                }
            ).toHelpDomain()
        )
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun changeHelpRequestStatus(
        helpRequestId: String,
        status: com.comunidapp.app.data.model.FosterHelpStatus
    ) = try {
        Result.success(remote.updateHelpStatus(helpRequestId, status.name).toHelpDomain())
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }

    override suspend fun recordContribution(
        helpRequestId: String,
        description: String,
        amountMinor: Long?,
        quantity: Int?,
        status: com.comunidapp.app.data.model.FosterContributionStatus
    ) = try {
        Result.success(
            remote.recordContribution(
                buildJsonObject {
                    put("p_help_request_id", helpRequestId)
                    put("p_description", description)
                    put("p_amount_minor", amountMinor)
                    put("p_quantity", quantity)
                    put("p_status", status.name)
                }
            ).toContributionDomain()
        )
    } catch (t: Throwable) {
        M10FosterErrorMapper.failure(t)
    }
}

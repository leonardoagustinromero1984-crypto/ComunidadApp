package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.FosterAvailabilityStatus
import com.comunidapp.app.data.model.FosterHomeProfile
import com.comunidapp.app.data.model.FosterHomePublicListing
import com.comunidapp.app.data.model.FosterHomeRequest
import com.comunidapp.app.data.model.FosterHomeStatus
import com.comunidapp.app.data.model.FosterPlacement
import com.comunidapp.app.data.remote.supabase.m10.M10FosterErrorMapper
import com.comunidapp.app.data.remote.supabase.m10.SupabaseFosterM10RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m10.toDomain
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
}

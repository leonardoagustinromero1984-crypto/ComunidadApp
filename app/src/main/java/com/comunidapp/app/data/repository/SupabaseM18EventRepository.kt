package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.CreateM18EventInput
import com.comunidapp.app.data.model.M18CommunityEvent
import com.comunidapp.app.data.model.M18EventCapacitySummary
import com.comunidapp.app.data.model.M18EventRegistration
import com.comunidapp.app.data.model.M18EventReminder
import com.comunidapp.app.data.model.M18EventOperationsSummary
import com.comunidapp.app.data.model.M18EventParticipantItem
import com.comunidapp.app.data.model.M18EventSearchFilter
import com.comunidapp.app.data.model.M18EventStatus
import com.comunidapp.app.data.model.M18PublicEvent
import com.comunidapp.app.data.model.M18PublicRegistrationStats
import com.comunidapp.app.data.model.M18RegistrationStatus
import com.comunidapp.app.data.model.UpdateM18EventCapacityInput
import com.comunidapp.app.data.model.UpdateM18EventDetailsInput
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.data.remote.supabase.m18.M18EventErrorMapper
import com.comunidapp.app.data.remote.supabase.m18.SupabaseM18RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m18.toM18CommunityEvent
import com.comunidapp.app.data.remote.supabase.m18.toM18EventCapacitySummary
import com.comunidapp.app.data.remote.supabase.m18.toM18EventRegistration
import com.comunidapp.app.data.remote.supabase.m18.toM18PublicEvent
import com.comunidapp.app.data.remote.supabase.m18.toM18PublicRegistrationStats
import com.comunidapp.app.domain.m18.M18EventOperationsService
import com.comunidapp.app.domain.organization.authorization.OrganizationPermissionCode
import com.comunidapp.app.domain.organization.OrganizationId
import com.comunidapp.app.domain.user.AccountStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseM18EventRepository(
    private val remote: SupabaseM18RemoteDataSource = SupabaseM18RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M18EventRepository {

    override fun observeEventById(eventId: String): Flow<M18CommunityEvent?> = flow {
        emit(getEventInternal(eventId).getOrNull())
    }

    override fun observeEventsForOrganization(organizationId: String): Flow<List<M18CommunityEvent>> =
        flow {
            emit(
                runCatching {
                    remote.listOrgEvents(organizationId).map { it.toM18CommunityEvent() }
                }.getOrElse { emptyList() }
            )
        }

    override suspend fun searchPublicEvents(filter: M18EventSearchFilter): Result<List<M18PublicEvent>> =
        try {
            val list = remote.listPublic(
                buildJsonObject {
                    put("p_query", filter.query.takeIf { it.isNotBlank() })
                    put("p_type", filter.type?.name)
                    put("p_organization_id", filter.organizationId)
                    put("p_active_only", filter.activeOnly)
                    put("p_completed_only", filter.completedOnly)
                    put("p_with_open_spots_only", filter.withOpenSpotsOnly)
                    put("p_upcoming_only", filter.upcomingOnly)
                }
            ).map { it.toM18PublicEvent() }
            Result.success(list)
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun getPublicEventById(eventId: String): Result<M18PublicEvent> = try {
        if (eventId.isBlank()) M18EventErrorMapper.fail("M18_EVENT_NOT_FOUND")
        else Result.success(remote.getPublic(eventId).toM18PublicEvent())
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun createEvent(input: CreateM18EventInput): Result<M18CommunityEvent> = try {
        Result.success(
            remote.createEvent(
                buildJsonObject {
                    put("p_organization_id", input.organizationId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_event_type", input.eventType.name)
                    put("p_max_capacity", input.maxCapacity)
                    put("p_waitlist_enabled", input.waitlistEnabled)
                    put("p_venue_name", input.venueName)
                    put("p_pet_id", input.reference.petId)
                    put("p_pet_public_name", input.reference.petPublicName)
                    put("p_shelter_profile_id", input.reference.shelterProfileId)
                    put("p_shelter_public_name", input.reference.shelterPublicName)
                    put("p_public_location_text", input.reference.publicLocationText)
                    put("p_cover_image_ref", input.coverImageRef)
                    put(
                        "p_starts_at",
                        java.time.Instant.ofEpochMilli(input.startsAt).toString()
                    )
                    put(
                        "p_ends_at",
                        java.time.Instant.ofEpochMilli(input.endsAt).toString()
                    )
                    input.checkInOpensAt?.let {
                        put("p_check_in_opens_at", java.time.Instant.ofEpochMilli(it).toString())
                    }
                    input.checkInClosesAt?.let {
                        put("p_check_in_closes_at", java.time.Instant.ofEpochMilli(it).toString())
                    }
                }
            ).toM18CommunityEvent()
        )
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun updateEventDetails(
        input: UpdateM18EventDetailsInput
    ): Result<M18CommunityEvent> = try {
        Result.success(
            remote.updateEventDetails(
                buildJsonObject {
                    put("p_event_id", input.eventId)
                    put("p_title", input.title)
                    put("p_description", input.description)
                    put("p_event_type", input.eventType.name)
                    put("p_venue_name", input.venueName)
                    put("p_pet_id", input.reference.petId)
                    put("p_pet_public_name", input.reference.petPublicName)
                    put("p_shelter_profile_id", input.reference.shelterProfileId)
                    put("p_shelter_public_name", input.reference.shelterPublicName)
                    put("p_public_location_text", input.reference.publicLocationText)
                    put(
                        "p_starts_at",
                        java.time.Instant.ofEpochMilli(input.startsAt).toString()
                    )
                    put(
                        "p_ends_at",
                        java.time.Instant.ofEpochMilli(input.endsAt).toString()
                    )
                }
            ).toM18CommunityEvent()
        )
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun updateEventCapacity(
        input: UpdateM18EventCapacityInput
    ): Result<M18CommunityEvent> = try {
        Result.success(
            remote.updateEventCapacity(
                buildJsonObject {
                    put("p_event_id", input.eventId)
                    put("p_max_capacity", input.maxCapacity)
                    put("p_waitlist_enabled", input.waitlistEnabled)
                }
            ).toM18CommunityEvent()
        )
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun publishEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.PUBLISHED)

    override suspend fun pauseEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.PAUSED)

    override suspend fun completeEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.COMPLETED)

    override suspend fun cancelEvent(eventId: String): Result<M18CommunityEvent> =
        transition(eventId, M18EventStatus.CANCELLED)

    override suspend fun observeCapacitySummary(eventId: String): Result<M18EventCapacitySummary> =
        try {
            Result.success(remote.getCapacitySummary(eventId).toM18EventCapacitySummary())
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun observePublicRegistrationStats(
        eventId: String
    ): Result<M18PublicRegistrationStats> = try {
        Result.success(remote.getPublicRegistrationStats(eventId).toM18PublicRegistrationStats())
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun registerForEvent(eventId: String): Result<M18EventRegistration> = try {
        Result.success(remote.registerForEvent(eventId).toM18EventRegistration())
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun cancelRegistration(eventId: String): Result<M18EventRegistration> = try {
        Result.success(remote.cancelRegistration(eventId).toM18EventRegistration())
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun checkInRegistration(registrationId: String): Result<M18EventRegistration> =
        try {
            Result.success(remote.checkInRegistration(registrationId).toM18EventRegistration())
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun scheduleReminder(eventId: String): Result<M18EventReminder> =
        M18EventErrorMapper.fail("M18_NOTIFICATION_INFRASTRUCTURE_UNAVAILABLE")

    override suspend fun refreshEvent(eventId: String): Result<M18CommunityEvent> =
        getEventInternal(eventId)

    override suspend fun canManageOrganization(organizationId: String): Boolean {
        val user = AuthProvider.repository.getCurrentUser() ?: return false
        val accountStatus = runCatching { AccountStatus.valueOf(user.accountStatus) }
            .getOrDefault(AccountStatus.ACTIVE)
        return runCatching {
            DataProvider.organizationPermissionRepository.hasPermission(
                organizationId = OrganizationId(organizationId),
                userId = user.id,
                accountStatus = accountStatus,
                permission = OrganizationPermissionCode.EVENT_MANAGE
            )
        }.getOrDefault(false)
    }

    override suspend fun isOrganizationEligible(organizationId: String): Boolean =
        runCatching { remote.isOrganizationEligible(organizationId) }.getOrDefault(false)

    override suspend fun getMyRegistration(eventId: String): M18EventRegistration? =
        runCatching {
            remote.getMyRegistration(eventId)?.toM18EventRegistration()
        }.getOrNull()

    override suspend fun listRegistrationsForManage(
        eventId: String
    ): Result<List<M18EventRegistration>> = try {
        Result.success(
            remote.listRegistrationsForManage(eventId).map { it.toM18EventRegistration() }
        )
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    override suspend fun observeOperationsSummary(eventId: String): Result<M18EventOperationsSummary> =
        try {
            val event = remote.getEvent(eventId).toM18CommunityEvent()
            val regs = remote.listRegistrationsForManage(eventId).map { it.toM18EventRegistration() }
            Result.success(M18EventOperationsService.buildOperationsSummary(event, regs))
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun listParticipantItems(eventId: String): Result<List<M18EventParticipantItem>> =
        try {
            val event = remote.getEvent(eventId).toM18CommunityEvent()
            val items = remote.listRegistrationsForManage(eventId).map { dto ->
                M18EventOperationsService.toParticipantItem(dto.toM18EventRegistration(), event)
            }
            Result.success(items)
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun promoteNextWaitlisted(eventId: String): Result<M18EventRegistration?> =
        try {
            val promoted = remote.promoteNextWaitlisted(eventId)?.toM18EventRegistration()
            Result.success(promoted)
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun markAttendance(registrationId: String): Result<M18EventRegistration> =
        try {
            Result.success(remote.markAttendance(registrationId).toM18EventRegistration())
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun markNoShow(registrationId: String): Result<M18EventRegistration> =
        try {
            Result.success(remote.markNoShow(registrationId).toM18EventRegistration())
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }

    override suspend fun refreshOperations(eventId: String): Result<M18EventOperationsSummary> =
        observeOperationsSummary(eventId)

    override fun observeRegistrationForCurrentUser(eventId: String): Flow<M18RegistrationStatus?> =
        flow {
            emit(getMyRegistration(eventId)?.status)
        }

    private suspend fun getEventInternal(eventId: String): Result<M18CommunityEvent> = try {
        if (eventId.isBlank()) M18EventErrorMapper.fail("M18_EVENT_NOT_FOUND")
        else Result.success(remote.getEvent(eventId).toM18CommunityEvent())
    } catch (t: Throwable) {
        M18EventErrorMapper.failure(t)
    }

    private suspend fun transition(eventId: String, status: M18EventStatus): Result<M18CommunityEvent> =
        try {
            Result.success(remote.transitionEvent(eventId, status.name).toM18CommunityEvent())
        } catch (t: Throwable) {
            M18EventErrorMapper.failure(t)
        }
}

package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.VeterinaryAppointment
import com.comunidapp.app.data.model.VeterinaryAppointmentSlot
import com.comunidapp.app.data.model.VeterinaryAppointmentStatusHistory
import com.comunidapp.app.data.model.VeterinaryAvailabilityException
import com.comunidapp.app.data.model.VeterinaryAvailabilityRule
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryDirectoryFilter
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryPublicListing
import com.comunidapp.app.data.model.VeterinaryScheduleSettings
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryException
import com.comunidapp.app.data.remote.supabase.m12.SupabaseVeterinaryM12RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m12.toDomain
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private fun VeterinaryClinicProfile.asPublicListing(
    pros: List<VeterinaryProfessional>,
    svcs: List<VeterinaryService>
): VeterinaryPublicListing = VeterinaryPublicListing(
    id = id,
    organizationId = organizationId,
    displayName = displayName,
    description = description,
    publicZoneText = publicZoneText,
    verificationStatus = verificationStatus,
    specialties = pros.flatMap { it.specialties }.toSet(),
    serviceCategories = svcs.filter { it.active }.map { it.category }.toSet(),
    offersEmergencyCare = offersEmergencyCare,
    isOpen24Hours = isOpen24Hours,
    logoAssetRef = logoAssetRef,
    status = status
)

class SupabaseVeterinaryClinicRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryClinicRepository, VeterinaryClinicLifecycle {

    override fun observeManagedClinics(): Flow<List<VeterinaryClinicProfile>> = flow {
        emit(runCatching { remote.listManagedClinics().map { it.toDomain() } }.getOrElse { emptyList() })
    }

    override suspend fun getManagedClinic(clinicId: String): Result<VeterinaryClinicProfile> = try {
        if (clinicId.isBlank()) M12VeterinaryErrorMapper.fail("VETERINARY_CLINIC_NOT_FOUND")
        else Result.success(remote.getManagedClinic(clinicId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun createLocalDraft(input: CreateVeterinaryClinicDraftInput): Result<VeterinaryClinicProfile> =
        try {
            Result.success(
                remote.createClinicDraft(
                    buildJsonObject {
                        put("p_organization_id", input.organizationId)
                        put("p_display_name", input.displayName)
                        put("p_public_zone_text", input.publicZoneText)
                        put("p_branch_id", input.branchId)
                        put("p_description", input.description)
                        put("p_public_address_text", input.publicAddressText)
                        put("p_public_phone", input.publicPhone)
                        put("p_public_email", input.publicEmail)
                        put("p_website_url", input.websiteUrl)
                        put("p_logo_asset_ref", input.logoAssetRef)
                        put("p_cover_asset_ref", input.coverAssetRef)
                        put("p_offers_emergency_care", input.offersEmergencyCare)
                        put("p_is_open_24_hours", input.isOpen24Hours)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M12VeterinaryErrorMapper.failure(t)
        }

    override suspend fun updateLocalDraft(input: UpdateVeterinaryClinicDraftInput): Result<VeterinaryClinicProfile> =
        try {
            Result.success(
                remote.updateClinicProfile(
                    buildJsonObject {
                        put("p_clinic_id", input.clinicId)
                        put("p_display_name", input.displayName)
                        put("p_public_zone_text", input.publicZoneText)
                        put("p_description", input.description)
                        put("p_public_address_text", input.publicAddressText)
                        put("p_public_phone", input.publicPhone)
                        put("p_public_email", input.publicEmail)
                        put("p_website_url", input.websiteUrl)
                        put("p_logo_asset_ref", input.logoAssetRef)
                        put("p_cover_asset_ref", input.coverAssetRef)
                        put("p_offers_emergency_care", input.offersEmergencyCare)
                        put("p_is_open_24_hours", input.isOpen24Hours)
                    }
                ).toDomain()
            )
        } catch (t: Throwable) {
            M12VeterinaryErrorMapper.failure(t)
        }

    override fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> = flow {
        emit(
            runCatching { remote.listManagedProfessionals(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>> = flow {
        emit(
            runCatching { remote.listManagedServices(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>> = flow {
        emit(
            runCatching { remote.listManagedHours(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun changeStatus(
        clinicId: String,
        status: VeterinaryClinicStatus
    ): Result<VeterinaryClinicProfile> = try {
        Result.success(remote.changeClinicStatus(clinicId, status.name).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun requestVerification(clinicId: String): Result<VeterinaryClinicProfile> = try {
        Result.success(remote.requestClinicVerification(clinicId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun reviewVerification(
        clinicId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryClinicProfile> = try {
        Result.success(remote.reviewClinicVerification(clinicId, decision.name).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }
}

class SupabaseVeterinaryDirectoryRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryDirectoryRepository {

    override fun observePublicClinics(filter: VeterinaryDirectoryFilter): Flow<List<VeterinaryPublicListing>> =
        flow {
            emit(
                runCatching {
                    remote.listPublicClinics(
                        buildJsonObject {
                            put("p_query", filter.query)
                            put("p_zone", filter.zoneText)
                            put("p_specialty", filter.specialty?.name)
                            put("p_service_category", filter.serviceCategory?.name)
                            put("p_emergency_only", filter.emergencyCareOnly)
                            put("p_open_24_only", filter.open24HoursOnly)
                            put("p_verified_only", filter.verifiedOnly)
                        }
                    ).map { row ->
                        val clinic = row.toDomain()
                        clinic.asPublicListing(emptyList(), emptyList())
                    }
                }.getOrElse { emptyList() }
            )
        }

    override suspend fun getPublicClinic(clinicId: String): Result<VeterinaryClinicProfile> = try {
        if (clinicId.isBlank()) M12VeterinaryErrorMapper.fail("VETERINARY_CLINIC_NOT_FOUND")
        else Result.success(remote.getPublicClinic(clinicId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }
}

class SupabaseVeterinaryProfessionalRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryProfessionalRepository, VeterinaryProfessionalOpsRepository {

    override fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> = flow {
        emit(
            runCatching { remote.listPublicProfessionals(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun getProfessional(professionalId: String): Result<VeterinaryProfessional> =
        M12VeterinaryErrorMapper.fail("VETERINARY_PROFESSIONAL_NOT_FOUND")

    override suspend fun createProfessional(input: CreateVeterinaryProfessionalInput) = try {
        Result.success(
            remote.createProfessional(
                buildJsonObject {
                    put("p_display_name", input.displayName)
                    put("p_organization_id", input.organizationId)
                    put("p_user_id", input.userId)
                    put("p_license_number", input.licenseNumber)
                    put("p_license_jurisdiction", input.licenseJurisdiction)
                    put("p_biography", input.biography)
                    put("p_public_contact_enabled", input.publicContactEnabled)
                    put("p_avatar_asset_ref", input.avatarAssetRef)
                    putJsonArray("p_specialties") {
                        input.specialties.forEach { add(JsonPrimitive(it.name)) }
                    }
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun updateProfessional(input: UpdateVeterinaryProfessionalInput) = try {
        Result.success(
            remote.updateProfessional(
                buildJsonObject {
                    put("p_professional_id", input.professionalId)
                    put("p_display_name", input.displayName)
                    put("p_license_number", input.licenseNumber)
                    put("p_license_jurisdiction", input.licenseJurisdiction)
                    put("p_biography", input.biography)
                    put("p_public_contact_enabled", input.publicContactEnabled)
                    put("p_avatar_asset_ref", input.avatarAssetRef)
                    put("p_status", input.status?.name)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun linkProfessional(clinicId: String, professionalId: String, roleTitle: String?) =
        try {
            remote.linkProfessional(
                buildJsonObject {
                    put("p_clinic_id", clinicId)
                    put("p_professional_id", professionalId)
                    put("p_role_title", roleTitle)
                }
            )
            Result.success(
                com.comunidapp.app.data.model.VeterinaryClinicProfessionalLink(
                    id = "remote",
                    clinicId = clinicId,
                    professionalId = professionalId,
                    roleTitle = roleTitle,
                    active = true,
                    linkedBy = "",
                    linkedAt = java.time.Instant.now()
                )
            )
        } catch (t: Throwable) {
            M12VeterinaryErrorMapper.failure(t)
        }

    override suspend fun unlinkProfessional(clinicId: String, professionalId: String) = try {
        remote.unlinkProfessional(
            buildJsonObject {
                put("p_clinic_id", clinicId)
                put("p_professional_id", professionalId)
            }
        )
        Result.success(
            com.comunidapp.app.data.model.VeterinaryClinicProfessionalLink(
                id = "remote",
                clinicId = clinicId,
                professionalId = professionalId,
                active = false,
                linkedBy = "",
                linkedAt = java.time.Instant.EPOCH,
                unlinkedAt = java.time.Instant.now()
            )
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun replaceSpecialties(
        professionalId: String,
        specialties: Set<com.comunidapp.app.data.model.VeterinarySpecialty>
    ) = try {
        Result.success(
            remote.replaceSpecialties(
                buildJsonObject {
                    put("p_professional_id", professionalId)
                    putJsonArray("p_specialties") {
                        specialties.forEach { add(JsonPrimitive(it.name)) }
                    }
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun requestProfessionalVerification(professionalId: String): Result<VeterinaryProfessional> =
        M12VeterinaryErrorMapper.fail("VETERINARY_REPOSITORY_FAILURE")

    override suspend fun reviewProfessionalVerification(
        professionalId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryProfessional> =
        M12VeterinaryErrorMapper.fail("VETERINARY_REPOSITORY_FAILURE")

    override fun observeManagedProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> = flow {
        emit(
            runCatching { remote.listManagedProfessionals(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

class SupabaseVeterinaryServiceRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryServiceRepository {
    override suspend fun createService(input: CreateVeterinaryServiceInput) = try {
        Result.success(
            remote.createService(
                buildJsonObject {
                    put("p_clinic_id", input.clinicId)
                    put("p_name", input.name)
                    put("p_category", input.category.name)
                    put("p_description", input.description)
                    putJsonArray("p_species") {
                        input.species.forEach { add(JsonPrimitive(it.name)) }
                    }
                    put("p_requires_appointment", input.requiresAppointment)
                    put("p_emergency_available", input.emergencyAvailable)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun updateService(input: UpdateVeterinaryServiceInput) = try {
        Result.success(
            remote.updateService(
                buildJsonObject {
                    put("p_service_id", input.serviceId)
                    put("p_name", input.name)
                    put("p_category", input.category.name)
                    put("p_description", input.description)
                    putJsonArray("p_species") {
                        input.species.forEach { add(JsonPrimitive(it.name)) }
                    }
                    put("p_requires_appointment", input.requiresAppointment)
                    put("p_emergency_available", input.emergencyAvailable)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun changeServiceActive(serviceId: String, active: Boolean) = try {
        Result.success(
            remote.changeServiceStatus(
                buildJsonObject {
                    put("p_service_id", serviceId)
                    put("p_active", active)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>> = flow {
        emit(
            runCatching { remote.listManagedServices(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

class SupabaseVeterinaryOpeningHoursRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryOpeningHoursRepository {
    override suspend fun replaceWeekly(clinicId: String, hours: List<VeterinaryOpeningHours>) = try {
        Result.success(
            remote.replaceOpeningHours(
                buildJsonObject {
                    put("p_clinic_id", clinicId)
                    putJsonArray("p_hours") {
                        hours.forEach { h ->
                            add(
                                buildJsonObject {
                                    put("day_of_week", h.dayOfWeek.value)
                                    put("closed", h.closed)
                                    put("opens_at", h.opensAt?.toString())
                                    put("closes_at", h.closesAt?.toString())
                                    put("emergency_only", h.emergencyOnly)
                                }
                            )
                        }
                    }
                }
            ).map { it.toDomain() }
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>> = flow {
        emit(
            runCatching { remote.listManagedHours(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

private const val M12_DEFAULT_TIMEZONE = "America/Argentina/Buenos_Aires"

class SupabaseVeterinaryScheduleRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryScheduleRepository {

    override suspend fun getSettings(clinicId: String): Result<VeterinaryScheduleSettings> = try {
        val rows = remote.getScheduleSettings(clinicId)
        Result.success(
            rows.firstOrNull()?.toDomain() ?: VeterinaryScheduleSettings(clinicId = clinicId)
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun saveSettings(
        settings: VeterinaryScheduleSettings
    ): Result<VeterinaryScheduleSettings> = try {
        Result.success(
            remote.upsertScheduleSettings(
                buildJsonObject {
                    put("p_clinic_id", settings.clinicId)
                    put("p_timezone_name", settings.timezoneName)
                    put("p_booking_horizon_days", settings.bookingHorizonDays)
                    put("p_minimum_notice_minutes", settings.minimumNoticeMinutes)
                    put("p_cancellation_notice_minutes", settings.cancellationNoticeMinutes)
                    put("p_default_slot_duration_minutes", settings.defaultSlotDurationMinutes)
                    put("p_active", settings.active)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeManagedAvailability(clinicId: String): Flow<ManagedVeterinaryAvailability> = flow {
        emit(
            runCatching {
                val row = remote.listManagedAvailability(clinicId)
                ManagedVeterinaryAvailability(
                    rules = row.rules.map { it.toDomain() },
                    exceptions = row.exceptions.map { it.toDomain() }
                )
            }.getOrElse { ManagedVeterinaryAvailability(emptyList(), emptyList()) }
        )
    }

    override suspend fun createRule(
        input: CreateVeterinaryAvailabilityRuleInput
    ): Result<VeterinaryAvailabilityRule> = try {
        Result.success(
            remote.createAvailabilityRule(
                buildJsonObject {
                    put("p_clinic_id", input.clinicId)
                    put("p_day_of_week", input.dayOfWeek.value)
                    put("p_starts_at", input.startsAt.toString())
                    put("p_ends_at", input.endsAt.toString())
                    put("p_slot_duration_minutes", input.slotDurationMinutes)
                    put("p_capacity_per_slot", input.capacityPerSlot)
                    put("p_professional_id", input.professionalId)
                    put("p_service_id", input.serviceId)
                    put("p_valid_from", input.validFrom?.toString())
                    put("p_valid_until", input.validUntil?.toString())
                    put("p_active", true)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun updateRule(
        input: UpdateVeterinaryAvailabilityRuleInput
    ): Result<VeterinaryAvailabilityRule> = try {
        Result.success(
            remote.updateAvailabilityRule(
                buildJsonObject {
                    put("p_rule_id", input.ruleId)
                    put("p_day_of_week", input.dayOfWeek.value)
                    put("p_starts_at", input.startsAt.toString())
                    put("p_ends_at", input.endsAt.toString())
                    put("p_slot_duration_minutes", input.slotDurationMinutes)
                    put("p_capacity_per_slot", input.capacityPerSlot)
                    put("p_professional_id", input.professionalId)
                    put("p_service_id", input.serviceId)
                    put("p_valid_from", input.validFrom?.toString())
                    put("p_valid_until", input.validUntil?.toString())
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun changeRuleStatus(
        ruleId: String,
        active: Boolean
    ): Result<VeterinaryAvailabilityRule> = try {
        Result.success(remote.changeAvailabilityRuleStatus(ruleId, active).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun createException(
        input: CreateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException> = try {
        Result.success(
            remote.createAvailabilityException(
                buildJsonObject {
                    put("p_clinic_id", input.clinicId)
                    put("p_exception_date", input.exceptionDate.toString())
                    put("p_type", input.type.name)
                    put("p_rule_id", input.ruleId)
                    put("p_starts_at", input.startsAt?.toString())
                    put("p_ends_at", input.endsAt?.toString())
                    put("p_capacity_per_slot", input.capacityPerSlot)
                    put("p_reason", input.reason)
                    put("p_active", true)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun updateException(
        input: UpdateVeterinaryAvailabilityExceptionInput
    ): Result<VeterinaryAvailabilityException> = try {
        Result.success(
            remote.updateAvailabilityException(
                buildJsonObject {
                    put("p_exception_id", input.exceptionId)
                    put("p_exception_date", input.exceptionDate.toString())
                    put("p_type", input.type.name)
                    put("p_rule_id", input.ruleId)
                    put("p_starts_at", input.startsAt?.toString())
                    put("p_ends_at", input.endsAt?.toString())
                    put("p_capacity_per_slot", input.capacityPerSlot)
                    put("p_reason", input.reason)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun changeExceptionStatus(
        exceptionId: String,
        active: Boolean
    ): Result<VeterinaryAvailabilityException> = try {
        Result.success(remote.changeAvailabilityExceptionStatus(exceptionId, active).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeAvailableSlots(
        clinicId: String,
        serviceId: String,
        date: LocalDate,
        professionalId: String?
    ): Flow<List<VeterinaryAppointmentSlot>> = flow {
        emit(
            runCatching {
                remote.listAvailableSlots(clinicId, serviceId, date.toString(), professionalId)
                    .map { it.toDomain() }
            }.getOrElse { emptyList() }
        )
    }
}

class SupabaseVeterinaryAppointmentRepository(
    private val remote: SupabaseVeterinaryM12RemoteDataSource = SupabaseVeterinaryM12RemoteDataSource()
) : VeterinaryAppointmentRepository {

    override suspend fun requestAppointment(
        input: RequestVeterinaryAppointmentInput
    ): Result<VeterinaryAppointment> = try {
        // El RPC 047 exige p_ends_at; la ventana se resuelve consultando el slot calculado
        // (mismo criterio de cupo que el servidor), sin tabla de slots pre-generados.
        val settings = remote.getScheduleSettings(input.clinicId).firstOrNull()?.toDomain()
            ?: throw M12VeterinaryException(
                "VETERINARY_SLOT_NOT_AVAILABLE",
                M12VeterinaryErrorMapper.userMessage("VETERINARY_SLOT_NOT_AVAILABLE")
            )
        val zone = runCatching { ZoneId.of(settings.timezoneName) }
            .getOrElse { ZoneId.of(M12_DEFAULT_TIMEZONE) }
        val date = input.startsAt.atZone(zone).toLocalDate()
        val slot = remote.listAvailableSlots(
            input.clinicId, input.serviceId, date.toString(), input.professionalId
        ).map { it.toDomain() }.firstOrNull {
            it.startsAt == input.startsAt &&
                (input.professionalId == null || it.professionalId == null ||
                    it.professionalId == input.professionalId)
        } ?: throw M12VeterinaryException(
            "VETERINARY_SLOT_NOT_AVAILABLE",
            M12VeterinaryErrorMapper.userMessage("VETERINARY_SLOT_NOT_AVAILABLE")
        )

        Result.success(
            remote.requestAppointment(
                buildJsonObject {
                    put("p_clinic_id", input.clinicId)
                    put("p_service_id", input.serviceId)
                    put("p_pet_id", input.petId)
                    put("p_starts_at", input.startsAt.toString())
                    put("p_ends_at", slot.endsAt.toString())
                    put("p_professional_id", input.professionalId)
                    put("p_request_note", input.requestNote)
                }
            ).toDomain()
        )
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun getAppointment(appointmentId: String): Result<VeterinaryAppointment> = try {
        Result.success(remote.getAppointment(appointmentId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeMyAppointments(): Flow<List<VeterinaryAppointment>> = flow {
        emit(
            runCatching { remote.listMyAppointments().map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override fun observeManagedAppointments(clinicId: String): Flow<List<VeterinaryAppointment>> = flow {
        emit(
            runCatching { remote.listManagedAppointments(clinicId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }

    override suspend fun confirmAppointment(appointmentId: String): Result<VeterinaryAppointment> = try {
        Result.success(remote.confirmAppointment(appointmentId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun rejectAppointment(
        appointmentId: String,
        reason: String
    ): Result<VeterinaryAppointment> = try {
        Result.success(remote.rejectAppointment(appointmentId, reason).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun cancelMyAppointment(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryAppointment> = try {
        Result.success(remote.cancelMyAppointment(appointmentId, reason).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun cancelManagedAppointment(
        appointmentId: String,
        reason: String?
    ): Result<VeterinaryAppointment> = try {
        Result.success(remote.cancelManagedAppointment(appointmentId, reason).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun completeAppointment(appointmentId: String): Result<VeterinaryAppointment> = try {
        Result.success(remote.completeAppointment(appointmentId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun markNoShow(appointmentId: String): Result<VeterinaryAppointment> = try {
        Result.success(remote.markNoShow(appointmentId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override suspend fun expireAppointment(appointmentId: String): Result<VeterinaryAppointment> = try {
        Result.success(remote.expireAppointment(appointmentId).toDomain())
    } catch (t: Throwable) {
        M12VeterinaryErrorMapper.failure(t)
    }

    override fun observeAppointmentHistory(
        appointmentId: String
    ): Flow<List<VeterinaryAppointmentStatusHistory>> = flow {
        emit(
            runCatching { remote.listAppointmentHistory(appointmentId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        )
    }
}

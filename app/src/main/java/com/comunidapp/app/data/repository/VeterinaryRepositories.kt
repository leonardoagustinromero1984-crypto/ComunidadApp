package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AnimalSpecies
import com.comunidapp.app.data.model.VeterinaryClinicProfessionalLink
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryDirectoryFilter
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryPermissionCodes
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryProfessionalStatus
import com.comunidapp.app.data.model.VeterinaryPublicListing
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.model.VeterinaryAuditEvents
import com.comunidapp.app.data.model.VeterinaryM06Hooks
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Evento auditable M07 (mock). */
data class M12AuditEvent(val eventKey: String, val resourceId: String)

class M12VeterinaryMemoryStore {
    val clinics = MutableStateFlow<List<VeterinaryClinicProfile>>(emptyList())
    val professionals = MutableStateFlow<List<VeterinaryProfessional>>(emptyList())
    val services = MutableStateFlow<List<VeterinaryService>>(emptyList())
    val openingHours = MutableStateFlow<List<VeterinaryOpeningHours>>(emptyList())
    /** orgId → status (mock M03). */
    val organizationStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    /** orgId → userIds with veterinary.profile.manage. */
    val orgManagers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** orgId → userIds with veterinary.profile.read. */
    val orgViewers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    /** Shelter volunteer userIds — must NOT grant veterinary authority. */
    val shelterVolunteerUserIds = MutableStateFlow<Set<String>>(emptySet())
    val auditEvents = MutableStateFlow<List<M12AuditEvent>>(emptyList())
    val m06Hooks = MutableStateFlow<List<String>>(emptyList())
    /** When true, repository methods fail with VETERINARY_REPOSITORY_FAILURE. */
    var forceFailure: Boolean = false

    fun clear() {
        clinics.value = emptyList()
        professionals.value = emptyList()
        services.value = emptyList()
        openingHours.value = emptyList()
        organizationStatus.value = emptyMap()
        orgManagers.value = emptyMap()
        orgViewers.value = emptyMap()
        shelterVolunteerUserIds.value = emptySet()
        auditEvents.value = emptyList()
        m06Hooks.value = emptyList()
        forceFailure = false
    }

    fun recordAudit(eventKey: String, resourceId: String) {
        auditEvents.value = listOf(M12AuditEvent(eventKey, resourceId)) + auditEvents.value
    }

    fun recordM06Hook(hook: String) {
        m06Hooks.value = listOf(hook) + m06Hooks.value
    }

    fun seedDemoData() {
        if (clinics.value.isNotEmpty()) return
        organizationStatus.value = mapOf(
            "org-vet-demo-1" to "ACTIVE",
            "org-vet-demo-2" to "ACTIVE"
        )
        orgManagers.value = mapOf("org-vet-demo-1" to setOf("manager-vet-1"))
        orgViewers.value = mapOf(
            "org-vet-demo-1" to setOf("manager-vet-1", "viewer-vet-1"),
            "org-vet-demo-2" to setOf("viewer-vet-2")
        )
        val now = Instant.parse("2026-01-15T12:00:00Z")
        val clinicA = VeterinaryClinicProfile(
            id = "clinic-demo-norte",
            organizationId = "org-vet-demo-1",
            branchId = "branch-norte",
            displayName = "Clínica Demo Norte (ficticia)",
            description = "Centro veterinario de ejemplo para pruebas locales de LeoVer.",
            status = VeterinaryClinicStatus.ACTIVE,
            verificationStatus = VeterinaryVerificationStatus.VERIFIED,
            publicZoneText = "Zona Norte",
            publicAddressText = "Av. Ejemplo 100",
            publicContactEnabled = true,
            publicPhone = "+54 11 5555-0100",
            publicEmail = "contacto@clinicademonorte.test",
            websiteUrl = "https://clinicademonorte.test",
            socialLinks = mapOf("instagram" to "https://instagram.com/clinicademonorte_test"),
            logoAssetRef = "m05://vet/logo-norte",
            offersEmergencyCare = true,
            isOpen24Hours = false,
            createdAt = now,
            updatedAt = now
        )
        val clinicB = VeterinaryClinicProfile(
            id = "clinic-demo-sur",
            organizationId = "org-vet-demo-2",
            branchId = null,
            displayName = "Guardia Demo Sur (ficticia)",
            description = "Guardia 24 h de ejemplo. Datos ficticios.",
            status = VeterinaryClinicStatus.ACTIVE,
            verificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
            publicZoneText = "Zona Sur",
            publicContactEnabled = false,
            publicPhone = null,
            publicEmail = null,
            offersEmergencyCare = true,
            isOpen24Hours = true,
            logoAssetRef = "file_asset:vet-logo-sur",
            createdAt = now,
            updatedAt = now
        )
        val clinicDraft = VeterinaryClinicProfile(
            id = "clinic-demo-draft",
            organizationId = "org-vet-demo-1",
            displayName = "Borrador Demo Centro",
            description = "Borrador local; sin persistencia remota.",
            status = VeterinaryClinicStatus.DRAFT,
            verificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
            publicZoneText = "Centro",
            offersEmergencyCare = false,
            isOpen24Hours = false,
            createdAt = now,
            updatedAt = now
        )
        clinics.value = listOf(clinicA, clinicB, clinicDraft)
        professionals.value = listOf(
            VeterinaryProfessional(
                id = "pro-demo-1",
                userId = "pro-user-1",
                clinicId = clinicA.id,
                displayName = "Dra. Ana Ejemplo",
                licenseNumber = "MN-10001",
                licenseJurisdiction = "CABA",
                verificationStatus = VeterinaryVerificationStatus.VERIFIED,
                biography = "Medicina general (dato ficticio).",
                specialties = setOf(
                    VeterinarySpecialty.GENERAL_MEDICINE,
                    VeterinarySpecialty.DERMATOLOGY
                ),
                publicContactEnabled = true,
                publicPhone = "+54 11 5555-0101",
                publicEmail = "ana@clinicademonorte.test",
                avatarAssetRef = "m05://vet/pro-ana",
                status = VeterinaryProfessionalStatus.ACTIVE
            ),
            VeterinaryProfessional(
                id = "pro-demo-2",
                userId = "pro-user-2",
                clinicId = clinicA.id,
                displayName = "Dr. Bruno Ejemplo",
                licenseNumber = null,
                verificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
                specialties = setOf(VeterinarySpecialty.SURGERY),
                publicContactEnabled = false,
                publicPhone = "+54 11 5555-9999",
                publicEmail = "bruno-privado@test.local",
                status = VeterinaryProfessionalStatus.ACTIVE
            ),
            VeterinaryProfessional(
                id = "pro-demo-3",
                clinicId = clinicB.id,
                displayName = "Equipo Guardia Sur",
                specialties = setOf(VeterinarySpecialty.EMERGENCY_AND_CRITICAL_CARE),
                verificationStatus = VeterinaryVerificationStatus.PENDING,
                publicContactEnabled = false,
                status = VeterinaryProfessionalStatus.ACTIVE
            )
        )
        services.value = listOf(
            VeterinaryService(
                id = "svc-demo-1",
                clinicId = clinicA.id,
                name = "Consulta general",
                category = VeterinaryServiceCategory.CONSULTATION,
                species = setOf(AnimalSpecies.DOG, AnimalSpecies.CAT),
                active = true,
                requiresAppointment = true,
                emergencyAvailable = false
            ),
            VeterinaryService(
                id = "svc-demo-2",
                clinicId = clinicA.id,
                name = "Guardia de emergencias",
                category = VeterinaryServiceCategory.EMERGENCY_GUARD,
                active = true,
                requiresAppointment = false,
                emergencyAvailable = true
            ),
            VeterinaryService(
                id = "svc-demo-3",
                clinicId = clinicB.id,
                name = "Urgencias 24 h",
                category = VeterinaryServiceCategory.EMERGENCY_GUARD,
                active = true,
                requiresAppointment = false,
                emergencyAvailable = true
            ),
            VeterinaryService(
                id = "svc-demo-4",
                clinicId = clinicB.id,
                name = "Vacunación",
                category = VeterinaryServiceCategory.VACCINATION,
                active = true,
                requiresAppointment = true,
                emergencyAvailable = false
            )
        )
        openingHours.value = listOf(
            VeterinaryOpeningHours(
                clinicId = clinicA.id,
                dayOfWeek = DayOfWeek.MONDAY,
                closed = false,
                opensAt = LocalTime.of(9, 0),
                closesAt = LocalTime.of(19, 0)
            ),
            VeterinaryOpeningHours(
                clinicId = clinicA.id,
                dayOfWeek = DayOfWeek.SUNDAY,
                closed = true
            ),
            VeterinaryOpeningHours(
                clinicId = clinicB.id,
                dayOfWeek = DayOfWeek.MONDAY,
                closed = false,
                opensAt = LocalTime.MIDNIGHT,
                closesAt = LocalTime.of(23, 59),
                emergencyOnly = false
            )
        )
        orgBranches.value = mapOf("branch-norte" to "org-vet-demo-1")
        platformReviewers.value = setOf("m04-reviewer-1")
        clinicProfessionalLinks.value = listOf(
            VeterinaryClinicProfessionalLink(
                id = "link-demo-1",
                clinicId = clinicA.id,
                professionalId = "pro-demo-1",
                roleTitle = "Titular",
                active = true,
                linkedBy = "manager-vet-1",
                linkedAt = now
            ),
            VeterinaryClinicProfessionalLink(
                id = "link-demo-2",
                clinicId = clinicA.id,
                professionalId = "pro-demo-2",
                active = true,
                linkedBy = "manager-vet-1",
                linkedAt = now
            ),
            VeterinaryClinicProfessionalLink(
                id = "link-demo-3",
                clinicId = clinicB.id,
                professionalId = "pro-demo-3",
                active = true,
                linkedBy = "viewer-vet-2",
                linkedAt = now
            )
        )
    }
}

data class CreateVeterinaryClinicDraftInput(
    val organizationId: String,
    val branchId: String? = null,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String,
    val publicAddressText: String? = null,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val websiteUrl: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val logoAssetRef: String? = null,
    val coverAssetRef: String? = null,
    val offersEmergencyCare: Boolean = false,
    val isOpen24Hours: Boolean = false
)

data class UpdateVeterinaryClinicDraftInput(
    val clinicId: String,
    val displayName: String,
    val description: String? = null,
    val publicZoneText: String,
    val publicAddressText: String? = null,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val websiteUrl: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
    val logoAssetRef: String? = null,
    val coverAssetRef: String? = null,
    val offersEmergencyCare: Boolean = false,
    val isOpen24Hours: Boolean = false,
    val status: VeterinaryClinicStatus? = null
)

interface VeterinaryClinicRepository {
    fun observeManagedClinics(): Flow<List<VeterinaryClinicProfile>>
    suspend fun getManagedClinic(clinicId: String): Result<VeterinaryClinicProfile>
    suspend fun createLocalDraft(input: CreateVeterinaryClinicDraftInput): Result<VeterinaryClinicProfile>
    suspend fun updateLocalDraft(input: UpdateVeterinaryClinicDraftInput): Result<VeterinaryClinicProfile>
    fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>>
    fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>>
    fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>>
}

interface VeterinaryProfessionalRepository {
    fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>>
    suspend fun getProfessional(professionalId: String): Result<VeterinaryProfessional>
}

interface VeterinaryDirectoryRepository {
    fun observePublicClinics(filter: VeterinaryDirectoryFilter): Flow<List<VeterinaryPublicListing>>
    suspend fun getPublicClinic(clinicId: String): Result<VeterinaryClinicProfile>
}

private fun failM12(code: String): Nothing =
    throw M12VeterinaryException(code, M12VeterinaryErrorMapper.userMessage(code))

private fun M12VeterinaryMemoryStore.canManage(actor: String, orgId: String): Boolean =
    orgManagers.value[orgId]?.contains(actor) == true

private fun M12VeterinaryMemoryStore.canView(actor: String, orgId: String): Boolean =
    canManage(actor, orgId) || orgViewers.value[orgId]?.contains(actor) == true

private fun M12VeterinaryMemoryStore.requireOrgEligible(orgId: String) {
    val st = organizationStatus.value[orgId] ?: failM12("ORGANIZATION_NOT_ELIGIBLE")
    if (st != "ACTIVE") failM12("ORGANIZATION_NOT_ELIGIBLE")
}

internal fun M12VeterinaryMemoryStore.requireOrgEligibleInternal(orgId: String) =
    requireOrgEligible(orgId)

private fun M12VeterinaryMemoryStore.checkForcedFailure() {
    if (forceFailure) failM12("VETERINARY_REPOSITORY_FAILURE")
}

private fun M12VeterinaryMemoryStore.toPublicListing(clinic: VeterinaryClinicProfile): VeterinaryPublicListing {
    val pros = professionals.value.filter {
        it.clinicId == clinic.id && it.status == VeterinaryProfessionalStatus.ACTIVE
    }
    val svcs = services.value.filter { it.clinicId == clinic.id && it.active }
    return VeterinaryPublicListing(
        id = clinic.id,
        organizationId = clinic.organizationId,
        displayName = clinic.displayName,
        description = clinic.description,
        publicZoneText = clinic.publicZoneText,
        verificationStatus = clinic.verificationStatus,
        specialties = pros.flatMap { it.specialties }.toSet(),
        serviceCategories = svcs.map { it.category }.toSet(),
        offersEmergencyCare = clinic.offersEmergencyCare,
        isOpen24Hours = clinic.isOpen24Hours,
        logoAssetRef = clinic.logoAssetRef,
        status = clinic.status
    )
}

private fun matchesFilter(
    listing: VeterinaryPublicListing,
    filter: VeterinaryDirectoryFilter
): Boolean {
    val q = filter.query?.trim()?.lowercase(Locale.ROOT)
    if (!q.isNullOrEmpty()) {
        val hay = buildString {
            append(listing.displayName)
            append(' ')
            append(listing.description.orEmpty())
            append(' ')
            append(listing.publicZoneText)
            append(' ')
            append(listing.specialties.joinToString(" ") { it.name })
            append(' ')
            append(listing.serviceCategories.joinToString(" ") { it.name })
        }.lowercase(Locale.ROOT)
        if (!hay.contains(q)) return false
    }
    val zone = filter.zoneText?.trim()?.lowercase(Locale.ROOT)
    if (!zone.isNullOrEmpty() && !listing.publicZoneText.lowercase(Locale.ROOT).contains(zone)) {
        return false
    }
    if (filter.specialty != null && filter.specialty !in listing.specialties) return false
    if (filter.serviceCategory != null && filter.serviceCategory !in listing.serviceCategories) {
        return false
    }
    if (filter.emergencyCareOnly && !listing.offersEmergencyCare) return false
    if (filter.open24HoursOnly && !listing.isOpen24Hours) return false
    if (filter.verifiedOnly &&
        listing.verificationStatus != VeterinaryVerificationStatus.VERIFIED
    ) {
        return false
    }
    return true
}

fun redactProfessionalForPublic(pro: VeterinaryProfessional): VeterinaryProfessional {
    if (pro.publicContactEnabled) return pro
    return pro.copy(publicPhone = null, publicEmail = null)
}

class MockVeterinaryClinicRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryClinicRepository {

    override fun observeManagedClinics(): Flow<List<VeterinaryClinicProfile>> =
        store.clinics.map { list ->
            val actor = actorUserId().orEmpty()
            if (actor.isBlank()) emptyList()
            else list.filter { store.canView(actor, it.organizationId) }
        }

    override suspend fun getManagedClinic(clinicId: String): Result<VeterinaryClinicProfile> =
        runCatching {
            store.checkForcedFailure()
            if (clinicId.isBlank()) failM12("VETERINARY_CLINIC_NOT_FOUND")
            val actor = actorUserId() ?: failM12("NOT_AUTHENTICATED")
            val clinic = store.clinics.value.find { it.id == clinicId }
                ?: failM12("VETERINARY_CLINIC_NOT_FOUND")
            if (!store.canView(actor, clinic.organizationId)) failM12("VETERINARY_CLINIC_FORBIDDEN")
            clinic
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun createLocalDraft(
        input: CreateVeterinaryClinicDraftInput
    ): Result<VeterinaryClinicProfile> = runCatching {
        store.checkForcedFailure()
        val actor = actorUserId() ?: failM12("NOT_AUTHENTICATED")
        if (input.organizationId.isBlank()) failM12("VETERINARY_ORGANIZATION_REQUIRED")
        store.validateDraftExtras(input.organizationId, input.branchId)
        val orgId = VeterinaryValidators.requireOrganizationId(input.organizationId)
        store.requireOrgEligible(orgId)
        if (!store.canManage(actor, orgId)) failM12("VETERINARY_CLINIC_FORBIDDEN")
        // AccountType / shelter volunteer must not grant manage.
        if (store.shelterVolunteerUserIds.value.contains(actor) && !store.canManage(actor, orgId)) {
            failM12("VETERINARY_CLINIC_FORBIDDEN")
        }
        val name = VeterinaryValidators.requireDisplayName(input.displayName)
        val zone = VeterinaryValidators.requirePublicZone(input.publicZoneText)
        VeterinaryValidators.validateEmail(input.publicEmail)
        VeterinaryValidators.validateWebsiteUrl(input.websiteUrl)
        VeterinaryValidators.validateMediaRef(input.logoAssetRef)
        VeterinaryValidators.validateMediaRef(input.coverAssetRef)
        val now = Instant.now()
        val row = VeterinaryClinicProfile(
            id = UUID.randomUUID().toString(),
            organizationId = orgId,
            branchId = VeterinaryValidators.normalizeBranchId(input.branchId),
            displayName = name,
            description = input.description?.trim()?.takeIf { it.isNotEmpty() },
            status = VeterinaryClinicStatus.DRAFT,
            verificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
            publicZoneText = zone,
            publicAddressText = input.publicAddressText?.trim()?.takeIf { it.isNotEmpty() },
            publicPhone = input.publicPhone?.trim()?.takeIf { it.isNotEmpty() },
            publicEmail = input.publicEmail?.trim()?.takeIf { it.isNotEmpty() },
            websiteUrl = input.websiteUrl?.trim()?.takeIf { it.isNotEmpty() },
            socialLinks = input.socialLinks,
            logoAssetRef = input.logoAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            offersEmergencyCare = input.offersEmergencyCare,
            isOpen24Hours = input.isOpen24Hours,
            publicContactEnabled = false,
            createdAt = now,
            updatedAt = now
        )
        store.clinics.value = listOf(row) + store.clinics.value
        store.recordAudit(VeterinaryAuditEvents.VETERINARY_CLINIC_DRAFT_CREATED, row.id)
        store.recordM06Hook(VeterinaryM06Hooks.VETERINARY_CLINIC_DRAFT_CREATED)
        row
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun updateLocalDraft(
        input: UpdateVeterinaryClinicDraftInput
    ): Result<VeterinaryClinicProfile> = runCatching {
        store.checkForcedFailure()
        val actor = actorUserId() ?: failM12("NOT_AUTHENTICATED")
        if (input.clinicId.isBlank()) failM12("VETERINARY_CLINIC_NOT_FOUND")
        val existing = store.clinics.value.find { it.id == input.clinicId }
            ?: failM12("VETERINARY_CLINIC_NOT_FOUND")
        if (!store.canManage(actor, existing.organizationId)) failM12("VETERINARY_CLINIC_FORBIDDEN")
        val name = VeterinaryValidators.requireDisplayName(input.displayName)
        val zone = VeterinaryValidators.requirePublicZone(input.publicZoneText)
        VeterinaryValidators.validateEmail(input.publicEmail)
        VeterinaryValidators.validateWebsiteUrl(input.websiteUrl)
        VeterinaryValidators.validateMediaRef(input.logoAssetRef)
        VeterinaryValidators.validateMediaRef(input.coverAssetRef)
        val hours = store.openingHours.value.filter { it.clinicId == existing.id }
        hours.forEach { VeterinaryValidators.validateOpeningHours(it) }
        if (!VeterinaryValidators.isOpen24HoursCoherent(input.isOpen24Hours, hours)) {
            failM12("VETERINARY_OPENING_HOURS_INVALID")
        }
        val services = store.services.value.filter { it.clinicId == existing.id }
        if (!VeterinaryValidators.isEmergencyCoherent(input.offersEmergencyCare, services)) {
            failM12("VETERINARY_CLINIC_INVALID")
        }
        val updated = existing.copy(
            displayName = name,
            description = input.description?.trim()?.takeIf { it.isNotEmpty() },
            publicZoneText = zone,
            publicAddressText = input.publicAddressText?.trim()?.takeIf { it.isNotEmpty() },
            publicPhone = input.publicPhone?.trim()?.takeIf { it.isNotEmpty() },
            publicEmail = input.publicEmail?.trim()?.takeIf { it.isNotEmpty() },
            websiteUrl = input.websiteUrl?.trim()?.takeIf { it.isNotEmpty() },
            socialLinks = input.socialLinks,
            logoAssetRef = input.logoAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            coverAssetRef = input.coverAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            offersEmergencyCare = input.offersEmergencyCare,
            isOpen24Hours = input.isOpen24Hours,
            status = input.status ?: existing.status,
            // Never auto-verify in Bloque 1.
            verificationStatus = existing.verificationStatus,
            updatedAt = Instant.now()
        )
        store.clinics.value = store.clinics.value.map { if (it.id == updated.id) updated else it }
        store.recordAudit(VeterinaryAuditEvents.VETERINARY_CLINIC_PROFILE_UPDATED, updated.id)
        store.recordM06Hook(VeterinaryM06Hooks.VETERINARY_CLINIC_PROFILE_UPDATED)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> =
        store.professionals.map { list ->
            list.filter { it.clinicId == clinicId }.map { redactProfessionalForPublic(it) }
        }

    override fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>> =
        store.services.map { list -> list.filter { it.clinicId == clinicId } }

    override fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>> =
        store.openingHours.map { list -> list.filter { it.clinicId == clinicId } }
}

class MockVeterinaryProfessionalRepository(
    private val store: M12VeterinaryMemoryStore
) : VeterinaryProfessionalRepository {

    override fun observeClinicProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> =
        store.professionals.map { list ->
            list.filter { it.clinicId == clinicId }.map { redactProfessionalForPublic(it) }
        }

    override suspend fun getProfessional(professionalId: String): Result<VeterinaryProfessional> =
        runCatching {
            store.checkForcedFailure()
            if (professionalId.isBlank()) failM12("VETERINARY_PROFESSIONAL_NOT_FOUND")
            val pro = store.professionals.value.find { it.id == professionalId }
                ?: failM12("VETERINARY_PROFESSIONAL_NOT_FOUND")
            redactProfessionalForPublic(pro)
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })
}

class MockVeterinaryDirectoryRepository(
    private val store: M12VeterinaryMemoryStore
) : VeterinaryDirectoryRepository {

    override fun observePublicClinics(
        filter: VeterinaryDirectoryFilter
    ): Flow<List<VeterinaryPublicListing>> =
        combine(store.clinics, store.professionals, store.services) { clinics, _, _ ->
            clinics
                .filter { it.status.isPubliclyListable }
                .map { store.toPublicListing(it) }
                .filter { matchesFilter(it, filter) }
        }

    override suspend fun getPublicClinic(clinicId: String): Result<VeterinaryClinicProfile> =
        runCatching {
            store.checkForcedFailure()
            if (clinicId.isBlank()) failM12("VETERINARY_CLINIC_NOT_FOUND")
            val clinic = store.clinics.value.find { it.id == clinicId }
                ?: failM12("VETERINARY_CLINIC_NOT_FOUND")
            if (!clinic.status.isPubliclyListable && clinic.status != VeterinaryClinicStatus.DRAFT) {
                // Drafts are not public; inactive etc.
                if (clinic.status != VeterinaryClinicStatus.ACTIVE) {
                    failM12("VETERINARY_CLINIC_INACTIVE")
                }
            }
            if (!clinic.status.isPubliclyListable) failM12("VETERINARY_CLINIC_INACTIVE")
            redactClinicForPublic(clinic)
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })
}

/** Helper for tests / authority checks without AccountType. */
object VeterinaryAuthority {
    fun accountTypeGrantsVeterinaryManage(accountType: String?): Boolean = false

    fun shelterVolunteerGrantsVeterinaryManage(): Boolean = false

    fun hasDeclaredPermission(code: String): Boolean =
        code in VeterinaryPermissionCodes.all
}

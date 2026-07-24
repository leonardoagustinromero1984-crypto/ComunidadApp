package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.AnimalSpecies
import com.comunidapp.app.data.model.VeterinaryClinicProfessionalLink
import com.comunidapp.app.data.model.VeterinaryClinicProfile
import com.comunidapp.app.data.model.VeterinaryClinicStatus
import com.comunidapp.app.data.model.VeterinaryOpeningHours
import com.comunidapp.app.data.model.VeterinaryProfessional
import com.comunidapp.app.data.model.VeterinaryProfessionalStatus
import com.comunidapp.app.data.model.VeterinaryService
import com.comunidapp.app.data.model.VeterinaryServiceCategory
import com.comunidapp.app.data.model.VeterinarySpecialty
import com.comunidapp.app.data.model.VeterinaryVerificationStatus
import com.comunidapp.app.data.model.VeterinaryAuditEvents
import com.comunidapp.app.data.model.VeterinaryM06Hooks
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryErrorMapper
import com.comunidapp.app.data.remote.supabase.m12.M12VeterinaryException
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * LeoVer M12 Bloque 2 — operaciones de persistencia local (fake) y contratos extendidos.
 * Tests usan estos mocks; Supabase usa RPC equivalentes.
 */

private fun failM12b2(code: String): Nothing =
    throw M12VeterinaryException(code, M12VeterinaryErrorMapper.userMessage(code))

fun M12VeterinaryMemoryStore.ensureBlock2Fields() {
    // no-op marker; fields added below on store via companion accessors
}

// --- Store extensions (fields live on store via these mutable maps held here if missing) ---

private val storeLinks = mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<List<VeterinaryClinicProfessionalLink>>>()
private val storeReviewers = mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Set<String>>>()
private val storeBranches = mutableMapOf<M12VeterinaryMemoryStore, MutableStateFlow<Map<String, String>>>()

val M12VeterinaryMemoryStore.clinicProfessionalLinks: MutableStateFlow<List<VeterinaryClinicProfessionalLink>>
    get() = storeLinks.getOrPut(this) { MutableStateFlow(emptyList()) }

val M12VeterinaryMemoryStore.platformReviewers: MutableStateFlow<Set<String>>
    get() = storeReviewers.getOrPut(this) { MutableStateFlow(setOf("m04-reviewer-1")) }

val M12VeterinaryMemoryStore.orgBranches: MutableStateFlow<Map<String, String>>
    get() = storeBranches.getOrPut(this) {
        MutableStateFlow(mapOf("branch-norte" to "org-vet-demo-1"))
    }

fun M12VeterinaryMemoryStore.clearBlock2() {
    clinicProfessionalLinks.value = emptyList()
    platformReviewers.value = setOf("m04-reviewer-1")
    orgBranches.value = mapOf("branch-norte" to "org-vet-demo-1")
}

fun redactClinicForPublic(clinic: VeterinaryClinicProfile): VeterinaryClinicProfile {
    if (clinic.publicContactEnabled) return clinic
    return clinic.copy(publicPhone = null, publicEmail = null)
}

fun redactProfessionalPublicProjection(pro: VeterinaryProfessional): VeterinaryProfessional {
    val base = redactProfessionalForPublic(pro)
    return base.copy(licenseNumber = null) // never expose full license in public listing
}

private fun M12VeterinaryMemoryStore.requireBranch(orgId: String, branchId: String?) {
    if (branchId.isNullOrBlank()) return
    val owner = orgBranches.value[branchId]
    if (owner == null || owner != orgId) failM12b2("VETERINARY_BRANCH_INVALID")
}

private fun allowedClinicTransition(
    from: VeterinaryClinicStatus,
    to: VeterinaryClinicStatus
): Boolean = when (from) {
    VeterinaryClinicStatus.DRAFT -> to == VeterinaryClinicStatus.ACTIVE || to == VeterinaryClinicStatus.ARCHIVED
    VeterinaryClinicStatus.ACTIVE ->
        to == VeterinaryClinicStatus.PAUSED ||
            to == VeterinaryClinicStatus.SUSPENDED ||
            to == VeterinaryClinicStatus.ARCHIVED
    VeterinaryClinicStatus.PAUSED ->
        to == VeterinaryClinicStatus.ACTIVE || to == VeterinaryClinicStatus.ARCHIVED
    VeterinaryClinicStatus.SUSPENDED ->
        to == VeterinaryClinicStatus.ACTIVE || to == VeterinaryClinicStatus.ARCHIVED
    VeterinaryClinicStatus.ARCHIVED, VeterinaryClinicStatus.UNKNOWN -> false
}

private fun M12VeterinaryMemoryStore.assertActivation(clinicId: String) {
    val clinic = clinics.value.find { it.id == clinicId } ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
    if (clinic.publicZoneText.isBlank()) failM12b2("VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS")
    val hours = openingHours.value.filter { it.clinicId == clinicId }
    val okHours = clinic.isOpen24Hours || (
        hours.isNotEmpty() && hours.all { runCatching { VeterinaryValidators.validateOpeningHours(it) }.isSuccess }
        )
    if (!okHours) failM12b2("VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS")
    val hasService = services.value.any { it.clinicId == clinicId && it.active }
    if (!hasService) failM12b2("VETERINARY_CLINIC_ACTIVATION_REQUIREMENTS")
}

data class CreateVeterinaryProfessionalInput(
    val displayName: String,
    val userId: String? = null,
    val licenseNumber: String? = null,
    val licenseJurisdiction: String? = null,
    val biography: String? = null,
    val specialties: Set<VeterinarySpecialty> = emptySet(),
    val publicContactEnabled: Boolean = false,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val avatarAssetRef: String? = null,
    val organizationId: String
)

data class UpdateVeterinaryProfessionalInput(
    val professionalId: String,
    val displayName: String,
    val licenseNumber: String? = null,
    val licenseJurisdiction: String? = null,
    val biography: String? = null,
    val publicContactEnabled: Boolean = false,
    val publicPhone: String? = null,
    val publicEmail: String? = null,
    val avatarAssetRef: String? = null,
    val status: VeterinaryProfessionalStatus? = null
)

data class CreateVeterinaryServiceInput(
    val clinicId: String,
    val name: String,
    val category: VeterinaryServiceCategory,
    val description: String? = null,
    val species: Set<AnimalSpecies> = emptySet(),
    val requiresAppointment: Boolean = true,
    val emergencyAvailable: Boolean = false
)

data class UpdateVeterinaryServiceInput(
    val serviceId: String,
    val name: String,
    val category: VeterinaryServiceCategory,
    val description: String? = null,
    val species: Set<AnimalSpecies> = emptySet(),
    val requiresAppointment: Boolean = true,
    val emergencyAvailable: Boolean = false
)

interface VeterinaryClinicLifecycle {
    suspend fun changeStatus(clinicId: String, status: VeterinaryClinicStatus): Result<VeterinaryClinicProfile>
    suspend fun requestVerification(clinicId: String): Result<VeterinaryClinicProfile>
    suspend fun reviewVerification(
        clinicId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryClinicProfile>
}

interface VeterinaryProfessionalOpsRepository {
    suspend fun createProfessional(input: CreateVeterinaryProfessionalInput): Result<VeterinaryProfessional>
    suspend fun updateProfessional(input: UpdateVeterinaryProfessionalInput): Result<VeterinaryProfessional>
    suspend fun linkProfessional(
        clinicId: String,
        professionalId: String,
        roleTitle: String? = null
    ): Result<VeterinaryClinicProfessionalLink>
    suspend fun unlinkProfessional(
        clinicId: String,
        professionalId: String
    ): Result<VeterinaryClinicProfessionalLink>
    suspend fun replaceSpecialties(
        professionalId: String,
        specialties: Set<VeterinarySpecialty>
    ): Result<VeterinaryProfessional>
    suspend fun requestProfessionalVerification(professionalId: String): Result<VeterinaryProfessional>
    suspend fun reviewProfessionalVerification(
        professionalId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryProfessional>
    fun observeManagedProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>>
}

interface VeterinaryServiceRepository {
    suspend fun createService(input: CreateVeterinaryServiceInput): Result<VeterinaryService>
    suspend fun updateService(input: UpdateVeterinaryServiceInput): Result<VeterinaryService>
    suspend fun changeServiceActive(serviceId: String, active: Boolean): Result<VeterinaryService>
    fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>>
}

interface VeterinaryOpeningHoursRepository {
    suspend fun replaceWeekly(
        clinicId: String,
        hours: List<VeterinaryOpeningHours>
    ): Result<List<VeterinaryOpeningHours>>
    fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>>
}

class MockVeterinaryClinicLifecycle(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryClinicLifecycle {

    private fun canManage(actor: String, orgId: String) =
        store.orgManagers.value[orgId]?.contains(actor) == true

    override suspend fun changeStatus(
        clinicId: String,
        status: VeterinaryClinicStatus
    ): Result<VeterinaryClinicProfile> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        if (status == VeterinaryClinicStatus.SUSPENDED &&
            actor !in store.platformReviewers.value
        ) {
            // Managers cannot suspend via M04 path; only ACTIVE→SUSPENDED with M04 or admin.
            // Per prompt: ACTIVE → SUSPENDED (M04/admin). Managers use PAUSED.
            failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        }
        if (!allowedClinicTransition(clinic.status, status)) {
            failM12b2("VETERINARY_CLINIC_INVALID_TRANSITION")
        }
        if (status == VeterinaryClinicStatus.ACTIVE) {
            store.requireOrgEligibleInternal(clinic.organizationId)
            // duplicate active org+branch
            val conflict = store.clinics.value.any {
                it.id != clinic.id &&
                    it.organizationId == clinic.organizationId &&
                    it.branchId == clinic.branchId &&
                    it.status == VeterinaryClinicStatus.ACTIVE
            }
            if (conflict) failM12b2("VETERINARY_CLINIC_ALREADY_EXISTS")
            store.assertActivation(clinicId)
        }
        val now = Instant.now()
        val updated = clinic.copy(
            status = status,
            archivedAt = if (status == VeterinaryClinicStatus.ARCHIVED) now else clinic.archivedAt,
            updatedAt = now
        )
        store.clinics.value = store.clinics.value.map { if (it.id == clinicId) updated else it }
        store.recordAudit("VETERINARY_CLINIC_STATUS_CHANGED", clinicId)
        store.recordM06Hook("VETERINARY_CLINIC_STATUS_CHANGED")
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun requestVerification(clinicId: String): Result<VeterinaryClinicProfile> =
        runCatching {
            if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
            val clinic = store.clinics.value.find { it.id == clinicId }
                ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
            if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
            val next = when (clinic.verificationStatus) {
                VeterinaryVerificationStatus.UNVERIFIED,
                VeterinaryVerificationStatus.REJECTED,
                VeterinaryVerificationStatus.SUSPENDED -> VeterinaryVerificationStatus.PENDING
                else -> failM12b2("VETERINARY_VERIFICATION_INVALID_TRANSITION")
            }
            val updated = clinic.copy(verificationStatus = next, updatedAt = Instant.now())
            store.clinics.value = store.clinics.value.map { if (it.id == clinicId) updated else it }
            store.recordAudit("VETERINARY_CLINIC_VERIFICATION_CHANGED", clinicId)
            store.recordM06Hook("VETERINARY_CLINIC_VERIFICATION_REQUESTED")
            updated
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun reviewVerification(
        clinicId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryClinicProfile> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        if (actor !in store.platformReviewers.value) failM12b2("VETERINARY_VERIFICATION_FORBIDDEN")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        // Manager cannot self-verify even if somehow reviewer
        if (canManage(actor, clinic.organizationId) && actor !in store.platformReviewers.value) {
            failM12b2("VETERINARY_VERIFICATION_FORBIDDEN")
        }
        val allowed = when (clinic.verificationStatus) {
            VeterinaryVerificationStatus.PENDING ->
                decision == VeterinaryVerificationStatus.VERIFIED ||
                    decision == VeterinaryVerificationStatus.REJECTED
            VeterinaryVerificationStatus.VERIFIED ->
                decision == VeterinaryVerificationStatus.SUSPENDED
            VeterinaryVerificationStatus.SUSPENDED ->
                decision == VeterinaryVerificationStatus.PENDING ||
                    decision == VeterinaryVerificationStatus.VERIFIED
            else -> false
        }
        if (!allowed) failM12b2("VETERINARY_VERIFICATION_INVALID_TRANSITION")
        val updated = clinic.copy(verificationStatus = decision, updatedAt = Instant.now())
        store.clinics.value = store.clinics.value.map { if (it.id == clinicId) updated else it }
        store.recordAudit("VETERINARY_CLINIC_VERIFICATION_CHANGED", clinicId)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })
}

class MockVeterinaryProfessionalOpsRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryProfessionalOpsRepository {

    private fun canManage(actor: String, orgId: String) =
        store.orgManagers.value[orgId]?.contains(actor) == true

    override suspend fun createProfessional(
        input: CreateVeterinaryProfessionalInput
    ): Result<VeterinaryProfessional> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        if (input.organizationId.isBlank()) failM12b2("VETERINARY_ORGANIZATION_REQUIRED")
        store.requireOrgEligibleInternal(input.organizationId)
        if (!canManage(actor, input.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val name = input.displayName.trim()
        if (name.isEmpty()) failM12b2("VETERINARY_PROFESSIONAL_INVALID")
        VeterinaryValidators.validateMediaRef(input.avatarAssetRef)
        val invalidSpec = input.specialties.any { it == VeterinarySpecialty.UNKNOWN }
        if (invalidSpec) failM12b2("VETERINARY_SPECIALTY_INVALID")
        val row = VeterinaryProfessional(
            id = UUID.randomUUID().toString(),
            userId = input.userId,
            clinicId = null,
            displayName = name,
            licenseNumber = VeterinaryValidators.normalizeLicense(input.licenseNumber),
            licenseJurisdiction = input.licenseJurisdiction?.trim()?.takeIf { it.isNotEmpty() },
            verificationStatus = VeterinaryVerificationStatus.UNVERIFIED,
            biography = input.biography?.trim()?.takeIf { it.isNotEmpty() },
            specialties = input.specialties,
            publicContactEnabled = input.publicContactEnabled,
            publicPhone = input.publicPhone?.trim()?.takeIf { it.isNotEmpty() },
            publicEmail = input.publicEmail?.trim()?.takeIf { it.isNotEmpty() },
            avatarAssetRef = input.avatarAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            status = VeterinaryProfessionalStatus.ACTIVE
        )
        store.professionals.value = listOf(row) + store.professionals.value
        store.recordAudit("VETERINARY_PROFESSIONAL_CREATED", row.id)
        row
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun updateProfessional(
        input: UpdateVeterinaryProfessionalInput
    ): Result<VeterinaryProfessional> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val existing = store.professionals.value.find { it.id == input.professionalId }
            ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_FOUND")
        val orgId = store.clinicProfessionalLinks.value
            .firstOrNull { it.professionalId == existing.id && it.active }
            ?.let { link -> store.clinics.value.find { it.id == link.clinicId }?.organizationId }
            ?: store.clinics.value.find { it.id == existing.clinicId }?.organizationId
        if (orgId == null || !canManage(actor, orgId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        VeterinaryValidators.validateMediaRef(input.avatarAssetRef)
        val updated = existing.copy(
            displayName = input.displayName.trim().ifEmpty { failM12b2("VETERINARY_PROFESSIONAL_INVALID") },
            licenseNumber = VeterinaryValidators.normalizeLicense(input.licenseNumber),
            licenseJurisdiction = input.licenseJurisdiction?.trim()?.takeIf { it.isNotEmpty() },
            biography = input.biography?.trim()?.takeIf { it.isNotEmpty() },
            publicContactEnabled = input.publicContactEnabled,
            publicPhone = input.publicPhone?.trim()?.takeIf { it.isNotEmpty() },
            publicEmail = input.publicEmail?.trim()?.takeIf { it.isNotEmpty() },
            avatarAssetRef = input.avatarAssetRef?.trim()?.takeIf { it.isNotEmpty() },
            status = input.status ?: existing.status
        )
        store.professionals.value = store.professionals.value.map { if (it.id == updated.id) updated else it }
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun linkProfessional(
        clinicId: String,
        professionalId: String,
        roleTitle: String?
    ): Result<VeterinaryClinicProfessionalLink> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        store.professionals.value.find { it.id == professionalId }
            ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_FOUND")
        val dup = store.clinicProfessionalLinks.value.any {
            it.clinicId == clinicId && it.professionalId == professionalId && it.active
        }
        if (dup) failM12b2("VETERINARY_PROFESSIONAL_ALREADY_LINKED")
        val now = Instant.now()
        val link = VeterinaryClinicProfessionalLink(
            id = UUID.randomUUID().toString(),
            clinicId = clinicId,
            professionalId = professionalId,
            roleTitle = roleTitle?.trim()?.takeIf { it.isNotEmpty() },
            active = true,
            linkedBy = actor,
            linkedAt = now
        )
        store.clinicProfessionalLinks.value = listOf(link) + store.clinicProfessionalLinks.value
        // Linked professional does NOT get manage authority
        store.professionals.value = store.professionals.value.map {
            if (it.id == professionalId) it.copy(clinicId = clinicId) else it
        }
        store.recordAudit("VETERINARY_PROFESSIONAL_LINKED", professionalId)
        store.recordM06Hook(VeterinaryM06Hooks.VETERINARY_PROFESSIONAL_LINKED)
        link
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun unlinkProfessional(
        clinicId: String,
        professionalId: String
    ): Result<VeterinaryClinicProfessionalLink> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val link = store.clinicProfessionalLinks.value.find {
            it.clinicId == clinicId && it.professionalId == professionalId && it.active
        } ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_LINKED")
        val updated = link.copy(active = false, unlinkedAt = Instant.now())
        store.clinicProfessionalLinks.value = store.clinicProfessionalLinks.value.map {
            if (it.id == link.id) updated else it
        }
        store.recordAudit("VETERINARY_PROFESSIONAL_UNLINKED", professionalId)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun replaceSpecialties(
        professionalId: String,
        specialties: Set<VeterinarySpecialty>
    ): Result<VeterinaryProfessional> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val pro = store.professionals.value.find { it.id == professionalId }
            ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_FOUND")
        if (specialties.any { it == VeterinarySpecialty.UNKNOWN }) {
            failM12b2("VETERINARY_SPECIALTY_INVALID")
        }
        val orgId = store.clinics.value.find { it.id == pro.clinicId }?.organizationId
            ?: store.clinicProfessionalLinks.value.firstOrNull { it.professionalId == professionalId && it.active }
                ?.let { l -> store.clinics.value.find { it.id == l.clinicId }?.organizationId }
        if (orgId == null || !canManage(actor, orgId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val updated = pro.copy(specialties = specialties)
        store.professionals.value = store.professionals.value.map { if (it.id == professionalId) updated else it }
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun requestProfessionalVerification(
        professionalId: String
    ): Result<VeterinaryProfessional> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val pro = store.professionals.value.find { it.id == professionalId }
            ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_FOUND")
        val orgId = store.clinics.value.find { it.id == pro.clinicId }?.organizationId
            ?: failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        if (!canManage(actor, orgId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val next = when (pro.verificationStatus) {
            VeterinaryVerificationStatus.UNVERIFIED,
            VeterinaryVerificationStatus.REJECTED,
            VeterinaryVerificationStatus.SUSPENDED -> VeterinaryVerificationStatus.PENDING
            else -> failM12b2("VETERINARY_VERIFICATION_INVALID_TRANSITION")
        }
        val updated = pro.copy(verificationStatus = next)
        store.professionals.value = store.professionals.value.map { if (it.id == professionalId) updated else it }
        store.recordM06Hook("VETERINARY_PROFESSIONAL_VERIFICATION_REQUESTED")
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun reviewProfessionalVerification(
        professionalId: String,
        decision: VeterinaryVerificationStatus
    ): Result<VeterinaryProfessional> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        if (actor !in store.platformReviewers.value) failM12b2("VETERINARY_VERIFICATION_FORBIDDEN")
        val pro = store.professionals.value.find { it.id == professionalId }
            ?: failM12b2("VETERINARY_PROFESSIONAL_NOT_FOUND")
        if (pro.verificationStatus != VeterinaryVerificationStatus.PENDING &&
            decision != VeterinaryVerificationStatus.SUSPENDED
        ) {
            if (pro.verificationStatus != VeterinaryVerificationStatus.VERIFIED) {
                failM12b2("VETERINARY_VERIFICATION_INVALID_TRANSITION")
            }
        }
        val updated = pro.copy(verificationStatus = decision)
        store.professionals.value = store.professionals.value.map { if (it.id == professionalId) updated else it }
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeManagedProfessionals(clinicId: String): Flow<List<VeterinaryProfessional>> =
        store.professionals.map { list ->
            val linkedIds = store.clinicProfessionalLinks.value
                .filter { it.clinicId == clinicId && it.active }
                .map { it.professionalId }
                .toSet()
            list.filter { it.clinicId == clinicId || it.id in linkedIds }
        }
}

class MockVeterinaryServiceRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryServiceRepository {

    private fun canManage(actor: String, orgId: String) =
        store.orgManagers.value[orgId]?.contains(actor) == true

    override suspend fun createService(input: CreateVeterinaryServiceInput): Result<VeterinaryService> =
        runCatching {
            if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
            val clinic = store.clinics.value.find { it.id == input.clinicId }
                ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
            if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
            val name = input.name.trim()
            if (name.isEmpty() || input.category == VeterinaryServiceCategory.UNKNOWN) {
                failM12b2("VETERINARY_SERVICE_INVALID")
            }
            val dup = store.services.value.any {
                it.clinicId == input.clinicId &&
                    it.active &&
                    it.name.equals(name, ignoreCase = true) &&
                    it.category == input.category
            }
            if (dup) failM12b2("VETERINARY_SERVICE_DUPLICATE")
            if (input.emergencyAvailable && !clinic.offersEmergencyCare &&
                input.category != VeterinaryServiceCategory.EMERGENCY_GUARD
            ) {
                // still allow creating; clinic flag can be updated separately
            }
            val row = VeterinaryService(
                id = UUID.randomUUID().toString(),
                clinicId = input.clinicId,
                name = name,
                category = input.category,
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                species = input.species,
                active = true,
                requiresAppointment = input.requiresAppointment,
                emergencyAvailable = input.emergencyAvailable
            )
            store.services.value = listOf(row) + store.services.value
            store.recordAudit("VETERINARY_SERVICE_CHANGED", row.id)
            row
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun updateService(input: UpdateVeterinaryServiceInput): Result<VeterinaryService> =
        runCatching {
            if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
            val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
            val existing = store.services.value.find { it.id == input.serviceId }
                ?: failM12b2("VETERINARY_SERVICE_NOT_FOUND")
            val clinic = store.clinics.value.find { it.id == existing.clinicId }
                ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
            if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
            val name = input.name.trim()
            if (name.isEmpty()) failM12b2("VETERINARY_SERVICE_INVALID")
            val dup = store.services.value.any {
                it.id != existing.id &&
                    it.clinicId == existing.clinicId &&
                    it.active &&
                    it.name.equals(name, ignoreCase = true) &&
                    it.category == input.category
            }
            if (dup) failM12b2("VETERINARY_SERVICE_DUPLICATE")
            val updated = existing.copy(
                name = name,
                category = input.category,
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                species = input.species,
                requiresAppointment = input.requiresAppointment,
                emergencyAvailable = input.emergencyAvailable
            )
            store.services.value = store.services.value.map { if (it.id == updated.id) updated else it }
            store.recordAudit("VETERINARY_SERVICE_CHANGED", updated.id)
            updated
        }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override suspend fun changeServiceActive(
        serviceId: String,
        active: Boolean
    ): Result<VeterinaryService> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val existing = store.services.value.find { it.id == serviceId }
            ?: failM12b2("VETERINARY_SERVICE_NOT_FOUND")
        val clinic = store.clinics.value.find { it.id == existing.clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val updated = existing.copy(active = active)
        store.services.value = store.services.value.map { if (it.id == serviceId) updated else it }
        store.recordAudit("VETERINARY_SERVICE_CHANGED", serviceId)
        updated
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeClinicServices(clinicId: String): Flow<List<VeterinaryService>> =
        store.services.map { list -> list.filter { it.clinicId == clinicId } }
}

class MockVeterinaryOpeningHoursRepository(
    private val actorUserId: () -> String?,
    private val store: M12VeterinaryMemoryStore
) : VeterinaryOpeningHoursRepository {

    private fun canManage(actor: String, orgId: String) =
        store.orgManagers.value[orgId]?.contains(actor) == true

    override suspend fun replaceWeekly(
        clinicId: String,
        hours: List<VeterinaryOpeningHours>
    ): Result<List<VeterinaryOpeningHours>> = runCatching {
        if (store.forceFailure) failM12b2("VETERINARY_REPOSITORY_FAILURE")
        val actor = actorUserId() ?: failM12b2("NOT_AUTHENTICATED")
        val clinic = store.clinics.value.find { it.id == clinicId }
            ?: failM12b2("VETERINARY_CLINIC_NOT_FOUND")
        if (!canManage(actor, clinic.organizationId)) failM12b2("VETERINARY_CLINIC_FORBIDDEN")
        val days = hours.map { it.dayOfWeek }
        if (days.size != days.distinct().size) failM12b2("VETERINARY_OPENING_HOURS_DUPLICATE_DAY")
        hours.forEach { row ->
            if (!row.closed && (row.opensAt == null || row.closesAt == null)) {
                failM12b2("VETERINARY_OPENING_HOURS_INCOMPLETE")
            }
            VeterinaryValidators.validateOpeningHours(row)
        }
        if (!VeterinaryValidators.isOpen24HoursCoherent(clinic.isOpen24Hours, hours)) {
            failM12b2("VETERINARY_OPENING_HOURS_INVALID")
        }
        val normalized = hours.map { it.copy(clinicId = clinicId) }
        store.openingHours.value =
            store.openingHours.value.filter { it.clinicId != clinicId } + normalized
        store.recordAudit("VETERINARY_OPENING_HOURS_REPLACED", clinicId)
        normalized
    }.fold({ Result.success(it) }, { M12VeterinaryErrorMapper.failure(it) })

    override fun observeClinicOpeningHours(clinicId: String): Flow<List<VeterinaryOpeningHours>> =
        store.openingHours.map { list -> list.filter { it.clinicId == clinicId } }
}

/** Enhances create draft with org required code + branch check. */
fun M12VeterinaryMemoryStore.validateDraftExtras(organizationId: String, branchId: String?) {
    if (organizationId.isBlank()) failM12b2("VETERINARY_ORGANIZATION_REQUIRED")
    requireBranch(organizationId, branchId)
}

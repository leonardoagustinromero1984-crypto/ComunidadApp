package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M28CareStatus
import com.comunidapp.app.data.model.M28ClinicDashboardSummary
import com.comunidapp.app.data.model.M28CreateCareDraftInput
import com.comunidapp.app.data.model.M28CreatePassportProposalInput
import com.comunidapp.app.data.model.M28CreateVaccinationInput
import com.comunidapp.app.data.model.M28ExportDisclaimer
import com.comunidapp.app.data.model.M28ExportSnapshot
import com.comunidapp.app.data.model.M28FollowUpStatus
import com.comunidapp.app.data.model.M28GrantProfessionalAccessInput
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28GrantStatus
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28PatientSummary
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.M28ProfessionalCare
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType
import com.comunidapp.app.data.model.M28UpdateCareDraftInput
import com.comunidapp.app.data.model.M28VaccinationRecord
import com.comunidapp.app.data.model.Pet
import com.comunidapp.app.data.remote.supabase.m28.SupabaseM28RemoteDataSource
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface M28Repository {
    suspend fun grantAccess(input: M28GrantProfessionalAccessInput): Result<M28ProfessionalAccessGrant>
    suspend fun revokeAccess(grantId: String): Result<M28ProfessionalAccessGrant>
    suspend fun listGrantsForResponsible(petId: String): Result<List<M28ProfessionalAccessGrant>>
    suspend fun listClinicPatients(clinicId: String, actorUserId: String, actorOrgRole: String?): Result<List<M28PatientSummary>>
    suspend fun createCareDraft(input: M28CreateCareDraftInput, actorUserId: String): Result<M28ProfessionalCare>
    suspend fun updateCareDraft(input: M28UpdateCareDraftInput, actorUserId: String): Result<M28ProfessionalCare>
    suspend fun finalizeCare(careId: String, actorUserId: String): Result<M28ProfessionalCare>
    suspend fun supersedeCare(careId: String, reason: String, actorUserId: String): Result<M28ProfessionalCare>
    suspend fun getCare(careId: String, actorUserId: String, actorOrgRole: String?): Result<M28ProfessionalCare>
    suspend fun listPetCares(clinicId: String, petId: String, actorUserId: String, actorOrgRole: String?): Result<List<M28ProfessionalCare>>
    suspend fun createVaccination(input: M28CreateVaccinationInput, actorUserId: String): Result<M28VaccinationRecord>
    suspend fun createPassportProposal(input: M28CreatePassportProposalInput, actorUserId: String): Result<M28PassportUpdateProposal>
    suspend fun listProposalsForResponsible(petId: String, actorUserId: String): Result<List<M28PassportUpdateProposal>>
    suspend fun decideProposal(proposalId: String, decision: M28ProposalDecision, note: String?, actorUserId: String): Result<M28PassportUpdateProposal>
    suspend fun requestExport(clinicId: String, petId: String?, clientRequestId: String, actorUserId: String, actorOrgRole: String?): Result<M28ExportSnapshot>
    suspend fun clinicDashboard(clinicId: String, actorUserId: String): Result<M28ClinicDashboardSummary>
}

class M28AccessPolicy(
    private val isResponsible: (petId: String, userId: String) -> Boolean,
    private val hasActiveGrant: (petId: String, clinicId: String, purpose: M28GrantPurpose) -> Boolean,
    private val hasOrgCareRead: (clinicId: String, userId: String, role: String?) -> Boolean,
    private val hasOrgCareWrite: (clinicId: String, userId: String, role: String?) -> Boolean,
    private val canViewClinicalNotes: (clinicId: String, userId: String, role: String?) -> Boolean
) {
    fun requireResponsible(petId: String, userId: String) {
        if (!isResponsible(petId, userId)) throw M28AccessException("M28_GRANT_REVOKED")
    }

    fun requireClinicRead(clinicId: String, petId: String, userId: String, role: String?) {
        if (!hasOrgCareRead(clinicId, userId, role)) throw M28AccessException("M28_PROFESSIONAL_SUSPENDED")
        if (!hasActiveGrant(petId, clinicId, M28GrantPurpose.HISTORICAL_READ) &&
            !hasActiveGrant(petId, clinicId, M28GrantPurpose.CURRENT_CARE)
        ) {
            throw M28AccessException("M28_GRANT_REVOKED")
        }
    }

    fun requireClinicWrite(clinicId: String, petId: String, userId: String, role: String?) {
        if (!hasOrgCareWrite(clinicId, userId, role)) throw M28AccessException("M28_PROFESSIONAL_SUSPENDED")
        if (!hasActiveGrant(petId, clinicId, M28GrantPurpose.CURRENT_CARE)) {
            throw M28AccessException("M28_GRANT_REVOKED")
        }
    }

    fun filterClinicalNotes(care: M28ProfessionalCare, clinicId: String, userId: String, role: String?): M28ProfessionalCare {
        return if (canViewClinicalNotes(clinicId, userId, role)) care
        else care.copy(clinicalNotes = null)
    }
}

class M28AccessException(val code: String) : Exception(code)

class M28MemoryStore {
    private val idSeq = AtomicLong(0)
    private val mutex = Mutex()
    val grants = mutableListOf<M28ProfessionalAccessGrant>()
    val cares = mutableListOf<M28ProfessionalCare>()
    val vaccinations = mutableListOf<M28VaccinationRecord>()
    val proposals = mutableListOf<M28PassportUpdateProposal>()
    val relationships = mutableListOf<M28PatientSummary>()
    val passportCredentialsCreated = mutableListOf<String>()
    val audit = mutableListOf<String>()
    val finalizedAppointmentIds = mutableSetOf<String>()
    val exportRequests = mutableMapOf<String, M28ExportSnapshot>()

    fun nextId(prefix: String) = "${prefix}_${idSeq.incrementAndGet()}"

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

class MockM28Repository(
    private val store: M28MemoryStore,
    private val actorUserId: () -> String?,
    private val resolvePet: (String) -> Pet?,
    private val isPetResponsible: (petId: String, userId: String) -> Boolean,
    private val orgRoleForClinic: (clinicId: String, userId: String) -> String?,
    private val clinicOrgId: (clinicId: String) -> String = { "org_$it" }
) : M28Repository {

    private fun policy() = M28AccessPolicy(
        isResponsible = isPetResponsible,
        hasActiveGrant = { pet, clinic, purpose ->
            store.grants.any {
                it.petId == pet && it.status == M28GrantStatus.ACTIVE &&
                    (it.clinicId == clinic || it.clinicId == null) &&
                    it.purposes.contains(purpose)
            }
        },
        hasOrgCareRead = { _, _, role ->
            role != null && role !in setOf("NONE", "UNKNOWN")
        },
        hasOrgCareWrite = { _, user, role -> role in setOf("OWNER", "ADMIN", "VETERINARIAN") },
        canViewClinicalNotes = { _, _, role -> role in setOf("OWNER", "ADMIN", "VETERINARIAN") }
    )

    override suspend fun grantAccess(input: M28GrantProfessionalAccessInput): Result<M28ProfessionalAccessGrant> =
        runCatching {
            store.withLock {
                val actor = actorUserId() ?: error("NOT_AUTHENTICATED")
                policy().requireResponsible(input.petId, actor)
                resolvePet(input.petId) ?: error("M28_PET_NOT_FOUND")
                val grant = M28ProfessionalAccessGrant(
                    id = store.nextId("grant"),
                    petId = input.petId,
                    grantedByUserId = actor,
                    clinicId = input.clinicId,
                    professionalId = input.professionalId,
                    purposes = input.purposes,
                    status = M28GrantStatus.ACTIVE,
                    validFromEpochMs = System.currentTimeMillis(),
                    validUntilEpochMs = input.validUntilEpochMs,
                    revokedAtEpochMs = null
                )
                store.grants.add(grant)
                store.audit.add("grant.created")
                grant
            }
        }

    override suspend fun revokeAccess(grantId: String): Result<M28ProfessionalAccessGrant> = runCatching {
        store.withLock {
            val actor = actorUserId() ?: error("NOT_AUTHENTICATED")
            val idx = store.grants.indexOfFirst { it.id == grantId }
            if (idx < 0) error("M28_GRANT_REVOKED")
            val g = store.grants[idx]
            policy().requireResponsible(g.petId, actor)
            val updated = g.copy(status = M28GrantStatus.REVOKED, revokedAtEpochMs = System.currentTimeMillis())
            store.grants[idx] = updated
            store.audit.add("grant.revoked")
            updated
        }
    }

    override suspend fun listGrantsForResponsible(petId: String): Result<List<M28ProfessionalAccessGrant>> =
        runCatching {
            val actor = actorUserId() ?: error("NOT_AUTHENTICATED")
            policy().requireResponsible(petId, actor)
            store.grants.filter { it.petId == petId }
        }

    override suspend fun listClinicPatients(clinicId: String, actorUserId: String, actorOrgRole: String?): Result<List<M28PatientSummary>> =
        runCatching {
            if (actorOrgRole == null || actorOrgRole == "RECEPTION_ONLY") {
                // reception can list names but not clinical - still allowed list
            }
            if (actorOrgRole == "NONE") throw M28AccessException("M28_ESTABLISHMENT_SUSPENDED")
            store.relationships.filter { true }
        }

    override suspend fun createCareDraft(input: M28CreateCareDraftInput, actorUserId: String): Result<M28ProfessionalCare> =
        runCatching {
            store.withLock {
                val role = orgRoleForClinic(input.clinicId, actorUserId)
                policy().requireClinicWrite(input.clinicId, input.petId, actorUserId, role)
                val existing = store.cares.find { it.appointmentId == input.appointmentId && input.appointmentId != null }
                if (existing != null) return@withLock existing
                val care = M28ProfessionalCare(
                    id = store.nextId("care"),
                    petId = input.petId,
                    clinicId = input.clinicId,
                    professionalId = "prof_$actorUserId",
                    appointmentId = input.appointmentId,
                    careTypeCode = input.careTypeCode,
                    careTypeLabel = input.careTypeCode,
                    reason = null,
                    weightKg = null,
                    findingsSummary = null,
                    clinicalNotes = null,
                    observations = null,
                    status = M28CareStatus.DRAFT,
                    version = 1,
                    supersedesCareId = null,
                    createdBy = actorUserId,
                    finalizedBy = null,
                    finalizedAtEpochMs = null,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                store.cares.add(care)
                store.audit.add("care.created")
                care
            }
        }

    override suspend fun updateCareDraft(input: M28UpdateCareDraftInput, actorUserId: String): Result<M28ProfessionalCare> =
        runCatching {
            store.withLock {
                val idx = store.cares.indexOfFirst { it.id == input.careId }
                if (idx < 0) error("M28_PET_NOT_FOUND")
                val current = store.cares[idx]
                if (current.status != M28CareStatus.DRAFT) error("M28_EDIT_CONFLICT")
                val role = orgRoleForClinic(current.clinicId, actorUserId)
                policy().requireClinicWrite(current.clinicId, current.petId, actorUserId, role)
                val updated = current.copy(
                    reason = input.reason ?: current.reason,
                    weightKg = input.weightKg ?: current.weightKg,
                    findingsSummary = input.findingsSummary ?: current.findingsSummary,
                    clinicalNotes = input.clinicalNotes ?: current.clinicalNotes,
                    observations = input.observations ?: current.observations,
                    careTypeCode = input.careTypeCode ?: current.careTypeCode,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                store.cares[idx] = updated
                updated
            }
        }

    override suspend fun finalizeCare(careId: String, actorUserId: String): Result<M28ProfessionalCare> = runCatching {
        store.withLock {
            val idx = store.cares.indexOfFirst { it.id == careId }
            if (idx < 0) error("M28_PET_NOT_FOUND")
            val current = store.cares[idx]
            if (current.status == M28CareStatus.FINALIZED) return@withLock current
            if (current.status != M28CareStatus.DRAFT) error("M28_EDIT_CONFLICT")
            val appt = current.appointmentId
            if (appt != null && store.finalizedAppointmentIds.contains(appt)) error("M28_CARE_DUPLICATE")
            val role = orgRoleForClinic(current.clinicId, actorUserId)
            policy().requireClinicWrite(current.clinicId, current.petId, actorUserId, role)
            val finalized = current.copy(
                status = M28CareStatus.FINALIZED,
                finalizedBy = actorUserId,
                finalizedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            store.cares[idx] = finalized
            if (appt != null) store.finalizedAppointmentIds.add(appt)
            store.audit.add("care.finalized")
            finalized
        }
    }

    override suspend fun supersedeCare(careId: String, reason: String, actorUserId: String): Result<M28ProfessionalCare> =
        runCatching {
            store.withLock {
                val idx = store.cares.indexOfFirst { it.id == careId }
                if (idx < 0) error("M28_PET_NOT_FOUND")
                val original = store.cares[idx]
                if (original.status != M28CareStatus.FINALIZED) error("M28_EDIT_CONFLICT")
                val role = orgRoleForClinic(original.clinicId, actorUserId)
                policy().requireClinicWrite(original.clinicId, original.petId, actorUserId, role)
                store.cares[idx] = original.copy(status = M28CareStatus.CORRECTED)
                val corrected = original.copy(
                    id = store.nextId("care"),
                    status = M28CareStatus.FINALIZED,
                    version = original.version + 1,
                    supersedesCareId = original.id,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    finalizedBy = actorUserId,
                    finalizedAtEpochMs = System.currentTimeMillis()
                )
                store.cares.add(corrected)
                store.audit.add("care.corrected")
                corrected
            }
        }

    override suspend fun getCare(careId: String, actorUserId: String, actorOrgRole: String?): Result<M28ProfessionalCare> =
        runCatching {
            val care = store.cares.firstOrNull { it.id == careId } ?: error("M28_PET_NOT_FOUND")
            policy().requireClinicRead(care.clinicId, care.petId, actorUserId, actorOrgRole)
            policy().filterClinicalNotes(care, care.clinicId, actorUserId, actorOrgRole)
        }

    override suspend fun listPetCares(clinicId: String, petId: String, actorUserId: String, actorOrgRole: String?): Result<List<M28ProfessionalCare>> =
        runCatching {
            policy().requireClinicRead(clinicId, petId, actorUserId, actorOrgRole)
            store.cares.filter { it.clinicId == clinicId && it.petId == petId }
                .map { policy().filterClinicalNotes(it, clinicId, actorUserId, actorOrgRole) }
        }

    override suspend fun createVaccination(input: M28CreateVaccinationInput, actorUserId: String): Result<M28VaccinationRecord> =
        runCatching {
            val role = orgRoleForClinic(input.clinicId, actorUserId)
            policy().requireClinicWrite(input.clinicId, input.petId, actorUserId, role)
            val record = M28VaccinationRecord(
                id = store.nextId("vac"),
                careId = input.careId,
                petId = input.petId,
                clinicId = input.clinicId,
                professionalId = "prof_$actorUserId",
                vaccineCode = input.vaccineCode,
                vaccineLabel = input.vaccineLabel,
                administeredAtEpochMs = input.administeredAtEpochMs,
                dose = input.dose,
                batchNumber = input.batchNumber,
                manufacturer = input.manufacturer,
                nextDueAtEpochMs = input.nextDueAtEpochMs,
                notes = input.notes
            )
            store.vaccinations.add(record)
            store.audit.add("vaccination.created")
            record
        }

    override suspend fun createPassportProposal(input: M28CreatePassportProposalInput, actorUserId: String): Result<M28PassportUpdateProposal> =
        runCatching {
            val role = orgRoleForClinic(input.clinicId, actorUserId)
            policy().requireClinicWrite(input.clinicId, input.petId, actorUserId, role)
            if (!store.grants.any { it.petId == input.petId && it.purposes.contains(M28GrantPurpose.PASSPORT_PROPOSAL) && it.status == M28GrantStatus.ACTIVE }) {
                throw M28AccessException("M28_GRANT_REVOKED")
            }
            val proposal = M28PassportUpdateProposal(
                id = store.nextId("prop"),
                petId = input.petId,
                passportId = input.passportId,
                sourceCareId = input.sourceCareId,
                clinicId = input.clinicId,
                proposedByProfessionalId = "prof_$actorUserId",
                proposalType = input.proposalType,
                previousValueJson = input.previousValueJson,
                proposedValueJson = input.proposedValueJson,
                status = M28ProposalStatus.PENDING,
                decisionNote = null,
                decidedBy = null,
                decidedAtEpochMs = null,
                createdAtEpochMs = System.currentTimeMillis()
            )
            store.proposals.add(proposal)
            store.audit.add("passport.proposal.created")
            proposal
        }

    override suspend fun listProposalsForResponsible(petId: String, actorUserId: String): Result<List<M28PassportUpdateProposal>> =
        runCatching {
            policy().requireResponsible(petId, actorUserId)
            store.proposals.filter { it.petId == petId }
        }

    override suspend fun decideProposal(
        proposalId: String,
        decision: M28ProposalDecision,
        note: String?,
        actorUserId: String
    ): Result<M28PassportUpdateProposal> = runCatching {
        store.withLock {
            val idx = store.proposals.indexOfFirst { it.id == proposalId }
            if (idx < 0) error("M28_PROPOSAL_ALREADY_RESOLVED")
            val p = store.proposals[idx]
            if (p.status != M28ProposalStatus.PENDING) error("M28_PROPOSAL_ALREADY_RESOLVED")
            if (!isPetResponsible(p.petId, actorUserId)) error("M28_GRANT_REVOKED")
            val status = when (decision) {
                M28ProposalDecision.ACCEPT -> {
                    store.passportCredentialsCreated.add(p.id)
                    M28ProposalStatus.ACCEPTED
                }
                M28ProposalDecision.REJECT -> M28ProposalStatus.REJECTED
                M28ProposalDecision.CORRECTION_REQUESTED -> M28ProposalStatus.PENDING
            }
            val updated = p.copy(
                status = status,
                decisionNote = note,
                decidedBy = actorUserId,
                decidedAtEpochMs = System.currentTimeMillis()
            )
            store.proposals[idx] = updated
            store.audit.add("passport.proposal.decided")
            updated
        }
    }

    override suspend fun requestExport(
        clinicId: String,
        petId: String?,
        clientRequestId: String,
        actorUserId: String,
        actorOrgRole: String?
    ): Result<M28ExportSnapshot> = runCatching {
        if (actorOrgRole !in setOf("OWNER", "ADMIN", "VETERINARIAN")) throw M28AccessException("M28_EXPORT_FAILED")
        if (petId != null) policy().requireClinicRead(clinicId, petId, actorUserId, actorOrgRole)
        store.exportRequests[clientRequestId]?.let { return@runCatching it }
        val snapshot = M28ExportSnapshot(
            disclaimer = M28ExportDisclaimer.TEXT,
            clinicName = "Clinic $clinicId",
            generatedAtEpochMs = System.currentTimeMillis(),
            pet = mapOf("name" to (petId?.let { resolvePet(it)?.name } ?: "All")),
            cares = store.cares.filter { it.clinicId == clinicId && (petId == null || it.petId == petId) }
                .map { mapOf("id" to it.id, "status" to it.status.name) },
            vaccinations = emptyList(),
            followUps = emptyList(),
            documents = emptyList()
        )
        store.exportRequests[clientRequestId] = snapshot
        store.audit.add("export.completed")
        snapshot
    }

    override suspend fun clinicDashboard(clinicId: String, actorUserId: String): Result<M28ClinicDashboardSummary> =
        runCatching {
            M28ClinicDashboardSummary(0, 0, store.relationships, store.proposals.count { it.status == M28ProposalStatus.PENDING })
        }
}

class SupabaseM28Repository(
    private val remote: SupabaseM28RemoteDataSource = SupabaseM28RemoteDataSource()
) : M28Repository {
    override suspend fun grantAccess(input: M28GrantProfessionalAccessInput) = runCatching {
        remote.grantProfessionalAccess(input.petId, input.clinicId, input.professionalId, input.purposes, input.validUntilEpochMs)
    }

    override suspend fun revokeAccess(grantId: String) = runCatching { remote.revokeProfessionalAccess(grantId) }

    override suspend fun listGrantsForResponsible(petId: String) = runCatching { remote.listGrantsForResponsible(petId) }

    override suspend fun listClinicPatients(clinicId: String, actorUserId: String, actorOrgRole: String?) =
        runCatching { remote.listClinicPatients(clinicId) }

    override suspend fun createCareDraft(input: M28CreateCareDraftInput, actorUserId: String) = runCatching {
        remote.createCareDraft(input.clinicId, input.petId, input.appointmentId, input.careTypeCode, input.clientRequestId)
    }

    override suspend fun updateCareDraft(input: M28UpdateCareDraftInput, actorUserId: String) = runCatching {
        remote.updateCareDraft(input.careId, input.reason, input.weightKg, input.findingsSummary, input.clinicalNotes, input.observations, input.careTypeCode)
    }

    override suspend fun finalizeCare(careId: String, actorUserId: String) = runCatching { remote.finalizeCare(careId) }

    override suspend fun supersedeCare(careId: String, reason: String, actorUserId: String) =
        runCatching { remote.supersedeCare(careId, reason) }

    override suspend fun getCare(careId: String, actorUserId: String, actorOrgRole: String?) =
        runCatching { remote.getCare(careId) }

    override suspend fun listPetCares(clinicId: String, petId: String, actorUserId: String, actorOrgRole: String?) =
        runCatching { remote.listPetCares(clinicId, petId) }

    override suspend fun createVaccination(input: M28CreateVaccinationInput, actorUserId: String) = runCatching {
        remote.createVaccinationRecord(
            buildJsonObject {
                putNullable("p_care_id", input.careId)
                put("p_pet_id", input.petId)
                put("p_clinic_id", input.clinicId)
                put("p_vaccine_code", input.vaccineCode)
                put("p_vaccine_label", input.vaccineLabel)
                put("p_administered_at", java.time.Instant.ofEpochMilli(input.administeredAtEpochMs).toString())
                putNullable("p_dose", input.dose)
                putNullable("p_batch_number", input.batchNumber)
                putNullable("p_manufacturer", input.manufacturer)
                putNullable("p_next_due_at", input.nextDueAtEpochMs?.let { java.time.Instant.ofEpochMilli(it).toString() })
                putNullable("p_notes", input.notes)
            }
        )
    }

    override suspend fun createPassportProposal(input: M28CreatePassportProposalInput, actorUserId: String) = runCatching {
        remote.createPassportProposal(
            buildJsonObject {
                put("p_pet_id", input.petId)
                put("p_passport_id", input.passportId)
                put("p_clinic_id", input.clinicId)
                putNullable("p_source_care_id", input.sourceCareId)
                putNullable("p_source_vaccination_id", input.sourceVaccinationId)
                put("p_proposal_type", input.proposalType.name)
                put("p_proposed_value", input.proposedValueJson)
                putNullable("p_previous_value", input.previousValueJson)
                put("p_client_request_id", input.clientRequestId)
            }
        )
    }

    override suspend fun listProposalsForResponsible(petId: String, actorUserId: String) =
        runCatching { remote.listProposalsForResponsible(petId) }

    override suspend fun decideProposal(proposalId: String, decision: M28ProposalDecision, note: String?, actorUserId: String) =
        runCatching { remote.decideProposal(proposalId, decision, note) }

    override suspend fun requestExport(clinicId: String, petId: String?, clientRequestId: String, actorUserId: String, actorOrgRole: String?) =
        runCatching { remote.requestExport(clinicId, clientRequestId, petId) }

    override suspend fun clinicDashboard(clinicId: String, actorUserId: String) =
        runCatching { remote.clinicDashboard(clinicId) }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, null as String?) else put(key, value)
    }
}

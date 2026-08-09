package com.comunidapp.app.data.model

/** LeoVer M28 — Portal veterinario profesional (Pilot Minimum). */
enum class M28GrantPurpose {
    CURRENT_CARE,
    HISTORICAL_READ,
    DOCUMENTS,
    PASSPORT_PROPOSAL
}

enum class M28GrantStatus { ACTIVE, REVOKED, EXPIRED }

data class M28ProfessionalAccessGrant(
    val id: String,
    val petId: String,
    val grantedByUserId: String,
    val clinicId: String?,
    val professionalId: String?,
    val purposes: List<M28GrantPurpose>,
    val status: M28GrantStatus,
    val validFromEpochMs: Long,
    val validUntilEpochMs: Long?,
    val revokedAtEpochMs: Long?,
    val clinicName: String? = null
)

enum class M28CareStatus { DRAFT, FINALIZED, CORRECTED, VOID }

data class M28ProfessionalCare(
    val id: String,
    val petId: String,
    val clinicId: String,
    val professionalId: String?,
    val appointmentId: String?,
    val careTypeCode: String,
    val careTypeLabel: String,
    val reason: String?,
    val weightKg: Double?,
    val findingsSummary: String?,
    val clinicalNotes: String?,
    val observations: String?,
    val status: M28CareStatus,
    val version: Int,
    val supersedesCareId: String?,
    val createdBy: String,
    val finalizedBy: String?,
    val finalizedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

data class M28VaccinationRecord(
    val id: String,
    val careId: String?,
    val petId: String,
    val clinicId: String,
    val professionalId: String?,
    val vaccineCode: String,
    val vaccineLabel: String,
    val administeredAtEpochMs: Long,
    val dose: String?,
    val batchNumber: String?,
    val manufacturer: String?,
    val nextDueAtEpochMs: Long?,
    val notes: String?,
    val provenance: String = "LOADED_BY_PROFESSIONAL"
)

enum class M28DocumentVisibility { CLINIC_STAFF, RESPONSIBLE_SHARED, PROFESSIONAL_ONLY }

data class M28ProfessionalDocument(
    val id: String,
    val petId: String,
    val careId: String?,
    val clinicId: String,
    val uploadedBy: String,
    val assetRef: String,
    val documentType: String,
    val title: String,
    val visibility: M28DocumentVisibility,
    val createdAtEpochMs: Long
)

enum class M28FollowUpStatus { PENDING, SCHEDULED, COMPLETED, CANCELLED, OVERDUE }

data class M28FollowUp(
    val id: String,
    val petId: String,
    val careId: String?,
    val clinicId: String,
    val professionalId: String?,
    val followUpTypeCode: String,
    val status: M28FollowUpStatus,
    val dueAtEpochMs: Long?,
    val notes: String?,
    val completedAtEpochMs: Long?
)

enum class M28ProposalType { VACCINATION, WEIGHT, CONTROL_EVENT, HEALTH_DOCUMENT, OTHER }

enum class M28ProposalStatus { PENDING, ACCEPTED, REJECTED, CANCELLED, SUPERSEDED }

data class M28PassportUpdateProposal(
    val id: String,
    val petId: String,
    val passportId: String,
    val sourceCareId: String?,
    val clinicId: String,
    val proposedByProfessionalId: String?,
    val proposalType: M28ProposalType,
    val previousValueJson: String?,
    val proposedValueJson: String,
    val status: M28ProposalStatus,
    val decisionNote: String?,
    val decidedBy: String?,
    val decidedAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val clinicName: String? = null,
    val professionalName: String? = null
)

data class M28PatientSummary(
    val petId: String,
    val petName: String,
    val species: String?,
    val lastCareAtEpochMs: Long?,
    val relationshipId: String
)

data class M28ClinicDashboardSummary(
    val appointmentsToday: Int,
    val pendingFollowUps: Int,
    val recentPatients: List<M28PatientSummary>,
    val pendingProposals: Int
)

data class M28ExportSnapshot(
    val disclaimer: String,
    val clinicName: String,
    val generatedAtEpochMs: Long,
    val pet: Map<String, String?>,
    val cares: List<Map<String, String?>>,
    val vaccinations: List<Map<String, String?>>,
    val followUps: List<Map<String, String?>>,
    val documents: List<Map<String, String?>>
)

data class M28GrantProfessionalAccessInput(
    val petId: String,
    val clinicId: String?,
    val professionalId: String?,
    val purposes: List<M28GrantPurpose>,
    val validUntilEpochMs: Long? = null
)

data class M28CreateCareDraftInput(
    val clinicId: String,
    val petId: String,
    val appointmentId: String? = null,
    val careTypeCode: String = "GENERAL_CONSULT",
    val clientRequestId: String
)

data class M28UpdateCareDraftInput(
    val careId: String,
    val reason: String? = null,
    val weightKg: Double? = null,
    val findingsSummary: String? = null,
    val clinicalNotes: String? = null,
    val observations: String? = null,
    val careTypeCode: String? = null
)

data class M28CreateVaccinationInput(
    val careId: String?,
    val petId: String,
    val clinicId: String,
    val vaccineCode: String,
    val vaccineLabel: String,
    val administeredAtEpochMs: Long,
    val dose: String? = null,
    val batchNumber: String? = null,
    val manufacturer: String? = null,
    val nextDueAtEpochMs: Long? = null,
    val notes: String? = null
)

data class M28CreatePassportProposalInput(
    val petId: String,
    val passportId: String,
    val clinicId: String,
    val sourceCareId: String?,
    val sourceVaccinationId: String?,
    val proposalType: M28ProposalType,
    val proposedValueJson: String,
    val previousValueJson: String? = null,
    val clientRequestId: String
)

enum class M28ProposalDecision { ACCEPT, REJECT, CORRECTION_REQUESTED }

object M28ExportDisclaimer {
    const val TEXT =
        "Documento generado por LeoVer como exportación de información registrada en la plataforma; " +
            "no constituye por sí mismo registro sanitario oficial ni reemplaza obligaciones " +
            "profesionales/regulatorias aplicables."
}

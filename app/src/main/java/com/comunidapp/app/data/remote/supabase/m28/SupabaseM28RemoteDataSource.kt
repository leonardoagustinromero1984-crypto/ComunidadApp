package com.comunidapp.app.data.remote.supabase.m28

import com.comunidapp.app.data.model.M28CareStatus
import com.comunidapp.app.data.model.M28ClinicDashboardSummary
import com.comunidapp.app.data.model.M28DocumentVisibility
import com.comunidapp.app.data.model.M28ExportSnapshot
import com.comunidapp.app.data.model.M28FollowUpStatus
import com.comunidapp.app.data.model.M28GrantPurpose
import com.comunidapp.app.data.model.M28GrantStatus
import com.comunidapp.app.data.model.M28PassportUpdateProposal
import com.comunidapp.app.data.model.M28PatientSummary
import com.comunidapp.app.data.model.M28ProfessionalAccessGrant
import com.comunidapp.app.data.model.M28ProfessionalCare
import com.comunidapp.app.data.model.M28ProfessionalDocument
import com.comunidapp.app.data.model.M28ProposalDecision
import com.comunidapp.app.data.model.M28ProposalStatus
import com.comunidapp.app.data.model.M28ProposalType
import com.comunidapp.app.data.model.M28VaccinationRecord
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
data class M28GrantRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("granted_by_user_id") val grantedByUserId: String,
    @SerialName("clinic_id") val clinicId: String? = null,
    @SerialName("professional_id") val professionalId: String? = null,
    val purposes: List<String> = emptyList(),
    val status: String = "ACTIVE",
    @SerialName("valid_from") val validFrom: String? = null,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("revoked_at") val revokedAt: String? = null
)

@Serializable
data class M28PatientRelationshipRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("last_care_at") val lastCareAt: String? = null
)

@Serializable
data class M28CareRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("professional_id") val professionalId: String? = null,
    @SerialName("appointment_id") val appointmentId: String? = null,
    @SerialName("care_type_code") val careTypeCode: String,
    @SerialName("care_type_label_snapshot") val careTypeLabel: String,
    val reason: String? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("findings_summary") val findingsSummary: String? = null,
    @SerialName("clinical_notes") val clinicalNotes: String? = null,
    val observations: String? = null,
    val status: String = "DRAFT",
    val version: Int = 1,
    @SerialName("supersedes_care_id") val supersedesCareId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("finalized_by") val finalizedBy: String? = null,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class M28ProposalRow(
    val id: String,
    @SerialName("pet_id") val petId: String,
    @SerialName("passport_id") val passportId: String,
    @SerialName("source_care_id") val sourceCareId: String? = null,
    @SerialName("clinic_id") val clinicId: String,
    @SerialName("proposed_by_professional_id") val proposedByProfessionalId: String? = null,
    @SerialName("proposal_type") val proposalType: String,
    @SerialName("previous_value") val previousValue: JsonElement? = null,
    @SerialName("proposed_value") val proposedValue: JsonElement,
    val status: String = "PENDING",
    @SerialName("decision_note") val decisionNote: String? = null,
    @SerialName("decided_by") val decidedBy: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class M28ExportRequestRow(
    val id: String
)

class SupabaseM28RemoteDataSource {
    suspend fun grantProfessionalAccess(
        petId: String,
        clinicId: String?,
        professionalId: String?,
        purposes: List<M28GrantPurpose>,
        validUntilEpochMs: Long?
    ): M28ProfessionalAccessGrant = mapGrantRow(
        rpcSingle<M28GrantRow>("m28_grant_professional_access", buildJsonObject {
            put("p_pet_id", petId)
            putNullable("p_clinic_id", clinicId)
            putNullable("p_professional_id", professionalId)
            put("p_purposes", JsonArray(purposes.map { JsonPrimitive(it.name) }))
            putNullable("p_valid_until", validUntilEpochMs?.let(::iso))
        })
    )

    suspend fun revokeProfessionalAccess(grantId: String): M28ProfessionalAccessGrant =
        mapGrantRow(rpcSingle<M28GrantRow>("m28_revoke_professional_access", idParam("p_grant_id", grantId)))

    suspend fun listGrantsForResponsible(petId: String): List<M28ProfessionalAccessGrant> =
        rpcList<M28GrantRow>("m28_list_grants_for_responsible", buildJsonObject { put("p_pet_id", petId) })
            .map { mapGrantRow(it) }

    suspend fun listClinicPatients(clinicId: String): List<M28PatientSummary> {
        val rows = rpcList<M28PatientRelationshipRow>(
            "m28_list_clinic_patients",
            buildJsonObject { put("p_clinic_id", clinicId) }
        )
        return rows.map { row ->
            M28PatientSummary(
                petId = row.petId,
                petName = row.petId.take(8),
                species = null,
                lastCareAtEpochMs = row.lastCareAt?.let(::parseTs),
                relationshipId = row.id
            )
        }
    }

    suspend fun createCareDraft(
        clinicId: String,
        petId: String,
        appointmentId: String?,
        careTypeCode: String,
        clientRequestId: String
    ): M28ProfessionalCare = mapCareRow(
        rpcSingle<M28CareRow>("m28_create_care_draft", buildJsonObject {
            put("p_clinic_id", clinicId)
            put("p_pet_id", petId)
            putNullable("p_appointment_id", appointmentId)
            put("p_care_type_code", careTypeCode)
            put("p_client_request_id", clientRequestId)
        })
    )

    suspend fun updateCareDraft(
        careId: String,
        reason: String?,
        weightKg: Double?,
        findingsSummary: String?,
        clinicalNotes: String?,
        observations: String?,
        careTypeCode: String?
    ): M28ProfessionalCare = mapCareRow(
        rpcSingle<M28CareRow>("m28_update_care_draft", buildJsonObject {
            put("p_care_id", careId)
            putNullable("p_reason", reason)
            if (weightKg != null) put("p_weight_kg", weightKg) else put("p_weight_kg", null as String?)
            putNullable("p_findings_summary", findingsSummary)
            putNullable("p_clinical_notes", clinicalNotes)
            putNullable("p_observations", observations)
            putNullable("p_care_type_code", careTypeCode)
        })
    )

    suspend fun finalizeCare(careId: String): M28ProfessionalCare =
        mapCareRow(rpcSingle<M28CareRow>("m28_finalize_care", idParam("p_care_id", careId)))

    suspend fun supersedeCare(careId: String, correctionReason: String): M28ProfessionalCare =
        mapCareRow(
            rpcSingle<M28CareRow>(
                "m28_supersede_care",
                buildJsonObject {
                    put("p_care_id", careId)
                    put("p_correction_reason", correctionReason)
                }
            )
        )

    suspend fun getCare(careId: String): M28ProfessionalCare =
        mapCareRow(rpcSingle<M28CareRow>("m28_get_care", idParam("p_care_id", careId)))

    suspend fun listPetCares(clinicId: String, petId: String): List<M28ProfessionalCare> =
        rpcList<M28CareRow>("m28_list_pet_cares", buildJsonObject {
            put("p_clinic_id", clinicId)
            put("p_pet_id", petId)
        }).map { mapCareRow(it) }

    suspend fun createVaccinationRecord(params: JsonObject): M28VaccinationRecord =
        mapVaccination(rpcSingle<JsonElement>("m28_create_vaccination_record", params))

    suspend fun createPassportProposal(params: JsonObject): M28PassportUpdateProposal =
        mapProposalRow(rpcSingle<M28ProposalRow>("m28_create_passport_update_proposal", params))

    suspend fun listProposalsForResponsible(petId: String): List<M28PassportUpdateProposal> =
        rpcList<M28ProposalRow>("m28_list_passport_update_proposals_for_responsible", buildJsonObject { put("p_pet_id", petId) })
            .map { mapProposalRow(it) }

    suspend fun decideProposal(
        proposalId: String,
        decision: M28ProposalDecision,
        decisionNote: String?
    ): M28PassportUpdateProposal = mapProposalRow(
        rpcSingle<M28ProposalRow>("m28_decide_passport_update_proposal", buildJsonObject {
            put("p_proposal_id", proposalId)
            put("p_decision", decision.name)
            putNullable("p_decision_note", decisionNote)
        })
    )

    suspend fun requestExport(clinicId: String, clientRequestId: String, petId: String?): M28ExportSnapshot {
        val row = rpcSingle<M28ExportRequestRow>(
            "m28_request_export",
            buildJsonObject {
                put("p_clinic_id", clinicId)
                put("p_client_request_id", clientRequestId)
                putNullable("p_pet_id", petId)
            }
        )
        return getExportSnapshot(row.id)
    }

    suspend fun getExportSnapshot(exportRequestId: String): M28ExportSnapshot {
        val el = rpcSingle<JsonElement>("m28_get_export_snapshot", buildJsonObject {
            put("p_export_request_id", exportRequestId)
        })
        return mapExport(el)
    }

    suspend fun clinicDashboard(clinicId: String): M28ClinicDashboardSummary {
        val o = rpcSingle<JsonObject>("m28_list_clinic_dashboard_summary", buildJsonObject { put("p_clinic_id", clinicId) })
        return M28ClinicDashboardSummary(
            appointmentsToday = o.int("appointments_today"),
            pendingFollowUps = o.int("follow_ups_pending"),
            recentPatients = emptyList(),
            pendingProposals = o.int("passport_proposals_pending")
        )
    }

    private suspend inline fun <reified T> rpcSingle(fn: String, params: JsonObject): T =
        supabase.postgrest.rpc(function = fn, parameters = params).decodeSingle()

    private suspend inline fun <reified T> rpcList(fn: String, params: JsonObject): List<T> =
        supabase.postgrest.rpc(function = fn, parameters = params).decodeList()

    private fun mapGrantRow(row: M28GrantRow): M28ProfessionalAccessGrant =
        M28ProfessionalAccessGrant(
            id = row.id,
            petId = row.petId,
            grantedByUserId = row.grantedByUserId,
            clinicId = row.clinicId,
            professionalId = row.professionalId,
            purposes = row.purposes.mapNotNull { p ->
                M28GrantPurpose.entries.firstOrNull { it.name == p.uppercase() }
            },
            status = enumValue(row.status, M28GrantStatus.ACTIVE),
            validFromEpochMs = parseTs(row.validFrom),
            validUntilEpochMs = row.validUntil?.let(::parseTs),
            revokedAtEpochMs = row.revokedAt?.let(::parseTs)
        )

    private fun mapCareRow(row: M28CareRow): M28ProfessionalCare =
        M28ProfessionalCare(
            id = row.id,
            petId = row.petId,
            clinicId = row.clinicId,
            professionalId = row.professionalId,
            appointmentId = row.appointmentId,
            careTypeCode = row.careTypeCode,
            careTypeLabel = row.careTypeLabel,
            reason = row.reason,
            weightKg = row.weightKg,
            findingsSummary = row.findingsSummary,
            clinicalNotes = row.clinicalNotes,
            observations = row.observations,
            status = enumValue(row.status, M28CareStatus.DRAFT),
            version = row.version,
            supersedesCareId = row.supersedesCareId,
            createdBy = row.createdBy,
            finalizedBy = row.finalizedBy,
            finalizedAtEpochMs = row.finalizedAt?.let(::parseTs),
            createdAtEpochMs = parseTs(row.createdAt),
            updatedAtEpochMs = parseTs(row.updatedAt)
        )

    private fun mapProposalRow(row: M28ProposalRow): M28PassportUpdateProposal =
        M28PassportUpdateProposal(
            id = row.id,
            petId = row.petId,
            passportId = row.passportId,
            sourceCareId = row.sourceCareId,
            clinicId = row.clinicId,
            proposedByProfessionalId = row.proposedByProfessionalId,
            proposalType = enumValue(row.proposalType, M28ProposalType.OTHER),
            previousValueJson = row.previousValue?.toString(),
            proposedValueJson = row.proposedValue.toString(),
            status = enumValue(row.status, M28ProposalStatus.PENDING),
            decisionNote = row.decisionNote,
            decidedBy = row.decidedBy,
            decidedAtEpochMs = row.decidedAt?.let(::parseTs),
            createdAtEpochMs = parseTs(row.createdAt)
        )

    private fun mapVaccination(el: JsonElement): M28VaccinationRecord {
        val o = el.jsonObject
        return M28VaccinationRecord(
            id = o.string("id"),
            careId = o.stringOrNull("care_id"),
            petId = o.string("pet_id"),
            clinicId = o.string("clinic_id"),
            professionalId = o.stringOrNull("professional_id"),
            vaccineCode = o.string("vaccine_code"),
            vaccineLabel = o.string("vaccine_label_snapshot"),
            administeredAtEpochMs = parseTs(o.stringOrNull("administered_at")),
            dose = o.stringOrNull("dose"),
            batchNumber = o.stringOrNull("batch_number"),
            manufacturer = o.stringOrNull("manufacturer"),
            nextDueAtEpochMs = o.stringOrNull("next_due_at")?.let(::parseTs),
            notes = o.stringOrNull("notes")
        )
    }

    private fun mapExport(el: JsonElement): M28ExportSnapshot {
        val o = el.jsonObject
        fun mapList(key: String): List<Map<String, String?>> =
            o[key]?.jsonArray.orEmpty().map { item ->
                item.jsonObject.entries.associate { (k, v) ->
                    k to v.jsonPrimitive.contentOrNull
                }
            }
        val petObj = o["pet"]?.jsonObject
        val petMap = petObj?.entries?.associate { (k, v) -> k to v.jsonPrimitive.contentOrNull }.orEmpty()
        return M28ExportSnapshot(
            disclaimer = o.string("disclaimer"),
            clinicName = o.string("clinic_name"),
            generatedAtEpochMs = parseTs(o.stringOrNull("generated_at")),
            pet = petMap,
            cares = mapList("cares"),
            vaccinations = mapList("vaccinations"),
            followUps = mapList("follow_ups"),
            documents = mapList("documents")
        )
    }

    private fun idParam(key: String, value: String) = buildJsonObject { put(key, value) }

    private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
        if (value == null) put(key, null as String?) else put(key, value)
    }

    private fun JsonObject.string(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull ?: error("missing $key")

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int =
        get(key)?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

    private fun parseTs(raw: String?): Long =
        raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrElse { 0L } } ?: 0L

    private fun iso(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private inline fun <reified T : Enum<T>> enumValue(raw: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
}

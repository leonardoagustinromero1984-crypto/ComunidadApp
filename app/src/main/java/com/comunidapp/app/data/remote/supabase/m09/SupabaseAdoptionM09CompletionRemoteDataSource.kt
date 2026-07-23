package com.comunidapp.app.data.remote.supabase.m09

import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** LeoVer M09 bloque 3 — RPC completion / follow-up. */
class SupabaseAdoptionM09CompletionRemoteDataSource {

    suspend fun scheduleInterview(
        adoptionId: String,
        applicationId: String,
        scheduledAtIso: String,
        type: String,
        locationOrLink: String?,
        notes: String?
    ): AdoptionInterviewRow = decodeOne(
        "m09_schedule_interview",
        buildJsonObject {
            put("p_adoption_id", adoptionId)
            put("p_application_id", applicationId)
            put("p_scheduled_at", scheduledAtIso)
            put("p_type", type)
            locationOrLink?.let { put("p_location_or_link", it) }
            notes?.let { put("p_notes", it) }
        }
    )

    suspend fun confirmInterview(id: String): AdoptionInterviewRow =
        decodeOne("m09_confirm_interview", buildJsonObject { put("p_interview_id", id) })

    suspend fun completeInterview(id: String, outcome: String?): AdoptionInterviewRow =
        decodeOne(
            "m09_complete_interview",
            buildJsonObject {
                put("p_interview_id", id)
                outcome?.let { put("p_outcome", it) }
            }
        )

    suspend fun cancelInterview(id: String): AdoptionInterviewRow =
        decodeOne("m09_cancel_interview", buildJsonObject { put("p_interview_id", id) })

    suspend fun requestDocument(
        adoptionId: String,
        applicationId: String,
        docType: String,
        required: Boolean
    ): AdoptionDocumentRow = decodeOne(
        "m09_request_document",
        buildJsonObject {
            put("p_adoption_id", adoptionId)
            put("p_application_id", applicationId)
            put("p_doc_type", docType)
            put("p_required", required)
        }
    )

    suspend fun submitDocumentReference(requirementId: String, storagePath: String): AdoptionDocumentRow =
        decodeOne(
            "m09_submit_document_reference",
            buildJsonObject {
                put("p_requirement_id", requirementId)
                put("p_storage_path", storagePath)
            }
        )

    suspend fun reviewDocument(
        requirementId: String,
        approve: Boolean,
        reason: String?
    ): AdoptionDocumentRow = decodeOne(
        "m09_review_document",
        buildJsonObject {
            put("p_requirement_id", requirementId)
            put("p_approve", approve)
            reason?.let { put("p_rejection_reason", it) }
        }
    )

    suspend fun createAgreement(
        adoptionId: String,
        applicationId: String,
        termsVersion: String,
        termsSnapshot: String
    ): AdoptionAgreementRow = decodeOne(
        "m09_create_adoption_agreement",
        buildJsonObject {
            put("p_adoption_id", adoptionId)
            put("p_application_id", applicationId)
            put("p_terms_version", termsVersion)
            put("p_terms_snapshot", termsSnapshot)
        }
    )

    suspend fun acceptAgreement(id: String): AdoptionAgreementRow =
        decodeOne("m09_accept_adoption_agreement", buildJsonObject { put("p_agreement_id", id) })

    suspend fun cancelAgreement(id: String): AdoptionAgreementRow =
        decodeOne("m09_cancel_adoption_agreement", buildJsonObject { put("p_agreement_id", id) })

    suspend fun finalizeAdoption(adoptionId: String): AdoptionFinalizationRow =
        decodeOne("m09_finalize_adoption", buildJsonObject { put("p_adoption_id", adoptionId) })

    suspend fun getFollowUpPlan(adoptionId: String): AdoptionFollowUpPlanRow =
        decodeOne("m09_get_followup_plan", buildJsonObject { put("p_adoption_id", adoptionId) })

    suspend fun completeFollowUpCheck(
        checkId: String,
        notes: String?,
        welfare: String,
        evidenceRef: String?
    ): AdoptionFollowUpCheckRow = decodeOne(
        "m09_complete_followup_check",
        buildJsonObject {
            put("p_check_id", checkId)
            notes?.let { put("p_notes", it) }
            put("p_welfare_status", welfare)
            evidenceRef?.let { put("p_evidence_ref", it) }
        }
    )

    private suspend inline fun <reified T : Any> decodeOne(name: String, params: JsonObject): T =
        supabase.postgrest.rpc(function = name, parameters = params).decodeList<T>().first()
}

package com.comunidapp.app.data.remote.supabase.m09

import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * LeoVer M09 — RPC-only writes for adoption publications.
 */
class SupabaseAdoptionM09RemoteDataSource {

    suspend fun listPublished(): List<AdoptionPublicationRow> =
        supabase.postgrest.rpc(function = "m09_list_published_adoptions")
            .decodeList()

    suspend fun listMine(): List<AdoptionPublicationRow> =
        supabase.postgrest.rpc(function = "m09_list_my_adoptions")
            .decodeList()

    suspend fun getById(adoptionId: String): AdoptionPublicationRow =
        supabase.postgrest.rpc(
            function = "m09_get_adoption",
            parameters = buildJsonObject { put("p_adoption_id", adoptionId) }
        ).decodeList<AdoptionPublicationRow>().first()

    suspend fun create(params: CreateAdoptionParams): AdoptionPublicationRow =
        supabase.postgrest.rpc(
            function = "m09_create_adoption_publication",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_title", params.title)
                put("p_description", params.description)
                put("p_requirements", params.requirements)
                put("p_location_text", params.locationText)
                put("p_publish", params.publish)
            }
        ).decodeList<AdoptionPublicationRow>().first()

    suspend fun update(params: UpdateAdoptionParams): AdoptionPublicationRow =
        supabase.postgrest.rpc(
            function = "m09_update_adoption_publication",
            parameters = buildJsonObject {
                put("p_adoption_id", params.adoptionId)
                put("p_title", params.title)
                put("p_description", params.description)
                put("p_requirements", params.requirements)
                put("p_location_text", params.locationText)
            }
        ).decodeList<AdoptionPublicationRow>().first()

    suspend fun setStatus(adoptionId: String, status: String): AdoptionPublicationRow =
        supabase.postgrest.rpc(
            function = "m09_set_adoption_status",
            parameters = buildJsonObject {
                put("p_adoption_id", adoptionId)
                put("p_status", status)
            }
        ).decodeList<AdoptionPublicationRow>().first()

    suspend fun markAdopted(adoptionId: String): AdoptionPublicationRow =
        supabase.postgrest.rpc(
            function = "m09_mark_adoption_adopted",
            parameters = buildJsonObject { put("p_adoption_id", adoptionId) }
        ).decodeList<AdoptionPublicationRow>().first()

    // --- Applications (bloque 2) ---

    suspend fun listMyApplications(): List<AdoptionApplicationRow> =
        supabase.postgrest.rpc(function = "m09_list_my_applications")
            .decodeList()

    suspend fun listReceivedApplications(status: String? = null): List<AdoptionApplicationRow> =
        supabase.postgrest.rpc(
            function = "m09_list_received_applications",
            parameters = buildJsonObject {
                status?.let { put("p_status", it) }
            }
        ).decodeList()

    suspend fun getApplication(applicationId: String): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_get_application",
            parameters = buildJsonObject { put("p_application_id", applicationId) }
        ).decodeList<AdoptionApplicationRow>().first()

    suspend fun submitApplication(params: SubmitApplicationParams): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_submit_application",
            parameters = buildJsonObject {
                put("p_adoption_id", params.adoptionId)
                put("p_message", params.message)
                params.housingType?.let { put("p_housing_type", it) }
                params.hasOtherPets?.let { put("p_has_other_pets", it) }
                params.previousExperience?.let { put("p_previous_experience", it) }
                params.contactPhone?.let { put("p_contact_phone", it) }
            }
        ).decodeList<AdoptionApplicationRow>().first()

    suspend fun withdrawApplication(applicationId: String): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_withdraw_application",
            parameters = buildJsonObject { put("p_application_id", applicationId) }
        ).decodeList<AdoptionApplicationRow>().first()

    suspend fun markApplicationUnderReview(applicationId: String): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_mark_application_under_review",
            parameters = buildJsonObject { put("p_application_id", applicationId) }
        ).decodeList<AdoptionApplicationRow>().first()

    suspend fun acceptApplication(applicationId: String): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_accept_application",
            parameters = buildJsonObject { put("p_application_id", applicationId) }
        ).decodeList<AdoptionApplicationRow>().first()

    suspend fun rejectApplication(applicationId: String, reason: String?): AdoptionApplicationRow =
        supabase.postgrest.rpc(
            function = "m09_reject_application",
            parameters = buildJsonObject {
                put("p_application_id", applicationId)
                reason?.let { put("p_rejection_reason", it) }
            }
        ).decodeList<AdoptionApplicationRow>().first()
}

package com.comunidapp.app.data.remote.supabase.m09

import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * LeoVer M09 — RPC-only writes for adoption publications.
 */
class SupabaseAdoptionM09RemoteDataSource {

    suspend fun listPublished(): List<AdoptionPublicationRow> =
        rpcList("m09_list_published_adoptions")

    suspend fun listMine(): List<AdoptionPublicationRow> =
        rpcList("m09_list_my_adoptions")

    suspend fun getById(adoptionId: String): AdoptionPublicationRow =
        rpcOne(
            "m09_get_adoption",
            buildJsonObject { put("p_adoption_id", adoptionId) }
        )

    suspend fun create(params: CreateAdoptionParams): AdoptionPublicationRow =
        rpcOne(
            "m09_create_adoption_publication",
            buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_title", params.title)
                put("p_description", params.description)
                put("p_requirements", params.requirements)
                put("p_location_text", params.locationText)
                put("p_publish", params.publish)
            }
        )

    suspend fun update(params: UpdateAdoptionParams): AdoptionPublicationRow =
        rpcOne(
            "m09_update_adoption_publication",
            buildJsonObject {
                put("p_adoption_id", params.adoptionId)
                put("p_title", params.title)
                put("p_description", params.description)
                put("p_requirements", params.requirements)
                put("p_location_text", params.locationText)
            }
        )

    suspend fun setStatus(adoptionId: String, status: String): AdoptionPublicationRow =
        rpcOne(
            "m09_set_adoption_status",
            buildJsonObject {
                put("p_adoption_id", adoptionId)
                put("p_status", status)
            }
        )

    suspend fun markAdopted(adoptionId: String): AdoptionPublicationRow =
        rpcOne(
            "m09_mark_adoption_adopted",
            buildJsonObject { put("p_adoption_id", adoptionId) }
        )

    // --- Applications (bloque 2) ---

    suspend fun listMyApplications(): List<AdoptionApplicationRow> =
        rpcList("m09_list_my_applications")

    suspend fun listReceivedApplications(status: String? = null): List<AdoptionApplicationRow> =
        rpcList(
            "m09_list_received_applications",
            buildJsonObject {
                status?.let { put("p_status", it) }
            }
        )

    suspend fun getApplication(applicationId: String): AdoptionApplicationRow =
        rpcOne(
            "m09_get_application",
            buildJsonObject { put("p_application_id", applicationId) }
        )

    suspend fun submitApplication(params: SubmitApplicationParams): AdoptionApplicationRow =
        rpcOne(
            "m09_submit_application",
            buildJsonObject {
                put("p_adoption_id", params.adoptionId)
                put("p_message", params.message)
                params.housingType?.let { put("p_housing_type", it) }
                params.hasOtherPets?.let { put("p_has_other_pets", it) }
                params.previousExperience?.let { put("p_previous_experience", it) }
                params.contactPhone?.let { put("p_contact_phone", it) }
            }
        )

    suspend fun withdrawApplication(applicationId: String): AdoptionApplicationRow =
        rpcOne(
            "m09_withdraw_application",
            buildJsonObject { put("p_application_id", applicationId) }
        )

    suspend fun markApplicationUnderReview(applicationId: String): AdoptionApplicationRow =
        rpcOne(
            "m09_mark_application_under_review",
            buildJsonObject { put("p_application_id", applicationId) }
        )

    suspend fun acceptApplication(applicationId: String): AdoptionApplicationRow =
        rpcOne(
            "m09_accept_application",
            buildJsonObject { put("p_application_id", applicationId) }
        )

    suspend fun rejectApplication(applicationId: String, reason: String?): AdoptionApplicationRow =
        rpcOne(
            "m09_reject_application",
            buildJsonObject {
                put("p_application_id", applicationId)
                reason?.let { put("p_rejection_reason", it) }
            }
        )

    private suspend inline fun <reified T : Any> rpcOne(
        name: String,
        params: kotlinx.serialization.json.JsonObject = buildJsonObject { }
    ): T {
        val element: JsonElement = supabase.postgrest.rpc(function = name, parameters = params)
            .decodeAs()
        return M09RpcDecoding.decodeRow(element)
    }

    private suspend inline fun <reified T : Any> rpcList(
        name: String,
        params: kotlinx.serialization.json.JsonObject = buildJsonObject { }
    ): List<T> {
        val element: JsonElement = supabase.postgrest.rpc(function = name, parameters = params)
            .decodeAs()
        return M09RpcDecoding.decodeRows(element)
    }
}

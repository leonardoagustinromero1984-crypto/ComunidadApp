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
}

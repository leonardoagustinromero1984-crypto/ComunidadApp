package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * LeoVer M08 — Supabase PostgREST implementation (RPC + SELECT RLS).
 * No direct INSERT/UPDATE/DELETE on `pets`.
 */
class SupabasePetM08RemoteDataSource : PetM08RemoteDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listAccessiblePets(status: String?): List<AccessiblePetM08Row> {
        return supabase.postgrest.rpc(
            function = "m08_list_accessible_pets",
            parameters = buildJsonObject {
                if (status != null) put("p_status", status) else put("p_status", JsonNull)
            }
        ).decodeList()
    }

    override suspend fun getPetById(petId: String): PetM08Row? {
        return try {
            supabase.from("pets")
                .select {
                    filter { eq("id", petId) }
                }
                .decodeSingleOrNull()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun createPetWithPrincipal(params: CreatePetWithPrincipalParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_create_pet_with_principal",
            parameters = buildJsonObject {
                put("p_name", params.name)
                put("p_species", params.species)
                put("p_sex", params.sex)
                put("p_size", params.size)
                put("p_description", params.description)
                putNullable("p_organization_id", params.organizationId)
                putNullable("p_microchip_id", params.microchipId)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun updatePetProfile(params: UpdatePetProfileParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_update_pet_profile",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_name", params.name)
                put("p_species", params.species)
                putNullable("p_breed", params.breed)
                put("p_sex", params.sex)
                put("p_size", params.size)
                put("p_description", params.description)
                put("p_age_years", params.ageYears)
                put("p_age_months", params.ageMonths)
                putNullable("p_color", params.color)
                putNullable("p_microchip_id", params.microchipId)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun updatePetHealth(params: UpdatePetHealthParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_update_pet_health",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_vaccinations", json.encodeToJsonElement(params.vaccinations))
                put("p_reminders", json.encodeToJsonElement(params.reminders))
                putNullable("p_last_deworming", params.lastDeworming)
                putNullable("p_deworming_product", params.dewormingProduct)
                putNullable("p_last_flea_treatment", params.lastFleaTreatment)
                putNullable("p_flea_treatment_product", params.fleaTreatmentProduct)
                putNullable("p_sterilized", params.sterilized)
                putNullable("p_last_vet_visit", params.lastVetVisit)
                putNullable("p_health_notes", params.healthNotes)
                if (params.weightKg != null) put("p_weight_kg", params.weightKg) else put("p_weight_kg", JsonNull)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun getPetAccessContext(petId: String): PetAccessContextRow {
        return supabase.postgrest.rpc(
            function = "m08_get_pet_access_context",
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeList<PetAccessContextRow>().first()
    }

    override suspend fun archivePet(params: ArchivePetParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_archive_pet",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_reason", params.reason)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun restorePet(params: RestorePetParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_restore_pet",
            parameters = buildJsonObject { put("p_pet_id", params.petId) }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun markPetDeceased(params: MarkPetDeceasedParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_mark_pet_deceased",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_reason", params.reason)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun setPetAvatarAsset(params: SetPetAvatarAssetParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_set_pet_avatar_asset",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_asset_id", params.assetId)
            }
        ).decodeList<PetM08Row>().first()
    }

    override suspend fun detectDuplicates(
        params: DetectPetDuplicateParams
    ): List<PetDuplicateCandidateRow> {
        return supabase.postgrest.rpc(
            function = "m08_detect_pet_duplicate_candidates",
            parameters = buildJsonObject {
                putNullable("p_microchip", params.microchip)
                putNullable("p_name", params.name)
            }
        ).decodeList()
    }

    override suspend fun assignResponsibility(
        params: AssignPetResponsibilityParams
    ): PetResponsibilityM08Row {
        return supabase.postgrest.rpc(
            function = "m08_assign_pet_responsibility",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_role_code", params.roleCode)
                putNullable("p_person_id", params.personId)
                putNullable("p_organization_id", params.organizationId)
                putNullable("p_ends_at", params.endsAt)
                putNullable("p_reason", params.reason)
            }
        ).decodeList<PetResponsibilityM08Row>().first()
    }

    override suspend fun revokeResponsibility(
        params: RevokePetResponsibilityParams
    ): PetResponsibilityM08Row {
        return supabase.postgrest.rpc(
            function = "m08_revoke_pet_responsibility",
            parameters = buildJsonObject {
                put("p_responsibility_id", params.responsibilityId)
            }
        ).decodeList<PetResponsibilityM08Row>().first()
    }

    override suspend fun listResponsibilities(petId: String): List<PetResponsibilityM08Row> {
        return supabase.from("pet_responsibilities")
            .select {
                filter { eq("pet_id", petId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    override suspend fun grantAuthorization(
        params: GrantPetAuthorizationParams
    ): PetAuthorizationM08Row {
        return supabase.postgrest.rpc(
            function = "m08_grant_pet_authorization",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_person_id", params.personId)
                put("p_capabilities", json.encodeToJsonElement(params.capabilities))
                putNullable("p_valid_until", params.validUntil)
            }
        ).decodeList<PetAuthorizationM08Row>().first()
    }

    override suspend fun revokeAuthorization(
        params: RevokePetAuthorizationParams
    ): PetAuthorizationM08Row {
        return supabase.postgrest.rpc(
            function = "m08_revoke_pet_authorization",
            parameters = buildJsonObject {
                put("p_authorization_id", params.authorizationId)
            }
        ).decodeList<PetAuthorizationM08Row>().first()
    }

    override suspend fun listAuthorizations(petId: String): List<PetAuthorizationM08Row> {
        return supabase.from("pet_authorizations")
            .select {
                filter { eq("pet_id", petId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    override suspend fun initiateTransfer(params: InitiatePetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_initiate_pet_transfer",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_to_person_id", params.toPersonId)
                putNullable("p_to_organization_id", params.toOrganizationId)
                putNullable("p_expires_at", params.expiresAt)
            }
        ).decodeList<PetTransferM08Row>().first()
    }

    override suspend fun acceptTransfer(params: AcceptPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_accept_pet_transfer",
            parameters = buildJsonObject { put("p_transfer_id", params.transferId) }
        ).decodeList<PetTransferM08Row>().first()
    }

    override suspend fun rejectTransfer(params: RejectPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_reject_pet_transfer",
            parameters = buildJsonObject { put("p_transfer_id", params.transferId) }
        ).decodeList<PetTransferM08Row>().first()
    }

    override suspend fun cancelTransfer(params: CancelPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_cancel_pet_transfer",
            parameters = buildJsonObject {
                put("p_transfer_id", params.transferId)
                putNullable("p_reason", params.reason)
            }
        ).decodeList<PetTransferM08Row>().first()
    }

    override suspend fun listTransfers(petId: String): List<PetTransferM08Row> {
        return supabase.from("pet_transfers")
            .select {
                filter { eq("pet_id", petId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList()
    }

    override suspend fun listStatusHistory(petId: String): List<PetStatusHistoryM08Row> {
        return supabase.from("pet_status_history")
            .select {
                filter { eq("pet_id", petId) }
                // pet_status_history has changed_at (no created_at column).
                order("changed_at", Order.DESCENDING)
            }
            .decodeList()
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: String?
    ) {
        if (value.isNullOrBlank()) put(key, JsonNull) else put(key, value)
    }
}

package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.canonical.CanonicalVitacoraNumberRow
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHealthDto
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHolderRow
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetRow
import com.comunidapp.app.data.remote.supabase.canonical.toAccessibleRow
import com.comunidapp.app.data.remote.supabase.canonical.toPetM08Row
import com.comunidapp.app.data.remote.supabase.canonical.withCanonicalHealth
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalHolderMapping
import com.comunidapp.app.domain.pets.PetBirth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * LeoVer M08 — Supabase PostgREST implementation (RPC + SELECT RLS).
 * No direct INSERT/UPDATE/DELETE on `pets`.
 * Profile/avatar/health writes use CanonicalBackend RPCs; archive/restore stay on M08.
 */
class SupabasePetM08RemoteDataSource : PetM08RemoteDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun listAccessiblePets(status: String?): List<AccessiblePetM08Row> {
        val element: JsonElement = supabase.from(CanonicalBackend.PETS)
            .select {
                if (status != null) {
                    filter { eq("lifecycle_status", status) }
                }
            }
            .decodeAs()
        val rows = M08RpcDecoding.decodeRows<CanonicalPetRow>(element)
        val numbers = runCatching { loadVitacoraNumbers() }.getOrDefault(emptyMap())
        com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
            "PET-STAGE=LIST-RESULT count=${rows.size} status=${status ?: "ALL"}"
        )
        return rows.map { it.copy(publicVitacoraNumber = numbers[it.id] ?: it.publicVitacoraNumber).toAccessibleRow() }
    }

    override suspend fun getPetById(petId: String): PetM08Row? {
        val result = supabase.from(CanonicalBackend.PETS)
            .select {
                filter { eq("id", petId) }
            }
        val element = runCatching {
            json.parseToJsonElement(result.data)
        }.getOrElse { error ->
            throw M08PetException("SERIALIZATION", "PET_GET_PARSE", error)
        }
        val row = runCatching {
            M08RpcDecoding.decodeRows<CanonicalPetRow>(element).firstOrNull()?.let {
                val numbers = loadVitacoraNumbers(it.id)
                it.copy(publicVitacoraNumber = numbers[it.id] ?: it.publicVitacoraNumber).toPetM08Row()
            }
        }.getOrElse { error ->
            throw M08PetException("SERIALIZATION", "PET_GET_PARSE", error)
        } ?: return null
        return applyCanonicalHealth(row)
    }

    private suspend fun loadVitacoraNumbers(petId: String? = null): Map<String, Long> {
        val element: JsonElement = supabase.from(CanonicalBackend.VITACORA_PROFILES)
            .select {
                if (petId != null) filter { eq("pet_id", petId) }
            }
            .decodeAs()
        return M08RpcDecoding.decodeRows<CanonicalVitacoraNumberRow>(element)
            .mapNotNull { row -> row.publicVitacoraNumber?.let { row.petId to it } }
            .toMap()
    }

    private suspend fun applyCanonicalHealth(row: PetM08Row): PetM08Row {
        val result = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_GET_PET_HEALTH,
            parameters = buildJsonObject { put("p_pet_id", row.id) }
        )
        val health = runCatching {
            val element = json.parseToJsonElement(result.data)
            M08RpcDecoding.decodeRow<CanonicalPetHealthDto>(element)
        }.getOrNull() ?: return row
        return row.withCanonicalHealth(health)
    }

    override suspend fun createPetWithPrincipal(params: CreatePetWithPrincipalParams): PetM08Row {
        com.comunidapp.app.domain.auth.AuthSessionAccess.requireUser(supabase.auth)
        val speciesCode = com.comunidapp.app.domain.pets.PetSpeciesCatalog.toRpcCode(params.species)
        val result = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_CREATE_PET,
            parameters = buildJsonObject {
                put("p_name", params.name)
                put("p_species", speciesCode)
                put("p_birth_precision", params.birthPrecision)
                putNullable("p_birth_date", params.birthDate)
                if (params.birthYear != null) put("p_birth_year", params.birthYear) else put("p_birth_year", JsonNull)
                if (params.birthMonth != null) put("p_birth_month", params.birthMonth) else put("p_birth_month", JsonNull)
                if (params.estimatedAgeMonths != null) {
                    put("p_estimated_age_months", params.estimatedAgeMonths)
                } else {
                    put("p_estimated_age_months", JsonNull)
                }
                putNullable("p_estimated_as_of", params.estimatedAsOf)
            }
        )
        val decoded = CanonCreatePetUuid.decode(result.data)
        val petId = decoded.id
            ?: findOwnPetByName(params.name)?.id
            ?: throw M08PetException(
                "SERIALIZATION",
                "PET_CREATE_PARSE_${decoded.kind.uppercase()}"
            )
        return try {
            getPetById(petId) ?: fallbackCreatedRow(petId, params, speciesCode)
        } catch (error: Exception) {
            fallbackCreatedRow(petId, params, speciesCode)
        }
    }

    private fun fallbackCreatedRow(
        petId: String,
        params: CreatePetWithPrincipalParams,
        speciesCode: String
    ): PetM08Row = PetM08Row(
        id = petId,
        name = params.name,
        species = speciesCode,
        sex = params.sex,
        size = params.size,
        description = params.description
    )

    private suspend fun findOwnPetByName(name: String): PetM08Row? {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return null
        val result = supabase.from(CanonicalBackend.PETS)
            .select {
                filter {
                    eq("created_by_user_id", uid)
                    eq("name", name)
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
        val element = runCatching { json.parseToJsonElement(result.data) }.getOrNull() ?: return null
        return runCatching {
            M08RpcDecoding.decodeRows<CanonicalPetRow>(element).firstOrNull()?.toPetM08Row()
        }.getOrNull()
    }

    override suspend fun updatePetProfile(params: UpdatePetProfileParams): PetM08Row {
        val birth = PetBirth.estimatedFromDisplayAge(params.ageYears, params.ageMonths)
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_UPDATE_PET,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_name", params.name)
                put("p_species", params.species)
                put("p_breed_id", JsonNull)
                put("p_sex", params.sex)
                put("p_size", params.size)
                put("p_home_locality_id", JsonNull)
                put("p_birth_precision", birth.precision.name)
                putNullable("p_birth_date", birth.birthDate?.toString())
                if (birth.birthYear != null) put("p_birth_year", birth.birthYear) else put("p_birth_year", JsonNull)
                if (birth.birthMonth != null) put("p_birth_month", birth.birthMonth) else put("p_birth_month", JsonNull)
                if (birth.estimatedAgeMonths != null) {
                    put("p_estimated_age_months", birth.estimatedAgeMonths)
                } else {
                    put("p_estimated_age_months", JsonNull)
                }
                putNullable("p_estimated_as_of", birth.estimatedAsOf?.toString())
            }
        )
        return getPetById(params.petId) ?: error("PET_UPDATE_EMPTY")
    }

    override suspend fun updatePetHealth(params: UpdatePetHealthParams): PetM08Row {
        params.vaccinations.forEach { vaccination ->
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_VACCINATION,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_vaccine_name", vaccination.name)
                    putNullable("p_administered_on", vaccination.date)
                }
            )
        }
        if (!params.lastDeworming.isNullOrBlank()) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_PARASITE_TREATMENT,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_kind", "DEWORMING")
                    putNullable("p_product_name", params.dewormingProduct)
                    put("p_treated_on", params.lastDeworming)
                }
            )
        }
        if (!params.lastFleaTreatment.isNullOrBlank()) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_PARASITE_TREATMENT,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_kind", "ANTIPARASITIC")
                    putNullable("p_product_name", params.fleaTreatmentProduct)
                    put("p_treated_on", params.lastFleaTreatment)
                }
            )
        }
        if (params.weightKg != null && params.weightKg > 0f) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_WEIGHT,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_kilograms", params.weightKg.toDouble())
                    put("p_measured_on", java.time.LocalDate.now().toString())
                }
            )
        }
        if (!params.healthNotes.isNullOrBlank()) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_SET_PET_CARE_INSTRUCTIONS,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_specials", params.healthNotes)
                }
            )
        }
        params.allergies.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_ALLERGY,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_name", name)
                }
            )
        }
        params.medications.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_MEDICATION,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_name", name)
                    put("p_instructions", JsonNull)
                }
            )
        }
        params.conditions.map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_RECORD_PET_CONDITION,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_name", name)
                }
            )
        }
        return getPetById(params.petId) ?: error("PET_HEALTH_EMPTY")
    }

    override suspend fun getPetAccessContext(petId: String): PetAccessContextRow {
        val pet = getPetById(petId) ?: throw M08PetException("PET_NOT_FOUND", "PET_NOT_FOUND")
        val holders = runCatching { listResponsibilities(petId) }.getOrDefault(emptyList())
        val relation = when {
            holders.any { it.roleCode == "PRINCIPAL" || it.roleCode == "OWNER" } -> "OWNER"
            holders.any { !it.organizationId.isNullOrBlank() } -> "ORG_RESPONSIBLE"
            holders.isNotEmpty() -> "AUTHORIZED"
            else -> "HOLDER"
        }
        return PetAccessContextRow(
            petId = pet.id,
            relationCode = relation,
            canRead = true,
            canUpdate = true,
            canManageHealth = true,
            canManageMedia = true,
            canManageResponsibilities = true,
            canViewHistory = true
        )
    }

    override suspend fun archivePet(params: ArchivePetParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_archive_pet",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_reason", params.reason)
            }
        ).rpcOne()
    }

    override suspend fun restorePet(params: RestorePetParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_restore_pet",
            parameters = buildJsonObject { put("p_pet_id", params.petId) }
        ).rpcOne()
    }

    override suspend fun markPetDeceased(params: MarkPetDeceasedParams): PetM08Row {
        return supabase.postgrest.rpc(
            function = "m08_mark_pet_deceased",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_reason", params.reason)
            }
        ).rpcOne()
    }

    override suspend fun setPetAvatarAsset(params: SetPetAvatarAssetParams): PetM08Row {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_SET_PET_AVATAR,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_media_asset_id", params.assetId)
            }
        )
        return try {
            getPetById(params.petId)?.copy(avatarFileAssetId = params.assetId)
                ?: PetM08Row(
                    id = params.petId,
                    name = "",
                    species = "OTHER",
                    sex = "UNKNOWN",
                    size = "UNKNOWN",
                    avatarFileAssetId = params.assetId
                )
        } catch (_: Exception) {
            PetM08Row(
                id = params.petId,
                name = "",
                species = "OTHER",
                sex = "UNKNOWN",
                size = "UNKNOWN",
                avatarFileAssetId = params.assetId
            )
        }
    }

    override suspend fun detectDuplicates(
        params: DetectPetDuplicateParams
    ): List<PetDuplicateCandidateRow> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = "m08_detect_pet_duplicate_candidates",
            parameters = buildJsonObject {
                putNullable("p_microchip", params.microchip)
                putNullable("p_name", params.name)
            }
        ).decodeAs()
        return M08RpcDecoding.decodeRows(element)
    }

    override suspend fun assignResponsibility(
        params: AssignPetResponsibilityParams
    ): PetResponsibilityM08Row {
        if (!params.organizationId.isNullOrBlank()) {
            val linkId = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_SET_ORG_RESPONSIBLE,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_org_id", params.organizationId)
                }
            ).decodeAs<String>()
            return PetResponsibilityM08Row(
                id = linkId,
                petId = params.petId,
                roleCode = "RESPONSIBLE",
                organizationId = params.organizationId,
                createdBy = ""
            )
        }
        val personId = params.personId ?: error("PERSON_REQUIRED")
        if (params.roleCode.equals("TEMPORARY_CUSTODIAN", ignoreCase = true)) {
            val custodyId = supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_OPEN_CUSTODY,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    put("p_kind", "PERSON")
                    put("p_person", personId)
                    put("p_org", JsonNull)
                    put("p_source", "FAMILY")
                    put("p_source_id", params.petId)
                    put("p_purpose", "TEMPORARY_CUSTODY")
                }
            ).decodeAs<String>()
            return PetResponsibilityM08Row(
                id = custodyId,
                petId = params.petId,
                roleCode = "TEMPORARY_CUSTODIAN",
                personId = personId,
                createdBy = ""
            )
        }
        val role = if (params.roleCode.equals("OWNER", ignoreCase = true)) "OWNER" else "AUTHORIZED"
        val linkId = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ADD_PET_PERSON,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_person_id", personId)
                put("p_role", role)
            }
        ).decodeAs<String>()
        return PetResponsibilityM08Row(
            id = linkId,
            petId = params.petId,
            roleCode = role,
            personId = personId,
            createdBy = ""
        )
    }

    override suspend fun revokeResponsibility(
        params: RevokePetResponsibilityParams
    ): PetResponsibilityM08Row {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_END_PET_RESPONSIBILITY,
            parameters = buildJsonObject { put("p_link_id", params.responsibilityId) }
        )
        return PetResponsibilityM08Row(
            id = params.responsibilityId,
            petId = "",
            roleCode = "ENDED",
            personId = null,
            createdBy = ""
        )
    }

    override suspend fun listResponsibilities(petId: String): List<PetResponsibilityM08Row> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_PET_HOLDERS,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeAs()
        val holders = M08RpcDecoding.decodeRows<CanonicalPetHolderRow>(element)
        return CanonicalHolderMapping.map(
            holders.map { holder ->
                CanonicalHolderMapping.HolderInput(
                    linkId = holder.linkId,
                    holderKind = holder.holderKind,
                    role = holder.role,
                    status = holder.status,
                    personId = holder.personId,
                    organizationId = holder.organizationId,
                    displayName = holder.displayName
                )
            }
        ).map { mapped ->
            PetResponsibilityM08Row(
                id = mapped.linkId,
                petId = petId,
                roleCode = mapped.uiRole.name,
                personId = mapped.personId,
                organizationId = mapped.organizationId,
                status = mapped.status,
                createdBy = "",
                displayName = mapped.displayName
            )
        }
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
        ).rpcOne()
    }

    override suspend fun revokeAuthorization(
        params: RevokePetAuthorizationParams
    ): PetAuthorizationM08Row {
        return supabase.postgrest.rpc(
            function = "m08_revoke_pet_authorization",
            parameters = buildJsonObject {
                put("p_authorization_id", params.authorizationId)
            }
        ).rpcOne()
    }

    override suspend fun listAuthorizations(petId: String): List<PetAuthorizationM08Row> {
        val element: JsonElement = supabase.from("pet_authorizations")
            .select {
                filter { eq("pet_id", petId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeAs()
        return M08RpcDecoding.decodeRows(element)
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
        ).rpcOne()
    }

    override suspend fun acceptTransfer(params: AcceptPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_accept_pet_transfer",
            parameters = buildJsonObject { put("p_transfer_id", params.transferId) }
        ).rpcOne()
    }

    override suspend fun rejectTransfer(params: RejectPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_reject_pet_transfer",
            parameters = buildJsonObject { put("p_transfer_id", params.transferId) }
        ).rpcOne()
    }

    override suspend fun cancelTransfer(params: CancelPetTransferParams): PetTransferM08Row {
        return supabase.postgrest.rpc(
            function = "m08_cancel_pet_transfer",
            parameters = buildJsonObject {
                put("p_transfer_id", params.transferId)
                putNullable("p_reason", params.reason)
            }
        ).rpcOne()
    }

    override suspend fun listTransfers(petId: String): List<PetTransferM08Row> {
        val element: JsonElement = supabase.from("pet_transfers")
            .select {
                filter { eq("pet_id", petId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeAs()
        return M08RpcDecoding.decodeRows(element)
    }

    override suspend fun listStatusHistory(petId: String): List<PetStatusHistoryM08Row> {
        val element: JsonElement = supabase.from("pet_status_history")
            .select {
                filter { eq("pet_id", petId) }
                // pet_status_history has changed_at (no created_at column).
                order("changed_at", Order.DESCENDING)
            }
            .decodeAs()
        return M08RpcDecoding.decodeRows(element)
    }

    private suspend inline fun <reified T : Any> io.github.jan.supabase.postgrest.result.PostgrestResult.rpcOne(): T {
        val element: JsonElement = decodeAs()
        return M08RpcDecoding.decodeRow(element)
    }

    private suspend inline fun <reified T : Any> io.github.jan.supabase.postgrest.result.PostgrestResult.rpcOneOrNull(): T? {
        return runCatching { rpcOne<T>() }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        key: String,
        value: String?
    ) {
        if (value.isNullOrBlank()) put(key, JsonNull) else put(key, value)
    }
}

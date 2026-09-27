package com.comunidapp.app.data.remote.supabase.m08

import com.comunidapp.app.data.remote.supabase.canonical.CanonicalVitacoraNumberRow
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetHolderRow
import com.comunidapp.app.data.remote.supabase.canonical.CanonicalPetRow
import com.comunidapp.app.data.remote.supabase.canonical.toAccessibleRow
import com.comunidapp.app.data.remote.supabase.canonical.toPetM08Row
import com.comunidapp.app.data.remote.supabase.canonical.withCanonicalHealth
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import com.comunidapp.app.domain.canonical.CanonicalHolderMapping
import com.comunidapp.app.data.repository.CanonicalCareContextRow
import com.comunidapp.app.domain.pets.PetBirth
import com.comunidapp.app.domain.pets.PetCustodyAuthority
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * LeoVer M08 — Supabase PostgREST implementation (RPC + SELECT RLS).
 * No direct INSERT/UPDATE/DELETE on `pets`.
 * Profile/avatar/health writes use CanonicalBackend RPCs; archive uses canon_archive_pet.
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
        val decoded = runCatching {
            M08RpcDecoding.decodeRows<CanonicalPetRow>(element).firstOrNull()
        }.getOrElse { error ->
            throw M08PetException("SERIALIZATION", "PET_GET_PARSE", error)
        } ?: return null
        val numbers = runCatching { loadVitacoraNumbers(decoded.id) }.getOrDefault(emptyMap())
        val row = decoded
            .copy(publicVitacoraNumber = numbers[decoded.id] ?: decoded.publicVitacoraNumber)
            .toPetM08Row()
        val withOwner = attachActiveOwner(row)
        return attachSpeciesName(
            attachBreedName(applyCanonicalHealth(withOwner), decoded.breedId, decoded.speciesCode),
            decoded.speciesCode
        )
    }

    private suspend fun loadCreatedByUserId(petId: String): String? {
        val result = supabase.from(CanonicalBackend.PETS)
            .select {
                filter { eq("id", petId) }
            }
        val element = json.parseToJsonElement(result.data)
        return M08RpcDecoding.decodeRows<CanonicalPetRow>(element)
            .firstOrNull()
            ?.createdByUserId
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun attachActiveOwner(row: PetM08Row): PetM08Row {
        val creatorId = row.createdByUserId?.takeIf { it.isNotBlank() }
        if (creatorId != null) {
            return row.copy(ownerId = creatorId)
        }
        if (!row.ownerId.isNullOrBlank()) return row
        val ownerId = runCatching { loadCanonicalHolderRows(row.id) }.getOrNull()
            ?.firstOrNull {
                it.status.equals("ACTIVE", ignoreCase = true) &&
                    it.role.equals("OWNER", ignoreCase = true) &&
                    it.holderKind.equals("PERSON", ignoreCase = true)
            }
            ?.personId
            ?.takeIf { it.isNotBlank() }
            ?: return row
        return row.copy(ownerId = ownerId)
    }

    private suspend fun attachSpeciesName(row: PetM08Row, speciesCode: String): PetM08Row {
        if (speciesCode.isBlank()) return row
        val species = runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_GET_SPECIES,
                buildJsonObject { put("p_species_id", speciesCode) }
            ).decodeAs()
            M08RpcDecoding.decodeRows<SpeciesCatalogRpcRow>(element).firstOrNull()
                ?: runCatching { M08RpcDecoding.decodeRow<SpeciesCatalogRpcRow>(element) }.getOrNull()
        }.getOrNull() ?: return row
        return row.copy(
            speciesName = species.name,
            secondaryLabelSingular = species.secondaryLabelSingular
        )
    }

    private suspend fun attachBreedName(
        row: PetM08Row,
        breedId: String?,
        speciesCode: String
    ): PetM08Row {
        val active = listBreedsForSpecies(speciesCode)
        val name = PetBreedCatalog.nameForId(active, breedId)
            ?: historicalBreed(breedId)?.name
            ?: row.breed
        val withId = if (breedId.isNullOrBlank()) row else row.copy(breedId = breedId)
        return if (name.isNullOrBlank()) withId else withId.copy(breed = name)
    }

    private suspend fun historicalBreed(breedId: String?): com.comunidapp.app.data.repository.CatalogBreed? {
        if (breedId.isNullOrBlank()) return null
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_GET_BREED,
                buildJsonObject { put("p_id", breedId) }
            ).decodeAs()
            M08RpcDecoding.decodeRows<BreedCatalogRpcRow>(element).firstOrNull()
                ?: runCatching { M08RpcDecoding.decodeRow<BreedCatalogRpcRow>(element) }.getOrNull()
        }.getOrNull()?.let { row ->
            com.comunidapp.app.data.repository.CatalogBreed(
                id = row.id,
                speciesCode = row.speciesCode,
                name = row.name,
                sortKey = row.sortKey,
                active = row.active
            )
        }
    }

    private suspend fun resolveBreedId(speciesCode: String, breedName: String?): String? {
        return PetBreedCatalog.idForName(listBreedsForSpecies(speciesCode), breedName)
    }

    private suspend fun listBreedsForSpecies(speciesCode: String): List<com.comunidapp.app.data.repository.CatalogBreed> {
        if (speciesCode.isBlank()) return emptyList()
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_LIST_BREEDS,
                buildJsonObject { put("p_species_code", speciesCode) }
            ).decodeAs()
            M08RpcDecoding.decodeRows<BreedCatalogRpcRow>(element).map { row ->
                com.comunidapp.app.data.repository.CatalogBreed(
                    id = row.id,
                    speciesCode = row.speciesCode,
                    name = row.name,
                    sortKey = row.sortKey,
                    active = row.active
                )
            }
        }.getOrDefault(emptyList())
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
        val result = runCatching {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_GET_PET_HEALTH,
                parameters = buildJsonObject { put("p_pet_id", row.id) }
            )
        }
        if (result.isFailure) {
            val reason = sanitizeHealthReadError(result.exceptionOrNull())
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-HEALTH-READ RPC-FAIL petId=${row.id} reason=$reason"
            )
            return row.copy(healthReadFailed = true)
        }
        val health = runCatching {
            val rpc = result.getOrThrow()
            val decoded = runCatching { rpc.decodeAs<JsonElement>() }.getOrNull()
            com.comunidapp.app.data.remote.supabase.canonical.PetHealthRpcPayload.resolve(
                decodedElement = decoded,
                rawData = rpc.data
            )
        }
        if (health.isFailure) {
            val reason = sanitizeHealthReadError(health.exceptionOrNull())
            com.comunidapp.app.domain.pets.PetCreateDiagnostic.logStaging(
                "PET-HEALTH-READ DECODE-FAIL petId=${row.id} reason=$reason"
            )
            return row.copy(healthReadFailed = true)
        }
        return row.withCanonicalHealth(health.getOrThrow())
    }

    private fun sanitizeHealthReadError(error: Throwable?): String {
        val raw = error?.message.orEmpty()
        return raw
            .replace(Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""), "[email]")
            .replace(Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"""), "[token]")
            .take(160)
            .ifBlank { error?.javaClass?.simpleName ?: "UNKNOWN" }
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
                put("p_management_context_kind", params.managementContextKind ?: "PERSON")
                putNullable("p_management_context_id", params.managementContextId)
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
        val breedId = resolveBreedId(params.species, params.breed) ?: params.breedId
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_UPDATE_PET,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_name", params.name)
                put("p_species", params.species)
                if (breedId != null) put("p_breed_id", breedId) else put("p_breed_id", JsonNull)
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
        if (!com.comunidapp.app.domain.pets.PetHealthPersist.hasData(params)) {
            return getPetById(params.petId) ?: error("PET_HEALTH_EMPTY")
        }
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
        if (params.sterilized != null ||
            !params.lastVetVisit.isNullOrBlank() ||
            !params.healthNotes.isNullOrBlank()
        ) {
            supabase.postgrest.rpc(
                function = CanonicalBackend.RPC_UPSERT_PET_DECLARED_HEALTH_PROFILE,
                parameters = buildJsonObject {
                    put("p_pet_id", params.petId)
                    putNullable("p_notes", params.healthNotes)
                    putNullable("p_sterilized", params.sterilized)
                    putNullable("p_last_vet_visit", params.lastVetVisit)
                }
            )
        }
        return getPetById(params.petId) ?: error("PET_HEALTH_EMPTY")
    }

    override suspend fun getPetAccessContext(petId: String): PetAccessContextRow {
        runCatching { loadCanonicalCareContext(petId) }.getOrNull()?.let { return it }
        val pet = getPetById(petId) ?: throw M08PetException("PET_NOT_FOUND", "PET_NOT_FOUND")
        val holderRows = runCatching { loadCanonicalHolderRows(petId) }.getOrElse { emptyList() }
        val holders = runCatching { listResponsibilities(petId) }.getOrDefault(emptyList())
        val uid = supabase.auth.currentUserOrNull()?.id
        val createdBy = pet.createdByUserId?.takeIf { it.isNotBlank() } ?: pet.ownerId
        // Prefer live PRINCIPAL / OWNER holders over creator — creator is not current custodian
        // after a care transfer.
        val principalHolder = holderRows.firstOrNull {
            it.status.equals("ACTIVE", ignoreCase = true) &&
                it.role.equals("PRINCIPAL", ignoreCase = true)
        } ?: holderRows.firstOrNull {
            it.status.equals("ACTIVE", ignoreCase = true) &&
                it.role.equals("OWNER", ignoreCase = true)
        }
        val custodianKind = when {
            principalHolder?.holderKind.equals("ORGANIZATION", ignoreCase = true) -> "ORGANIZATION"
            !principalHolder?.organizationId.isNullOrBlank() &&
                principalHolder?.personId.isNullOrBlank() -> "ORGANIZATION"
            else -> pet.managementContextKind?.takeIf { it.equals("ORGANIZATION", true) } ?: "PERSON"
        }
        val custodianPersonId = when (custodianKind) {
            "PERSON" -> principalHolder?.personId?.takeIf { it.isNotBlank() } ?: createdBy
            else -> null
        }
        val custodianOrgId = when (custodianKind) {
            "ORGANIZATION" -> principalHolder?.organizationId?.takeIf { it.isNotBlank() }
                ?: pet.managementContextId
            else -> null
        }
        val isCustodian = PetCustodyAuthority.isCurrentPersonCustodian(uid, custodianKind, custodianPersonId)
        val activePersonOwners = holderRows.filter {
            it.status.equals("ACTIVE", ignoreCase = true) &&
                it.role.equals("OWNER", ignoreCase = true) &&
                it.holderKind.equals("PERSON", ignoreCase = true)
        }
        val isActiveOwner = uid != null && activePersonOwners.any { it.personId == uid }
        val canEdit = isActiveOwner || isCustodian
        val relation = when {
            isCustodian -> "CUSTODIAN"
            canEdit -> "OWNER"
            holders.any { !it.organizationId.isNullOrBlank() } -> "ORG_RESPONSIBLE"
            holders.isNotEmpty() -> "AUTHORIZED"
            else -> "HOLDER"
        }
        val principalName = principalHolder?.displayName?.trim()?.takeIf { it.isNotEmpty() }
            ?: holders.firstOrNull {
                it.roleCode.equals("PRINCIPAL", ignoreCase = true) ||
                    it.roleCode.equals("OWNER", ignoreCase = true)
            }?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        return PetAccessContextRow(
            petId = pet.id,
            relationCode = relation,
            principalPersonId = custodianPersonId,
            principalOrganizationId = custodianOrgId,
            principalDisplayName = principalName,
            canRead = true,
            canUpdate = canEdit,
            canManageHealth = canEdit,
            canManageMedia = isCustodian,
            canManageResponsibilities = PetCustodyAuthority.canManageResponsibilities(
                uid, custodianKind, custodianPersonId
            ),
            canManageAuthorizations = isCustodian,
            canInitiateTransfer = PetCustodyAuthority.canInitiateTransfer(
                uid, custodianKind, custodianPersonId
            ),
            canCancelTransfer = isCustodian,
            canArchive = PetCustodyAuthority.canArchive(uid, custodianKind, custodianPersonId),
            canMarkDeceased = PetCustodyAuthority.canMarkDeceased(uid, custodianKind, custodianPersonId),
            canViewHistory = true
        )
    }

    private suspend fun loadCanonicalCareContext(petId: String): PetAccessContextRow {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_GET_PET_CARE_CONTEXT,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        ).decodeAs()
        val row = M08RpcDecoding.decodeRow<CanonicalCareContextRow>(element)
        return PetAccessContextRow(
            petId = row.petId,
            relationCode = row.relationCode,
            principalPersonId = row.principalPersonId,
            principalOrganizationId = row.principalOrganizationId,
            principalDisplayName = row.principalDisplayName,
            canRead = row.canRead,
            canUpdate = row.canUpdate,
            canManageHealth = row.canManageHealth,
            canManageMedia = row.canManageMedia,
            canManageResponsibilities = row.canManageResponsibilities,
            canManageAuthorizations = row.canManageAuthorizations,
            canInitiateTransfer = row.canInitiateTransfer,
            canAcceptTransfer = row.canAcceptTransfer,
            canCancelTransfer = row.canCancelTransfer,
            canArchive = row.canArchive,
            canRestore = row.canRestore,
            canMarkDeceased = row.canMarkDeceased,
            canViewHistory = row.canViewHistory
        )
    }

    override suspend fun archivePet(params: ArchivePetParams): PetM08Row {
        supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_ARCHIVE_PET,
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                putNullable("p_reason", params.reason)
            }
        )
        return getPetById(params.petId) ?: throw M08PetException("PET_NOT_FOUND", "PET_NOT_FOUND")
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
        val holders = loadCanonicalHolderRows(petId)
        val creatorPersonId = runCatching { loadCreatedByUserId(petId) }.getOrNull()
        val mappedHolders = CanonicalHolderMapping.map(
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
            },
            creatorPersonId = creatorPersonId
        )
        val careByLink = holders.associate { it.linkId to it.careRole }
        return mappedHolders.map { mapped ->
            PetResponsibilityM08Row(
                id = mapped.linkId,
                petId = petId,
                roleCode = mapped.uiRole.name,
                personId = mapped.personId,
                organizationId = mapped.organizationId,
                status = mapped.status,
                createdBy = "",
                displayName = mapped.displayName,
                careRole = careByLink[mapped.linkId]
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

    private suspend fun loadCanonicalHolderRows(petId: String): List<CanonicalPetHolderRow> {
        val result = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_PET_HOLDERS,
            parameters = buildJsonObject { put("p_pet_id", petId) }
        )
        val element = runCatching { result.decodeAs<JsonElement>() }
            .getOrElse { json.parseToJsonElement(result.data) }
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

    override suspend fun listPetsForPersonProfile(personUserId: String): List<ProfilePetRow> {
        val element: JsonElement = supabase.postgrest.rpc(
            function = CanonicalBackend.RPC_LIST_PETS_FOR_PERSON_PROFILE,
            parameters = buildJsonObject { put("p_user_id", personUserId) }
        ).decodeAs()
        return M08RpcDecoding.decodeRows(element)
    }
}

@Serializable
private data class BreedCatalogRpcRow(
    val id: String,
    @SerialName("species_code") val speciesCode: String = "",
    val name: String,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true
)

@Serializable
private data class SpeciesCatalogRpcRow(
    val code: String,
    val name: String,
    @SerialName("secondary_label_singular") val secondaryLabelSingular: String? = null
)

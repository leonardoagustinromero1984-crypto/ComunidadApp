package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

internal data class RemoteCreatePetParams(
    val name: String,
    val species: String,
    val sex: String,
    val size: String,
    val description: String
)

internal data class RemoteUpdatePetProfileParams(
    val petId: String,
    val name: String,
    val species: String,
    val breed: String?,
    val sex: String,
    val size: String,
    val description: String,
    val ageYears: Int,
    val ageMonths: Int,
    val color: String?,
    val microchipId: String? = null
)

internal data class RemoteUpdatePetHealthParams(
    val petId: String,
    val vaccinations: List<VaccinationRecordDto> = emptyList(),
    val reminders: List<PetReminderDto> = emptyList(),
    val lastDeworming: String? = null,
    val dewormingProduct: String? = null,
    val lastFleaTreatment: String? = null,
    val fleaTreatmentProduct: String? = null,
    val sterilized: String? = null,
    val lastVetVisit: String? = null,
    val healthNotes: String? = null,
    val weightKg: Float? = null
)

internal interface PetsRemoteGateway {
    suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>>
    suspend fun fetchPetById(petId: String): Result<RemotePetRow?>
    suspend fun createPetWithPrincipal(params: RemoteCreatePetParams): Result<RemotePetRow>
    suspend fun updatePetProfile(params: RemoteUpdatePetProfileParams): Result<RemotePetRow>
    suspend fun setPetAvatarAsset(petId: String, assetId: String): Result<RemotePetRow>
    suspend fun updatePetHealth(params: RemoteUpdatePetHealthParams): Result<RemotePetRow>
    suspend fun archivePet(petId: String, reason: String?): Result<RemotePetRow>
    suspend fun restorePet(petId: String): Result<RemotePetRow>
    suspend fun markPetDeceased(petId: String, reason: String?): Result<RemotePetRow>
}

internal class SupabasePetsRemoteGateway(
    private val client: SupabaseClient
) : PetsRemoteGateway {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>> {
        return try {
            val rows = client.postgrest.rpc(
                function = "m08_list_accessible_pets",
                parameters = buildJsonObject {
                    put("p_status", "ACTIVE")
                }
            ).decodeList<RemoteAccessiblePetRow>()
            Result.success(rows)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun fetchPetById(petId: String): Result<RemotePetRow?> {
        return try {
            val row = client.from("pets")
                .select {
                    filter { eq("id", petId) }
                }
                .decodeSingleOrNull<RemotePetRow>()
            Result.success(row)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun createPetWithPrincipal(
        params: RemoteCreatePetParams
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_create_pet_with_principal",
            parameters = buildJsonObject {
                put("p_name", params.name)
                put("p_species", params.species)
                put("p_sex", params.sex)
                put("p_size", params.size)
                put("p_description", params.description)
                put("p_organization_id", JsonNull)
                put("p_microchip_id", JsonNull)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_CREATE_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun updatePetProfile(
        params: RemoteUpdatePetProfileParams
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_update_pet_profile",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_name", params.name)
                put("p_species", params.species)
                if (params.breed != null) put("p_breed", params.breed) else put("p_breed", JsonNull)
                put("p_sex", params.sex)
                put("p_size", params.size)
                put("p_description", params.description)
                put("p_age_years", params.ageYears)
                put("p_age_months", params.ageMonths)
                if (params.color != null) put("p_color", params.color) else put("p_color", JsonNull)
                put("p_microchip_id", JsonNull)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_UPDATE_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun setPetAvatarAsset(
        petId: String,
        assetId: String
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_set_pet_avatar_asset",
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                put("p_asset_id", assetId)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_AVATAR_SET_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun updatePetHealth(
        params: RemoteUpdatePetHealthParams
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_update_pet_health",
            parameters = buildJsonObject {
                put("p_pet_id", params.petId)
                put("p_vaccinations", json.encodeToJsonElement(params.vaccinations))
                put("p_reminders", json.encodeToJsonElement(params.reminders))
                if (params.lastDeworming != null) {
                    put("p_last_deworming", params.lastDeworming)
                } else {
                    put("p_last_deworming", JsonNull)
                }
                if (params.dewormingProduct != null) {
                    put("p_deworming_product", params.dewormingProduct)
                } else {
                    put("p_deworming_product", JsonNull)
                }
                if (params.lastFleaTreatment != null) {
                    put("p_last_flea_treatment", params.lastFleaTreatment)
                } else {
                    put("p_last_flea_treatment", JsonNull)
                }
                if (params.fleaTreatmentProduct != null) {
                    put("p_flea_treatment_product", params.fleaTreatmentProduct)
                } else {
                    put("p_flea_treatment_product", JsonNull)
                }
                if (params.sterilized != null) {
                    put("p_sterilized", params.sterilized)
                } else {
                    put("p_sterilized", JsonNull)
                }
                if (params.lastVetVisit != null) {
                    put("p_last_vet_visit", params.lastVetVisit)
                } else {
                    put("p_last_vet_visit", JsonNull)
                }
                if (params.healthNotes != null) {
                    put("p_health_notes", params.healthNotes)
                } else {
                    put("p_health_notes", JsonNull)
                }
                if (params.weightKg != null) {
                    put("p_weight_kg", params.weightKg)
                } else {
                    put("p_weight_kg", JsonNull)
                }
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_HEALTH_UPDATE_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun archivePet(
        petId: String,
        reason: String?
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_archive_pet",
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                if (reason != null) put("p_reason", reason) else put("p_reason", JsonNull)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_ARCHIVE_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun restorePet(petId: String): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_restore_pet",
            parameters = buildJsonObject {
                put("p_pet_id", petId)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_RESTORE_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }

    override suspend fun markPetDeceased(
        petId: String,
        reason: String?
    ): Result<RemotePetRow> = try {
        val rows = client.postgrest.rpc(
            function = "m08_mark_pet_deceased",
            parameters = buildJsonObject {
                put("p_pet_id", petId)
                if (reason != null) put("p_reason", reason) else put("p_reason", JsonNull)
            }
        ).decodeList<RemotePetRow>()
        val row = rows.firstOrNull()
            ?: return Result.failure(IllegalStateException("PET_DECEASED_EMPTY"))
        Result.success(row)
    } catch (t: Throwable) {
        Result.failure(t)
    }
}

internal class FakePetsRemoteGateway(
    var list: List<RemoteAccessiblePetRow> = emptyList(),
    var detail: RemotePetRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var createError: Throwable? = null,
    var updateError: Throwable? = null,
    var setAvatarError: Throwable? = null,
    var healthError: Throwable? = null,
    var archiveError: Throwable? = null,
    var restoreError: Throwable? = null,
    var deceasedError: Throwable? = null,
    var listCalls: Int = 0,
    var createCalls: Int = 0,
    var updateCalls: Int = 0,
    var setAvatarCalls: Int = 0,
    var healthCalls: Int = 0,
    var archiveCalls: Int = 0,
    var restoreCalls: Int = 0,
    var deceasedCalls: Int = 0,
    var lastCreate: RemoteCreatePetParams? = null,
    var lastUpdate: RemoteUpdatePetProfileParams? = null,
    var lastHealth: RemoteUpdatePetHealthParams? = null,
    var lastArchivePetId: String? = null,
    var lastArchiveReason: String? = null,
    var lastRestorePetId: String? = null,
    var lastDeceasedPetId: String? = null,
    var lastDeceasedReason: String? = null,
    var lastAvatarPetId: String? = null,
    var lastAvatarAssetId: String? = null,
    var created: RemotePetRow? = null
) : PetsRemoteGateway {
    override suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>> {
        listCalls++
        listError?.let { return Result.failure(it) }
        return Result.success(list)
    }

    override suspend fun fetchPetById(petId: String): Result<RemotePetRow?> {
        detailError?.let { return Result.failure(it) }
        val row = detail?.takeIf { it.id == petId }
            ?: created?.takeIf { it.id == petId }
            ?: list.firstOrNull { it.id == petId }?.let { accessibleToPetRow(it) }
        return Result.success(row)
    }

    override suspend fun createPetWithPrincipal(
        params: RemoteCreatePetParams
    ): Result<RemotePetRow> {
        createCalls++
        lastCreate = params
        createError?.let { return Result.failure(it) }
        val row = created ?: RemotePetRow(
            id = "pet-new-$createCalls",
            name = params.name,
            species = params.species,
            sex = params.sex,
            size = params.size,
            description = params.description,
            status = "ACTIVE"
        )
        created = row
        list = list + RemoteAccessiblePetRow(
            id = row.id,
            name = row.name,
            photoUrl = row.photoUrl,
            species = row.species,
            sex = row.sex,
            breed = row.breed,
            status = row.status,
            size = row.size,
            description = row.description,
            ageYears = row.ageYears,
            ageMonths = row.ageMonths,
            color = row.color,
            avatarFileAssetId = row.avatarFileAssetId,
            ownerId = row.ownerId
        )
        return Result.success(row)
    }

    override suspend fun updatePetProfile(
        params: RemoteUpdatePetProfileParams
    ): Result<RemotePetRow> {
        updateCalls++
        lastUpdate = params
        updateError?.let { return Result.failure(it) }
        val existing = created?.takeIf { it.id == params.petId }
            ?: detail?.takeIf { it.id == params.petId }
            ?: list.firstOrNull { it.id == params.petId }?.let { accessibleToPetRow(it) }
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        val updated = existing.copy(
            name = params.name,
            species = params.species,
            breed = params.breed,
            sex = params.sex,
            size = params.size,
            description = params.description,
            ageYears = params.ageYears,
            ageMonths = params.ageMonths,
            color = params.color
        )
        created = updated
        detail = if (detail?.id == params.petId) updated else detail
        list = list.map {
            if (it.id == params.petId) {
                it.copy(
                    name = updated.name,
                    species = updated.species,
                    breed = updated.breed,
                    sex = updated.sex,
                    size = updated.size,
                    description = updated.description,
                    ageYears = updated.ageYears,
                    ageMonths = updated.ageMonths,
                    color = updated.color,
                    avatarFileAssetId = updated.avatarFileAssetId,
                    status = updated.status
                )
            } else it
        }
        return Result.success(updated)
    }

    override suspend fun setPetAvatarAsset(
        petId: String,
        assetId: String
    ): Result<RemotePetRow> {
        setAvatarCalls++
        lastAvatarPetId = petId
        lastAvatarAssetId = assetId
        setAvatarError?.let { return Result.failure(it) }
        val existing = created?.takeIf { it.id == petId }
            ?: detail?.takeIf { it.id == petId }
            ?: list.firstOrNull { it.id == petId }?.let { accessibleToPetRow(it) }
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        val updated = existing.copy(avatarFileAssetId = assetId)
        created = updated
        detail = if (detail?.id == petId) updated else detail
        list = list.map {
            if (it.id == petId) it.copy(avatarFileAssetId = assetId) else it
        }
        return Result.success(updated)
    }

    override suspend fun updatePetHealth(
        params: RemoteUpdatePetHealthParams
    ): Result<RemotePetRow> {
        healthCalls++
        lastHealth = params
        healthError?.let { return Result.failure(it) }
        val existing = created?.takeIf { it.id == params.petId }
            ?: detail?.takeIf { it.id == params.petId }
            ?: list.firstOrNull { it.id == params.petId }?.let { accessibleToPetRow(it) }
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        val updated = existing.copy(
            vaccinations = params.vaccinations,
            reminders = params.reminders,
            lastDeworming = params.lastDeworming,
            dewormingProduct = params.dewormingProduct,
            lastFleaTreatment = params.lastFleaTreatment,
            fleaTreatmentProduct = params.fleaTreatmentProduct,
            sterilized = params.sterilized,
            lastVetVisit = params.lastVetVisit,
            healthNotes = params.healthNotes,
            weightKg = params.weightKg
        )
        created = updated
        detail = if (detail?.id == params.petId) updated else detail
        return Result.success(updated)
    }

    override suspend fun archivePet(petId: String, reason: String?): Result<RemotePetRow> {
        archiveCalls++
        lastArchivePetId = petId
        lastArchiveReason = reason
        archiveError?.let { return Result.failure(it) }
        val existing = resolvePet(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        when (existing.status.uppercase()) {
            "ARCHIVED" -> return Result.failure(IllegalStateException("PET_ALREADY_ARCHIVED"))
            "DECEASED" -> return Result.failure(IllegalStateException("PET_DECEASED_CANNOT_ARCHIVE"))
        }
        val updated = existing.copy(status = "ARCHIVED")
        applyPetUpdate(updated)
        list = list.filterNot { it.id == petId }
        return Result.success(updated)
    }

    override suspend fun restorePet(petId: String): Result<RemotePetRow> {
        restoreCalls++
        lastRestorePetId = petId
        restoreError?.let { return Result.failure(it) }
        val existing = resolvePet(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        when (existing.status.uppercase()) {
            "DECEASED" -> return Result.failure(IllegalStateException("PET_DECEASED_CANNOT_RESTORE"))
            "ARCHIVED" -> Unit
            else -> return Result.failure(IllegalStateException("PET_NOT_ARCHIVED"))
        }
        val updated = existing.copy(status = "ACTIVE")
        applyPetUpdate(updated)
        if (list.none { it.id == petId }) {
            list = list + RemoteAccessiblePetRow(
                id = updated.id,
                name = updated.name,
                photoUrl = updated.photoUrl,
                species = updated.species,
                sex = updated.sex,
                breed = updated.breed,
                status = updated.status,
                size = updated.size,
                description = updated.description,
                ageYears = updated.ageYears,
                ageMonths = updated.ageMonths,
                color = updated.color,
                avatarFileAssetId = updated.avatarFileAssetId,
                ownerId = updated.ownerId
            )
        } else {
            list = list.map {
                if (it.id == petId) it.copy(status = "ACTIVE") else it
            }
        }
        return Result.success(updated)
    }

    override suspend fun markPetDeceased(petId: String, reason: String?): Result<RemotePetRow> {
        deceasedCalls++
        lastDeceasedPetId = petId
        lastDeceasedReason = reason
        deceasedError?.let { return Result.failure(it) }
        val existing = resolvePet(petId)
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        if (existing.status.uppercase() == "DECEASED") {
            return Result.failure(IllegalStateException("PET_ALREADY_DECEASED"))
        }
        val updated = existing.copy(status = "DECEASED")
        applyPetUpdate(updated)
        list = list.filterNot { it.id == petId }
        return Result.success(updated)
    }

    private fun resolvePet(petId: String): RemotePetRow? =
        created?.takeIf { it.id == petId }
            ?: detail?.takeIf { it.id == petId }
            ?: list.firstOrNull { it.id == petId }?.let { accessibleToPetRow(it) }

    private fun applyPetUpdate(updated: RemotePetRow) {
        created = updated
        detail = if (detail?.id == updated.id) updated else detail
        list = list.map {
            if (it.id == updated.id) {
                it.copy(
                    name = updated.name,
                    species = updated.species,
                    breed = updated.breed,
                    sex = updated.sex,
                    size = updated.size,
                    description = updated.description,
                    ageYears = updated.ageYears,
                    ageMonths = updated.ageMonths,
                    color = updated.color,
                    avatarFileAssetId = updated.avatarFileAssetId,
                    status = updated.status
                )
            } else it
        }
    }

    private fun accessibleToPetRow(it: RemoteAccessiblePetRow) = RemotePetRow(
        id = it.id,
        name = it.name,
        photoUrl = it.photoUrl,
        species = it.species,
        sex = it.sex,
        breed = it.breed,
        status = it.status,
        size = it.size,
        description = it.description,
        ageYears = it.ageYears,
        ageMonths = it.ageMonths,
        color = it.color,
        avatarFileAssetId = it.avatarFileAssetId,
        ownerId = it.ownerId
    )
}

internal fun mapPetsThrowable(t: Throwable): String {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "PET_NAME_REQUIRED" in code ->
            "El nombre de la mascota es obligatorio."
        "PET_WEIGHT_INVALID" in code ->
            "El peso no es válido."
        "PET_AVATAR_ASSET_NOT_FOUND" in code ->
            "No encontramos el archivo de avatar."
        "PET_AVATAR_PURPOSE_INVALID" in code ->
            "El archivo no es un avatar de mascota válido."
        "PET_ALREADY_ARCHIVED" in code ->
            "La mascota ya está archivada."
        "PET_NOT_ARCHIVED" in code ->
            "Solo se pueden restaurar mascotas archivadas."
        "PET_ALREADY_DECEASED" in code ->
            "La mascota ya está marcada como fallecida."
        "PET_DECEASED_CANNOT_ARCHIVE" in code ->
            "No se puede archivar una mascota fallecida."
        "PET_DECEASED_CANNOT_RESTORE" in code ->
            "No se puede restaurar una mascota fallecida."
        "PET_MICROCHIP_ACTIVE_CONFLICT" in code ->
            "Hay un conflicto con el microchip activo."
        "FORBIDDEN" in code ->
            "No tenés permiso para esta acción."
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw ||
            "forbidden" in raw ->
            "No tenés permiso para esta acción."
        "404" in raw || "not found" in raw || "PET_NOT_FOUND" in code ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}

internal fun classifyPetsWrite(t: Throwable): PetsWriteKind {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "401" in raw || "not_authenticated" in raw || "jwt" in raw -> PetsWriteKind.UNAUTHENTICATED
        "FORBIDDEN" in code || "403" in raw || "forbidden" in raw || "rls" in raw ->
            PetsWriteKind.FORBIDDEN
        "PET_MICROCHIP_ACTIVE_CONFLICT" in code ||
            "PET_ALREADY_ARCHIVED" in code ||
            "PET_NOT_ARCHIVED" in code ||
            "PET_ALREADY_DECEASED" in code ||
            "PET_DECEASED_CANNOT_ARCHIVE" in code ||
            "PET_DECEASED_CANNOT_RESTORE" in code ->
            PetsWriteKind.CONFLICT
        "PET_NAME_REQUIRED" in code || "PET_NAME_TOO_LONG" in code ||
            "PET_AVATAR_PURPOSE_INVALID" in code || "PET_WEIGHT_INVALID" in code ->
            PetsWriteKind.VALIDATION
        else -> PetsWriteKind.BACKEND
    }
}

internal enum class PetsWriteKind {
    UNAUTHENTICATED,
    FORBIDDEN,
    CONFLICT,
    VALIDATION,
    BACKEND
}

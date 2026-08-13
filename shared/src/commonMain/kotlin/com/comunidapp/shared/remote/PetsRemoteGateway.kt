package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class RemoteCreatePetParams(
    val name: String,
    val species: String,
    val sex: String,
    val size: String,
    val description: String
)

internal interface PetsRemoteGateway {
    suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>>
    suspend fun fetchPetById(petId: String): Result<RemotePetRow?>
    suspend fun createPetWithPrincipal(params: RemoteCreatePetParams): Result<RemotePetRow>
    suspend fun setPetAvatarAsset(petId: String, assetId: String): Result<RemotePetRow>
}

internal class SupabasePetsRemoteGateway(
    private val client: SupabaseClient
) : PetsRemoteGateway {

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
}

internal class FakePetsRemoteGateway(
    var list: List<RemoteAccessiblePetRow> = emptyList(),
    var detail: RemotePetRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var createError: Throwable? = null,
    var setAvatarError: Throwable? = null,
    var listCalls: Int = 0,
    var createCalls: Int = 0,
    var setAvatarCalls: Int = 0,
    var lastCreate: RemoteCreatePetParams? = null,
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
            ?: list.firstOrNull { it.id == petId }?.let {
                RemotePetRow(
                    id = it.id,
                    name = it.name,
                    photoUrl = it.photoUrl,
                    species = it.species,
                    sex = it.sex,
                    breed = it.breed,
                    status = it.status,
                    avatarFileAssetId = it.avatarFileAssetId,
                    ownerId = it.ownerId
                )
            }
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
            avatarFileAssetId = row.avatarFileAssetId,
            ownerId = row.ownerId
        )
        return Result.success(row)
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
            ?: list.firstOrNull { it.id == petId }?.let {
                RemotePetRow(
                    id = it.id,
                    name = it.name,
                    photoUrl = it.photoUrl,
                    species = it.species,
                    sex = it.sex,
                    breed = it.breed,
                    status = it.status,
                    avatarFileAssetId = it.avatarFileAssetId,
                    ownerId = it.ownerId
                )
            }
            ?: return Result.failure(IllegalStateException("PET_NOT_FOUND"))
        val updated = existing.copy(avatarFileAssetId = assetId)
        created = updated
        detail = if (detail?.id == petId) updated else detail
        list = list.map {
            if (it.id == petId) it.copy(avatarFileAssetId = assetId) else it
        }
        return Result.success(updated)
    }
}

internal fun mapPetsThrowable(t: Throwable): String {
    val code = t.message.orEmpty()
    val raw = code.lowercase()
    return when {
        "PET_NAME_REQUIRED" in code ->
            "El nombre de la mascota es obligatorio."
        "PET_AVATAR_ASSET_NOT_FOUND" in code ->
            "No encontramos el archivo de avatar."
        "PET_AVATAR_PURPOSE_INVALID" in code ->
            "El archivo no es un avatar de mascota válido."
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
        "PET_MICROCHIP_ACTIVE_CONFLICT" in code -> PetsWriteKind.CONFLICT
        "PET_NAME_REQUIRED" in code || "PET_NAME_TOO_LONG" in code ||
            "PET_AVATAR_PURPOSE_INVALID" in code ->
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

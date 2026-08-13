package com.comunidapp.shared.remote

import com.comunidapp.shared.ui.ErrorSanitizer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface PetsRemoteGateway {
    suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>>
    suspend fun fetchPetById(petId: String): Result<RemotePetRow?>
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
}

internal class FakePetsRemoteGateway(
    var list: List<RemoteAccessiblePetRow> = emptyList(),
    var detail: RemotePetRow? = null,
    var listError: Throwable? = null,
    var detailError: Throwable? = null,
    var listCalls: Int = 0
) : PetsRemoteGateway {
    override suspend fun listAccessibleActivePets(): Result<List<RemoteAccessiblePetRow>> {
        listCalls++
        listError?.let { return Result.failure(it) }
        return Result.success(list)
    }

    override suspend fun fetchPetById(petId: String): Result<RemotePetRow?> {
        detailError?.let { return Result.failure(it) }
        val row = detail?.takeIf { it.id == petId }
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
}

internal fun mapPetsThrowable(t: Throwable): String {
    val raw = t.message.orEmpty().lowercase()
    return when {
        "401" in raw || "jwt" in raw || "not authenticated" in raw || "session" in raw ->
            "Tu sesión no está disponible."
        "403" in raw || "permission" in raw || "rls" in raw || "policy" in raw ->
            "No tenés permiso para ver estas mascotas."
        "404" in raw || "not found" in raw || "PET_NOT_FOUND" in t.message.orEmpty() ->
            "No encontramos ese contenido."
        "network" in raw || "timeout" in raw || "unable to resolve" in raw ->
            "Problema de conexión. Intentá nuevamente."
        else -> ErrorSanitizer.sanitize(t)
    }
}

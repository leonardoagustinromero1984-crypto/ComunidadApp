package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.PetHealthCatalog
import com.comunidapp.app.data.model.PetSpecies
import com.comunidapp.app.data.remote.supabase.m08.M08RpcDecoding
import com.comunidapp.app.data.remote.supabase.supabase
import com.comunidapp.app.domain.canonical.CanonicalBackend
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class CatalogSpecies(val code: String, val name: String, val sortKey: Int, val active: Boolean)
data class CatalogBreed(
    val id: String,
    val speciesCode: String,
    val name: String,
    val sortKey: Int,
    val active: Boolean
)
data class CatalogHealthProduct(
    val id: String,
    val kind: String,
    val code: String,
    val displayName: String,
    val speciesCode: String?,
    val active: Boolean,
    val sortOrder: Int
)

interface MasterCatalogRepository {
    suspend fun listSpecies(includeInactive: Boolean = false): List<CatalogSpecies>
    suspend fun listBreeds(speciesCode: String, includeInactive: Boolean = false): List<CatalogBreed>
    suspend fun listHealthProducts(
        kind: String,
        speciesCode: String? = null,
        includeInactive: Boolean = false
    ): List<CatalogHealthProduct>
    suspend fun upsertSpecies(code: String, name: String, sortKey: Int, active: Boolean): Result<Unit>
    suspend fun upsertBreed(
        id: String?,
        speciesCode: String,
        name: String,
        sortKey: Int,
        active: Boolean
    ): Result<Unit>
    suspend fun upsertHealthProduct(
        id: String?,
        kind: String,
        code: String,
        displayName: String,
        speciesCode: String?,
        sortOrder: Int,
        active: Boolean
    ): Result<Unit>
    suspend fun upsertLocation(
        id: String?,
        kind: String,
        parentId: String?,
        name: String,
        isoCode: String?,
        sortKey: Int,
        active: Boolean
    ): Result<Unit>
}

class InMemoryMasterCatalogRepository : MasterCatalogRepository {
    override suspend fun listSpecies(includeInactive: Boolean): List<CatalogSpecies> =
        PetSpecies.entries.mapIndexed { index, species ->
            CatalogSpecies(species.name, species.name, index, true)
        }

    override suspend fun listBreeds(speciesCode: String, includeInactive: Boolean): List<CatalogBreed> =
        emptyList()

    override suspend fun listHealthProducts(
        kind: String,
        speciesCode: String?,
        includeInactive: Boolean
    ): List<CatalogHealthProduct> {
        val names = when (kind.uppercase()) {
            "VACCINE" -> PetHealthCatalog.vaccinesForSpecies(
                PetSpecies.fromString(speciesCode)
            )
            "FLEA" -> PetHealthCatalog.fleaAndTickProducts
            "DEWORMER" -> PetHealthCatalog.dewormingProducts
            else -> emptyList()
        }
        return names.mapIndexed { index, name ->
            CatalogHealthProduct("", kind.uppercase(), name, name, speciesCode, true, index)
        }
    }

    override suspend fun upsertSpecies(code: String, name: String, sortKey: Int, active: Boolean) =
        Result.success(Unit)

    override suspend fun upsertBreed(
        id: String?,
        speciesCode: String,
        name: String,
        sortKey: Int,
        active: Boolean
    ) = Result.success(Unit)

    override suspend fun upsertHealthProduct(
        id: String?,
        kind: String,
        code: String,
        displayName: String,
        speciesCode: String?,
        sortOrder: Int,
        active: Boolean
    ) = Result.success(Unit)

    override suspend fun upsertLocation(
        id: String?,
        kind: String,
        parentId: String?,
        name: String,
        isoCode: String?,
        sortKey: Int,
        active: Boolean
    ) = Result.success(Unit)
}

@Serializable
private data class SpeciesRow(
    val code: String,
    val name: String,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true
)

@Serializable
private data class BreedRow(
    val id: String,
    @SerialName("species_code") val speciesCode: String,
    val name: String,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true
)

@Serializable
private data class HealthProductRow(
    val id: String,
    val kind: String,
    val code: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("species_code") val speciesCode: String? = null,
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0
)

class CanonicalMasterCatalogRepository : MasterCatalogRepository {
    override suspend fun listSpecies(includeInactive: Boolean): List<CatalogSpecies> {
        val rpc = if (includeInactive) CanonicalBackend.RPC_ADMIN_LIST_SPECIES else CanonicalBackend.RPC_LIST_SPECIES
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(rpc).decodeAs()
            M08RpcDecoding.decodeRows<SpeciesRow>(element).map {
                CatalogSpecies(it.code, it.name, it.sortKey, it.active)
            }
        }.getOrElse {
            InMemoryMasterCatalogRepository().listSpecies(includeInactive)
        }
    }

    override suspend fun listBreeds(speciesCode: String, includeInactive: Boolean): List<CatalogBreed> {
        if (!includeInactive && speciesCode.isBlank()) return emptyList()
        val rpc = if (includeInactive) CanonicalBackend.RPC_ADMIN_LIST_BREEDS else CanonicalBackend.RPC_LIST_BREEDS
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                rpc,
                buildJsonObject {
                    if (speciesCode.isBlank()) put("p_species_code", JsonNull)
                    else put("p_species_code", speciesCode)
                }
            ).decodeAs()
            M08RpcDecoding.decodeRows<BreedRow>(element).map {
                CatalogBreed(it.id, it.speciesCode, it.name, it.sortKey, it.active)
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun listHealthProducts(
        kind: String,
        speciesCode: String?,
        includeInactive: Boolean
    ): List<CatalogHealthProduct> {
        return runCatching {
            val element: JsonElement = if (includeInactive) {
                supabase.postgrest.rpc(
                    CanonicalBackend.RPC_ADMIN_LIST_HEALTH_PRODUCTS,
                    buildJsonObject { put("p_kind", kind) }
                ).decodeAs()
            } else {
                supabase.postgrest.rpc(
                    CanonicalBackend.RPC_LIST_PET_HEALTH_PRODUCTS,
                    buildJsonObject {
                        put("p_kind", kind)
                        if (speciesCode.isNullOrBlank()) put("p_species_code", JsonNull)
                        else put("p_species_code", speciesCode)
                    }
                ).decodeAs()
            }
            M08RpcDecoding.decodeRows<HealthProductRow>(element).map {
                CatalogHealthProduct(
                    it.id, it.kind, it.code, it.displayName, it.speciesCode, it.active, it.sortOrder
                )
            }
        }.getOrElse {
            InMemoryMasterCatalogRepository().listHealthProducts(kind, speciesCode, includeInactive)
        }
    }

    override suspend fun upsertSpecies(code: String, name: String, sortKey: Int, active: Boolean): Result<Unit> =
        runCatching {
            supabase.postgrest.rpc(
                CanonicalBackend.RPC_ADMIN_UPSERT_SPECIES,
                buildJsonObject {
                    put("p_code", code)
                    put("p_name", name)
                    put("p_sort_key", sortKey)
                    put("p_active", active)
                }
            )
            Unit
        }

    override suspend fun upsertBreed(
        id: String?,
        speciesCode: String,
        name: String,
        sortKey: Int,
        active: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_UPSERT_BREED,
            buildJsonObject {
                if (id.isNullOrBlank()) put("p_id", JsonNull) else put("p_id", id)
                put("p_species_code", speciesCode)
                put("p_name", name)
                put("p_sort_key", sortKey)
                put("p_active", active)
            }
        )
        Unit
    }

    override suspend fun upsertHealthProduct(
        id: String?,
        kind: String,
        code: String,
        displayName: String,
        speciesCode: String?,
        sortOrder: Int,
        active: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_UPSERT_HEALTH_PRODUCT,
            buildJsonObject {
                if (id.isNullOrBlank()) put("p_id", JsonNull) else put("p_id", id)
                put("p_kind", kind)
                put("p_code", code)
                put("p_display_name", displayName)
                if (speciesCode.isNullOrBlank()) put("p_species_code", JsonNull)
                else put("p_species_code", speciesCode)
                put("p_sort_order", sortOrder)
                put("p_active", active)
            }
        )
        Unit
    }

    override suspend fun upsertLocation(
        id: String?,
        kind: String,
        parentId: String?,
        name: String,
        isoCode: String?,
        sortKey: Int,
        active: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_UPSERT_LOCATION_NODE,
            buildJsonObject {
                if (id.isNullOrBlank()) put("p_id", JsonNull) else put("p_id", id)
                put("p_kind", kind)
                if (parentId.isNullOrBlank()) put("p_parent_id", JsonNull) else put("p_parent_id", parentId)
                put("p_name", name)
                if (isoCode.isNullOrBlank()) put("p_iso_code", JsonNull) else put("p_iso_code", isoCode)
                put("p_sort_key", sortKey)
                put("p_active", active)
            }
        )
        Unit
    }
}

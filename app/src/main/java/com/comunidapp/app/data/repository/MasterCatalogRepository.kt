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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class CatalogSpecies(
    val code: String,
    val name: String,
    val sortKey: Int,
    val active: Boolean,
    val id: String? = null,
    val status: String = if (active) "ACTIVE" else "INACTIVE",
    val statusLabel: String = "",
    val secondaryClassificationEnabled: Boolean = false,
    val secondaryClassificationKind: String = "NONE",
    val secondaryLabelSingular: String? = null,
    val secondaryLabelPlural: String? = null,
    val catalogVisibility: String = "VISIBLE",
    val codeInUse: Boolean = false
)
data class CatalogBreed(
    val id: String,
    val speciesCode: String,
    val name: String,
    val sortKey: Int,
    val active: Boolean,
    val code: String? = null,
    val speciesId: String? = null
)
data class CatalogHealthProduct(
    val id: String,
    val kind: String,
    val code: String,
    val displayName: String,
    val speciesCode: String?,
    val active: Boolean,
    val sortOrder: Int,
    val speciesCodes: List<String> = emptyList(),
    val unscoped: Boolean = speciesCodes.isEmpty() && speciesCode.isNullOrBlank()
)

data class CatalogServiceCategory(
    val code: String,
    val name: String,
    val sortKey: Int,
    val active: Boolean
)

interface MasterCatalogRepository {
    suspend fun listSpecies(includeInactive: Boolean = false): List<CatalogSpecies>
    suspend fun getSpecies(speciesId: String): CatalogSpecies?
    suspend fun listBreeds(speciesCode: String, includeInactive: Boolean = false): List<CatalogBreed>
    suspend fun getBreed(id: String): CatalogBreed?
    suspend fun listSecondaryItems(speciesId: String, includeInactive: Boolean = false): List<CatalogBreed> =
        listBreeds(speciesId, includeInactive)
    suspend fun getSecondaryItem(id: String): CatalogBreed? = getBreed(id)
    suspend fun listHealthProducts(
        kind: String,
        speciesCode: String? = null,
        includeInactive: Boolean = false
    ): List<CatalogHealthProduct>
    suspend fun listServiceCategories(includeInactive: Boolean = false): List<CatalogServiceCategory>
    suspend fun upsertSpecies(code: String, name: String, sortKey: Int, active: Boolean): Result<Unit>
    suspend fun upsertSpeciesConfig(
        code: String,
        name: String,
        sortKey: Int,
        status: String,
        secondaryEnabled: Boolean,
        secondaryKind: String,
        secondaryLabelSingular: String?,
        secondaryLabelPlural: String?
    ): Result<Unit>
    suspend fun setSpeciesStatus(speciesId: String, status: String): Result<Unit>
    suspend fun upsertBreed(
        id: String?,
        speciesCode: String,
        name: String,
        sortKey: Int,
        active: Boolean
    ): Result<Unit>
    suspend fun upsertSecondaryItem(
        id: String?,
        speciesId: String,
        name: String,
        sortOrder: Int,
        active: Boolean
    ): Result<Unit> = upsertBreed(id, speciesId, name, sortOrder, active)
    suspend fun setSecondaryItemStatus(id: String, active: Boolean): Result<Unit>
    suspend fun setHealthProductSpecies(productId: String, speciesCodes: List<String>): Result<Unit>
    suspend fun upsertHealthProduct(
        id: String?,
        kind: String,
        code: String,
        displayName: String,
        speciesCode: String?,
        sortOrder: Int,
        active: Boolean
    ): Result<Unit>
    suspend fun upsertServiceCategory(code: String, name: String, sortKey: Int, active: Boolean): Result<Unit>
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

    override suspend fun getSpecies(speciesId: String): CatalogSpecies? =
        listSpecies(includeInactive = true).firstOrNull { it.code.equals(speciesId, true) }

    override suspend fun listBreeds(speciesCode: String, includeInactive: Boolean): List<CatalogBreed> =
        emptyList()

    override suspend fun upsertSpeciesConfig(
        code: String,
        name: String,
        sortKey: Int,
        status: String,
        secondaryEnabled: Boolean,
        secondaryKind: String,
        secondaryLabelSingular: String?,
        secondaryLabelPlural: String?
    ) = Result.success(Unit)

    override suspend fun setSpeciesStatus(speciesId: String, status: String) = Result.success(Unit)

    override suspend fun setSecondaryItemStatus(id: String, active: Boolean) = Result.success(Unit)

    override suspend fun setHealthProductSpecies(productId: String, speciesCodes: List<String>) =
        Result.success(Unit)

    override suspend fun getBreed(id: String): CatalogBreed? = null

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

    override suspend fun listServiceCategories(includeInactive: Boolean): List<CatalogServiceCategory> =
        emptyList()

    override suspend fun upsertServiceCategory(code: String, name: String, sortKey: Int, active: Boolean) =
        Result.success(Unit)

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
    val id: String? = null,
    val code: String,
    val name: String,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true,
    val status: String? = null,
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("secondary_classification_enabled") val secondaryEnabled: Boolean = false,
    @SerialName("secondary_classification_kind") val secondaryKind: String = "NONE",
    @SerialName("secondary_label_singular") val secondaryLabelSingular: String? = null,
    @SerialName("secondary_label_plural") val secondaryLabelPlural: String? = null,
    @SerialName("catalog_visibility") val catalogVisibility: String = "VISIBLE",
    @SerialName("code_in_use") val codeInUse: Boolean = false
)

@Serializable
private data class BreedRow(
    val id: String,
    @SerialName("species_code") val speciesCode: String = "",
    @SerialName("species_id") val speciesId: String? = null,
    val name: String,
    val code: String? = null,
    @SerialName("sort_key") val sortKey: Int = 0,
    @SerialName("sort_order") val sortOrder: Int? = null,
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
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("species_codes") val speciesCodes: List<String> = emptyList(),
    val unscoped: Boolean = false
)

@Serializable
private data class ServiceCategoryRow(
    val code: String,
    val name: String,
    @SerialName("sort_key") val sortKey: Int = 0,
    val active: Boolean = true
)

private fun SpeciesRow.toCatalog(): CatalogSpecies = CatalogSpecies(
    code = code,
    name = name,
    sortKey = sortKey,
    active = active,
    id = id,
    status = status ?: if (active) "ACTIVE" else "INACTIVE",
    statusLabel = statusLabel.orEmpty(),
    secondaryClassificationEnabled = secondaryEnabled,
    secondaryClassificationKind = secondaryKind,
    secondaryLabelSingular = secondaryLabelSingular,
    secondaryLabelPlural = secondaryLabelPlural,
    catalogVisibility = catalogVisibility,
    codeInUse = codeInUse
)

private fun BreedRow.toCatalog(): CatalogBreed = CatalogBreed(
    id = id,
    speciesCode = speciesCode,
    name = name,
    sortKey = sortOrder ?: sortKey,
    active = active,
    code = code,
    speciesId = speciesId
)

class CanonicalMasterCatalogRepository : MasterCatalogRepository {
    override suspend fun listSpecies(includeInactive: Boolean): List<CatalogSpecies> {
        val rpc = if (includeInactive) CanonicalBackend.RPC_ADMIN_LIST_SPECIES else CanonicalBackend.RPC_LIST_SPECIES
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(rpc).decodeAs()
            M08RpcDecoding.decodeRows<SpeciesRow>(element).map { it.toCatalog() }
        }.getOrElse {
            InMemoryMasterCatalogRepository().listSpecies(includeInactive)
        }
    }

    override suspend fun getSpecies(speciesId: String): CatalogSpecies? {
        if (speciesId.isBlank()) return null
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_GET_SPECIES,
                buildJsonObject { put("p_species_id", speciesId) }
            ).decodeAs()
            val row = M08RpcDecoding.decodeRows<SpeciesRow>(element).firstOrNull()
                ?: runCatching { M08RpcDecoding.decodeRow<SpeciesRow>(element) }.getOrNull()
            row?.toCatalog()
        }.getOrNull()
    }

    override suspend fun getBreed(id: String): CatalogBreed? {
        if (id.isBlank()) return null
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_GET_BREED,
                buildJsonObject { put("p_id", id) }
            ).decodeAs()
            val row = M08RpcDecoding.decodeRows<BreedRow>(element).firstOrNull()
                ?: runCatching { M08RpcDecoding.decodeRow<BreedRow>(element) }.getOrNull()
            row?.toCatalog()
        }.getOrNull()
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
            M08RpcDecoding.decodeRows<BreedRow>(element).map { it.toCatalog() }
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
                    id = it.id,
                    kind = it.kind,
                    code = it.code,
                    displayName = it.displayName,
                    speciesCode = it.speciesCode,
                    active = it.active,
                    sortOrder = it.sortOrder,
                    speciesCodes = it.speciesCodes,
                    unscoped = it.unscoped || (it.speciesCodes.isEmpty() && it.speciesCode.isNullOrBlank())
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

    override suspend fun upsertSpeciesConfig(
        code: String,
        name: String,
        sortKey: Int,
        status: String,
        secondaryEnabled: Boolean,
        secondaryKind: String,
        secondaryLabelSingular: String?,
        secondaryLabelPlural: String?
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_UPSERT_SPECIES,
            buildJsonObject {
                put("p_code", code)
                put("p_name", name)
                put("p_sort_key", sortKey)
                put("p_active", status.equals("ACTIVE", true))
                put("p_status", status)
                put("p_secondary_enabled", secondaryEnabled)
                put("p_secondary_kind", secondaryKind)
                if (secondaryLabelSingular.isNullOrBlank()) put("p_secondary_label_singular", JsonNull)
                else put("p_secondary_label_singular", secondaryLabelSingular)
                if (secondaryLabelPlural.isNullOrBlank()) put("p_secondary_label_plural", JsonNull)
                else put("p_secondary_label_plural", secondaryLabelPlural)
            }
        )
        Unit
    }

    override suspend fun setSpeciesStatus(speciesId: String, status: String): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_SET_SPECIES_STATUS,
            buildJsonObject {
                put("p_species_id", speciesId)
                put("p_status", status)
            }
        )
        Unit
    }

    override suspend fun setSecondaryItemStatus(id: String, active: Boolean): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_SET_SPECIES_SECONDARY_STATUS,
            buildJsonObject {
                put("p_id", id)
                put("p_active", active)
            }
        )
        Unit
    }

    override suspend fun setHealthProductSpecies(
        productId: String,
        speciesCodes: List<String>
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_SET_HEALTH_PRODUCT_SPECIES,
            buildJsonObject {
                put("p_product_id", productId)
                putJsonArray("p_species_ids") {
                    speciesCodes.forEach { add(it) }
                }
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

    override suspend fun listServiceCategories(includeInactive: Boolean): List<CatalogServiceCategory> {
        return runCatching {
            val element: JsonElement = supabase.postgrest.rpc(
                CanonicalBackend.RPC_ADMIN_LIST_SERVICE_CATEGORIES
            ).decodeAs()
            M08RpcDecoding.decodeRows<ServiceCategoryRow>(element).map {
                CatalogServiceCategory(it.code, it.name, it.sortKey, it.active)
            }.filter { includeInactive || it.active }
        }.getOrDefault(emptyList())
    }

    override suspend fun upsertServiceCategory(
        code: String,
        name: String,
        sortKey: Int,
        active: Boolean
    ): Result<Unit> = runCatching {
        supabase.postgrest.rpc(
            CanonicalBackend.RPC_ADMIN_UPSERT_SERVICE_CATEGORY,
            buildJsonObject {
                put("p_code", code)
                put("p_name", name)
                put("p_sort_key", sortKey)
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

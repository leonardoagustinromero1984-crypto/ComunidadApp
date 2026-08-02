package com.comunidapp.app.data.remote.supabase.m22

import com.comunidapp.app.data.model.M22BranchStatus
import com.comunidapp.app.data.model.M22CoverageArea
import com.comunidapp.app.data.model.M22CoverageType
import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22ProviderBranch
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.model.M22ProviderProfile
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.data.model.M22PublicBranch
import com.comunidapp.app.data.model.M22PublicProviderDetail
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.data.model.M22PublicServiceOffering
import com.comunidapp.app.data.model.M22ServiceOffering
import com.comunidapp.app.data.remote.supabase.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement?.longOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull

private fun JsonElement?.boolean(default: Boolean = false): Boolean =
    (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: default

private fun JsonObject.string(key: String): String? = this[key].stringOrNull()

private fun JsonObject.long(key: String): Long? = this[key].longOrNull()

private fun JsonObject.boolean(key: String, default: Boolean = false): Boolean = this[key].boolean(default)

private fun parseTimestamp(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

fun JsonObject.toM22PublicListing(): M22PublicProviderListing = M22PublicProviderListing(
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M22ProviderCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    branchCount = long("branch_count")?.toInt() ?: 0,
    priceSummary = string("price_summary")
)

fun JsonObject.toM22PublicBranch(): M22PublicBranch = M22PublicBranch(
    name = string("name").orEmpty(),
    city = string("city").orEmpty(),
    neighborhood = string("neighborhood"),
    coverage = string("coverage").orEmpty()
)

fun JsonObject.toM22PublicOffering(): M22PublicServiceOffering = M22PublicServiceOffering(
    name = string("name").orEmpty(),
    description = string("description").orEmpty(),
    priceType = enumOr(string("price_type"), M22PriceType.QUOTE),
    priceAmount = long("price_amount_cents"),
    currency = string("currency") ?: "ARS"
)

fun JsonObject.toM22PublicDetail(): M22PublicProviderDetail = M22PublicProviderDetail(
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M22ProviderCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    branches = (this["branches"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonObject)?.toM22PublicBranch()
    },
    offerings = (this["offerings"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonObject)?.toM22PublicOffering()
    }
)

fun JsonObject.toM22ProviderProfile(): M22ProviderProfile = M22ProviderProfile(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    organizationId = string("organization_id"),
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M22ProviderCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    status = enumOr(string("status"), M22ProviderStatus.DRAFT),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM22ProviderBranch(): M22ProviderBranch = M22ProviderBranch(
    id = string("id").orEmpty(),
    providerId = string("provider_id").orEmpty(),
    name = string("name").orEmpty(),
    city = string("city").orEmpty(),
    neighborhood = string("neighborhood"),
    coverage = M22CoverageArea(
        type = enumOr(string("coverage_type"), M22CoverageType.CITY),
        city = string("coverage_city") ?: string("city").orEmpty(),
        neighborhood = string("coverage_neighborhood"),
        radiusKm = long("coverage_radius_km")?.toInt()
    ),
    status = enumOr(string("status"), M22BranchStatus.ACTIVE)
)

fun JsonObject.toM22ServiceOffering(): M22ServiceOffering = M22ServiceOffering(
    id = string("id").orEmpty(),
    providerId = string("provider_id").orEmpty(),
    branchId = string("branch_id"),
    name = string("name").orEmpty(),
    description = string("description").orEmpty(),
    priceType = enumOr(string("price_type"), M22PriceType.QUOTE),
    priceAmount = long("price_amount_cents"),
    currency = string("currency") ?: "ARS",
    active = boolean("active", true)
)

class SupabaseM22RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

    private suspend inline fun <reified T : Any> list(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function, parameters).decodeList()

    suspend fun listCatalog(category: String?, city: String?): List<JsonObject> = list(
        "m22_list_catalog", buildJsonObject {
            put("p_category", category)
            put("p_city", city)
        }
    )

    suspend fun getProviderDetail(providerId: String): JsonObject = one(
        "m22_get_provider_detail", buildJsonObject { put("p_provider_id", providerId) }
    )

    suspend fun listMyProviders(): List<JsonObject> = list("m22_list_my_providers", buildJsonObject {})

    suspend fun createProvider(
        displayName: String, category: String, description: String, city: String, organizationId: String?
    ): JsonObject = one("m22_create_provider", buildJsonObject {
        put("p_display_name", displayName); put("p_category", category); put("p_description", description)
        put("p_city", city); put("p_organization_id", organizationId)
    })

    suspend fun updateProvider(
        providerId: String, displayName: String?, description: String?, city: String?, status: String?
    ): JsonObject = one("m22_update_provider", buildJsonObject {
        put("p_provider_id", providerId); put("p_display_name", displayName); put("p_description", description)
        put("p_city", city); put("p_status", status)
    })

    suspend fun upsertBranch(
        providerId: String, branchId: String?, name: String, city: String, neighborhood: String?,
        coverage: M22CoverageArea, status: String
    ): JsonObject = one("m22_upsert_branch", buildJsonObject {
        put("p_provider_id", providerId); put("p_branch_id", branchId); put("p_name", name); put("p_city", city)
        put("p_neighborhood", neighborhood); put("p_coverage_type", coverage.type.name)
        put("p_coverage_city", coverage.city); put("p_coverage_neighborhood", coverage.neighborhood)
        put("p_coverage_radius_km", coverage.radiusKm); put("p_status", status)
    })

    suspend fun upsertOffering(
        providerId: String, offeringId: String?, branchId: String?, name: String, description: String,
        priceType: String, priceAmountCents: Long?, currency: String, active: Boolean
    ): JsonObject = one("m22_upsert_offering", buildJsonObject {
        put("p_provider_id", providerId); put("p_offering_id", offeringId); put("p_branch_id", branchId)
        put("p_name", name); put("p_description", description); put("p_price_type", priceType)
        put("p_price_amount_cents", priceAmountCents); put("p_currency", currency); put("p_active", active)
    })

    suspend fun archiveProvider(providerId: String): JsonObject =
        one("m22_archive_provider", buildJsonObject { put("p_provider_id", providerId) })
}

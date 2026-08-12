package com.comunidapp.shared.poc.m22.data

import com.comunidapp.shared.poc.m22.domain.M22PocPrivacy
import com.comunidapp.shared.poc.m22.model.M22PocBranch
import com.comunidapp.shared.poc.m22.model.M22PocCategory
import com.comunidapp.shared.poc.m22.model.M22PocDetail
import com.comunidapp.shared.poc.m22.model.M22PocListing
import com.comunidapp.shared.poc.m22.model.M22PocOffering
import com.comunidapp.shared.poc.m22.model.M22PocPriceType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * READ-ONLY Supabase implementation for POC.
 * Uses production RPCs: m22_list_catalog / m22_get_provider_detail.
 */
class SupabaseM22PocCatalogRepository(
    private val client: SupabaseClient
) : M22PocCatalogRepository {

    override fun observeCatalog(): Flow<List<M22PocListing>> = flow {
        val rows = client.postgrest.rpc(
            function = "m22_list_catalog",
            parameters = buildJsonObject {
                put("p_category", JsonNull)
                put("p_city", JsonNull)
            }
        ).decodeList<JsonObject>()
        emit(rows.mapNotNull { it.toListingOrNull() }.map(M22PocPrivacy::sanitizeListing))
    }

    override fun observeDetail(providerId: String): Flow<M22PocDetail?> = flow {
        val row = runCatching {
            client.postgrest.rpc(
                function = "m22_get_provider_detail",
                parameters = buildJsonObject { put("p_provider_id", providerId) }
            ).decodeSingle<JsonObject>()
        }.getOrNull()
        emit(row?.toDetailOrNull(providerId)?.let(M22PocPrivacy::sanitizeDetail))
    }

    companion object {
        fun create(config: PocSupabaseConfig): SupabaseM22PocCatalogRepository {
            require(config.isUsable) { "POC Supabase config is not usable (https url + anon key required)" }
            val client = createSupabaseClient(
                supabaseUrl = config.url.trim(),
                supabaseKey = config.anonKey.trim()
            ) {
                install(Postgrest)
            }
            return SupabaseM22PocCatalogRepository(client)
        }
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

private fun JsonObject.toListingOrNull(): M22PocListing? {
    val name = str("display_name") ?: return null
    val id = str("id") ?: name
    return M22PocListing(
        id = id,
        displayName = name,
        category = enumOr(str("category"), M22PocCategory.OTHER),
        description = str("description").orEmpty(),
        city = str("city").orEmpty(),
        branchCount = long("branch_count")?.toInt() ?: 0,
        priceSummary = str("price_summary")
    )
}

private fun JsonObject.toDetailOrNull(fallbackId: String): M22PocDetail? {
    val name = str("display_name") ?: return null
    val branches = (this["branches"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el.jsonObject
        M22PocBranch(
            name = o.str("name").orEmpty(),
            city = o.str("city").orEmpty(),
            neighborhood = o.str("neighborhood"),
            coverage = o.str("coverage").orEmpty()
        )
    }
    val offerings = (this["offerings"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el.jsonObject
        M22PocOffering(
            name = o.str("name").orEmpty(),
            description = o.str("description").orEmpty(),
            priceType = enumOr(o.str("price_type"), M22PocPriceType.QUOTE),
            priceAmount = o.long("price_amount_cents") ?: o.long("price_amount"),
            currency = o.str("currency") ?: "ARS"
        )
    }
    return M22PocDetail(
        id = str("id") ?: fallbackId,
        displayName = name,
        category = enumOr(str("category"), M22PocCategory.OTHER),
        description = str("description").orEmpty(),
        city = str("city").orEmpty(),
        branches = branches,
        offerings = offerings
    )
}

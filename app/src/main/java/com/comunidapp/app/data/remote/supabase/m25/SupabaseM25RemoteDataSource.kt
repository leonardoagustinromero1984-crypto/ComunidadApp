package com.comunidapp.app.data.remote.supabase.m25

import com.comunidapp.app.data.model.*
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

private fun JsonElement?.longOrNull(): Long? = (this as? JsonPrimitive)?.longOrNull

private fun JsonObject.string(key: String): String? = this[key].stringOrNull()
private fun JsonObject.long(key: String): Long? = this[key].longOrNull()

private fun parseTimestamp(value: String?): Long =
    value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: System.currentTimeMillis()

private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

fun JsonObject.toM25PublicShopListing(): M25PublicShopListing = M25PublicShopListing(
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M25ShopCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    productCount = long("product_count")?.toInt() ?: 0,
    priceSummary = string("price_summary")
)

private fun JsonElement?.booleanOrNull(): Boolean? =
    when (this) {
        is JsonPrimitive -> contentOrNull?.toBooleanStrictOrNull()
        else -> null
    }

fun JsonObject.toM25PublicProduct(): M25PublicProduct = M25PublicProduct(
    name = string("name").orEmpty(),
    description = string("description").orEmpty(),
    listPriceCents = long("list_price_cents") ?: 0,
    currency = string("currency") ?: "ARS",
    inStock = this["in_stock"].booleanOrNull() ?: false
)

fun JsonObject.toM25PublicShopDetail(): M25PublicShopDetail = M25PublicShopDetail(
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M25ShopCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    products = (this["products"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toM25PublicProduct() }
)

fun JsonObject.toM25Shop(): M25Shop = M25Shop(
    id = string("id").orEmpty(),
    ownerUserId = string("owner_user_id").orEmpty(),
    organizationId = string("organization_id"),
    displayName = string("display_name").orEmpty(),
    category = enumOr(string("category"), M25ShopCategory.OTHER),
    description = string("description").orEmpty(),
    city = string("city").orEmpty(),
    status = enumOr(string("status"), M25ShopStatus.DRAFT),
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM25CartItem(): M25CartItem = M25CartItem(
    id = string("id").orEmpty(),
    userId = string("user_id").orEmpty(),
    productId = string("product_id").orEmpty(),
    shopId = string("shop_id").orEmpty(),
    quantity = long("quantity")?.toInt() ?: 1,
    clientLineId = string("client_line_id"),
    updatedAt = parseTimestamp(string("updated_at"))
)

fun JsonObject.toM25OrderSummary(): M25OrderSummary = M25OrderSummary(
    id = string("id").orEmpty(),
    shopName = string("shop_name").orEmpty(),
    status = enumOr(string("status"), M25OrderStatus.SUBMITTED),
    lineCount = long("line_count")?.toInt() ?: 0,
    subtotalCents = long("subtotal_cents") ?: 0,
    currency = string("currency") ?: "ARS",
    createdAt = parseTimestamp(string("created_at"))
)

class SupabaseM25RemoteDataSource {
    private suspend inline fun <reified T : Any> one(function: String, parameters: JsonObject): T =
        supabase.postgrest.rpc(function, parameters).decodeSingle()

    private suspend inline fun <reified T : Any> list(function: String, parameters: JsonObject): List<T> =
        supabase.postgrest.rpc(function, parameters).decodeList()

    suspend fun listCatalog(category: String?, city: String?): List<JsonObject> =
        list("m25_list_catalog", buildJsonObject { put("p_category", category); put("p_city", city) })

    suspend fun getShopDetail(shopId: String): JsonObject =
        one("m25_get_shop_detail", buildJsonObject { put("p_shop_id", shopId) })

    suspend fun listMyShops(): List<JsonObject> = list("m25_list_my_shops", buildJsonObject {})

    suspend fun createShop(displayName: String, category: String, description: String, city: String, organizationId: String?): JsonObject =
        one("m25_create_shop", buildJsonObject {
            put("p_display_name", displayName); put("p_category", category); put("p_description", description)
            put("p_city", city); put("p_organization_id", organizationId)
        })

    suspend fun listCart(): List<JsonObject> = list("m25_list_cart", buildJsonObject {})

    suspend fun addToCart(productId: String, quantity: Int, clientLineId: String?): JsonObject =
        one("m25_add_to_cart", buildJsonObject {
            put("p_product_id", productId); put("p_quantity", quantity); put("p_client_line_id", clientLineId)
        })

    suspend fun listMyOrders(): List<JsonObject> = list("m25_list_my_orders", buildJsonObject {})
}

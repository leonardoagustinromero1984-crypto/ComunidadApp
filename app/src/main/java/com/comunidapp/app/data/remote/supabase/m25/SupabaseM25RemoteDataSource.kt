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

fun JsonObject.toM25ShopProduct(): M25Product = M25Product(
    id = string("id").orEmpty(),
    shopId = string("shop_id").orEmpty(),
    sku = string("sku").orEmpty(),
    name = string("name").orEmpty(),
    description = string("description").orEmpty(),
    listPriceCents = long("list_price_cents") ?: 0,
    currency = string("currency") ?: "ARS",
    stockQuantity = long("stock_quantity")?.toInt() ?: 0,
    status = enumOr(string("status"), M25ProductStatus.ACTIVE)
)

fun JsonObject.toM25Promotion(): M25Promotion = M25Promotion(
    id = string("id").orEmpty(),
    shopId = string("shop_id").orEmpty(),
    code = string("code").orEmpty(),
    type = enumOr(string("promo_type"), M25PromotionType.PERCENTAGE),
    value = long("promo_value") ?: 0,
    status = enumOr(string("status"), M25PromotionStatus.DRAFT),
    startsAt = parseTimestamp(string("starts_at")),
    endsAt = parseTimestamp(string("ends_at"))
)

fun JsonObject.toM25MerchantMetrics(): M25MerchantMetrics = M25MerchantMetrics(
    created = long("created")?.toInt() ?: 0,
    accepted = long("accepted")?.toInt() ?: 0,
    preparing = long("preparing")?.toInt() ?: 0,
    dispatched = long("dispatched")?.toInt() ?: 0,
    delivered = long("delivered")?.toInt() ?: 0,
    cancelled = long("cancelled")?.toInt() ?: 0,
    returns = long("returns")?.toInt() ?: 0,
    unitsSold = long("units_sold")?.toInt() ?: 0,
    lowStockProducts = long("low_stock_products")?.toInt() ?: 0
)

fun JsonObject.toM25Order(): M25Order {
    val lines = (this["lines"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toM25OrderLine() }
    return M25Order(
        id = string("id").orEmpty(),
        shopId = string("shop_id").orEmpty(),
        customerUserId = string("customer_user_id").orEmpty(),
        status = enumOr(string("status"), M25OrderStatus.SUBMITTED),
        lines = lines,
        subtotalCents = long("subtotal_cents") ?: 0,
        discountCents = long("discount_cents") ?: 0,
        currency = string("currency") ?: "ARS",
        shippingMode = enumOr(string("shipping_mode"), M25ShippingMode.PICKUP),
        shippingCity = string("shipping_city").orEmpty(),
        shippingNotes = string("shipping_notes"),
        promotionCode = string("promotion_code"),
        clientRequestId = string("client_request_id"),
        tracking = string("tracking_code")?.let {
            M25ShippingTracking(
                status = enumOr(string("status"), M25OrderStatus.SUBMITTED),
                trackingCode = it,
                carrierText = string("carrier_text"),
                dispatchedAt = parseTimestamp(string("dispatched_at")),
                deliveredAt = parseTimestamp(string("delivered_at"))
            )
        },
        createdAt = parseTimestamp(string("created_at")),
        updatedAt = parseTimestamp(string("updated_at"))
    )
}

fun JsonObject.toM25OrderLine(): M25OrderLine = M25OrderLine(
    productId = string("product_id").orEmpty(),
    productName = string("product_name").orEmpty(),
    quantity = long("quantity")?.toInt() ?: 1,
    unitPriceCents = long("unit_price_cents") ?: 0,
    currency = string("currency") ?: "ARS",
    discountCents = long("discount_cents") ?: 0,
    subtotalCents = long("subtotal_cents") ?: 0
)

fun JsonObject.toM25OrderHistoryEntry(): M25OrderHistoryEntry = M25OrderHistoryEntry(
    id = string("id").orEmpty(),
    orderId = string("order_id").orEmpty(),
    fromStatus = string("from_status")?.let { enumOr(it, M25OrderStatus.SUBMITTED) },
    toStatus = enumOr(string("to_status"), M25OrderStatus.SUBMITTED),
    publicReason = string("public_reason"),
    actorRole = string("actor_role").orEmpty(),
    createdAt = parseTimestamp(string("created_at"))
)

fun JsonObject.toM25ReturnRequest(): M25ReturnRequest = M25ReturnRequest(
    id = string("id").orEmpty(),
    orderId = string("order_id").orEmpty(),
    customerUserId = string("customer_user_id").orEmpty(),
    reason = string("reason").orEmpty(),
    status = enumOr(string("status"), M25ReturnStatus.REQUESTED),
    lines = (this["lines"] as? JsonArray).orEmpty().mapNotNull { el ->
        (el as? JsonObject)?.let { o ->
            M25ReturnLine(o.string("product_id").orEmpty(), o.long("quantity")?.toInt() ?: 0)
        }
    },
    createdAt = parseTimestamp(string("created_at")),
    updatedAt = parseTimestamp(string("updated_at"))
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

    suspend fun listShopProducts(shopId: String): List<JsonObject> =
        list("m25_list_shop_products", buildJsonObject { put("p_shop_id", shopId) })

    suspend fun listShopPromotions(shopId: String): List<JsonObject> =
        list("m25_list_shop_promotions", buildJsonObject { put("p_shop_id", shopId) })

    suspend fun updateShop(shopId: String, displayName: String?, description: String?, city: String?, status: String?): JsonObject =
        one("m25_update_shop", buildJsonObject {
            put("p_shop_id", shopId); put("p_display_name", displayName); put("p_description", description)
            put("p_city", city); put("p_status", status)
        })

    suspend fun upsertProduct(input: UpsertM25ProductInput): JsonObject =
        one("m25_upsert_product", buildJsonObject {
            put("p_shop_id", input.shopId); put("p_product_id", input.productId); put("p_sku", input.sku)
            put("p_name", input.name); put("p_description", input.description); put("p_list_price_cents", input.listPriceCents)
            put("p_currency", input.currency); put("p_stock_quantity", input.stockQuantity); put("p_status", input.status.name)
        })

    suspend fun upsertPromotion(input: UpsertM25PromotionInput): JsonObject =
        one("m25_upsert_promotion", buildJsonObject {
            put("p_shop_id", input.shopId); put("p_promotion_id", input.promotionId); put("p_code", input.code)
            put("p_promo_type", input.type.name); put("p_promo_value", input.value)
            put("p_starts_at", java.time.Instant.ofEpochMilli(input.startsAt).toString())
            put("p_ends_at", java.time.Instant.ofEpochMilli(input.endsAt).toString())
            put("p_status", input.status.name)
        })

    suspend fun transitionShop(shopId: String, status: String): JsonObject =
        one("m25_transition_shop", buildJsonObject { put("p_shop_id", shopId); put("p_status", status) })

    suspend fun adjustStock(productId: String, newTotal: Int, reason: String): JsonObject =
        one("m25_adjust_stock", buildJsonObject { put("p_product_id", productId); put("p_new_total", newTotal); put("p_reason", reason) })

    suspend fun merchantMetrics(shopId: String): JsonObject =
        one("m25_merchant_metrics", buildJsonObject { put("p_shop_id", shopId) })

    suspend fun updateCartItem(cartItemId: String, quantity: Int): JsonObject =
        one("m25_update_cart_item", buildJsonObject { put("p_cart_item_id", cartItemId); put("p_quantity", quantity) })

    suspend fun removeCartItem(cartItemId: String) {
        one<JsonObject>("m25_remove_cart_item", buildJsonObject { put("p_cart_item_id", cartItemId) })
    }

    suspend fun clearCart() {
        one<JsonObject>("m25_clear_cart", buildJsonObject {})
    }

    suspend fun listShopOrders(shopId: String): List<JsonObject> =
        list("m25_list_shop_orders", buildJsonObject { put("p_shop_id", shopId) })

    suspend fun getOrder(orderId: String): JsonObject? = runCatching {
        one<JsonObject>("m25_get_order", buildJsonObject { put("p_order_id", orderId) })
    }.getOrNull()

    suspend fun listOrderHistory(orderId: String): List<JsonObject> =
        list("m25_list_order_history", buildJsonObject { put("p_order_id", orderId) })

    suspend fun submitOrder(input: SubmitM25OrderInput): JsonObject =
        one("m25_submit_order", buildJsonObject {
            put("p_shop_id", input.shopId); put("p_shipping_mode", input.shippingMode.name)
            put("p_shipping_city", input.shippingCity); put("p_shipping_notes", input.shippingNotes)
            put("p_promotion_code", input.promotionCode); put("p_client_request_id", input.clientRequestId)
        })

    suspend fun transitionOrder(orderId: String, status: String): JsonObject =
        one("m25_transition_order", buildJsonObject { put("p_order_id", orderId); put("p_status", status) })

    suspend fun rejectOrder(orderId: String, publicReason: String?): JsonObject =
        one("m25_reject_order", buildJsonObject { put("p_order_id", orderId); put("p_public_reason", publicReason) })

    suspend fun cancelOrderByCustomer(orderId: String, reason: String?): JsonObject =
        one("m25_cancel_order_customer", buildJsonObject { put("p_order_id", orderId); put("p_reason", reason) })

    suspend fun cancelOrderByMerchant(orderId: String, publicReason: String?): JsonObject =
        one("m25_cancel_order_merchant", buildJsonObject { put("p_order_id", orderId); put("p_public_reason", publicReason) })

    suspend fun shipOrder(orderId: String, trackingCode: String?, carrierText: String?): JsonObject =
        one("m25_ship_order", buildJsonObject {
            put("p_order_id", orderId); put("p_tracking_code", trackingCode); put("p_carrier_text", carrierText)
        })

    suspend fun requestReturn(input: RequestM25ReturnInput): JsonObject =
        one("m25_request_return", buildJsonObject {
            put("p_order_id", input.orderId); put("p_reason", input.reason)
            put("p_client_request_id", input.clientRequestId)
            put("p_lines", kotlinx.serialization.json.buildJsonArray {
                input.lines.forEach { line ->
                    add(buildJsonObject { put("product_id", line.productId); put("quantity", line.quantity) })
                }
            })
        })

    suspend fun approveReturn(returnId: String): JsonObject =
        one("m25_approve_return", buildJsonObject { put("p_return_id", returnId) })

    suspend fun rejectReturn(returnId: String, publicReason: String?): JsonObject =
        one("m25_reject_return", buildJsonObject { put("p_return_id", returnId); put("p_public_reason", publicReason) })

    suspend fun receiveReturn(returnId: String, replenishStock: Boolean): JsonObject =
        one("m25_receive_return", buildJsonObject { put("p_return_id", returnId); put("p_replenish_stock", replenishStock) })
}

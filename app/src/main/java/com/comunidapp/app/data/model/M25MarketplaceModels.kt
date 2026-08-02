package com.comunidapp.app.data.model

import com.comunidapp.app.domain.m25.M25PrivacySanitizer

/** LeoVer M25 — Marketplace, pedidos y promociones (Bloque 1 local; sin pagos). */
enum class M25ShopCategory { PET_FOOD, ACCESSORIES, HEALTH, GROOMING, OTHER }
enum class M25ShopStatus { DRAFT, ACTIVE, PAUSED, SUSPENDED, CLOSED, ARCHIVED }
enum class M25ProductStatus { DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK, PAUSED, ARCHIVED, REMOVED_BY_MODERATION }
enum class M25PromotionType { PERCENTAGE, FIXED_AMOUNT }
enum class M25PromotionStatus { DRAFT, ACTIVE, EXPIRED, ARCHIVED }
enum class M25OrderStatus {
    DRAFT, SUBMITTED, ACCEPTED, PREPARING, READY_FOR_DISPATCH, SHIPPED, DELIVERED,
    REJECTED, CANCELLED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_MERCHANT,
    RETURN_REQUESTED, RETURNED, CLOSED
}
enum class M25ReturnStatus { REQUESTED, APPROVED, REJECTED, RECEIVED, CLOSED }
enum class M25ShippingMode { PICKUP, DELIVERY }
enum class M25StockMovementType { RESERVE, RELEASE, COMMIT, REPLENISH, ADJUST }

data class M25CatalogFilter(
    val category: M25ShopCategory? = null,
    val city: String? = null
)

data class M25NotificationHookState(
    val available: Boolean = false,
    val orderSubmitted: Boolean = false,
    val orderShipped: Boolean = false,
    val message: String = "M25_NOTIFICATIONS_UNAVAILABLE"
)

data class M25Shop(
    val id: String,
    val ownerUserId: String,
    val organizationId: String? = null,
    val displayName: String,
    val category: M25ShopCategory,
    val description: String,
    val city: String,
    val status: M25ShopStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toPublicListing(productCount: Int, priceSummary: String?): M25PublicShopListing =
        M25PrivacySanitizer.toPublicListing(this, productCount, priceSummary)

    fun toPublicDetail(products: List<M25PublicProduct>): M25PublicShopDetail =
        M25PrivacySanitizer.toPublicDetail(this, products)
}

data class M25Product(
    val id: String,
    val shopId: String,
    val sku: String,
    val name: String,
    val description: String,
    val listPriceCents: Long,
    val currency: String = "ARS",
    val stockQuantity: Int,
    val status: M25ProductStatus = M25ProductStatus.ACTIVE
)

data class M25Promotion(
    val id: String,
    val shopId: String,
    val code: String,
    val type: M25PromotionType,
    val value: Long,
    val status: M25PromotionStatus,
    val startsAt: Long,
    val endsAt: Long
)

data class M25CartItem(
    val id: String,
    val userId: String,
    val productId: String,
    val shopId: String,
    val quantity: Int,
    val clientLineId: String? = null,
    val updatedAt: Long
)

data class M25OrderLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val currency: String = "ARS",
    val discountCents: Long = 0,
    val subtotalCents: Long = unitPriceCents * quantity
)

data class M25OrderHistoryEntry(
    val id: String,
    val orderId: String,
    val fromStatus: M25OrderStatus?,
    val toStatus: M25OrderStatus,
    val publicReason: String? = null,
    val actorRole: String,
    val createdAt: Long
)

data class M25ShippingTracking(
    val status: M25OrderStatus,
    val trackingCode: String? = null,
    val carrierText: String? = null,
    val dispatchedAt: Long? = null,
    val deliveredAt: Long? = null,
    val estimatedAt: Long? = null
)

data class M25StockMovement(
    val id: String,
    val productId: String,
    val movementType: M25StockMovementType,
    val quantity: Int,
    val reason: String? = null,
    val createdAt: Long
)

data class M25ReturnLine(
    val productId: String,
    val quantity: Int
)

data class M25ReturnRequest(
    val id: String,
    val orderId: String,
    val customerUserId: String,
    val reason: String,
    val status: M25ReturnStatus,
    val lines: List<M25ReturnLine> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

data class M25Order(
    val id: String,
    val shopId: String,
    val customerUserId: String,
    val status: M25OrderStatus,
    val lines: List<M25OrderLine>,
    val subtotalCents: Long,
    val discountCents: Long = 0,
    val currency: String = "ARS",
    val shippingMode: M25ShippingMode,
    val shippingCity: String,
    val shippingNotes: String? = null,
    val promotionCode: String? = null,
    val clientRequestId: String? = null,
    val tracking: M25ShippingTracking? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class M25MerchantMetrics(
    val created: Int = 0,
    val accepted: Int = 0,
    val preparing: Int = 0,
    val dispatched: Int = 0,
    val delivered: Int = 0,
    val cancelled: Int = 0,
    val returns: Int = 0,
    val unitsSold: Int = 0,
    val lowStockProducts: Int = 0
)

data class M25PublicShopListing(
    val displayName: String,
    val category: M25ShopCategory,
    val description: String,
    val city: String,
    val productCount: Int,
    val priceSummary: String? = null
)

data class M25PublicProduct(
    val name: String,
    val description: String,
    val listPriceCents: Long,
    val currency: String = "ARS",
    val inStock: Boolean
)

data class M25PublicShopDetail(
    val displayName: String,
    val category: M25ShopCategory,
    val description: String,
    val city: String,
    val products: List<M25PublicProduct>
)

data class M25OrderSummary(
    val id: String,
    val shopName: String,
    val status: M25OrderStatus,
    val lineCount: Int,
    val subtotalCents: Long,
    val currency: String,
    val createdAt: Long
)

data class CreateM25ShopInput(
    val displayName: String,
    val category: M25ShopCategory,
    val description: String,
    val city: String,
    val organizationId: String? = null
)

data class UpdateM25ShopInput(
    val shopId: String,
    val displayName: String? = null,
    val description: String? = null,
    val city: String? = null,
    val status: M25ShopStatus? = null
)

data class UpsertM25ProductInput(
    val shopId: String,
    val productId: String? = null,
    val sku: String,
    val name: String,
    val description: String,
    val listPriceCents: Long,
    val currency: String = "ARS",
    val stockQuantity: Int,
    val status: M25ProductStatus = M25ProductStatus.ACTIVE
)

data class AddM25CartItemInput(
    val productId: String,
    val quantity: Int,
    val clientLineId: String? = null
)

data class RequestM25ReturnInput(
    val orderId: String,
    val reason: String,
    val lines: List<M25ReturnLine>,
    val clientRequestId: String? = null
)

data class UpsertM25PromotionInput(
    val shopId: String,
    val promotionId: String? = null,
    val code: String,
    val type: M25PromotionType,
    val value: Long,
    val startsAt: Long,
    val endsAt: Long,
    val status: M25PromotionStatus = M25PromotionStatus.DRAFT
)

data class AdjustM25StockInput(
    val productId: String,
    val newTotal: Int,
    val reason: String
)

data class SubmitM25OrderInput(
    val shopId: String,
    val shippingMode: M25ShippingMode,
    val shippingCity: String,
    val shippingNotes: String? = null,
    val promotionCode: String? = null,
    val clientRequestId: String? = null
)

object M25MockUsers {
    const val MERCHANT = "mock_user_m25_merchant"
    const val CUSTOMER = "mock_user_m25_customer"
    const val OTHER_MERCHANT = "mock_user_m25_other_merchant"
    const val UNAUTHORIZED = "mock_user_unauthorized"
}

object M25MockShopIds {
    const val ACTIVE = "m25_shop_active"
    const val DRAFT = "m25_shop_draft"
    const val SUSPENDED = "m25_shop_suspended"
    const val EMPTY_PRODUCTS = "m25_shop_empty"
}

object M25MockProductIds {
    const val FOOD_BAG = "m25_product_food"
    const val COLLAR = "m25_product_collar"
    const val OUT_OF_STOCK = "m25_product_oos"
}

object M25MockOrderIds {
    const val SUBMITTED = "m25_order_submitted"
    const val DELIVERED = "m25_order_delivered"
}

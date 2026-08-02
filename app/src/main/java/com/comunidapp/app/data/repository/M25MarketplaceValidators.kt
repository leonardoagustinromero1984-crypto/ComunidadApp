package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.M25ProductStatus
import com.comunidapp.app.data.model.M25PromotionType
import com.comunidapp.app.data.model.M25ShopStatus
import com.comunidapp.app.domain.m25.M25ShopLifecycle

object M25MarketplaceValidators {
    fun validateShop(displayName: String, description: String, city: String): String? = when {
        !isSafeName(displayName) || city.trim().isEmpty() -> "M25_INVALID_SHOP"
        !isSafeDescription(description) -> "M25_INVALID_SHOP"
        else -> null
    }

    fun validateProduct(name: String, description: String, listPriceCents: Long, stockQuantity: Int): String? = when {
        !isSafeName(name) || !isSafeDescription(description) -> "M25_INVALID_PRODUCT"
        listPriceCents <= 0 -> "M25_INVALID_PRICE"
        stockQuantity < 0 -> "M25_INVALID_STOCK"
        else -> null
    }

    fun validatePromotion(code: String, type: M25PromotionType, value: Long): String? = when {
        code.trim().length !in 3..32 || !code.matches(Regex("[A-Z0-9_-]+")) -> "M25_INVALID_PROMOTION"
        type == M25PromotionType.PERCENTAGE && value !in 1..100 -> "M25_INVALID_PROMOTION"
        type == M25PromotionType.FIXED_AMOUNT && value <= 0 -> "M25_INVALID_PROMOTION"
        else -> null
    }

    fun validateCartQuantity(quantity: Int): String? =
        if (quantity in 1..99) null else "M25_INVALID_QUANTITY"

    fun validateShippingCity(city: String): String? =
        if (city.trim().length in 2..120 && !unsafe(city)) null else "M25_INVALID_SHIPPING"

    fun validateReturnReason(reason: String): String? =
        if (reason.trim().length in 10..500 && !unsafe(reason)) null else "M25_INVALID_RETURN"

    fun validateStockAdjustReason(reason: String): String? =
        if (reason.trim().length in 5..200 && !unsafe(reason)) null else "M25_INVALID_STOCK"

    fun validateShopStatusTransition(
        current: M25ShopStatus,
        target: M25ShopStatus,
        hasActiveProduct: Boolean = false
    ): String? = M25ShopLifecycle.validateTransition(current, target, hasActiveProduct)

    private fun isSafeName(value: String): Boolean =
        value.trim().length in 2..120 && !unsafe(value)

    private fun isSafeDescription(value: String): Boolean =
        value.trim().length in 10..2_000 && !unsafe(value)

    private fun unsafe(value: String): Boolean =
        Regex("(?i)<script|javascript:|on\\w+\\s*=|<iframe").containsMatchIn(value)
}

class M25MarketplaceException(val code: String, message: String) : IllegalStateException(message)

object M25MarketplaceErrors {
    fun userMessage(code: String): String = when (code) {
        "NOT_AUTHENTICATED" -> "Tenés que iniciar sesión."
        "M25_PERMISSION_DENIED" -> "No tenés permiso para gestionar esta tienda."
        "M25_SHOP_NOT_FOUND" -> "No encontramos la tienda."
        "M25_PRODUCT_NOT_FOUND" -> "No encontramos el producto."
        "M25_ORDER_NOT_FOUND" -> "No encontramos el pedido."
        "M25_CART_EMPTY" -> "El carrito está vacío."
        "M25_OUT_OF_STOCK" -> "No hay stock suficiente."
        "M25_INVALID_SHOP" -> "Los datos de la tienda no son válidos."
        "M25_INVALID_PRODUCT" -> "Los datos del producto no son válidos."
        "M25_INVALID_PRICE" -> "El precio informado no es válido."
        "M25_INVALID_STOCK" -> "El stock informado no es válido."
        "M25_INVALID_QUANTITY" -> "La cantidad no es válida."
        "M25_INVALID_SHIPPING" -> "Los datos de envío no son válidos."
        "M25_INVALID_RETURN" -> "El motivo de devolución no es válido."
        "M25_INVALID_ORDER_TRANSITION" -> "No se puede cambiar el estado del pedido."
        "M25_ORDER_TERMINAL" -> "El pedido ya está cerrado."
        "M25_SHOP_NOT_PUBLIC" -> "Esta tienda no está disponible."
        "M25_PROMOTION_INVALID" -> "La promoción no es válida."
        "M25_DUPLICATE_CLIENT_REQUEST" -> "La solicitud ya fue procesada."
        "M25_INVALID_RETURN" -> "La devolución no es válida."
        "M25_INVALID_RETURN_TRANSITION" -> "No se puede cambiar el estado de la devolución."
        "M25_INVALID_PROMOTION" -> "La promoción no es válida."
        else -> "No pudimos completar la operación."
    }

    fun <T> failure(error: Throwable): Result<T> = Result.failure(error)
}

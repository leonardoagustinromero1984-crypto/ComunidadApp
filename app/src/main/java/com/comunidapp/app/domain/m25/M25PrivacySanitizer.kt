package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.M25Product
import com.comunidapp.app.data.model.M25PublicProduct
import com.comunidapp.app.data.model.M25PublicShopDetail
import com.comunidapp.app.data.model.M25PublicShopListing
import com.comunidapp.app.data.model.M25Shop

object M25PrivacySanitizer {
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val phonePattern = Regex("(?i)(\\+?\\d[\\d\\s().-]{6,}\\d)")
    private val addressPattern = Regex("(?i)(calle|av\\.?|avenida|pasaje)\\s+[\\w\\s\\d]+")
    private val documentPattern = Regex("(?i)(dni|cuil|cuit|documento)\\s*[:#]?\\s*[\\w\\d-]+")

    fun scrubPublicText(text: String): String = text
        .replace(emailPattern, "[redactado]")
        .replace(phonePattern, "[redactado]")
        .replace(addressPattern, "[redactado]")
        .replace(documentPattern, "[redactado]")
        .trim()

    fun toPublicListing(shop: M25Shop, productCount: Int, priceSummary: String?): M25PublicShopListing =
        M25PublicShopListing(
            displayName = scrubPublicText(shop.displayName),
            category = shop.category,
            description = scrubPublicText(shop.description),
            city = scrubPublicText(shop.city),
            productCount = productCount,
            priceSummary = priceSummary
        )

    fun toPublicDetail(shop: M25Shop, products: List<M25PublicProduct>): M25PublicShopDetail =
        M25PublicShopDetail(
            displayName = scrubPublicText(shop.displayName),
            category = shop.category,
            description = scrubPublicText(shop.description),
            city = scrubPublicText(shop.city),
            products = products
        )

    fun toPublicProduct(product: M25Product): M25PublicProduct? =
        if (product.status != com.comunidapp.app.data.model.M25ProductStatus.ACTIVE) null
        else M25PublicProduct(
            name = scrubPublicText(product.name),
            description = scrubPublicText(product.description),
            listPriceCents = product.listPriceCents,
            currency = product.currency,
            inStock = product.stockQuantity > 0
        )
}

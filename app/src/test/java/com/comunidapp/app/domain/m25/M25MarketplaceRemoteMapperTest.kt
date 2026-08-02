package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.M25ShopCategory
import com.comunidapp.app.data.remote.supabase.m25.toM25OrderSummary
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopDetail
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopListing
import com.comunidapp.app.data.remote.supabase.m25.toM25Shop
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M25MarketplaceRemoteMapperTest {
    @Test
    fun publicShopListingMapsCatalogProjection() {
        val listing = buildJsonObject {
            put("display_name", "Huellitas Market")
            put("category", "PET_FOOD")
            put("description", "Alimentos para mascotas")
            put("city", "CABA")
            put("product_count", 3)
            put("price_summary", "ARS 8500")
        }.toM25PublicShopListing()
        assertEquals(M25ShopCategory.PET_FOOD, listing.category)
        assertEquals(3, listing.productCount)
        assertFalse(listing.toString().contains("owner_user_id"))
    }

    @Test
    fun publicShopDetailMapsProducts() {
        val detail = buildJsonObject {
            put("display_name", "Huellitas Market")
            put("category", "PET_FOOD")
            put("description", "Alimentos")
            put("city", "CABA")
            put("products", buildJsonArray {
                add(buildJsonObject {
                    put("name", "Collar"); put("description", "Ajustable")
                    put("list_price_cents", 8500); put("currency", "ARS"); put("in_stock", true)
                })
            })
        }.toM25PublicShopDetail()
        assertEquals("Collar", detail.products.single().name)
        assertTrue(detail.products.single().inStock)
    }

    @Test
    fun orderSummaryMapsWithoutPaymentFields() {
        val summary = buildJsonObject {
            put("id", "order-1"); put("shop_name", "Huellitas")
            put("status", "SUBMITTED"); put("line_count", 2)
            put("subtotal_cents", 53500); put("currency", "ARS")
            put("created_at", "2026-01-01T00:00:00Z")
        }.toM25OrderSummary()
        assertEquals(53500L, summary.subtotalCents)
        assertFalse(summary.toString().contains("payment"))
    }

    @Test
    fun internalShopMapsOwner() {
        val shop = buildJsonObject {
            put("id", "shop-1"); put("owner_user_id", "owner-1")
            put("display_name", "Tienda"); put("category", "OTHER")
            put("description", "Descripción válida"); put("city", "CABA")
            put("status", "DRAFT"); put("created_at", "2026-01-01T00:00:00Z"); put("updated_at", "2026-01-02T00:00:00Z")
        }.toM25Shop()
        assertEquals("owner-1", shop.ownerUserId)
    }
}

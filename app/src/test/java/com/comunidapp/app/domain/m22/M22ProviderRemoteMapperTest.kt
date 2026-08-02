package com.comunidapp.app.domain.m22

import com.comunidapp.app.data.model.M22PriceType
import com.comunidapp.app.data.model.M22ProviderCategory
import com.comunidapp.app.data.remote.supabase.m22.toM22ProviderProfile
import com.comunidapp.app.data.remote.supabase.m22.toM22PublicDetail
import com.comunidapp.app.data.remote.supabase.m22.toM22PublicListing
import com.comunidapp.app.data.remote.supabase.m22.toM22ServiceOffering
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class M22ProviderRemoteMapperTest {
    @Test
    fun publicListingMapsOnlyCatalogProjection() {
        val listing = buildJsonObject {
            put("display_name", "Patitas Centro")
            put("category", "GROOMING")
            put("description", "Baño y peluquería")
            put("city", "CABA")
            put("branch_count", 2)
            put("price_summary", "Desde")
        }.toM22PublicListing()

        assertEquals(M22ProviderCategory.GROOMING, listing.category)
        assertEquals(2, listing.branchCount)
        assertFalse(listing.toString().contains("owner_user_id"))
    }

    @Test
    fun publicDetailMapsNestedBranchesAndOfferings() {
        val detail = buildJsonObject {
            put("display_name", "Patitas Centro")
            put("category", "GROOMING")
            put("description", "Baño y peluquería")
            put("city", "CABA")
            put("branches", buildJsonArray {
                add(buildJsonObject {
                    put("name", "Sede Centro"); put("city", "CABA")
                    put("neighborhood", "Balvanera"); put("coverage", "CABA · Balvanera")
                })
            })
            put("offerings", buildJsonArray {
                add(buildJsonObject {
                    put("name", "Baño completo"); put("description", "Incluye secado")
                    put("price_type", "FIXED"); put("price_amount_cents", 18000); put("currency", "ARS")
                })
            })
        }.toM22PublicDetail()

        assertEquals("Sede Centro", detail.branches.single().name)
        assertEquals(M22PriceType.FIXED, detail.offerings.single().priceType)
        assertEquals(18000L, detail.offerings.single().priceAmount)
    }

    @Test
    fun internalProfileAndOfferingMapCentsAndNullQuotePrice() {
        val profile = buildJsonObject {
            put("id", "provider-1"); put("owner_user_id", "owner-1"); put("display_name", "Patitas")
            put("category", "GROOMING"); put("description", "Baño y peluquería"); put("city", "CABA")
            put("status", "DRAFT"); put("created_at", "2026-01-01T00:00:00Z"); put("updated_at", "2026-01-02T00:00:00Z")
        }.toM22ProviderProfile()
        val offering = buildJsonObject {
            put("id", "offering-1"); put("provider_id", "provider-1"); put("name", "A cotizar")
            put("description", "Según necesidades"); put("price_type", "QUOTE"); put("currency", "ARS"); put("active", true)
        }.toM22ServiceOffering()

        assertEquals("owner-1", profile.ownerUserId)
        assertEquals(M22PriceType.QUOTE, offering.priceType)
        assertNull(offering.priceAmount)
    }
}

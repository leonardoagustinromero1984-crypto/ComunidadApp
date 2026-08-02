package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class M25MarketplaceFoundationTest {
    private fun store() = M25MarketplaceMemoryStore().also { it.seedDefaults() }
    private fun marketplace(actor: String = M25MockUsers.MERCHANT, s: M25MarketplaceMemoryStore = store()) =
        MockM25MarketplaceRepository({ actor }, s)
    private fun cart(actor: String = M25MockUsers.CUSTOMER, s: M25MarketplaceMemoryStore = store()) =
        MockM25CartRepository({ actor }, s)
    private fun orders(actor: String = M25MockUsers.CUSTOMER, s: M25MarketplaceMemoryStore = store()) =
        MockM25OrderRepository({ actor }, s)

    @Test fun validShopAccepted() = assertNull(M25MarketplaceValidators.validateShop("Tienda", "Descripción válida de tienda.", "CABA"))
    @Test fun blankShopRejected() = assertEquals("M25_INVALID_SHOP", M25MarketplaceValidators.validateShop("", "Descripción válida de tienda.", "CABA"))
    @Test fun unsafeDescriptionRejected() = assertEquals("M25_INVALID_SHOP", M25MarketplaceValidators.validateShop("Tienda", "<script>x</script>", "CABA"))
    @Test fun fixedPriceMustBePositive() = assertEquals("M25_INVALID_PRICE", M25MarketplaceValidators.validateProduct("Prod", "Descripción válida.", 0, 5))
    @Test fun negativeStockRejected() = assertEquals("M25_INVALID_STOCK", M25MarketplaceValidators.validateProduct("Prod", "Descripción válida.", 1000, -1))
    @Test fun sanitizerRedactsEmail() = assertFalse(M25PrivacySanitizer.scrubPublicText("info@tienda.com").contains("@"))
    @Test fun catalogOnlyActiveShops() = runBlocking {
        val items = marketplace().observeCatalog().first()
        assertTrue(items.none { it.displayName == "Accesorios Norte" || it.displayName == "Salud Animal" })
    }
    @Test fun publicListingHasNoInternalIds() = runBlocking {
        val listing = marketplace().observeCatalog().first().first()
        assertFalse(listing.toString().contains("m25_shop"))
        assertFalse(listing.toString().contains("mock_user"))
    }
    @Test fun draftShopNotPublic() = runBlocking {
        assertNull(marketplace().observeShopDetail(M25MockShopIds.DRAFT).first())
    }
    @Test fun mockSeedsDeterministic() = runBlocking {
        assertEquals(marketplace().observeCatalog().first().map { it.displayName }, marketplace().observeCatalog().first().map { it.displayName })
    }
    @Test fun unauthorizedManageRejected() = runBlocking {
        assertTrue(marketplace(M25MockUsers.UNAUTHORIZED).publishShop(M25MockShopIds.ACTIVE).isFailure)
    }
    @Test fun cartIdempotencyByClientLineId() = runBlocking {
        val s = store()
        val repo = cart(s = s)
        val first = repo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1, "line-dup")).getOrThrow()
        val second = repo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1, "line-dup")).getOrThrow()
        assertEquals(first.id, second.id)
    }
    @Test fun outOfStockRejected() = runBlocking {
        assertTrue(cart().addItem(AddM25CartItemInput(M25MockProductIds.OUT_OF_STOCK, 1)).isFailure)
    }
    @Test fun orderSubmitIdempotent() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        val orderRepo = orders(s = s)
        val input = SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "req-dup-1")
        orderRepo.submitFromCart(input).getOrThrow()
        assertTrue(orderRepo.submitFromCart(input).isFailure)
    }
    @Test fun terminalOrderCannotTransition() = assertEquals("M25_ORDER_TERMINAL", M25OrderOperationsService.validateOrderTransition(M25OrderStatus.CANCELLED, M25OrderStatus.SHIPPED))
    @Test fun deliveredCanRequestReturn() = assertNull(M25OrderOperationsService.validateOrderTransition(M25OrderStatus.DELIVERED, M25OrderStatus.RETURN_REQUESTED))
}

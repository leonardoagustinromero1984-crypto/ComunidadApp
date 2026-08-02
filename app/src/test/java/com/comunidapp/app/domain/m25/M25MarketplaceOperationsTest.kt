package com.comunidapp.app.domain.m25

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class M25MarketplaceOperationsTest {
    private fun store() = M25MarketplaceMemoryStore().also { it.seedDefaults() }
    private fun marketplace(actor: String = M25MockUsers.MERCHANT, s: M25MarketplaceMemoryStore = store()) =
        MockM25MarketplaceRepository({ actor }, s)
    private fun cart(actor: String = M25MockUsers.CUSTOMER, s: M25MarketplaceMemoryStore = store()) =
        MockM25CartRepository({ actor }, s)
    private fun orders(actor: String = M25MockUsers.CUSTOMER, s: M25MarketplaceMemoryStore = store()) =
        MockM25OrderRepository({ actor }, s)

    @Test fun draftProductNotPublic() = runBlocking {
        val detail = marketplace().observeShopDetail(M25MockShopIds.DRAFT).first()
        assertNull(detail)
    }

    @Test fun activeProductPublic() = runBlocking {
        val detail = marketplace().observeShopDetail(M25MockShopIds.ACTIVE).first()
        assertTrue(detail!!.products.any { it.name.contains("Alimento") })
    }

    @Test fun outOfStockNotReservable() = runBlocking {
        assertTrue(cart().addItem(AddM25CartItemInput(M25MockProductIds.OUT_OF_STOCK, 1)).isFailure)
    }

    @Test fun stockReserveWorks() {
        val snap = M25InventorySnapshot("p1", 10, 0)
        val updated = M25InventoryOperationsService.reserve(snap, 2, "k1", emptySet()).getOrThrow().first
        assertEquals(2, updated.reservedQuantity)
    }

    @Test fun repeatedReserveDoesNotDuplicate() {
        val snap = M25InventorySnapshot("p1", 10, 0)
        val first = M25InventoryOperationsService.reserve(snap, 2, "k1", emptySet()).getOrThrow()
        val second = M25InventoryOperationsService.reserve(first.first, 2, "k1", first.second).getOrThrow()
        assertEquals(first.first.reservedQuantity, second.first.reservedQuantity)
    }

    @Test fun repeatedReleaseDoesNotOverIncrease() {
        val snap = M25InventorySnapshot("p1", 10, 2)
        val once = M25InventoryOperationsService.release(snap, 2).getOrThrow()
        val twice = M25InventoryOperationsService.release(once, 2).getOrThrow()
        assertEquals(0, twice.reservedQuantity)
        assertEquals(10, twice.totalQuantity)
    }

    @Test fun stockNeverNegative() {
        val snap = M25InventorySnapshot("p1", 1, 0)
        assertTrue(M25InventoryOperationsService.reserve(snap, 2, null, emptySet()).isFailure)
    }

    @Test fun validPromotionApplies() {
        val promo = M25Promotion("p", "s", "X", M25PromotionType.PERCENTAGE, 10, M25PromotionStatus.ACTIVE, 0, Long.MAX_VALUE)
        assertEquals(1000L, M25PromotionCalculator.discountFor(promo, 10000))
    }

    @Test fun expiredPromotionDoesNotApply() {
        val promo = M25Promotion("p", "s", "X", M25PromotionType.PERCENTAGE, 10, M25PromotionStatus.ACTIVE, 0, 1)
        val (_, discount) = M25PromotionCalculator.selectBest(listOf(promo), "X", 10000, now = 100)
        assertEquals(0L, discount)
    }

    @Test fun incompatiblePromotionsDoNotAccumulate() {
        val promos = listOf(
            M25Promotion("1", "s", "A", M25PromotionType.PERCENTAGE, 10, M25PromotionStatus.ACTIVE, 0, Long.MAX_VALUE),
            M25Promotion("2", "s", "B", M25PromotionType.FIXED_AMOUNT, 500, M25PromotionStatus.ACTIVE, 0, Long.MAX_VALUE)
        )
        assertTrue(M25PromotionCalculator.incompatibleAccumulation(promos))
    }

    @Test fun foreignCartNotAccessible() = runBlocking {
        assertTrue(cart(M25MockUsers.UNAUTHORIZED).observeCart().first().isEmpty())
    }

    @Test fun androidPriceNotAuthoritativeOnSubmit() = runBlocking {
        val s = store()
        val orderRepo = orders(s = s)
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        val order = orderRepo.submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "price-auth")).getOrThrow()
        assertEquals(8500L, order.lines.first().unitPriceCents)
        assertFalse(SubmitM25OrderInput::class.java.declaredFields.any { it.name.contains("price", ignoreCase = true) })
    }

    @Test fun submitRecalculatesPrice() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.FOOD_BAG, 2)).getOrThrow()
        val order = orders(s = s).submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "recalc")).getOrThrow()
        assertEquals(90000L, order.subtotalCents)
    }

    @Test fun submitReservesStock() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 3)).getOrThrow()
        orders(s = s).submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "reserve")).getOrThrow()
        assertEquals(3, s.inventory.value[M25MockProductIds.COLLAR]!!.reservedQuantity)
    }

    @Test fun submitRetryReturnsSameOrder() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        val input = SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "retry-order")
        val first = orders(s = s).submitFromCart(input).getOrThrow()
        val second = orders(s = s).submitFromCart(input).getOrThrow()
        assertEquals(first.id, second.id)
    }

    @Test fun lastUnitRaceLeavesOneWinner() = runBlocking {
        val s = store()
        s.products.value = s.products.value.map { if (it.id == M25MockProductIds.COLLAR) it.copy(stockQuantity = 1) else it }
        s.syncInventoryFromProducts()
        val cart1 = cart(M25MockUsers.CUSTOMER, s)
        val cart2 = cart("mock_user_m25_customer2", s)
        cart1.clearCart(); cart2.clearCart()
        cart1.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        cart2.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        val o1 = orders(M25MockUsers.CUSTOMER, s)
        val o2 = orders("mock_user_m25_customer2", s)
        val r1 = o1.submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "race1"))
        val r2 = o2.submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "race2"))
        assertTrue(r1.isSuccess xor r2.isSuccess)
    }

    @Test fun snapshotUnchangedWhenProductEdited() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 1)).getOrThrow()
        val order = orders(s = s).submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "snap")).getOrThrow()
        s.products.value = s.products.value.map { if (it.id == M25MockProductIds.COLLAR) it.copy(name = "Nuevo nombre", listPriceCents = 1) else it }
        assertEquals("Collar ajustable", order.lines.first().productName)
        assertEquals(8500L, order.lines.first().unitPriceCents)
    }

    @Test fun acceptWorks() = runBlocking {
        val s = store()
        orders(M25MockUsers.MERCHANT, s).acceptOrder(M25MockOrderIds.SUBMITTED).getOrThrow()
        assertEquals(M25OrderStatus.ACCEPTED, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun customerCannotAccept() = runBlocking {
        assertTrue(orders().acceptOrder(M25MockOrderIds.SUBMITTED).isFailure)
    }

    @Test fun rejectReleasesStock() = runBlocking {
        val s = store()
        val cartRepo = cart(s = s)
        cartRepo.clearCart()
        cartRepo.addItem(AddM25CartItemInput(M25MockProductIds.COLLAR, 2)).getOrThrow()
        val order = orders(s = s).submitFromCart(SubmitM25OrderInput(M25MockShopIds.ACTIVE, M25ShippingMode.PICKUP, "CABA", clientRequestId = "rej")).getOrThrow()
        orders(M25MockUsers.MERCHANT, s).rejectOrder(order.id).getOrThrow()
        assertEquals(0, s.inventory.value[M25MockProductIds.COLLAR]!!.reservedQuantity)
    }

    @Test fun preparingWorks() = runBlocking {
        val s = store()
        orders(M25MockUsers.MERCHANT, s).acceptOrder(M25MockOrderIds.SUBMITTED).getOrThrow()
        orders(M25MockUsers.MERCHANT, s).markPreparing(M25MockOrderIds.SUBMITTED).getOrThrow()
        assertEquals(M25OrderStatus.PREPARING, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun dispatchWorks() = runBlocking {
        val s = store()
        val repo = orders(M25MockUsers.MERCHANT, s)
        repo.acceptOrder(M25MockOrderIds.SUBMITTED).getOrThrow()
        repo.markPreparing(M25MockOrderIds.SUBMITTED).getOrThrow()
        repo.markShipped(M25MockOrderIds.SUBMITTED, "TRACK1", "Correo").getOrThrow()
        assertEquals(M25OrderStatus.SHIPPED, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun deliverWorks() = runBlocking {
        val s = store()
        val repo = orders(M25MockUsers.MERCHANT, s)
        repo.acceptOrder(M25MockOrderIds.SUBMITTED).getOrThrow()
        repo.markPreparing(M25MockOrderIds.SUBMITTED).getOrThrow()
        repo.markShipped(M25MockOrderIds.SUBMITTED).getOrThrow()
        repo.markDelivered(M25MockOrderIds.SUBMITTED).getOrThrow()
        assertEquals(M25OrderStatus.DELIVERED, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun customerCannotMarkDelivered() = runBlocking {
        assertTrue(orders().markDelivered(M25MockOrderIds.SUBMITTED).isFailure)
    }

    @Test fun customerCancelWorks() = runBlocking {
        val s = store()
        orders(s = s).cancelOrder(M25MockOrderIds.SUBMITTED, "Ya no lo necesito").getOrThrow()
        assertEquals(M25OrderStatus.CANCELLED_BY_CUSTOMER, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun merchantCancelWorks() = runBlocking {
        val s = store()
        orders(M25MockUsers.MERCHANT, s).cancelOrderByMerchant(M25MockOrderIds.SUBMITTED, "Sin stock operativo").getOrThrow()
        assertEquals(M25OrderStatus.CANCELLED_BY_MERCHANT, s.orders.value.first { it.id == M25MockOrderIds.SUBMITTED }.status)
    }

    @Test fun cancelIdempotent() = runBlocking {
        val s = store()
        val repo = orders(s = s)
        repo.cancelOrder(M25MockOrderIds.SUBMITTED).getOrThrow()
        assertTrue(repo.cancelOrder(M25MockOrderIds.SUBMITTED).isFailure)
    }

    @Test fun terminalDoesNotReopen() = assertEquals("M25_ORDER_TERMINAL", M25OrderOperationsService.validateOrderTransition(M25OrderStatus.REJECTED, M25OrderStatus.ACCEPTED))

    @Test fun returnRequestWorks() = runBlocking {
        val s = store()
        val ret = orders(s = s).requestReturn(RequestM25ReturnInput(M25MockOrderIds.DELIVERED, "Producto defectuoso reportado", listOf(M25ReturnLine(M25MockProductIds.FOOD_BAG, 1)))).getOrThrow()
        assertEquals(M25ReturnStatus.REQUESTED, ret.status)
    }

    @Test fun excessiveReturnQuantityRejected() = runBlocking {
        assertTrue(orders().requestReturn(RequestM25ReturnInput(M25MockOrderIds.DELIVERED, "Cantidad excesiva solicitada", listOf(M25ReturnLine(M25MockProductIds.FOOD_BAG, 99)))).isFailure)
    }

    @Test fun customerCannotApproveReturn() = runBlocking {
        val s = store()
        val ret = orders(s = s).requestReturn(RequestM25ReturnInput(M25MockOrderIds.DELIVERED, "Producto defectuoso reportado", listOf(M25ReturnLine(M25MockProductIds.FOOD_BAG, 1)))).getOrThrow()
        assertTrue(orders(s = s).approveReturn(ret.id).isFailure)
    }

    @Test fun privateEvidenceNotInPublicListing() = runBlocking {
        val listing = marketplace().observeCatalog().first().first()
        assertFalse(listing.toString().contains("evidence"))
    }

    @Test fun replenishRequiresPhysicalReceipt() = runBlocking {
        val s = store()
        val customer = orders(s = s)
        val merchant = orders(M25MockUsers.MERCHANT, s)
        val ret = customer.requestReturn(RequestM25ReturnInput(M25MockOrderIds.DELIVERED, "Producto defectuoso reportado", listOf(M25ReturnLine(M25MockProductIds.FOOD_BAG, 1)))).getOrThrow()
        merchant.approveReturn(ret.id).getOrThrow()
        val before = s.inventory.value[M25MockProductIds.FOOD_BAG]!!.totalQuantity
        merchant.markReturnReceived(ret.id, replenishStock = false).getOrThrow()
        assertEquals(before, s.inventory.value[M25MockProductIds.FOOD_BAG]!!.totalQuantity)
    }

    @Test fun m06UnavailableDoesNotBlock() = runBlocking {
        assertFalse(marketplace().observeNotificationsHook().first().available)
    }

    @Test fun buyerNotPublicInCatalog() = runBlocking {
        val listing = marketplace().observeCatalog().first().first().toString()
        assertFalse(listing.contains("mock_user"))
    }

    @Test fun addressNotPublicInCatalog() = runBlocking {
        val listing = marketplace().observeCatalog().first().first()
        assertFalse(listing.description.contains("calle", ignoreCase = true))
    }

    @Test fun mockDeterministic() = runBlocking {
        assertEquals(marketplace().observeCatalog().first().size, marketplace().observeCatalog().first().size)
    }

    @Test fun noPaymentStatusesExist() {
        val names = M25OrderStatus.entries.map { it.name }
        assertFalse(names.any { it.contains("PAID", ignoreCase = true) })
        assertFalse(names.any { it.contains("REFUND", ignoreCase = true) })
    }
}

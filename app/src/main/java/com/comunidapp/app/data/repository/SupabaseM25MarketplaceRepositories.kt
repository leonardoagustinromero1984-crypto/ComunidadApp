package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.remote.supabase.m25.M25MarketplaceErrorMapper
import com.comunidapp.app.data.remote.supabase.m25.SupabaseM25RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m25.toM25CartItem
import com.comunidapp.app.data.remote.supabase.m25.toM25MerchantMetrics
import com.comunidapp.app.data.remote.supabase.m25.toM25Order
import com.comunidapp.app.data.remote.supabase.m25.toM25OrderHistoryEntry
import com.comunidapp.app.data.remote.supabase.m25.toM25OrderSummary
import com.comunidapp.app.data.remote.supabase.m25.toM25Promotion
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopDetail
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopListing
import com.comunidapp.app.data.remote.supabase.m25.toM25ReturnRequest
import com.comunidapp.app.data.remote.supabase.m25.toM25Shop
import com.comunidapp.app.data.remote.supabase.m25.toM25ShopProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SupabaseM25MarketplaceRepository(
    private val remote: SupabaseM25RemoteDataSource = SupabaseM25RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M25MarketplaceRepository {

    private fun requireActor() {
        if (actorUserId() == null) throw M25MarketplaceException("NOT_AUTHENTICATED", M25MarketplaceErrors.userMessage("NOT_AUTHENTICATED"))
    }

    override fun observeCatalog(filter: M25CatalogFilter): Flow<List<M25PublicShopListing>> = flow {
        emit(runCatching {
            remote.listCatalog(filter.category?.name, filter.city?.trim()).map { it.toM25PublicShopListing() }
        }.getOrElse { emptyList() })
    }

    override fun observeShopDetail(shopId: String): Flow<M25PublicShopDetail?> = flow {
        emit(runCatching { remote.getShopDetail(shopId).toM25PublicShopDetail() }.getOrNull())
    }

    override fun observeMyShops(): Flow<List<M25Shop>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyShops().map { it.toM25Shop() } }.getOrElse { emptyList() })
    }

    override fun observeShopProducts(shopId: String): Flow<List<M25Product>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listShopProducts(shopId).map { it.toM25ShopProduct() } }.getOrElse { emptyList() })
    }

    override fun observeShopPromotions(shopId: String): Flow<List<M25Promotion>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listShopPromotions(shopId).map { it.toM25Promotion() } }.getOrElse { emptyList() })
    }

    override suspend fun createShop(input: CreateM25ShopInput): Result<M25Shop> = try {
        requireActor()
        M25MarketplaceValidators.validateShop(input.displayName, input.description, input.city)?.let { return M25MarketplaceErrorMapper.fail(it) }
        Result.success(remote.createShop(input.displayName.trim(), input.category.name, input.description.trim(), input.city.trim(), input.organizationId).toM25Shop())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun updateShop(input: UpdateM25ShopInput): Result<M25Shop> = try {
        requireActor()
        Result.success(remote.updateShop(input.shopId, input.displayName, input.description, input.city, input.status?.name).toM25Shop())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun upsertProduct(input: UpsertM25ProductInput): Result<M25Product> = try {
        requireActor()
        M25MarketplaceValidators.validateProduct(input.name, input.description, input.listPriceCents, input.stockQuantity)?.let { return M25MarketplaceErrorMapper.fail(it) }
        Result.success(remote.upsertProduct(input).toM25ShopProduct())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun upsertPromotion(input: UpsertM25PromotionInput): Result<M25Promotion> = try {
        requireActor()
        M25MarketplaceValidators.validatePromotion(input.code, input.type, input.value)?.let { return M25MarketplaceErrorMapper.fail(it) }
        Result.success(remote.upsertPromotion(input).toM25Promotion())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun publishShop(shopId: String): Result<M25Shop> = shopTransition(shopId, "ACTIVE")
    override suspend fun pauseShop(shopId: String): Result<M25Shop> = shopTransition(shopId, "PAUSED")
    override suspend fun closeShop(shopId: String): Result<M25Shop> = shopTransition(shopId, "CLOSED")
    override suspend fun suspendShop(shopId: String): Result<M25Shop> = shopTransition(shopId, "SUSPENDED")

    override suspend fun adjustStock(input: AdjustM25StockInput): Result<M25Product> = try {
        requireActor()
        M25MarketplaceValidators.validateStockAdjustReason(input.reason)?.let { return M25MarketplaceErrorMapper.fail(it) }
        Result.success(remote.adjustStock(input.productId, input.newTotal, input.reason).toM25ShopProduct())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun merchantMetrics(shopId: String): Result<M25MerchantMetrics> = try {
        requireActor()
        Result.success(remote.merchantMetrics(shopId).toM25MerchantMetrics())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override fun observeNotificationsHook(): Flow<M25NotificationHookState> =
        flow { emit(M25NotificationHookState()) }

    private suspend fun shopTransition(shopId: String, status: String): Result<M25Shop> = try {
        requireActor()
        Result.success(remote.transitionShop(shopId, status).toM25Shop())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
}

class SupabaseM25CartRepository(
    private val remote: SupabaseM25RemoteDataSource = SupabaseM25RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M25CartRepository {

    override fun observeCart(): Flow<List<M25CartItem>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listCart().map { it.toM25CartItem() } }.getOrElse { emptyList() })
    }

    override suspend fun addItem(input: AddM25CartItemInput): Result<M25CartItem> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            M25MarketplaceValidators.validateCartQuantity(input.quantity)?.let { return M25MarketplaceErrorMapper.fail(it) }
            Result.success(remote.addToCart(input.productId, input.quantity, input.clientLineId).toM25CartItem())
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<M25CartItem> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            Result.success(remote.updateCartItem(cartItemId, quantity).toM25CartItem())
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }

    override suspend fun removeItem(cartItemId: String): Result<Unit> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            remote.removeCartItem(cartItemId)
            Result.success(Unit)
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }

    override suspend fun clearCart(): Result<Unit> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            remote.clearCart()
            Result.success(Unit)
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }
}

class SupabaseM25OrderRepository(
    private val remote: SupabaseM25RemoteDataSource = SupabaseM25RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M25OrderRepository {

    override fun observeMyOrders(): Flow<List<M25OrderSummary>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyOrders().map { it.toM25OrderSummary() } }.getOrElse { emptyList() })
    }

    override fun observeShopOrders(shopId: String): Flow<List<M25Order>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listShopOrders(shopId).map { it.toM25Order() } }.getOrElse { emptyList() })
    }

    override fun observeOrder(orderId: String): Flow<M25Order?> = flow {
        if (actorUserId() == null) emit(null)
        else emit(runCatching { remote.getOrder(orderId)?.toM25Order() }.getOrNull())
    }

    override fun observeOrderHistory(orderId: String): Flow<List<M25OrderHistoryEntry>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listOrderHistory(orderId).map { it.toM25OrderHistoryEntry() } }.getOrElse { emptyList() })
    }

    override suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order> = orderOp { remote.submitOrder(input) }
    override suspend fun acceptOrder(orderId: String): Result<M25Order> = orderOp { remote.transitionOrder(orderId, "ACCEPTED") }
    override suspend fun rejectOrder(orderId: String, publicReason: String?): Result<M25Order> = orderOp { remote.rejectOrder(orderId, publicReason) }
    override suspend fun cancelOrder(orderId: String, reason: String?): Result<M25Order> = orderOp { remote.cancelOrderByCustomer(orderId, reason) }
    override suspend fun cancelOrderByMerchant(orderId: String, publicReason: String?): Result<M25Order> = orderOp { remote.cancelOrderByMerchant(orderId, publicReason) }
    override suspend fun markPreparing(orderId: String): Result<M25Order> = orderOp { remote.transitionOrder(orderId, "PREPARING") }
    override suspend fun markReadyForDispatch(orderId: String): Result<M25Order> = orderOp { remote.transitionOrder(orderId, "READY_FOR_DISPATCH") }
    override suspend fun markShipped(orderId: String, trackingCode: String?, carrierText: String?): Result<M25Order> =
        orderOp { remote.shipOrder(orderId, trackingCode, carrierText) }
    override suspend fun markDelivered(orderId: String): Result<M25Order> = orderOp { remote.transitionOrder(orderId, "DELIVERED") }
    override suspend fun requestReturn(input: RequestM25ReturnInput): Result<M25ReturnRequest> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            M25MarketplaceValidators.validateReturnReason(input.reason)?.let { return M25MarketplaceErrorMapper.fail(it) }
            Result.success(remote.requestReturn(input).toM25ReturnRequest())
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }
    override suspend fun approveReturn(returnId: String): Result<M25ReturnRequest> = returnOp { remote.approveReturn(returnId) }
    override suspend fun rejectReturn(returnId: String, publicReason: String?): Result<M25ReturnRequest> = returnOp { remote.rejectReturn(returnId, publicReason) }
    override suspend fun markReturnReceived(returnId: String, replenishStock: Boolean): Result<M25ReturnRequest> =
        returnOp { remote.receiveReturn(returnId, replenishStock) }

    private suspend fun orderOp(block: suspend () -> kotlinx.serialization.json.JsonObject): Result<M25Order> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            Result.success(block().toM25Order())
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }

    private suspend fun returnOp(block: suspend () -> kotlinx.serialization.json.JsonObject): Result<M25ReturnRequest> {
        return try {
            if (actorUserId() == null) return M25MarketplaceErrorMapper.fail("NOT_AUTHENTICATED")
            Result.success(block().toM25ReturnRequest())
        } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }
    }
}

package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.data.remote.supabase.m25.M25MarketplaceErrorMapper
import com.comunidapp.app.data.remote.supabase.m25.SupabaseM25RemoteDataSource
import com.comunidapp.app.data.remote.supabase.m25.toM25CartItem
import com.comunidapp.app.data.remote.supabase.m25.toM25OrderSummary
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopDetail
import com.comunidapp.app.data.remote.supabase.m25.toM25PublicShopListing
import com.comunidapp.app.data.remote.supabase.m25.toM25Shop
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

    override suspend fun createShop(input: CreateM25ShopInput): Result<M25Shop> = try {
        requireActor()
        M25MarketplaceValidators.validateShop(input.displayName, input.description, input.city)?.let { return M25MarketplaceErrorMapper.fail(it) }
        Result.success(remote.createShop(input.displayName.trim(), input.category.name, input.description.trim(), input.city.trim(), input.organizationId).toM25Shop())
    } catch (e: Throwable) { M25MarketplaceErrorMapper.failure(e) }

    override suspend fun updateShop(input: UpdateM25ShopInput): Result<M25Shop> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun upsertProduct(input: UpsertM25ProductInput): Result<M25Product> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun publishShop(shopId: String): Result<M25Shop> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun suspendShop(shopId: String): Result<M25Shop> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override fun observeNotificationsHook(): Flow<M25NotificationHookState> =
        flow { emit(M25NotificationHookState()) }
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
        } catch (e: Throwable) {
            M25MarketplaceErrorMapper.failure(e)
        }
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<M25CartItem> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun removeItem(cartItemId: String): Result<Unit> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun clearCart(): Result<Unit> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")
}

class SupabaseM25OrderRepository(
    private val remote: SupabaseM25RemoteDataSource = SupabaseM25RemoteDataSource(),
    private val actorUserId: () -> String? = { null }
) : M25OrderRepository {

    override fun observeMyOrders(): Flow<List<M25OrderSummary>> = flow {
        if (actorUserId() == null) emit(emptyList())
        else emit(runCatching { remote.listMyOrders().map { it.toM25OrderSummary() } }.getOrElse { emptyList() })
    }

    override fun observeShopOrders(shopId: String): Flow<List<M25Order>> = flow { emit(emptyList()) }

    override fun observeOrder(orderId: String): Flow<M25Order?> = flow { emit(null) }

    override suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun acceptOrder(orderId: String): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun cancelOrder(orderId: String, reason: String?): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun markPreparing(orderId: String): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun markShipped(orderId: String): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun markDelivered(orderId: String): Result<M25Order> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")

    override suspend fun requestReturn(orderId: String, reason: String): Result<M25ReturnRequest> =
        M25MarketplaceErrorMapper.fail("M25_NOT_IMPLEMENTED_REMOTE")
}

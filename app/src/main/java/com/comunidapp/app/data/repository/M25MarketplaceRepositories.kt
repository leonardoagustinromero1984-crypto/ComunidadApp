package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.domain.m25.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M25MarketplaceRepository {
    fun observeCatalog(filter: M25CatalogFilter = M25CatalogFilter()): Flow<List<M25PublicShopListing>>
    fun observeShopDetail(shopId: String): Flow<M25PublicShopDetail?>
    fun observeMyShops(): Flow<List<M25Shop>>
    fun observeShopProducts(shopId: String): Flow<List<M25Product>>
    fun observeShopPromotions(shopId: String): Flow<List<M25Promotion>>
    suspend fun createShop(input: CreateM25ShopInput): Result<M25Shop>
    suspend fun updateShop(input: UpdateM25ShopInput): Result<M25Shop>
    suspend fun upsertProduct(input: UpsertM25ProductInput): Result<M25Product>
    suspend fun upsertPromotion(input: UpsertM25PromotionInput): Result<M25Promotion>
    suspend fun publishShop(shopId: String): Result<M25Shop>
    suspend fun pauseShop(shopId: String): Result<M25Shop>
    suspend fun closeShop(shopId: String): Result<M25Shop>
    suspend fun suspendShop(shopId: String): Result<M25Shop>
    suspend fun adjustStock(input: AdjustM25StockInput): Result<M25Product>
    suspend fun merchantMetrics(shopId: String): Result<M25MerchantMetrics>
    fun observeNotificationsHook(): Flow<M25NotificationHookState>
}

interface M25CartRepository {
    fun observeCart(): Flow<List<M25CartItem>>
    suspend fun addItem(input: AddM25CartItemInput): Result<M25CartItem>
    suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<M25CartItem>
    suspend fun removeItem(cartItemId: String): Result<Unit>
    suspend fun clearCart(): Result<Unit>
}

interface M25OrderRepository {
    fun observeMyOrders(): Flow<List<M25OrderSummary>>
    fun observeShopOrders(shopId: String): Flow<List<M25Order>>
    fun observeOrder(orderId: String): Flow<M25Order?>
    fun observeOrderHistory(orderId: String): Flow<List<M25OrderHistoryEntry>>
    suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order>
    suspend fun acceptOrder(orderId: String): Result<M25Order>
    suspend fun rejectOrder(orderId: String, publicReason: String? = null): Result<M25Order>
    suspend fun cancelOrder(orderId: String, reason: String? = null): Result<M25Order>
    suspend fun cancelOrderByMerchant(orderId: String, publicReason: String? = null): Result<M25Order>
    suspend fun markPreparing(orderId: String): Result<M25Order>
    suspend fun markReadyForDispatch(orderId: String): Result<M25Order>
    suspend fun markShipped(orderId: String, trackingCode: String? = null, carrierText: String? = null): Result<M25Order>
    suspend fun markDelivered(orderId: String): Result<M25Order>
    suspend fun requestReturn(input: RequestM25ReturnInput): Result<M25ReturnRequest>
    suspend fun approveReturn(returnId: String): Result<M25ReturnRequest>
    suspend fun rejectReturn(returnId: String, publicReason: String? = null): Result<M25ReturnRequest>
    suspend fun markReturnReceived(returnId: String, replenishStock: Boolean): Result<M25ReturnRequest>
}

class M25MarketplaceMemoryStore {
    private val mutex = Mutex()
    private var sequence = 0
    val shops = MutableStateFlow<List<M25Shop>>(emptyList())
    val products = MutableStateFlow<List<M25Product>>(emptyList())
    val promotions = MutableStateFlow<List<M25Promotion>>(emptyList())
    val cartItems = MutableStateFlow<List<M25CartItem>>(emptyList())
    val orders = MutableStateFlow<List<M25Order>>(emptyList())
    val returns = MutableStateFlow<List<M25ReturnRequest>>(emptyList())
    val orderHistory = MutableStateFlow<List<M25OrderHistoryEntry>>(emptyList())
    val stockMovements = MutableStateFlow<List<M25StockMovement>>(emptyList())
    val inventory = MutableStateFlow<Map<String, M25InventorySnapshot>>(emptyMap())
    val clientRequests = MutableStateFlow<Set<String>>(emptySet())
    val reservationKeys = MutableStateFlow<Set<String>>(emptySet())

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
    fun nextId(prefix: String): String = "${prefix}_${++sequence}"

    fun seedDefaults() {
        if (shops.value.isNotEmpty()) return
        val stamp = 1_700_100_000_000L
        shops.value = listOf(
            shop(M25MockShopIds.ACTIVE, M25MockUsers.MERCHANT, "Huellitas Market", M25ShopCategory.PET_FOOD, "Alimentos y accesorios para mascotas.", "CABA", M25ShopStatus.ACTIVE, stamp),
            shop(M25MockShopIds.DRAFT, M25MockUsers.MERCHANT, "Accesorios Norte", M25ShopCategory.ACCESSORIES, "Collares, correas y juguetes.", "Vicente López", M25ShopStatus.DRAFT, stamp),
            shop(M25MockShopIds.SUSPENDED, M25MockUsers.OTHER_MERCHANT, "Salud Animal", M25ShopCategory.HEALTH, "Suplementos y cuidado.", "La Plata", M25ShopStatus.SUSPENDED, stamp),
            shop(M25MockShopIds.EMPTY_PRODUCTS, M25MockUsers.MERCHANT, "Tienda Vacía", M25ShopCategory.OTHER, "Tienda sin productos activos.", "CABA", M25ShopStatus.ACTIVE, stamp)
        )
        products.value = listOf(
            product(M25MockProductIds.FOOD_BAG, M25MockShopIds.ACTIVE, "FOOD-01", "Alimento premium 15kg", "Alimento balanceado para perros adultos.", 45000, 12),
            product(M25MockProductIds.COLLAR, M25MockShopIds.ACTIVE, "ACC-01", "Collar ajustable", "Collar reflectivo de nylon.", 8500, 30),
            product(M25MockProductIds.OUT_OF_STOCK, M25MockShopIds.ACTIVE, "ACC-02", "Juguete mordedor", "Juguete resistente para perros.", 5200, 0, M25ProductStatus.OUT_OF_STOCK),
            product("m25_product_draft", M25MockShopIds.DRAFT, "DRAFT-01", "Correa extensible", "Correa retráctil de 5 metros.", 12000, 5, M25ProductStatus.DRAFT)
        )
        syncInventoryFromProducts()
        promotions.value = listOf(
            M25Promotion("m25_promo_active", M25MockShopIds.ACTIVE, "HUELLA10", M25PromotionType.PERCENTAGE, 10, M25PromotionStatus.ACTIVE, stamp, stamp + 86_400_000_000)
        )
        cartItems.value = listOf(
            M25CartItem("m25_cart_1", M25MockUsers.CUSTOMER, M25MockProductIds.FOOD_BAG, M25MockShopIds.ACTIVE, 1, "m25_line_seed", stamp)
        )
        orders.value = listOf(
            order(M25MockOrderIds.SUBMITTED, M25MockShopIds.ACTIVE, M25MockUsers.CUSTOMER, M25OrderStatus.SUBMITTED, stamp),
            order(M25MockOrderIds.DELIVERED, M25MockShopIds.ACTIVE, M25MockUsers.CUSTOMER, M25OrderStatus.DELIVERED, stamp)
        )
    }

    fun syncInventoryFromProducts() {
        inventory.value = products.value.associate { p ->
            p.id to M25InventorySnapshot(p.id, p.stockQuantity, inventory.value[p.id]?.reservedQuantity ?: 0)
        }
    }

    private fun shop(id: String, owner: String, name: String, cat: M25ShopCategory, desc: String, city: String, status: M25ShopStatus, stamp: Long) =
        M25Shop(id, owner, if (id == M25MockShopIds.ACTIVE) "mock_org_m03" else null, name, cat, desc, city, status, stamp, stamp)

    private fun product(id: String, shopId: String, sku: String, name: String, desc: String, price: Long, stock: Int, status: M25ProductStatus = M25ProductStatus.ACTIVE) =
        M25Product(id, shopId, sku, name, desc, price, stockQuantity = stock, status = status)

    private fun order(id: String, shopId: String, customer: String, status: M25OrderStatus, stamp: Long) = M25Order(
        id, shopId, customer, status,
        listOf(M25OrderLine(M25MockProductIds.FOOD_BAG, "Alimento premium 15kg", 1, 45000, subtotalCents = 45000)),
        45000, 0, "ARS", M25ShippingMode.DELIVERY, "CABA", null, null, "m25_req_$id", null, stamp, stamp
    )
}

internal object M25MockInventoryHelper {
    fun snapshot(store: M25MarketplaceMemoryStore, productId: String): M25InventorySnapshot =
        store.inventory.value[productId] ?: M25InventorySnapshot(productId, 0, 0)

    fun putSnapshot(store: M25MarketplaceMemoryStore, snapshot: M25InventorySnapshot) {
        store.inventory.value = store.inventory.value + (snapshot.productId to snapshot)
        store.products.value = store.products.value.map { p ->
            if (p.id == snapshot.productId) p.copy(stockQuantity = snapshot.totalQuantity) else p
        }
    }

    fun recordMovement(store: M25MarketplaceMemoryStore, productId: String, type: M25StockMovementType, qty: Int, reason: String?) {
        store.stockMovements.value += M25StockMovement(
            store.nextId("m25_mov"), productId, type, qty, reason, System.currentTimeMillis()
        )
    }

    fun reserveForLines(store: M25MarketplaceMemoryStore, lines: List<M25OrderLine>, keyPrefix: String?) {
        lines.forEach { line ->
            val snap = snapshot(store, line.productId)
            val (updated, keys) = M25InventoryOperationsService.reserve(
                snap, line.quantity, keyPrefix?.let { "${it}_${line.productId}" }, store.reservationKeys.value
            ).getOrThrow()
            putSnapshot(store, updated)
            store.reservationKeys.value = keys
            recordMovement(store, line.productId, M25StockMovementType.RESERVE, line.quantity, keyPrefix)
        }
    }

    fun releaseForLines(store: M25MarketplaceMemoryStore, lines: List<M25OrderLine>) {
        lines.forEach { line ->
            val snap = snapshot(store, line.productId)
            val updated = M25InventoryOperationsService.release(snap, line.quantity).getOrThrow()
            putSnapshot(store, updated)
            recordMovement(store, line.productId, M25StockMovementType.RELEASE, line.quantity, null)
        }
    }

    fun commitForLines(store: M25MarketplaceMemoryStore, lines: List<M25OrderLine>) {
        lines.forEach { line ->
            val snap = snapshot(store, line.productId)
            val updated = M25InventoryOperationsService.commit(snap, line.quantity).getOrThrow()
            putSnapshot(store, updated)
            recordMovement(store, line.productId, M25StockMovementType.COMMIT, line.quantity, null)
        }
    }
}

internal object M25MockOrderHelper {
    fun appendHistory(store: M25MarketplaceMemoryStore, orderId: String, from: M25OrderStatus?, to: M25OrderStatus, role: String, reason: String? = null) {
        store.orderHistory.value += M25OrderHistoryEntry(
            store.nextId("m25_hist"), orderId, from, to, reason, role, System.currentTimeMillis()
        )
    }
}

abstract class M25MockRepositoryBase(
    protected val actorUserId: () -> String?,
    protected val store: M25MarketplaceMemoryStore
) {
    init { store.seedDefaults() }

    protected fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")
    protected suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try { Result.success(block()) } catch (e: Throwable) { M25MarketplaceErrors.failure(e) }
    }
    protected fun fail(code: String): Nothing = throw M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code))
    protected fun ownsShop(shopId: String, actor: String): Boolean =
        store.shops.value.any { it.id == shopId && it.ownerUserId == actor }
    protected fun shopActiveForOrders(shopId: String) {
        val shop = store.shops.value.firstOrNull { it.id == shopId } ?: fail("M25_SHOP_NOT_FOUND")
        if (shop.status != M25ShopStatus.ACTIVE) fail("M25_SHOP_NOT_PUBLIC")
    }
    protected fun productAvailable(product: M25Product, qty: Int) {
        if (product.status != M25ProductStatus.ACTIVE) fail("M25_PRODUCT_NOT_FOUND")
        val avail = M25InventoryOperationsService.available(M25MockInventoryHelper.snapshot(store, product.id))
        if (avail < qty) fail("M25_OUT_OF_STOCK")
    }
}

class MockM25MarketplaceRepository(
    actorUserId: () -> String?,
    store: M25MarketplaceMemoryStore = M25MarketplaceMemoryStore()
) : M25MockRepositoryBase(actorUserId, store), M25MarketplaceRepository {

    override fun observeCatalog(filter: M25CatalogFilter): Flow<List<M25PublicShopListing>> =
        store.shops.map { shops ->
            shops.filter {
                it.status == M25ShopStatus.ACTIVE &&
                    (filter.category == null || it.category == filter.category) &&
                    (filter.city.isNullOrBlank() || it.city.equals(filter.city.trim(), ignoreCase = true))
            }.map { shop ->
                val prods = store.products.value.filter { it.shopId == shop.id && it.status == M25ProductStatus.ACTIVE }
                shop.toPublicListing(prods.size, prods.minByOrNull { it.listPriceCents }?.let { "${it.currency} ${it.listPriceCents}" })
            }
        }

    override fun observeShopDetail(shopId: String): Flow<M25PublicShopDetail?> =
        store.shops.map { shops ->
            shops.firstOrNull { (it.id == shopId || it.displayName == shopId) && it.status == M25ShopStatus.ACTIVE }
                ?.let { shop -> shop.toPublicDetail(store.products.value.filter { it.shopId == shop.id }.mapNotNull(M25PrivacySanitizer::toPublicProduct)) }
        }

    override fun observeMyShops(): Flow<List<M25Shop>> = store.shops.map { shops ->
        val actor = actorUserId() ?: return@map emptyList()
        shops.filter { it.ownerUserId == actor }
    }

    override fun observeShopProducts(shopId: String): Flow<List<M25Product>> = store.products.map { prods ->
        val actor = actorUserId() ?: return@map emptyList()
        if (!ownsShop(shopId, actor)) emptyList() else prods.filter { it.shopId == shopId }
    }

    override fun observeShopPromotions(shopId: String): Flow<List<M25Promotion>> = store.promotions.map { promos ->
        val actor = actorUserId() ?: return@map emptyList()
        if (!ownsShop(shopId, actor)) emptyList() else promos.filter { it.shopId == shopId }
    }

    override suspend fun createShop(input: CreateM25ShopInput): Result<M25Shop> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateShop(input.displayName, input.description, input.city)?.let(::fail)
        val now = System.currentTimeMillis()
        M25Shop(store.nextId("m25_shop"), actor, input.organizationId, input.displayName.trim(), input.category, input.description.trim(), input.city.trim(), M25ShopStatus.DRAFT, now, now)
            .also { store.shops.value += it }
    }

    override suspend fun updateShop(input: UpdateM25ShopInput): Result<M25Shop> = mutate {
        val shop = owned(input.shopId)
        val updated = shop.copy(
            displayName = input.displayName?.trim() ?: shop.displayName,
            description = input.description?.trim() ?: shop.description,
            city = input.city?.trim() ?: shop.city,
            status = input.status ?: shop.status,
            updatedAt = System.currentTimeMillis()
        )
        M25MarketplaceValidators.validateShop(updated.displayName, updated.description, updated.city)?.let(::fail)
        input.status?.let { status ->
            M25MarketplaceValidators.validateShopStatusTransition(shop.status, status, hasActiveProduct(shop.id))?.let(::fail)
        }
        store.shops.value = store.shops.value.map { if (it.id == updated.id) updated else it }
        updated
    }

    override suspend fun upsertProduct(input: UpsertM25ProductInput): Result<M25Product> = mutate {
        owned(input.shopId)
        M25MarketplaceValidators.validateProduct(input.name, input.description, input.listPriceCents, input.stockQuantity)?.let(::fail)
        input.productId?.let { id ->
            store.products.value.firstOrNull { it.id == id && it.shopId == input.shopId } ?: fail("M25_PRODUCT_NOT_FOUND")
        }
        val product = input.productId?.let { id ->
            store.products.value.first { it.id == id }.copy(
                sku = input.sku.trim(), name = input.name.trim(), description = input.description.trim(),
                listPriceCents = input.listPriceCents, currency = input.currency, stockQuantity = input.stockQuantity, status = input.status
            )
        } ?: M25Product(store.nextId("m25_product"), input.shopId, input.sku.trim(), input.name.trim(), input.description.trim(), input.listPriceCents, input.currency, input.stockQuantity, input.status)
        store.products.value = store.products.value.filterNot { it.id == product.id } + product
        store.syncInventoryFromProducts()
        product
    }

    override suspend fun upsertPromotion(input: UpsertM25PromotionInput): Result<M25Promotion> = mutate {
        owned(input.shopId)
        M25MarketplaceValidators.validatePromotion(input.code, input.type, input.value)?.let(::fail)
        if (input.endsAt <= input.startsAt) fail("M25_INVALID_PROMOTION")
        val promo = input.promotionId?.let { id ->
            store.promotions.value.firstOrNull { it.id == id && it.shopId == input.shopId }?.copy(
                code = input.code.uppercase(), type = input.type, value = input.value,
                status = input.status, startsAt = input.startsAt, endsAt = input.endsAt
            ) ?: fail("M25_PROMOTION_INVALID")
        } ?: M25Promotion(store.nextId("m25_promo"), input.shopId, input.code.uppercase(), input.type, input.value, input.status, input.startsAt, input.endsAt)
        store.promotions.value = store.promotions.value.filterNot { it.id == promo.id } + promo
        promo
    }

    override suspend fun publishShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.ACTIVE)
    override suspend fun pauseShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.PAUSED)
    override suspend fun closeShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.CLOSED)
    override suspend fun suspendShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.SUSPENDED)

    override suspend fun adjustStock(input: AdjustM25StockInput): Result<M25Product> = mutate {
        val product = store.products.value.firstOrNull { it.id == input.productId } ?: fail("M25_PRODUCT_NOT_FOUND")
        owned(product.shopId)
        M25MarketplaceValidators.validateStockAdjustReason(input.reason)?.let(::fail)
        val snap = M25MockInventoryHelper.snapshot(store, product.id)
        val updated = M25InventoryOperationsService.adjust(snap, input.newTotal).getOrThrow()
        M25MockInventoryHelper.putSnapshot(store, updated)
        M25MockInventoryHelper.recordMovement(store, product.id, M25StockMovementType.ADJUST, input.newTotal - snap.totalQuantity, input.reason)
        store.products.value.first { it.id == product.id }
    }

    override suspend fun merchantMetrics(shopId: String): Result<M25MerchantMetrics> = mutate {
        owned(shopId)
        val shopOrders = store.orders.value.filter { it.shopId == shopId && it.status != M25OrderStatus.DRAFT }
        M25MerchantMetrics(
            created = shopOrders.count { it.status == M25OrderStatus.SUBMITTED },
            accepted = shopOrders.count { it.status == M25OrderStatus.ACCEPTED },
            preparing = shopOrders.count { it.status == M25OrderStatus.PREPARING },
            dispatched = shopOrders.count { it.status == M25OrderStatus.SHIPPED },
            delivered = shopOrders.count { it.status == M25OrderStatus.DELIVERED },
            cancelled = shopOrders.count { it.status.isCancellation() },
            returns = store.returns.value.count { r -> shopOrders.any { it.id == r.orderId } },
            unitsSold = shopOrders.filter { it.status == M25OrderStatus.DELIVERED }.sumOf { o -> o.lines.sumOf { it.quantity } },
            lowStockProducts = store.products.value.count { it.shopId == shopId && M25InventoryOperationsService.available(M25MockInventoryHelper.snapshot(store, it.id)) <= 2 }
        )
    }

    override fun observeNotificationsHook(): Flow<M25NotificationHookState> =
        kotlinx.coroutines.flow.flowOf(M25NotificationHookState())

    private suspend fun transitionShop(shopId: String, target: M25ShopStatus): Result<M25Shop> = mutate {
        val shop = owned(shopId)
        M25MarketplaceValidators.validateShopStatusTransition(shop.status, target, hasActiveProduct(shopId))?.let(::fail)
        if (shop.status == target) return@mutate shop
        shop.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.shops.value = store.shops.value.map { if (it.id == shopId) updated else it }
        }
    }

    private fun hasActiveProduct(shopId: String) = store.products.value.any { it.shopId == shopId && it.status == M25ProductStatus.ACTIVE && it.stockQuantity > 0 }
    private fun owned(shopId: String): M25Shop {
        val shop = store.shops.value.firstOrNull { it.id == shopId } ?: fail("M25_SHOP_NOT_FOUND")
        if (shop.ownerUserId != requireActor()) fail("M25_PERMISSION_DENIED")
        return shop
    }

    private fun M25OrderStatus.isCancellation() = this in setOf(M25OrderStatus.CANCELLED, M25OrderStatus.CANCELLED_BY_CUSTOMER, M25OrderStatus.CANCELLED_BY_MERCHANT)
}

class MockM25CartRepository(
    actorUserId: () -> String?,
    store: M25MarketplaceMemoryStore
) : M25MockRepositoryBase(actorUserId, store), M25CartRepository {

    override fun observeCart(): Flow<List<M25CartItem>> = store.cartItems.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.userId == actor }
    }

    override suspend fun addItem(input: AddM25CartItemInput): Result<M25CartItem> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateCartQuantity(input.quantity)?.let(::fail)
        val product = store.products.value.firstOrNull { it.id == input.productId } ?: fail("M25_PRODUCT_NOT_FOUND")
        shopActiveForOrders(product.shopId)
        productAvailable(product, input.quantity)
        input.clientLineId?.let { lineId ->
            store.cartItems.value.firstOrNull { it.userId == actor && it.clientLineId == lineId }?.let { return@mutate it }
        }
        val existing = store.cartItems.value.firstOrNull { it.userId == actor && it.productId == product.id }
        if (existing != null) {
            val updated = existing.copy(quantity = existing.quantity + input.quantity, updatedAt = System.currentTimeMillis())
            store.cartItems.value = store.cartItems.value.map { if (it.id == existing.id) updated else it }
            return@mutate updated
        }
        val now = System.currentTimeMillis()
        M25CartItem(store.nextId("m25_cart"), actor, product.id, product.shopId, input.quantity, input.clientLineId, now)
            .also { store.cartItems.value += it }
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<M25CartItem> = mutate {
        val actor = requireActor()
        if (quantity == 0) {
            store.cartItems.value = store.cartItems.value.filterNot { it.id == cartItemId && it.userId == actor }
            fail("M25_CART_EMPTY")
        }
        M25MarketplaceValidators.validateCartQuantity(quantity)?.let(::fail)
        val item = store.cartItems.value.firstOrNull { it.id == cartItemId && it.userId == actor } ?: fail("M25_PRODUCT_NOT_FOUND")
        val product = store.products.value.firstOrNull { it.id == item.productId } ?: fail("M25_PRODUCT_NOT_FOUND")
        productAvailable(product, quantity)
        item.copy(quantity = quantity, updatedAt = System.currentTimeMillis()).also { updated ->
            store.cartItems.value = store.cartItems.value.map { if (it.id == cartItemId) updated else it }
        }
    }

    override suspend fun removeItem(cartItemId: String): Result<Unit> = mutate {
        store.cartItems.value = store.cartItems.value.filterNot { it.id == cartItemId && it.userId == requireActor() }
        Unit
    }

    override suspend fun clearCart(): Result<Unit> = mutate {
        store.cartItems.value = store.cartItems.value.filterNot { it.userId == requireActor() }
        Unit
    }
}

class MockM25OrderRepository(
    actorUserId: () -> String?,
    store: M25MarketplaceMemoryStore
) : M25MockRepositoryBase(actorUserId, store), M25OrderRepository {

    override fun observeMyOrders(): Flow<List<M25OrderSummary>> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map emptyList()
        orders.filter { it.customerUserId == actor }.map { order ->
            val shopName = store.shops.value.firstOrNull { it.id == order.shopId }?.displayName ?: "Tienda"
            M25OrderSummary(order.id, shopName, order.status, order.lines.size, order.subtotalCents, order.currency, order.createdAt)
        }
    }

    override fun observeShopOrders(shopId: String): Flow<List<M25Order>> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map emptyList()
        if (!ownsShop(shopId, actor)) return@map emptyList()
        orders.filter { it.shopId == shopId && it.status != M25OrderStatus.DRAFT }
    }

    override fun observeOrder(orderId: String): Flow<M25Order?> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map null
        orders.firstOrNull { it.id == orderId && (it.customerUserId == actor || ownsShop(it.shopId, actor)) }
    }

    override fun observeOrderHistory(orderId: String): Flow<List<M25OrderHistoryEntry>> = store.orderHistory.map { hist ->
        val actor = actorUserId() ?: return@map emptyList()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: return@map emptyList()
        if (order.customerUserId != actor && !ownsShop(order.shopId, actor)) emptyList()
        else hist.filter { it.orderId == orderId }
    }

    override suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateShippingCity(input.shippingCity)?.let(::fail)
        shopActiveForOrders(input.shopId)
        input.clientRequestId?.let { reqId ->
            store.orders.value.firstOrNull { it.customerUserId == actor && it.clientRequestId == reqId }?.let { return@mutate it }
        }
        val cart = store.cartItems.value.filter { it.userId == actor && it.shopId == input.shopId }
        if (cart.isEmpty()) fail("M25_CART_EMPTY")
        val lines = cart.map { item ->
            val product = store.products.value.firstOrNull { it.id == item.productId } ?: fail("M25_PRODUCT_NOT_FOUND")
            productAvailable(product, item.quantity)
            val unit = product.listPriceCents
            M25OrderLine(product.id, product.name, item.quantity, unit, product.currency, subtotalCents = unit * item.quantity)
        }
        val subtotal = lines.sumOf { it.subtotalCents }
        val (_, discount) = M25PromotionCalculator.selectBest(store.promotions.value.filter { it.shopId == input.shopId }, input.promotionCode, subtotal)
        M25MockInventoryHelper.reserveForLines(store, lines, input.clientRequestId)
        val now = System.currentTimeMillis()
        val order = M25Order(
            store.nextId("m25_order"), input.shopId, actor, M25OrderStatus.SUBMITTED, lines,
            subtotal, discount, "ARS", input.shippingMode, input.shippingCity.trim(), input.shippingNotes?.trim(),
            input.promotionCode?.trim()?.uppercase(), input.clientRequestId,
            M25ShippingTracking(M25OrderStatus.SUBMITTED), now, now
        )
        store.orders.value += order
        store.cartItems.value = store.cartItems.value.filterNot { it.userId == actor && it.shopId == input.shopId }
        input.clientRequestId?.let { store.clientRequests.value = store.clientRequests.value + it }
        M25MockOrderHelper.appendHistory(store, order.id, null, M25OrderStatus.SUBMITTED, "CUSTOMER")
        order
    }

    override suspend fun acceptOrder(orderId: String): Result<M25Order> =
        transitionOrder(orderId, M25OrderStatus.ACCEPTED, merchant = true, role = "MERCHANT")

    override suspend fun rejectOrder(orderId: String, publicReason: String?): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (!ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (order.status == M25OrderStatus.REJECTED) return@mutate order
        M25OrderOperationsService.validateOrderTransition(order.status, M25OrderStatus.REJECTED)?.let(::fail)
        M25MockInventoryHelper.releaseForLines(store, order.lines)
        val now = System.currentTimeMillis()
        order.copy(status = M25OrderStatus.REJECTED, updatedAt = now).also { updated ->
            store.orders.value = store.orders.value.map { if (it.id == orderId) updated else it }
            M25MockOrderHelper.appendHistory(store, orderId, order.status, M25OrderStatus.REJECTED, "MERCHANT", publicReason)
        }
    }

    override suspend fun cancelOrder(orderId: String, reason: String?): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId && it.customerUserId == actor } ?: fail("M25_ORDER_NOT_FOUND")
        if (!M25OrderOperationsService.canCustomerCancel(order.status)) fail("M25_INVALID_ORDER_TRANSITION")
        cancelWithRelease(order, M25OrderStatus.CANCELLED_BY_CUSTOMER, "CUSTOMER", reason)
    }

    override suspend fun cancelOrderByMerchant(orderId: String, publicReason: String?): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (!ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (!M25OrderOperationsService.canMerchantCancel(order.status)) fail("M25_INVALID_ORDER_TRANSITION")
        cancelWithRelease(order, M25OrderStatus.CANCELLED_BY_MERCHANT, "MERCHANT", publicReason)
    }

    override suspend fun markPreparing(orderId: String): Result<M25Order> =
        transitionOrder(orderId, M25OrderStatus.PREPARING, merchant = true, role = "MERCHANT")

    override suspend fun markReadyForDispatch(orderId: String): Result<M25Order> =
        transitionOrder(orderId, M25OrderStatus.READY_FOR_DISPATCH, merchant = true, role = "MERCHANT")

    override suspend fun markShipped(orderId: String, trackingCode: String?, carrierText: String?): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (!ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (order.status == M25OrderStatus.SHIPPED) return@mutate order
        M25OrderOperationsService.validateOrderTransition(order.status, M25OrderStatus.SHIPPED)?.let(::fail)
        val now = System.currentTimeMillis()
        val tracking = M25ShippingTracking(M25OrderStatus.SHIPPED, trackingCode?.let(M25PrivacySanitizer::scrubPublicText), carrierText?.let(M25PrivacySanitizer::scrubPublicText), now, null, null)
        order.copy(status = M25OrderStatus.SHIPPED, tracking = tracking, updatedAt = now).also { updated ->
            store.orders.value = store.orders.value.map { if (it.id == orderId) updated else it }
            M25MockOrderHelper.appendHistory(store, orderId, order.status, M25OrderStatus.SHIPPED, "MERCHANT")
        }
    }

    override suspend fun markDelivered(orderId: String): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (!ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (order.status == M25OrderStatus.DELIVERED) return@mutate order
        M25OrderOperationsService.validateOrderTransition(order.status, M25OrderStatus.DELIVERED)?.let(::fail)
        M25MockInventoryHelper.commitForLines(store, order.lines)
        val now = System.currentTimeMillis()
        order.copy(status = M25OrderStatus.DELIVERED, tracking = order.tracking?.copy(status = M25OrderStatus.DELIVERED, deliveredAt = now) ?: M25ShippingTracking(M25OrderStatus.DELIVERED, deliveredAt = now), updatedAt = now)
            .also { updated ->
                store.orders.value = store.orders.value.map { if (it.id == orderId) updated else it }
                M25MockOrderHelper.appendHistory(store, orderId, order.status, M25OrderStatus.DELIVERED, "MERCHANT")
            }
    }

    override suspend fun requestReturn(input: RequestM25ReturnInput): Result<M25ReturnRequest> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateReturnReason(input.reason)?.let(::fail)
        input.clientRequestId?.let { key ->
            store.returns.value.firstOrNull { it.orderId == input.orderId && it.id == key }?.let { return@mutate it }
        }
        val order = store.orders.value.firstOrNull { it.id == input.orderId && it.customerUserId == actor } ?: fail("M25_ORDER_NOT_FOUND")
        if (order.status != M25OrderStatus.DELIVERED) fail("M25_INVALID_RETURN_TRANSITION")
        input.lines.forEach { line ->
            val orderLine = order.lines.firstOrNull { it.productId == line.productId } ?: fail("M25_INVALID_RETURN")
            if (line.quantity > orderLine.quantity) fail("M25_INVALID_RETURN")
        }
        val now = System.currentTimeMillis()
        store.orders.value = store.orders.value.map { if (it.id == input.orderId) it.copy(status = M25OrderStatus.RETURN_REQUESTED, updatedAt = now) else it }
        M25ReturnRequest(store.nextId("m25_return"), input.orderId, actor, input.reason.trim(), M25ReturnStatus.REQUESTED, input.lines, now, now)
            .also { store.returns.value += it }
    }

    override suspend fun approveReturn(returnId: String): Result<M25ReturnRequest> = transitionReturn(returnId, M25ReturnStatus.APPROVED, merchant = true)
    override suspend fun rejectReturn(returnId: String, publicReason: String?): Result<M25ReturnRequest> = transitionReturn(returnId, M25ReturnStatus.REJECTED, merchant = true, reason = publicReason)

    override suspend fun markReturnReceived(returnId: String, replenishStock: Boolean): Result<M25ReturnRequest> = mutate {
        val actor = requireActor()
        val ret = store.returns.value.firstOrNull { it.id == returnId } ?: fail("M25_INVALID_RETURN")
        val order = store.orders.value.firstOrNull { it.id == ret.orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (!ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        M25OrderOperationsService.validateReturnTransition(ret.status, M25ReturnStatus.RECEIVED)?.let(::fail)
        if (replenishStock) {
            ret.lines.forEach { line ->
                val snap = M25MockInventoryHelper.snapshot(store, line.productId)
                M25MockInventoryHelper.putSnapshot(store, M25InventoryOperationsService.replenish(snap, line.quantity).getOrThrow())
                M25MockInventoryHelper.recordMovement(store, line.productId, M25StockMovementType.REPLENISH, line.quantity, "RETURN")
            }
        }
        val now = System.currentTimeMillis()
        val updated = ret.copy(status = M25ReturnStatus.RECEIVED, updatedAt = now)
        store.returns.value = store.returns.value.map { if (it.id == returnId) updated else it }
        store.orders.value = store.orders.value.map { if (it.id == order.id) it.copy(status = M25OrderStatus.RETURNED, updatedAt = now) else it }
        updated
    }

    private suspend fun cancelWithRelease(order: M25Order, target: M25OrderStatus, role: String, reason: String?): M25Order {
        if (order.status == target) return order
        M25OrderOperationsService.validateOrderTransition(order.status, target)?.let(::fail)
        if (M25OrderOperationsService.releasesStockOnCancel(order.status)) M25MockInventoryHelper.releaseForLines(store, order.lines)
        val now = System.currentTimeMillis()
        return order.copy(status = target, updatedAt = now).also { updated ->
            store.orders.value = store.orders.value.map { if (it.id == order.id) updated else it }
            M25MockOrderHelper.appendHistory(store, order.id, order.status, target, role, reason)
        }
    }

    private suspend fun transitionOrder(orderId: String, target: M25OrderStatus, merchant: Boolean, role: String): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (merchant && !ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (!merchant && order.customerUserId != actor) fail("M25_PERMISSION_DENIED")
        if (order.status == target) return@mutate order
        M25OrderOperationsService.validateOrderTransition(order.status, target)?.let(::fail)
        order.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.orders.value = store.orders.value.map { if (it.id == orderId) updated else it }
            M25MockOrderHelper.appendHistory(store, orderId, order.status, target, role)
        }
    }

    private suspend fun transitionReturn(returnId: String, target: M25ReturnStatus, merchant: Boolean, reason: String? = null): Result<M25ReturnRequest> = mutate {
        val actor = requireActor()
        val ret = store.returns.value.firstOrNull { it.id == returnId } ?: fail("M25_INVALID_RETURN")
        val order = store.orders.value.firstOrNull { it.id == ret.orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (merchant && !ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (!merchant && ret.customerUserId != actor) fail("M25_PERMISSION_DENIED")
        if (ret.status == target) return@mutate ret
        M25OrderOperationsService.validateReturnTransition(ret.status, target)?.let(::fail)
        ret.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.returns.value = store.returns.value.map { if (it.id == returnId) updated else it }
        }
    }
}

package com.comunidapp.app.data.repository

import com.comunidapp.app.data.model.*
import com.comunidapp.app.domain.m25.M25OrderOperationsService
import com.comunidapp.app.domain.m25.M25PrivacySanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface M25MarketplaceRepository {
    fun observeCatalog(filter: M25CatalogFilter = M25CatalogFilter()): Flow<List<M25PublicShopListing>>
    fun observeShopDetail(shopId: String): Flow<M25PublicShopDetail?>
    fun observeMyShops(): Flow<List<M25Shop>>
    suspend fun createShop(input: CreateM25ShopInput): Result<M25Shop>
    suspend fun updateShop(input: UpdateM25ShopInput): Result<M25Shop>
    suspend fun upsertProduct(input: UpsertM25ProductInput): Result<M25Product>
    suspend fun publishShop(shopId: String): Result<M25Shop>
    suspend fun suspendShop(shopId: String): Result<M25Shop>
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
    suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order>
    suspend fun acceptOrder(orderId: String): Result<M25Order>
    suspend fun cancelOrder(orderId: String, reason: String? = null): Result<M25Order>
    suspend fun markPreparing(orderId: String): Result<M25Order>
    suspend fun markShipped(orderId: String): Result<M25Order>
    suspend fun markDelivered(orderId: String): Result<M25Order>
    suspend fun requestReturn(orderId: String, reason: String): Result<M25ReturnRequest>
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
    val clientRequests = MutableStateFlow<Set<String>>(emptySet())

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
            product(M25MockProductIds.OUT_OF_STOCK, M25MockShopIds.ACTIVE, "ACC-02", "Juguete mordedor", "Juguete resistente para perros.", 5200, 0),
            product("m25_product_draft", M25MockShopIds.DRAFT, "DRAFT-01", "Correa extensible", "Correa retráctil de 5 metros.", 12000, 5)
        )
        promotions.value = listOf(
            M25Promotion("m25_promo_active", M25MockShopIds.ACTIVE, "HUELLA10", M25PromotionType.PERCENTAGE, 10, M25PromotionStatus.ACTIVE, stamp, stamp + 86_400_000)
        )
        cartItems.value = listOf(
            M25CartItem("m25_cart_1", M25MockUsers.CUSTOMER, M25MockProductIds.FOOD_BAG, M25MockShopIds.ACTIVE, 1, "m25_line_seed", stamp)
        )
        orders.value = listOf(
            order(M25MockOrderIds.SUBMITTED, M25MockShopIds.ACTIVE, M25MockUsers.CUSTOMER, M25OrderStatus.SUBMITTED, stamp),
            order(M25MockOrderIds.DELIVERED, M25MockShopIds.ACTIVE, M25MockUsers.CUSTOMER, M25OrderStatus.DELIVERED, stamp)
        )
    }

    private fun shop(id: String, owner: String, name: String, cat: M25ShopCategory, desc: String, city: String, status: M25ShopStatus, stamp: Long) =
        M25Shop(id, owner, if (id == M25MockShopIds.ACTIVE) "mock_org_m03" else null, name, cat, desc, city, status, stamp, stamp)

    private fun product(id: String, shopId: String, sku: String, name: String, desc: String, price: Long, stock: Int) =
        M25Product(id, shopId, sku, name, desc, price, stockQuantity = stock)

    private fun order(id: String, shopId: String, customer: String, status: M25OrderStatus, stamp: Long) = M25Order(
        id, shopId, customer, status,
        listOf(M25OrderLine(M25MockProductIds.FOOD_BAG, "Alimento premium 15kg", 1, 45000)),
        45000, 0, "ARS", M25ShippingMode.DELIVERY, "CABA", null, null, "m25_req_$id", stamp, stamp
    )
}

class MockM25MarketplaceRepository(
    private val actorUserId: () -> String?,
    private val store: M25MarketplaceMemoryStore = M25MarketplaceMemoryStore()
) : M25MarketplaceRepository {
    init { store.seedDefaults() }

    override fun observeCatalog(filter: M25CatalogFilter): Flow<List<M25PublicShopListing>> =
        store.shops.map { shops ->
            shops.filter {
                it.status == M25ShopStatus.ACTIVE &&
                    (filter.category == null || it.category == filter.category) &&
                    (filter.city.isNullOrBlank() || it.city.equals(filter.city.trim(), ignoreCase = true))
            }.map { shop ->
                val prods = productsFor(shop.id).filter { it.status == M25ProductStatus.ACTIVE }
                shop.toPublicListing(prods.size, prods.minByOrNull { it.listPriceCents }?.let { "${it.currency} ${it.listPriceCents}" })
            }
        }

    override fun observeShopDetail(shopId: String): Flow<M25PublicShopDetail?> =
        store.shops.map { shops ->
            shops.firstOrNull { (it.id == shopId || it.displayName == shopId) && it.status == M25ShopStatus.ACTIVE }
                ?.let { shop -> shop.toPublicDetail(productsFor(shop.id).mapNotNull(M25PrivacySanitizer::toPublicProduct)) }
        }

    override fun observeMyShops(): Flow<List<M25Shop>> = store.shops.map { shops ->
        val actor = actorUserId() ?: return@map emptyList()
        shops.filter { it.ownerUserId == actor }
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
            M25MarketplaceValidators.validateShopStatusTransition(shop.status, status, hasActiveProduct())?.let(::fail)
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
            val old = store.products.value.first { it.id == id }
            old.copy(sku = input.sku.trim(), name = input.name.trim(), description = input.description.trim(), listPriceCents = input.listPriceCents, currency = input.currency, stockQuantity = input.stockQuantity, status = input.status)
        } ?: M25Product(store.nextId("m25_product"), input.shopId, input.sku.trim(), input.name.trim(), input.description.trim(), input.listPriceCents, input.currency, input.stockQuantity, input.status)
        store.products.value = store.products.value.filterNot { it.id == product.id } + product
        product
    }

    override suspend fun publishShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.ACTIVE)
    override suspend fun suspendShop(shopId: String): Result<M25Shop> = transitionShop(shopId, M25ShopStatus.SUSPENDED)
    override fun observeNotificationsHook(): Flow<M25NotificationHookState> =
        kotlinx.coroutines.flow.flowOf(M25NotificationHookState())

    private suspend fun transitionShop(shopId: String, target: M25ShopStatus): Result<M25Shop> = mutate {
        val shop = owned(shopId)
        M25MarketplaceValidators.validateShopStatusTransition(shop.status, target, productsFor(shopId).any { it.status == M25ProductStatus.ACTIVE && it.stockQuantity > 0 })?.let(::fail)
        if (shop.status == target) return@mutate shop
        shop.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.shops.value = store.shops.value.map { if (it.id == shopId) updated else it }
        }
    }

    private fun productsFor(shopId: String) = store.products.value.filter { it.shopId == shopId }
    private fun hasActiveProduct() = store.products.value.any { it.status == M25ProductStatus.ACTIVE }
    private fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")
    private fun owned(shopId: String): M25Shop {
        val actor = requireActor()
        val shop = store.shops.value.firstOrNull { it.id == shopId } ?: fail("M25_SHOP_NOT_FOUND")
        if (shop.ownerUserId != actor) fail("M25_PERMISSION_DENIED")
        return shop
    }
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try { Result.success(block()) } catch (e: Throwable) { M25MarketplaceErrors.failure(e) }
    }
    private fun fail(code: String): Nothing = throw M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code))
}

class MockM25CartRepository(
    private val actorUserId: () -> String?,
    private val store: M25MarketplaceMemoryStore
) : M25CartRepository {
    init { store.seedDefaults() }

    override fun observeCart(): Flow<List<M25CartItem>> = store.cartItems.map { items ->
        val actor = actorUserId() ?: return@map emptyList()
        items.filter { it.userId == actor }
    }

    override suspend fun addItem(input: AddM25CartItemInput): Result<M25CartItem> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateCartQuantity(input.quantity)?.let(::fail)
        val product = store.products.value.firstOrNull { it.id == input.productId && it.status == M25ProductStatus.ACTIVE } ?: fail("M25_PRODUCT_NOT_FOUND")
        if (product.stockQuantity < input.quantity) fail("M25_OUT_OF_STOCK")
        input.clientLineId?.let { lineId ->
            store.cartItems.value.firstOrNull { it.userId == actor && it.clientLineId == lineId }?.let { return@mutate it }
        }
        val now = System.currentTimeMillis()
        val item = M25CartItem(store.nextId("m25_cart"), actor, product.id, product.shopId, input.quantity, input.clientLineId, now)
        store.cartItems.value += item
        item
    }

    override suspend fun updateQuantity(cartItemId: String, quantity: Int): Result<M25CartItem> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateCartQuantity(quantity)?.let(::fail)
        val item = store.cartItems.value.firstOrNull { it.id == cartItemId && it.userId == actor } ?: fail("M25_PRODUCT_NOT_FOUND")
        val product = store.products.value.firstOrNull { it.id == item.productId } ?: fail("M25_PRODUCT_NOT_FOUND")
        if (product.stockQuantity < quantity) fail("M25_OUT_OF_STOCK")
        item.copy(quantity = quantity, updatedAt = System.currentTimeMillis()).also { updated ->
            store.cartItems.value = store.cartItems.value.map { if (it.id == cartItemId) updated else it }
        }
    }

    override suspend fun removeItem(cartItemId: String): Result<Unit> = mutate {
        val actor = requireActor()
        store.cartItems.value = store.cartItems.value.filterNot { it.id == cartItemId && it.userId == actor }
        Unit
    }

    override suspend fun clearCart(): Result<Unit> = mutate {
        val actor = requireActor()
        store.cartItems.value = store.cartItems.value.filterNot { it.userId == actor }
        Unit
    }

    private fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try { Result.success(block()) } catch (e: Throwable) { M25MarketplaceErrors.failure(e) }
    }
    private fun fail(code: String): Nothing = throw M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code))
}

class MockM25OrderRepository(
    private val actorUserId: () -> String?,
    private val store: M25MarketplaceMemoryStore
) : M25OrderRepository {
    init { store.seedDefaults() }

    override fun observeMyOrders(): Flow<List<M25OrderSummary>> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map emptyList()
        orders.filter { it.customerUserId == actor }.map { order ->
            val shopName = store.shops.value.firstOrNull { it.id == order.shopId }?.displayName ?: "Tienda"
            M25OrderSummary(order.id, shopName, order.status, order.lines.size, order.subtotalCents, order.currency, order.createdAt)
        }
    }

    override fun observeShopOrders(shopId: String): Flow<List<M25Order>> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map emptyList()
        val shop = store.shops.value.firstOrNull { it.id == shopId && it.ownerUserId == actor } ?: return@map emptyList()
        orders.filter { it.shopId == shop.id && it.status != M25OrderStatus.DRAFT }
    }

    override fun observeOrder(orderId: String): Flow<M25Order?> = store.orders.map { orders ->
        val actor = actorUserId() ?: return@map null
        orders.firstOrNull { it.id == orderId && (it.customerUserId == actor || ownsShop(it.shopId, actor)) }
    }

    override suspend fun submitFromCart(input: SubmitM25OrderInput): Result<M25Order> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateShippingCity(input.shippingCity)?.let(::fail)
        input.clientRequestId?.let { reqId ->
            if (reqId in store.clientRequests.value) fail("M25_DUPLICATE_CLIENT_REQUEST")
        }
        val cart = store.cartItems.value.filter { it.userId == actor && it.shopId == input.shopId }
        if (cart.isEmpty()) fail("M25_CART_EMPTY")
        val lines = cart.mapNotNull { item ->
            val product = store.products.value.firstOrNull { it.id == item.productId } ?: return@mapNotNull null
            if (product.stockQuantity < item.quantity) fail("M25_OUT_OF_STOCK")
            M25OrderLine(product.id, product.name, item.quantity, product.listPriceCents, product.currency)
        }
        if (lines.isEmpty()) fail("M25_CART_EMPTY")
        var subtotal = lines.sumOf { it.unitPriceCents * it.quantity }
        var discount = 0L
        input.promotionCode?.trim()?.uppercase()?.let { code ->
            val promo = store.promotions.value.firstOrNull { it.shopId == input.shopId && it.code == code && it.status == M25PromotionStatus.ACTIVE }
                ?: fail("M25_PROMOTION_INVALID")
            discount = when (promo.type) {
                M25PromotionType.PERCENTAGE -> subtotal * promo.value / 100
                M25PromotionType.FIXED_AMOUNT -> minOf(subtotal, promo.value)
            }
        }
        val now = System.currentTimeMillis()
        val order = M25Order(
            store.nextId("m25_order"), input.shopId, actor, M25OrderStatus.SUBMITTED, lines,
            subtotal, discount, "ARS", input.shippingMode, input.shippingCity.trim(), input.shippingNotes?.trim(),
            input.promotionCode?.trim()?.uppercase(), input.clientRequestId, now, now
        )
        store.orders.value += order
        store.cartItems.value = store.cartItems.value.filterNot { it.userId == actor && it.shopId == input.shopId }
        lines.forEach { line ->
            store.products.value = store.products.value.map { p ->
                if (p.id == line.productId) p.copy(stockQuantity = p.stockQuantity - line.quantity) else p
            }
        }
        input.clientRequestId?.let { store.clientRequests.value = store.clientRequests.value + it }
        order
    }

    override suspend fun acceptOrder(orderId: String): Result<M25Order> = transitionOrder(orderId, M25OrderStatus.ACCEPTED, merchant = true)
    override suspend fun cancelOrder(orderId: String, reason: String?): Result<M25Order> = transitionOrder(orderId, M25OrderStatus.CANCELLED, merchant = false)
    override suspend fun markPreparing(orderId: String): Result<M25Order> = transitionOrder(orderId, M25OrderStatus.PREPARING, merchant = true)
    override suspend fun markShipped(orderId: String): Result<M25Order> = transitionOrder(orderId, M25OrderStatus.SHIPPED, merchant = true)
    override suspend fun markDelivered(orderId: String): Result<M25Order> = transitionOrder(orderId, M25OrderStatus.DELIVERED, merchant = true)

    override suspend fun requestReturn(orderId: String, reason: String): Result<M25ReturnRequest> = mutate {
        val actor = requireActor()
        M25MarketplaceValidators.validateReturnReason(reason)?.let(::fail)
        val order = store.orders.value.firstOrNull { it.id == orderId && it.customerUserId == actor } ?: fail("M25_ORDER_NOT_FOUND")
        M25OrderOperationsService.validateOrderTransition(order.status, M25OrderStatus.RETURN_REQUESTED)?.let(::fail)
        val now = System.currentTimeMillis()
        store.orders.value = store.orders.value.map { if (it.id == orderId) it.copy(status = M25OrderStatus.RETURN_REQUESTED, updatedAt = now) else it }
        M25ReturnRequest(store.nextId("m25_return"), orderId, actor, reason.trim(), M25ReturnStatus.REQUESTED, now, now)
            .also { store.returns.value += it }
    }

    private suspend fun transitionOrder(orderId: String, target: M25OrderStatus, merchant: Boolean): Result<M25Order> = mutate {
        val actor = requireActor()
        val order = store.orders.value.firstOrNull { it.id == orderId } ?: fail("M25_ORDER_NOT_FOUND")
        if (merchant && !ownsShop(order.shopId, actor)) fail("M25_PERMISSION_DENIED")
        if (!merchant && order.customerUserId != actor) fail("M25_PERMISSION_DENIED")
        M25OrderOperationsService.validateOrderTransition(order.status, target)?.let(::fail)
        order.copy(status = target, updatedAt = System.currentTimeMillis()).also { updated ->
            store.orders.value = store.orders.value.map { if (it.id == orderId) updated else it }
        }
    }

    private fun ownsShop(shopId: String, actor: String): Boolean =
        store.shops.value.any { it.id == shopId && it.ownerUserId == actor }

    private fun requireActor(): String = actorUserId() ?: fail("NOT_AUTHENTICATED")
    private suspend fun <T> mutate(block: suspend () -> T): Result<T> = store.withLock {
        try { Result.success(block()) } catch (e: Throwable) { M25MarketplaceErrors.failure(e) }
    }
    private fun fail(code: String): Nothing = throw M25MarketplaceException(code, M25MarketplaceErrors.userMessage(code))
}

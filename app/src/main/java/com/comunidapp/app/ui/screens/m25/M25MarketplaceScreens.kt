package com.comunidapp.app.ui.screens.m25

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.M25PublicShopListing
import com.comunidapp.app.data.model.M25ShopStatus
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.*
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.LeoDimens

@Composable
@Suppress("UNUSED_PARAMETER")
fun M25HubScreen(
    onNavigateBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenManage: () -> Unit,
    viewModel: M25HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Marketplace", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M25HubUiState.Loading -> LoadingState()
                M25HubUiState.Empty -> EmptyState(title = "Sin tiendas", message = "Todavía no hay tiendas disponibles.")
                is M25HubUiState.Error -> ErrorState(message = s.message)
                is M25HubUiState.Content -> {
                    Text("Catálogo local sin cobros.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.shopCount} tiendas disponibles")
                    LeoPrimaryButton(
                        text = "Explorar tiendas",
                        onClick = onOpenCatalog
                    )
                    Text("Checkout, carrito y pedidos están fuera de V1.")
                    LeoOutlinedButton(
                        text = "Gestionar mis tiendas",
                        onClick = onOpenManage
                    )
                }
            }
        }
    }
}

@Composable
fun M25CatalogScreen(onNavigateBack: () -> Unit, onShopClick: (String) -> Unit, viewModel: M25CatalogViewModel = viewModel(factory = M25ViewModelFactories.catalog())) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Tiendas", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25CatalogUiState.Loading -> LoadingState()
                M25CatalogUiState.Empty -> EmptyState(title = "Sin resultados", message = "No hay tiendas activas.")
                is M25CatalogUiState.Error -> ErrorState(message = s.message)
                is M25CatalogUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.displayName }) { M25ShopCard(it, { onShopClick(it.displayName) }) }
                }
            }
        }
    }
}

@Composable
fun M25ShopDetailScreen(shopId: String, onNavigateBack: () -> Unit, viewModel: M25DetailViewModel = viewModel(factory = M25ViewModelFactories.detail(shopId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Tienda", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                M25DetailUiState.Loading -> LoadingState()
                M25DetailUiState.Empty -> EmptyState(title = "No disponible", message = "La tienda no está publicada.")
                is M25DetailUiState.Error -> ErrorState(message = s.message)
                is M25DetailUiState.Content -> {
                    Text(s.shop.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${s.shop.category} · ${s.shop.city}")
                    Text(s.shop.description)
                    Text("Productos", fontWeight = FontWeight.Bold)
                    s.shop.products.forEach { p ->
                        Text("${p.name} · ${p.currency} ${p.listPriceCents}${if (p.inStock) "" else " (sin stock)"}")
                    }
                }
            }
        }
    }
}

@Composable
fun M25CartScreen(onNavigateBack: () -> Unit, viewModel: M25CartViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Carrito", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25CartUiState.Loading -> LoadingState()
                M25CartUiState.Empty -> EmptyState(title = "Carrito vacío", message = "Agregá productos desde una tienda.")
                is M25CartUiState.Error -> ErrorState(message = s.message)
                is M25CartUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.items, key = { it.id }) { item ->
                        Text("Producto ${item.productId} · cantidad ${item.quantity}")
                    }
                }
            }
        }
    }
}

@Composable
fun M25OrdersScreen(onNavigateBack: () -> Unit, onOrderClick: (String) -> Unit = {}, viewModel: M25OrdersViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Mis pedidos", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25OrdersUiState.Loading -> LoadingState()
                M25OrdersUiState.Empty -> EmptyState(title = "Sin pedidos", message = "Todavía no realizaste pedidos.")
                is M25OrdersUiState.Error -> ErrorState(message = s.message)
                is M25OrdersUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.orders, key = { it.id }) { order ->
                        LeoListRow(
                            title = order.shopName,
                            subtitle = "${order.status} · ${order.currency} ${order.subtotalCents}",
                            onClick = { onOrderClick(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun M25ManageScreen(onNavigateBack: () -> Unit, onOpenMerchantOrders: (String) -> Unit = {}, viewModel: M25ManageViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Mis tiendas", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25ManageUiState.Loading -> LoadingState()
                M25ManageUiState.Empty -> EmptyState(title = "Sin tiendas", message = "Creá una tienda para comenzar.")
                is M25ManageUiState.Error -> ErrorState(message = s.message)
                is M25ManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.shops, key = { it.id }) { shop ->
                        Column(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(LeoDimens.SpaceMd)) {
                                Text(shop.displayName, fontWeight = FontWeight.Bold)
                                Text("${shop.status}${if (shop.status == M25ShopStatus.DRAFT) " · publicá con productos activos" else ""}")
                                if (shop.status == M25ShopStatus.ACTIVE || shop.status == M25ShopStatus.PAUSED) {
                                    LeoOutlinedButton(
                                        text = "Pedidos del comercio",
                                        onClick = { onOpenMerchantOrders(shop.id) }
                                    )
                                }
                            }
                            LeoHairline()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M25ShopCard(listing: M25PublicShopListing, onClick: () -> Unit) {
    LeoListRow(
        title = listing.displayName,
        subtitle = buildString {
            append("${listing.category} · ${listing.city}")
            listing.priceSummary?.let { append(" · $it") }
            append(" · ${listing.productCount} productos")
        },
        onClick = onClick
    )
}

@Composable
fun M25MerchantOrdersScreen(shopId: String, onNavigateBack: () -> Unit, viewModel: M25MerchantOrdersViewModel = viewModel(factory = M25ViewModelFactories.merchantOrders(shopId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Pedidos comercio", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25MerchantOrdersUiState.Loading -> LoadingState()
                M25MerchantOrdersUiState.Empty -> EmptyState(title = "Sin pedidos", message = "No hay pedidos para esta tienda.")
                is M25MerchantOrdersUiState.Error -> ErrorState(message = s.message)
                is M25MerchantOrdersUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.orders, key = { it.id }) { order ->
                        LeoListRow(
                            title = "${order.status} · ${order.lines.size} ítems",
                            subtitle = "${order.currency} ${order.subtotalCents}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun M25OrderDetailScreen(orderId: String, onNavigateBack: () -> Unit, viewModel: M25OrderDetailViewModel = viewModel(factory = M25ViewModelFactories.orderDetail(orderId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(containerColor = BrandBackground, topBar = { LeoTopAppBar(title = "Detalle pedido", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                M25OrderDetailUiState.Loading -> LoadingState()
                M25OrderDetailUiState.Empty -> EmptyState(title = "No encontrado", message = "El pedido no está disponible.")
                is M25OrderDetailUiState.Error -> ErrorState(message = s.message)
                is M25OrderDetailUiState.Content -> {
                    Text("Estado: ${s.order.status}", fontWeight = FontWeight.Bold)
                    Text("Subtotal operativo: ${s.order.currency} ${s.order.subtotalCents}")
                    Text("Pago no gestionado por LeoVer", color = MaterialTheme.colorScheme.primary)
                    s.order.lines.forEach { line -> Text("${line.productName} x${line.quantity}") }
                }
            }
        }
    }
}

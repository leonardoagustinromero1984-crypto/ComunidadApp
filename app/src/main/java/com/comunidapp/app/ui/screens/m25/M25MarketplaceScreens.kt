package com.comunidapp.app.ui.screens.m25

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.*

@Composable
fun M25HubScreen(
    onNavigateBack: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenManage: () -> Unit,
    viewModel: M25HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Marketplace", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M25HubUiState.Loading -> LoadingState()
                M25HubUiState.Empty -> EmptyState(title = "Sin tiendas", message = "Todavía no hay tiendas disponibles.")
                is M25HubUiState.Error -> ErrorState(message = s.message)
                is M25HubUiState.Content -> {
                    Text("LeoVer M25 · Catálogo local sin cobros.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.shopCount} tiendas disponibles")
                    Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) { Text("Explorar tiendas") }
                    OutlinedButton(onClick = onOpenCart, modifier = Modifier.fillMaxWidth()) { Text("Mi carrito") }
                    OutlinedButton(onClick = onOpenOrders, modifier = Modifier.fillMaxWidth()) { Text("Mis pedidos") }
                    OutlinedButton(onClick = onOpenManage, modifier = Modifier.fillMaxWidth()) { Text("Gestionar mis tiendas") }
                }
            }
        }
    }
}

@Composable
fun M25CatalogScreen(onNavigateBack: () -> Unit, onShopClick: (String) -> Unit, viewModel: M25CatalogViewModel = viewModel(factory = M25ViewModelFactories.catalog())) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Tiendas", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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
    Scaffold(topBar = { ComunidappTopBar(title = "Tienda", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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
    Scaffold(topBar = { ComunidappTopBar(title = "Carrito", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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
    Scaffold(topBar = { ComunidappTopBar(title = "Mis pedidos", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25OrdersUiState.Loading -> LoadingState()
                M25OrdersUiState.Empty -> EmptyState(title = "Sin pedidos", message = "Todavía no realizaste pedidos.")
                is M25OrdersUiState.Error -> ErrorState(message = s.message)
                is M25OrdersUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.orders, key = { it.id }) { order ->
                        Card(Modifier.fillMaxWidth().clickable { onOrderClick(order.id) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${order.shopName} · ${order.status}")
                                Text("${order.currency} ${order.subtotalCents}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M25ManageScreen(onNavigateBack: () -> Unit, onOpenMerchantOrders: (String) -> Unit = {}, viewModel: M25ManageViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Mis tiendas", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25ManageUiState.Loading -> LoadingState()
                M25ManageUiState.Empty -> EmptyState(title = "Sin tiendas", message = "Creá una tienda para comenzar.")
                is M25ManageUiState.Error -> ErrorState(message = s.message)
                is M25ManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.shops, key = { it.id }) { shop ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(shop.displayName, fontWeight = FontWeight.Bold)
                                Text("${shop.status}${if (shop.status == M25ShopStatus.DRAFT) " · publicá con productos activos" else ""}")
                                if (shop.status == M25ShopStatus.ACTIVE || shop.status == M25ShopStatus.PAUSED) {
                                    OutlinedButton(onClick = { onOpenMerchantOrders(shop.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Pedidos del comercio")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M25ShopCard(listing: M25PublicShopListing, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(listing.displayName, fontWeight = FontWeight.Bold)
            Text("${listing.category} · ${listing.city}")
            listing.priceSummary?.let { Text(it) }
            Text("${listing.productCount} productos")
        }
    }
}

@Composable
fun M25MerchantOrdersScreen(shopId: String, onNavigateBack: () -> Unit, viewModel: M25MerchantOrdersViewModel = viewModel(factory = M25ViewModelFactories.merchantOrders(shopId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Pedidos comercio", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M25MerchantOrdersUiState.Loading -> LoadingState()
                M25MerchantOrdersUiState.Empty -> EmptyState(title = "Sin pedidos", message = "No hay pedidos para esta tienda.")
                is M25MerchantOrdersUiState.Error -> ErrorState(message = s.message)
                is M25MerchantOrdersUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.orders, key = { it.id }) { order ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${order.status} · ${order.lines.size} ítems")
                                Text("${order.currency} ${order.subtotalCents}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun M25OrderDetailScreen(orderId: String, onNavigateBack: () -> Unit, viewModel: M25OrderDetailViewModel = viewModel(factory = M25ViewModelFactories.orderDetail(orderId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Detalle pedido", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
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

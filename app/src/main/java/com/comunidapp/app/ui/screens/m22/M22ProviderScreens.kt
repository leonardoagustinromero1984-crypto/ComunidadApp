package com.comunidapp.app.ui.screens.m22

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
import com.comunidapp.app.data.model.M22ProviderStatus
import com.comunidapp.app.data.model.M22PublicProviderListing
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M22CatalogUiState
import com.comunidapp.app.viewmodel.M22CatalogViewModel
import com.comunidapp.app.viewmodel.M22DetailUiState
import com.comunidapp.app.viewmodel.M22DetailViewModel
import com.comunidapp.app.viewmodel.M22HubUiState
import com.comunidapp.app.viewmodel.M22HubViewModel
import com.comunidapp.app.viewmodel.M22ManageUiState
import com.comunidapp.app.viewmodel.M22ManageViewModel
import com.comunidapp.app.viewmodel.M22ViewModelFactories

@Composable
fun M22HubScreen(onNavigateBack: () -> Unit, onOpenCatalog: () -> Unit, onOpenManage: () -> Unit, viewModel: M22HubViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Prestadores y servicios", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M22HubUiState.Loading -> LoadingState()
                M22HubUiState.Empty -> EmptyState(title = "Sin prestadores", message = "Todavía no hay prestadores disponibles.")
                is M22HubUiState.Error -> ErrorState(message = s.message)
                is M22HubUiState.Content -> {
                    Text("LeoVer M22 · Catálogo local de servicios.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.providerCount} prestadores disponibles")
                    Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) { Text("Explorar catálogo") }
                    OutlinedButton(onClick = onOpenManage, modifier = Modifier.fillMaxWidth()) { Text("Gestionar mis prestadores") }
                }
            }
        }
    }
}

@Composable
fun M22CatalogScreen(onNavigateBack: () -> Unit, onProviderClick: (String) -> Unit, viewModel: M22CatalogViewModel = viewModel(factory = M22ViewModelFactories.catalog())) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Catálogo de servicios", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M22CatalogUiState.Loading -> LoadingState()
                M22CatalogUiState.Empty -> EmptyState(title = "Sin resultados", message = "No hay prestadores activos para mostrar.")
                is M22CatalogUiState.Error -> ErrorState(message = s.message)
                is M22CatalogUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.items, key = { it.displayName }) { M22ProviderCard(it, { onProviderClick(it.displayName) }) }
                }
            }
        }
    }
}

@Composable
fun M22ProviderDetailScreen(providerId: String, onNavigateBack: () -> Unit, viewModel: M22DetailViewModel = viewModel(factory = M22ViewModelFactories.detail(providerId))) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Prestador", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val s = state) {
                M22DetailUiState.Loading -> LoadingState()
                M22DetailUiState.Empty -> EmptyState(title = "No disponible", message = "El prestador no está publicado.")
                is M22DetailUiState.Error -> ErrorState(message = s.message)
                is M22DetailUiState.Content -> {
                    Text(s.provider.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${s.provider.category} · ${s.provider.city}")
                    Text(s.provider.description)
                    Text("Sedes", fontWeight = FontWeight.Bold)
                    s.provider.branches.forEach { Text("${it.name} · ${it.coverage}") }
                    Text("Servicios", fontWeight = FontWeight.Bold)
                    s.provider.offerings.forEach { Text("${it.name} · ${it.priceType}${it.priceAmount?.let { amount -> " ARS $amount" }.orEmpty()}") }
                }
            }
        }
    }
}

@Composable
fun M22ManageScreen(onNavigateBack: () -> Unit, viewModel: M22ManageViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Mis prestadores", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (val s = state) {
                M22ManageUiState.Loading -> LoadingState()
                M22ManageUiState.Empty -> EmptyState(title = "Sin prestadores", message = "Creá un prestador para comenzar.")
                is M22ManageUiState.Error -> ErrorState(message = s.message)
                is M22ManageUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.providers, key = { it.id }) { provider ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(provider.displayName, fontWeight = FontWeight.Bold)
                            Text("${provider.category} · ${provider.status}")
                            when (provider.status) {
                                M22ProviderStatus.DRAFT -> Button(
                                    onClick = { viewModel.publish(provider.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Publicar") }
                                M22ProviderStatus.ACTIVE -> OutlinedButton(
                                    onClick = { viewModel.suspend(provider.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Suspender") }
                                M22ProviderStatus.SUSPENDED -> Button(
                                    onClick = { viewModel.publish(provider.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Reactivar") }
                                M22ProviderStatus.ARCHIVED -> Unit
                            }
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun M22ProviderCard(item: M22PublicProviderListing, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.displayName, fontWeight = FontWeight.Bold)
            Text("${item.category} · ${item.city}")
            Text(item.description, maxLines = 2)
            item.priceSummary?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

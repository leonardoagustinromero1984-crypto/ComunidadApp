package com.comunidapp.shared.poc.m22.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.comunidapp.shared.poc.m22.data.M22PocCatalogRepository
import com.comunidapp.shared.poc.m22.model.M22PocListing
import com.comunidapp.shared.poc.m22.viewmodel.M22PocCatalogUiState
import com.comunidapp.shared.poc.m22.viewmodel.M22PocCatalogViewModel
import com.comunidapp.shared.poc.m22.viewmodel.M22PocDetailUiState
import com.comunidapp.shared.poc.m22.viewmodel.M22PocDetailViewModel

/** Minimal shared navigation for POC only — does not replace ComunidappNavGraph. */
sealed class M22PocRoute {
    data object List : M22PocRoute()
    data class Detail(val providerId: String) : M22PocRoute()
}

@Composable
fun M22PocApp(
    repository: M22PocCatalogRepository,
    onClose: (() -> Unit)? = null
) {
    val nav = remember { androidx.compose.runtime.mutableStateOf<M22PocRoute>(M22PocRoute.List) }
    when (val route = nav.value) {
        M22PocRoute.List -> M22PocCatalogScreen(
            repository = repository,
            onOpenDetail = { nav.value = M22PocRoute.Detail(it) },
            onClose = onClose
        )
        is M22PocRoute.Detail -> M22PocDetailScreen(
            providerId = route.providerId,
            repository = repository,
            onBack = { nav.value = M22PocRoute.List }
        )
    }
}

@Composable
fun M22PocCatalogScreen(
    repository: M22PocCatalogRepository,
    onOpenDetail: (String) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val viewModel = remember(repository) { M22PocCatalogViewModel(repository) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "LeoVer KMP POC — Catálogo M22",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Compose Multiplatform compartido (lista → detalle).",
                color = MaterialTheme.colorScheme.primary
            )
            if (onClose != null) {
                TextButton(onClick = onClose) { Text("Cerrar POC") }
            }
            when (val s = state) {
                M22PocCatalogUiState.Loading -> PocLoading()
                M22PocCatalogUiState.Empty -> PocEmpty("Sin resultados", "No hay prestadores activos.")
                is M22PocCatalogUiState.Error -> PocError(s.message)
                is M22PocCatalogUiState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(s.items, key = { it.id }) { item ->
                        M22PocProviderCard(item) { onOpenDetail(item.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun M22PocDetailScreen(
    providerId: String,
    repository: M22PocCatalogRepository,
    onBack: () -> Unit
) {
    val viewModel = remember(providerId, repository) { M22PocDetailViewModel(providerId, repository) }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }
    val state by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Volver al catálogo") }
            when (val s = state) {
                M22PocDetailUiState.Loading -> PocLoading()
                M22PocDetailUiState.Empty -> PocEmpty("No disponible", "El prestador no está publicado.")
                is M22PocDetailUiState.Error -> PocError(s.message)
                is M22PocDetailUiState.Content -> {
                    Text(s.provider.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${s.provider.category} · ${s.provider.city}")
                    Text(s.provider.description)
                    Text("Sedes", fontWeight = FontWeight.Bold)
                    s.provider.branches.forEach { Text("${it.name} · ${it.coverage}") }
                    Text("Servicios", fontWeight = FontWeight.Bold)
                    s.provider.offerings.forEach {
                        Text(
                            "${it.name} · ${it.priceType}" +
                                (it.priceAmount?.let { amount -> " ${it.currency} $amount" }.orEmpty())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun M22PocProviderCard(item: M22PocListing, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.displayName, fontWeight = FontWeight.Bold)
            Text("${item.category} · ${item.city}")
            Text(item.description)
            item.priceSummary?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun PocLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { CircularProgressIndicator() }
}

@Composable
private fun PocEmpty(title: String, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(message)
    }
}

@Composable
private fun PocError(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Text(message)
        Button(onClick = {}) { Text("Reintentar (reiniciá la pantalla)") }
    }
}

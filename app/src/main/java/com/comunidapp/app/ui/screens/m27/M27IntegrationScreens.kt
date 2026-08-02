package com.comunidapp.app.ui.screens.m27

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.comunidapp.app.ui.components.ComunidappTopBar
import com.comunidapp.app.ui.components.state.EmptyState
import com.comunidapp.app.ui.components.state.ErrorState
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.viewmodel.M27ApiKeysViewModel
import com.comunidapp.app.viewmodel.M27ContractsViewModel
import com.comunidapp.app.viewmodel.M27HubUiState
import com.comunidapp.app.viewmodel.M27HubViewModel
import com.comunidapp.app.viewmodel.M27ListUiState
import com.comunidapp.app.viewmodel.M27OAuthViewModel
import com.comunidapp.app.viewmodel.M27RateLimitsViewModel
import com.comunidapp.app.viewmodel.M27WebhooksViewModel

@Composable
fun M27HubScreen(
    onNavigateBack: () -> Unit,
    onOpenWebhooks: () -> Unit,
    onOpenOAuth: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenContracts: () -> Unit,
    onOpenRateLimits: () -> Unit,
    viewModel: M27HubViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = { ComunidappTopBar(title = "Integraciones y API", showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val s = state) {
                M27HubUiState.Loading -> LoadingState()
                M27HubUiState.Empty -> EmptyState(title = "Sin integraciones", message = "Registrá webhooks, OAuth o claves API para empezar.")
                is M27HubUiState.Error -> ErrorState(message = s.message)
                is M27HubUiState.Content -> {
                    Text("LeoVer M27 · Webhooks, OAuth stub, límites y contratos versionados.", color = MaterialTheme.colorScheme.primary)
                    Text("${s.webhookCount} webhooks · ${s.oauthCount} apps OAuth · ${s.keyCount} claves · ${s.contractCount} contratos publicados")
                    Button(onClick = onOpenWebhooks, modifier = Modifier.fillMaxWidth()) { Text("Webhooks") }
                    OutlinedButton(onClick = onOpenOAuth, modifier = Modifier.fillMaxWidth()) { Text("Aplicaciones OAuth (stub)") }
                    OutlinedButton(onClick = onOpenApiKeys, modifier = Modifier.fillMaxWidth()) { Text("Claves API") }
                    OutlinedButton(onClick = onOpenContracts, modifier = Modifier.fillMaxWidth()) { Text("Contratos publicados") }
                    OutlinedButton(onClick = onOpenRateLimits, modifier = Modifier.fillMaxWidth()) { Text("Límites y sandbox") }
                }
            }
        }
    }
}

@Composable
private fun <T> M27ListScreen(
    title: String,
    onNavigateBack: () -> Unit,
    state: M27ListUiState<T>,
    itemLabel: (T) -> String,
    itemDetail: (T) -> String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Scaffold(topBar = { ComunidappTopBar(title = title, showBackButton = true, onBackClick = onNavigateBack) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            when (state) {
                M27ListUiState.Loading -> LoadingState()
                M27ListUiState.Empty -> EmptyState(title = "Sin registros", message = "No hay elementos para mostrar.")
                is M27ListUiState.Error -> ErrorState(message = state.message)
                is M27ListUiState.Content -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.items.size) { index ->
                            val item = state.items[index]
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(itemLabel(item), fontWeight = FontWeight.SemiBold)
                                    Text(itemDetail(item), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun M27WebhooksScreen(onNavigateBack: () -> Unit, viewModel: M27WebhooksViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M27ListScreen(
        title = "Webhooks",
        onNavigateBack = onNavigateBack,
        state = state,
        itemLabel = { "${it.label} (${it.environment})" },
        itemDetail = { "${it.targetUrl} · ${it.secretPrefix}*** · ${it.status}" },
        actionLabel = "Registrar webhook demo (sandbox)",
        onAction = viewModel::registerDemoWebhook
    )
}

@Composable
fun M27OAuthScreen(onNavigateBack: () -> Unit, viewModel: M27OAuthViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M27ListScreen(
        title = "OAuth (stub)",
        onNavigateBack = onNavigateBack,
        state = state,
        itemLabel = { "${it.name} (${it.environment})" },
        itemDetail = { "${it.redirectUri} · ${it.clientIdPrefix}*** · scopes: ${it.scopes.joinToString()}" },
        actionLabel = "Registrar app demo (sandbox)",
        onAction = viewModel::registerDemoApp
    )
}

@Composable
fun M27ApiKeysScreen(onNavigateBack: () -> Unit, viewModel: M27ApiKeysViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M27ListScreen(
        title = "Claves API",
        onNavigateBack = onNavigateBack,
        state = state,
        itemLabel = { "${it.label} (${it.environment})" },
        itemDetail = { "${it.keyPrefix}*** · ${it.status} · scopes: ${it.scopes.joinToString()}" }
    )
}

@Composable
fun M27ContractsScreen(onNavigateBack: () -> Unit, viewModel: M27ContractsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M27ListScreen(
        title = "Contratos API",
        onNavigateBack = onNavigateBack,
        state = state,
        itemLabel = { "${it.title} · ${it.version}" },
        itemDetail = { it.summary }
    )
}

@Composable
fun M27RateLimitsScreen(onNavigateBack: () -> Unit, viewModel: M27RateLimitsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    M27ListScreen(
        title = "Límites y sandbox",
        onNavigateBack = onNavigateBack,
        state = state,
        itemLabel = { it.environment.name },
        itemDetail = { "${it.requestsPerMinute}/min · ${it.requestsPerDay}/día" }
    )
}

package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.MyModerationAppealsViewModel

@Composable
fun MyModerationAppealsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MyModerationAppealsViewModel = viewModel(factory = MyModerationAppealsViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    AdministrativePhaseHost(
        title = "Mis apelaciones",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        emptyTitle = "Sin apelaciones",
        emptyMessage = "Podés presentar una apelación si tenés el id de la medida.",
        errorMessage = uiState.errorMessage ?: "No pudimos cargar tus apelaciones.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SnackbarHost(snackbar) }
            item {
                OutlinedTextField(
                    value = uiState.submitActionId,
                    onValueChange = viewModel::onActionIdChange,
                    label = { Text("Id de medida") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.statement,
                    onValueChange = viewModel::onStatementChange,
                    label = { Text("Declaración") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(onClick = { viewModel.submitAppeal() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enviar apelación")
                }
            }
            items(uiState.appeals, key = { it.id }) { a ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(a.status.name, style = MaterialTheme.typography.titleMedium)
                        Text(a.statement.take(160), style = MaterialTheme.typography.bodySmall)
                        a.decisionReason?.let {
                            Text("Decisión: $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

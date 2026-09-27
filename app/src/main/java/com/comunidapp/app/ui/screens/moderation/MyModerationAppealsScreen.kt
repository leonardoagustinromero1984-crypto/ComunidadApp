package com.comunidapp.app.ui.screens.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.ui.components.leo.LeoListRow
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.theme.LeoDimens
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
            contentPadding = PaddingValues(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
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
                LeoPrimaryButton(
                    text = "Enviar apelación",
                    onClick = { viewModel.submitAppeal() }
                )
            }
            items(uiState.appeals, key = { it.id }) { a ->
                LeoListRow(
                    title = a.status.name,
                    subtitle = buildString {
                        append(a.statement.take(160))
                        a.decisionReason?.let { append(" · Decisión: $it") }
                    }
                )
            }
        }
    }
}

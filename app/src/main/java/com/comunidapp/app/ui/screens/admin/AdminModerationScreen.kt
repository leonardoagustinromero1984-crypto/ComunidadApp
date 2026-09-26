package com.comunidapp.app.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.comunidapp.app.data.model.ContentReport
import com.comunidapp.app.ui.components.leo.LeoHairline
import com.comunidapp.app.ui.components.leo.LeoTopAppBar
import com.comunidapp.app.ui.components.state.LoadingState
import com.comunidapp.app.ui.theme.BrandBackground
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.viewmodel.AdminModerationViewModel

@Composable
fun AdminModerationScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminModerationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(uiState.accessChecked, uiState.accessAllowed) {
        if (uiState.accessChecked && !uiState.accessAllowed) {
            onNavigateBack()
        }
    }

    Scaffold(
        containerColor = BrandBackground,
        topBar = {
            LeoTopAppBar(
                title = "Moderación",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            !uiState.accessChecked -> {
                LoadingState(contentModifier = Modifier.padding(padding))
            }
            !uiState.accessAllowed -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tenés permiso para ver moderación.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.reports.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay reportes abiertos en LeoVer",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            canManage = uiState.canManageReports,
                            onDismiss = { viewModel.dismissReport(report.id) },
                            onAction = { viewModel.actionReport(report.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: ContentReport,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LeoDimens.SpaceMd)) {
            Text(
                text = targetLabel(report.targetType),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Motivo: ${report.reason}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Estado: ${statusLabel(report.status)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (canManage) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Desestimar") }
                    TextButton(onClick = onAction) { Text("Marcar para acción") }
                }
            }
        }
        LeoHairline()
    }
}

private fun targetLabel(type: com.comunidapp.app.data.model.ReportTargetType): String =
    when (type) {
        com.comunidapp.app.data.model.ReportTargetType.USER -> "Perfil"
        com.comunidapp.app.data.model.ReportTargetType.COMMENT -> "Comentario"
        com.comunidapp.app.data.model.ReportTargetType.POST -> "Publicación"
    }

private fun statusLabel(status: com.comunidapp.app.data.model.ReportStatus): String =
    when (status) {
        com.comunidapp.app.data.model.ReportStatus.OPEN -> "Abierto"
        com.comunidapp.app.data.model.ReportStatus.REVIEWED -> "Revisado"
        com.comunidapp.app.data.model.ReportStatus.DISMISSED -> "Desestimado"
        com.comunidapp.app.data.model.ReportStatus.ACTIONED -> "Marcado para acción"
    }

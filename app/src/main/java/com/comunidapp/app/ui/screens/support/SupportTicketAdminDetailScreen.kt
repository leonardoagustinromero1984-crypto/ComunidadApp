package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.comunidapp.app.data.provider.DataProvider
import com.comunidapp.app.domain.support.SupportTicketStatus
import com.comunidapp.app.ui.files.FileUploadProgressSection
import com.comunidapp.app.ui.files.PdfOrImageMimeTypes
import com.comunidapp.app.ui.files.rememberPdfOrImageDocumentPicker
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.moderation.SensitiveDataPresentation
import com.comunidapp.app.viewmodel.support.SupportTicketAdminDetailViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SupportTicketAdminDetailScreen(
    ticketId: String,
    onNavigateBack: () -> Unit,
    viewModel: SupportTicketAdminDetailViewModel = viewModel(
        factory = SupportTicketAdminDetailViewModel.factory(ticketId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by DataProvider.fileUploadCoordinator.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val visibleAttachmentPicker = rememberPdfOrImageDocumentPicker { uri ->
        uri?.let { viewModel.attachFile(it, internal = false) }
    }
    val internalAttachmentPicker = rememberPdfOrImageDocumentPicker { uri ->
        uri?.let { viewModel.attachFile(it, internal = true) }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    AdministrativePhaseHost(
        title = "Ticket staff",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar el ticket.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val ticket = uiState.ticket
        LazyColumn(
            modifier = contentModifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SnackbarHost(snackbar) }
            if (ticket != null) {
                item {
                    Text(ticket.subject, style = MaterialTheme.typography.titleMedium)
                    Text("Estado: ${ticket.status}")
                }
            }
            items(uiState.messages, key = { it.id }) { m ->
                val internal = SensitiveDataPresentation.isInternalMessage(m)
                Text(
                    if (internal) "[INTERNO] ${m.body}" else m.body,
                    color = if (internal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            if (uiState.canManage) {
                item {
                    Button(onClick = { viewModel.assignToMe() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Asignarme")
                    }
                }
                item {
                    OutlinedTextField(
                        value = uiState.draft,
                        onValueChange = viewModel::onDraftChange,
                        label = { Text("Respuesta visible") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(onClick = { viewModel.sendVisibleMessage() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Enviar respuesta")
                    }
                    Button(
                        onClick = { visibleAttachmentPicker.launch(PdfOrImageMimeTypes) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Adjuntar a respuesta") }
                }
                item {
                    OutlinedTextField(
                        value = uiState.internalDraft,
                        onValueChange = viewModel::onInternalDraftChange,
                        label = { Text("Nota interna") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Button(onClick = { viewModel.sendInternalNote() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar nota interna")
                    }
                    if (uiState.canViewSensitive) {
                        Button(
                            onClick = { internalAttachmentPicker.launch(PdfOrImageMimeTypes) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Adjuntar solo para staff") }
                    }
                    FileUploadProgressSection(
                        state = uploadState,
                        onCancel = {
                            uploadState.sessionId?.let { id ->
                                scope.launch { DataProvider.fileUploadCoordinator.cancel(id) }
                            }
                        },
                        onRetry = {
                            scope.launch { DataProvider.fileUploadCoordinator.retry() }
                        }
                    )
                }
                item {
                    Button(
                        onClick = {
                            viewModel.changeStatus(SupportTicketStatus.RESOLVED)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Resolver") }
                }
                item {
                    Button(
                        onClick = {
                            viewModel.changeStatus(SupportTicketStatus.CLOSED, "resolved_ok")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (uiState.confirmClose) "Confirmar cierre" else "Cerrar") }
                }
            }
        }
    }
}

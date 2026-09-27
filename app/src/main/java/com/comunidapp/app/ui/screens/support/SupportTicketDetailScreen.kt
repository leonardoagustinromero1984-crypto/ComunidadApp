package com.comunidapp.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.comunidapp.app.ui.files.FileUploadProgressSection
import com.comunidapp.app.ui.files.PdfOrImageMimeTypes
import com.comunidapp.app.ui.files.rememberPdfOrImageDocumentPicker
import com.comunidapp.app.ui.components.leo.LeoOutlinedButton
import com.comunidapp.app.ui.components.leo.LeoPrimaryButton
import com.comunidapp.app.ui.components.leo.LeoTextField
import com.comunidapp.app.ui.screens.moderation.AdministrativePhaseHost
import com.comunidapp.app.ui.theme.BrandText
import com.comunidapp.app.ui.theme.BrandTextSecondary
import com.comunidapp.app.ui.theme.LeoBody
import com.comunidapp.app.ui.theme.LeoCaption
import com.comunidapp.app.ui.theme.LeoDimens
import com.comunidapp.app.ui.theme.LeoSectionTitle
import com.comunidapp.app.viewmodel.moderation.AdministrativeScreenPhase
import com.comunidapp.app.viewmodel.support.SupportTicketDetailViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SupportTicketDetailScreen(
    ticketId: String,
    onNavigateBack: () -> Unit,
    viewModel: SupportTicketDetailViewModel = viewModel(
        factory = SupportTicketDetailViewModel.factory(ticketId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val uploadState by DataProvider.fileUploadCoordinator.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val attachmentPicker = rememberPdfOrImageDocumentPicker { uri ->
        uri?.let(viewModel::attachFile)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.phase) {
        if (uiState.phase == AdministrativeScreenPhase.AccessDenied) onNavigateBack()
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    AdministrativePhaseHost(
        title = "Ticket",
        phase = uiState.phase,
        onNavigateBack = onNavigateBack,
        errorMessage = uiState.errorMessage ?: "No pudimos cargar el ticket.",
        onRetry = { viewModel.refresh() }
    ) { contentModifier ->
        val ticket = uiState.ticket
        LazyColumn(
            modifier = contentModifier.fillMaxSize().padding(LeoDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(LeoDimens.SpaceCompact)
        ) {
            item { SnackbarHost(snackbar) }
            if (ticket != null) {
                item {
                    Text(ticket.subject, style = LeoSectionTitle, color = BrandText)
                    Text("Estado: ${ticket.status}", style = LeoCaption, color = BrandTextSecondary)
                    Text(ticket.description, style = LeoBody, color = BrandText)
                }
            }
            items(uiState.messages, key = { it.id }) { m ->
                Text(m.body, style = LeoBody, color = BrandText)
            }
            item {
                LeoTextField(
                    value = uiState.draft,
                    onValueChange = viewModel::onDraftChange,
                    label = "Responder",
                    singleLine = false,
                    minLines = 2
                )
            }
            item {
                LeoOutlinedButton(
                    text = "Adjuntar archivo",
                    onClick = { attachmentPicker.launch(PdfOrImageMimeTypes) },
                    enabled = uiState.phase != AdministrativeScreenPhase.Submitting
                )
                FileUploadProgressSection(
                    state = uploadState,
                    onCancel = {
                        uploadState.sessionId?.let { id ->
                            scope.launch { DataProvider.fileUploadCoordinator.cancel(id) }
                        }
                    },
                    onRetry = { scope.launch { DataProvider.fileUploadCoordinator.retry() } }
                )
                LeoPrimaryButton(
                    text = "Enviar mensaje",
                    onClick = { viewModel.sendMessage() },
                    enabled = uiState.phase != AdministrativeScreenPhase.Submitting
                )
            }
        }
    }
}
